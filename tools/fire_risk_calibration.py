#!/usr/bin/env python3
"""Where the fire-risk thresholds in FireRisk.kt come from.

Run it and it prints the numbers that are hard-coded in
`core/weather/FireRisk.kt`, plus the evidence for why the published thresholds
of two well-known fire indices were not used as they stand.

    python3 tools/fire_risk_calibration.py [saved-response.json]

It needs nothing but a network connection: Open-Meteo's archive endpoint is
free and keyless, the same as the forecast endpoint the app itself calls. Pass
a file to read a previously saved response instead — the archive rate-limits
anonymous callers, and re-running this a few times in an afternoon will hit it.

The short version of what it shows, for this village, over 2023-2025:

  * the Chandler Burning Index with its published bands calls about 70% of
    fire-season days "low";
  * the Angstrom index with its published bands calls about 44% of them
    "extreme";
  * neither is wrong as an index — both are calibrated to climates that are
    not Attica — so the app keeps Angstrom as the measure and replaces its
    thresholds with percentiles of this location's own fire-season days.
"""

import collections
import json
import sys
import urllib.request

LAT, LON, ELEVATION = 38.164715, 23.292164, 640
START, END = "2023-01-01", "2025-12-31"

# The percentiles the five levels are cut at, and the level names they produce.
PERCENTILES = (40, 70, 88, 97)
LEVELS = ("LOW", "MODERATE", "HIGH", "VERY_HIGH", "EXTREME")

# Must match FireRisk.kt and OpenMeteo.kt.
WINDY_BEAUFORT = 5
GUSTY_BEAUFORT = 8
DRY_SPELL_DAYS = 20
WET_DAY_MM = 1.0
SOAKING_MM = 5.0
# The history window the app actually asks the forecast for. The dry-day count
# cannot exceed it, so neither may this model.
PAST_DAYS = 31

BEAUFORT_UPPER_KMH = (1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118)


def beaufort(kmh):
    for step, upper in enumerate(BEAUFORT_UPPER_KMH):
        if kmh < upper:
            return step
    return 12


def angstrom(t, rh):
    """Lower is worse."""
    return rh / 20.0 + (27.0 - t) / 10.0


def chandler(t, rh):
    """Higher is worse."""
    return (((110 - 1.373 * rh) - 0.54 * (10.20 - t)) * (124 * 10 ** (-0.0142 * rh))) / 60


def fetch():
    if len(sys.argv) > 1:
        with open(sys.argv[1], encoding="utf-8") as saved:
            return json.load(saved)
    url = (
        "https://archive-api.open-meteo.com/v1/archive"
        f"?latitude={LAT}&longitude={LON}&elevation={ELEVATION}"
        f"&start_date={START}&end_date={END}"
        "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,wind_gusts_10m,precipitation"
        "&timezone=Europe%2FAthens"
    )
    with urllib.request.urlopen(url, timeout=120) as response:
        return json.load(response)


def main():
    hourly = fetch()["hourly"]

    # The app assesses the *worst hour of the day*, so the calibration has to
    # measure the same quantity. An earlier version of this script sampled
    # 13:00 only — which is how the Angstrom index is classically defined — and
    # so described a rule the app does not implement.
    rain_by_day = collections.defaultdict(float)
    # Rain accumulated up to and including each hour, so the model can relieve
    # on what had actually fallen by the hour it assesses rather than on the
    # day's forecast total. The app makes the same distinction.
    rain_so_far = {}
    # The strongest wind anywhere in the day: the permit threshold is a
    # statement about a day, not about the hour the index happens to pick.
    day_wind = collections.defaultdict(float)
    day_gust = collections.defaultdict(float)
    worst = {}
    for i, stamp in enumerate(hourly["time"]):
        day, _ = stamp.split("T")
        mm = hourly["precipitation"][i]
        if mm is not None:
            rain_by_day[day] += mm
        rain_so_far[stamp] = rain_by_day[day]
        day_wind[day] = max(day_wind[day], hourly["wind_speed_10m"][i] or 0.0)
        day_gust[day] = max(day_gust[day], hourly["wind_gusts_10m"][i] or 0.0)
        t, rh = hourly["temperature_2m"][i], hourly["relative_humidity_2m"][i]
        if t is None or rh is None:
            continue
        index = angstrom(t, rh)
        current = worst.get(day)
        if current is None or index < current[0]:
            worst[day] = (index, t, rh, stamp)

    days = sorted(worst)

    # Completed dry days before each day, capped at the app's own window.
    dry_before, run = {}, 0
    for day in days:
        dry_before[day] = min(run, PAST_DAYS)
        run = 0 if rain_by_day[day] >= WET_DAY_MM else run + 1

    season = [d for d in days if 5 <= int(d[5:7]) <= 10]
    print(f"{len(days)} days, {len(season)} of them in the fire season\n")

    # --- why the published bands were not used ----------------------------
    def share(counter, total):
        return "  ".join(f"{k} {100 * counter[k] / total:4.1f}%" for k in LEVELS)

    published_chandler = collections.Counter()
    published_angstrom = collections.Counter()
    for day in season:
        _, t, rh, _ = worst[day]
        c = chandler(t, rh)
        published_chandler[
            "LOW" if c < 50 else "MODERATE" if c < 75 else "HIGH" if c < 90
            else "VERY_HIGH" if c < 97.5 else "EXTREME"
        ] += 1
        a = angstrom(t, rh)
        published_angstrom[
            "LOW" if a >= 4.1 else "MODERATE" if a >= 3.0 else "HIGH" if a >= 2.5
            else "VERY_HIGH" if a >= 2.0 else "EXTREME"
        ] += 1
    print("Chandler, published bands :", share(published_chandler, len(season)))
    print("Angstrom, published bands :", share(published_angstrom, len(season)))

    # --- the thresholds the app actually uses ------------------------------
    values = sorted(worst[d][0] for d in season)

    def worse_than(percent):
        # Lower index is worse, so the p-th percentile of danger is the
        # (100-p)-th percentile of the value.
        position = int(round((1 - percent / 100) * (len(values) - 1)))
        return values[max(0, min(len(values) - 1, position))]

    cuts = [worse_than(p) for p in PERCENTILES]
    print("\nAngstrom percentiles of fire-season days here, at the day's worst hour:")
    for percent, cut in zip(PERCENTILES, cuts):
        print(f"  worse than {percent}% of days -> index <= {cut:.2f}")
    rounded = [round(c, 1) for c in cuts]
    print(f"  rounded, as in FireRisk.kt        -> {rounded}")

    # --- what the finished rule does ---------------------------------------
    def level_of(index):
        for step, cut in enumerate(rounded):
            if index > cut:
                return step
        return 4

    for label, pool in (("fire season", season), ("whole year", days)):
        counter = collections.Counter()
        forbidden = 0
        for day in pool:
            index, _, _, stamp = worst[day]
            step = level_of(index)
            windy = (
                beaufort(day_wind[day]) >= WINDY_BEAUFORT
                or beaufort(day_gust[day]) >= GUSTY_BEAUFORT
            )
            if windy:
                step += 1
            if dry_before[day] >= DRY_SPELL_DAYS:
                step += 1
            fallen = rain_so_far[stamp]
            if fallen >= SOAKING_MM:
                step -= 2
            elif fallen >= WET_DAY_MM:
                step -= 1
            step = max(0, min(4, step))
            counter[LEVELS[step]] += 1
            in_season = 5 <= int(day[5:7]) <= 10
            if in_season and (windy or step >= 3):
                forbidden += 1
        print(f"\nfinished rule, {label:11s}:", share(counter, len(pool)))
        print(f"  burning prohibited on {100 * forbidden / len(pool):.1f}% of these days")


if __name__ == "__main__":
    main()
