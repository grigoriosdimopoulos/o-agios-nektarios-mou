# Άγιος Νεκτάριος Αττικής — village app

An Android app for the residents of Agios Nektarios: an interactive map of the
village where anyone can drop a pin on a real-world problem — uncleaned
vegetation, a broken road, a fallen tree, a water outage — and the whole village
can follow, discuss and resolve it. Think of an issue tracker, except the
backlog is the place people live in.

Greek and English throughout, light and dark, with push notifications, direct
and group messaging, and an announcements channel for the community council.

> **Status.** Builds clean: debug and release APKs both assemble, R8 shrinking
> and lint pass, all 37 unit tests are green, and the Cloud Functions typecheck.
> What has *not* happened is a run against a real backend — no Firebase project,
> no device, no emulator — so everything past "it compiles" (sign-in, the map
> tiles, live queries, push delivery) is unverified. Follow
> [docs/SETUP.md](docs/SETUP.md) to point it at a project of your own.

---

## What it does

**The map.** The village is drawn as a set of neighbourhood polygons, each
carrying a badge with the number of open reports inside it. Tapping a
neighbourhood frames it and lists what is open there. Reports appear as pins
coloured by category; the two genuinely urgent categories (danger, fire risk)
pulse while they are open, and nothing else does — motion is used as
information, so it has to stay rare.

**Grouping.** Two neighbours reporting the same pothole should not produce two
pins. Reports of the same category within 45 m always merge, no matter how far
you zoom in, because at that distance they are one real-world problem. Beyond
that the merge radius is derived from the current zoom so pins never overlap on
screen; zoomed far out, categories stop mattering and everything nearby collapses
into one counter. Tapping a cluster opens the reports it contains.

**Duplicates, prevented rather than merged.** While composing a report, once you
have picked a category and a location, any similar reports already filed at that
spot appear inline — before you have typed the description.

**Reports.** Title, description, category, photos, a pin. Photos are stored as
bytes inside Firestore documents rather than in Cloud Storage, which would need
a billing account — free, at the cost of a 500 KB ceiling per picture. Upvote and downvote,
comment, and a lifecycle of Open → Acknowledged → In progress → Resolved /
Won't do. The author and moderators can edit, delete and change status; a
resident sees only the states that make sense for their own report, while
moderators additionally get the work-in-progress states.

**People.** Sign in with email and password or with Google. A profile carries
name, address in the village, neighbourhood, phone and email, a photo, and the
resident's own reports with counters for reports filed, resolved and upvoted.

**Talking.** Direct messages and group chats between residents, with unread
badges and push notifications. Announcements are a separate one-way channel that
only administrators can post to.

**Administration.** Administrators can change roles, suspend and restore
accounts, correct names, trigger a password reset, and delete an account along
with everything it wrote — and can moderate any report or comment.

**The weather, and what it means up here.** The map carries a strip with the
temperature, the wind in Beaufort with an arrow pointing where it is going, and
the date; tapping it opens the humidity, the rain, sunrise and sunset, how many
days it has been since it properly rained, and four days ahead. The map itself
can animate what is happening — the light warming and cooling with the cloud,
rain leaning the way the wind pushes it, streaks running with the wind above 3
Beaufort, snow lying when there is any — behind a switch that is off until
somebody turns it on. The forecast comes from Open-Meteo, which needs no key and
no account, and the last reading is kept on disk so the strip is populated
before the radio has woken up.

**Fire risk, which is the part that is not decoration.** The village is in pine
and fir on the skirts of Kithairon, so between May and October the question
"may I burn these cuttings" has consequences. The app answers it with a level
computed on the phone from the day's *worst* hour rather than the current one,
the reasons listed beside it — the wind is up, it has not rained in three weeks
— and, when the answer is no, it says so. The measure is the Ångström index,
but with its published thresholds replaced by percentiles of this village's own
fire-season days: checked against three years of reanalysis for this exact
point, the Chandler index called 53% of fire-season days "low" and Ångström's
own bands called 56% of them "extreme". `tools/fire_risk_calibration.py`
reproduces those numbers and the distribution the finished rule produces. It is labelled an indication everywhere it appears,
and every screen that shows it links to the daily map from Civil Protection,
which is what the burning bans actually follow.

**Useful numbers.** 112, 199, 166, 100 and the electricity fault lines are
compiled into the app and render with no account, no network and no rule
evaluation between a resident and the number. The local half — the surgery at
Vilia, the municipality, whoever holds the key to the water tank — is filled in
by an administrator, because a plausible-looking telephone number that nobody
checked is worse than an empty section.

**The calendar.** Liturgies, the πανηγύρι, the Saturday somebody has decided to
clear the forest track, the day the bins are emptied. Anyone in the village may
add one — unlike announcements, which speak *for* the village and stay
administrator-only. Work days, meetings and festivals carry "I'll be there" with
the names against it, because a work day with six names on it is an arrangement
and the same line with none is a wish.

**Settings.** Greek / English / follow the system. Light / dark / follow the
system. Per-category notification switches that the server actually honours
before sending.

---

## How it is built

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose, Material 3, a custom village palette |
| Navigation | Navigation Compose, with deep links for push notifications |
| State | ViewModel + `StateFlow`, unidirectional data flow |
| DI | Hilt |
| Backend | Firebase Auth + Firestore (free Spark plan; no server required) |
| Maps | MapLibre + OpenStreetMap tiles (no key, no account) |
| Local prefs | DataStore |

```
app/src/main/java/gr/agiosnektarios/village/
├── core/           models, geo maths, validation, DI, Firestore helpers
├── data/           repositories — the only things that touch Firebase
├── notifications/  FCM service, channels, token synchronisation
└── ui/             theme, shared components, one package per feature
firebase/
├── firestore.rules      the actual authorisation model
├── rules.test.mjs       52 assertions against the emulator
└── functions/           OPTIONAL: push notifications only, needs Blaze
```

### Three decisions worth knowing about

**It runs with no server at all.** The whole app works on Firebase's free
Spark plan, which means no Cloud Functions own the counters — clients maintain
them, and the security rules are the only thing constraining what they write. A
vote is one transaction: the vote document at `issues/{id}/votes/{uid}` (a path
that makes double-voting structurally impossible) plus a tally that may move by
exactly one, with `score == upvotes - downvotes` enforced server-side.

The honest cost: someone running a modified client could nudge a counter without
casting a real vote, because rules cannot see another document's pending write
inside a transaction. They cannot jump a report to a thousand upvotes, and they
cannot touch anyone else's content. For a village of neighbours that is the
right trade; for a public app it would not be.

**The map needs no API key.** Google Maps requires a billing account on the
Cloud project even though Android map loads are free, which would have forced a
card on file for a village app. It renders with MapLibre against keyless
OpenStreetMap vector tiles instead. Pins are bitmaps in a symbol layer rather
than per-marker composables — that is the one thing the swap cost — and OSM
attribution stays visible on the map, as its licence requires.

**Enums are stored as stable string ids, not as enums.** A document written by a
newer build never fails to deserialise on an older one; an unknown category
degrades to "Other" and an unknown status to "Open".

**The server does not guess your language.** Residents choose Greek or English in
Settings, independently of their device locale, so the server cannot know it.
Push notifications carry user-authored text verbatim (a comment is in whatever
language it was written in) but send a *key* for anything the product words
itself, which the app resolves against its own string resources.

### Where the geography lives

`VillageConfig.kt` holds the village centre and camera bounds;
`app/src/main/assets/village_blocks.json` holds the neighbourhood outlines.
Both currently contain **approximate placeholder data** — the coordinates are a
rough guess at the settlement's extent, and the neighbourhoods are a uniform
12-cell grid with plausible Greek names. Everything works out of the box, but
replacing these two files with surveyed data is the first real task. Nothing
else in the app hardcodes a coordinate.

---

## Getting started

**No computer? [docs/PHONE-SETUP.md](docs/PHONE-SETUP.md)** covers doing the
whole thing from an Android phone, where the browser steps are yours and the
terminal steps are someone else's.

Most of the Firebase setup is scripted:

```bash
npm install -g firebase-tools
./scripts/setup-firebase.sh
```

That creates the project, registers both Android apps, downloads
`google-services.json`, creates the Firestore database, deploys rules and
indexes, and prints the three things you have to click in a browser. Then:

```bash
./gradlew assembleDebug
```

Cloud Functions are entirely optional: they add push notifications and nothing
else, and they need the Blaze billing plan. Full instructions, troubleshooting, and how to make yourself the
first administrator are in **[docs/SETUP.md](docs/SETUP.md)**.

Neither `google-services.json` nor `local.properties` is committed; both are
git-ignored.

## Tests

```bash
./gradlew testDebugUnitTest            # 121 tests: geohashing, clustering, validation, permissions, weather
./gradlew assembleRelease              # also runs R8 and lint's fatal checks
./gradlew assemblePreview              # shrunk but debug-signed, for sending to a tester
cd firebase/functions && npx tsc --noEmit   # Cloud Functions typecheck
./gradlew recordPaparazziDebug         # re-record the UI goldens after a deliberate change
./gradlew verifyPaparazziDebug         # compare the rendered UI against them
npm --prefix firebase run test:rules   # 147 security-rule assertions, in the emulator
```

The unit tests cover the parts where a subtle mistake would be invisible in the
UI: geohash encoding, the clustering rules, phone and password validation, who
is allowed to edit what, and the weather — the forecast parser is checked
against a real captured response from this village's own coordinates, and the
fire index against values worked out by hand from the published formula.

Paparazzi renders whole screens to PNG on the JVM and compares them against the
images in `app/src/test/snapshots/images/`. It is the only way anything here is
looked at before it ships, and it has caught more than it has any right to: a
report count squeezed to one letter per line by a weather strip beside it, wind
drawn at an alpha that made it invisible, and a splash-screen golden that could
never match because it rendered the build's own commit SHA.

`firebase/rules.test.mjs` runs the security rules against the Firestore
emulator using the exact payloads the repositories write. Since the rules are
now the *only* thing standing between a client and the data, this matters more
than the Kotlin tests: it asserts that an ordinary resident can sign up, file a
report, vote and comment — and that nobody can promote themselves to admin,
jump a tally, write under someone else's name, unsuspend themselves, or read a
conversation they are not in.
