#!/usr/bin/env python3
"""Κάνει τις πολιτικές απορρήτου δημόσιες σελίδες.

Το Play ζητάει *σύνδεσμο* στην πολιτική απορρήτου, όχι αρχείο, και ο ελεγκτής
τον ανοίγει σε κινητό. Οι πηγές είναι τα δύο markdown στο store/· αυτό τα
μετατρέπει σε αυτοτελείς σελίδες στο docs/, από όπου τις σερβίρει το GitHub
Pages.

Γράφεται και ένα .nojekyll: χωρίς αυτό το Pages περνάει τον φάκελο από Jekyll,
που είναι ένα ακόμα πράγμα που μπορεί να χαλάσει σιωπηλά μια μέρα που κανείς
δεν κοιτάει.

    pip install markdown && python3 tools/privacy_page.py

Ξανατρέξ' το κάθε φορά που αλλάζει το store/privacy-policy.*.md, αλλιώς η
δημοσιευμένη πολιτική λέει άλλα από αυτήν στο repo — και η δημοσιευμένη είναι
αυτή που δεσμεύει.
"""

import pathlib
import re
import sys

import markdown

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"

# Δύο, γιατί τα δύο αρχεία γράφτηκαν στη γλώσσα τους. Ένα regex που ήξερε
# μόνο το ελληνικό άφηνε την αγγλική σελίδα να δημοσιευτεί με το κενό μέσα.
PLACEHOLDERS = ("ΣΥΜΠΛΗΡΩΣΕ ΕΔΩ", "FILL IN")

# Ένα φύλλο στυλ, μέσα στη σελίδα. Η σελίδα πρέπει να ανοίγει και σε κινητό με
# μισή μπάρα σήματος στο βουνό, οπότε δεν κατεβάζει τίποτα από πουθενά.
CSS = """
*,*::before,*::after{box-sizing:border-box}
:root{
  --bg:#fbfaf7; --ink:#1d2321; --dim:#5a625e; --line:#e0ddd5;
  --accent:#1f6b52; --card:#ffffff; --warn-bg:#fdece9; --warn-ink:#8f2d20;
}
@media (prefers-color-scheme:dark){
  :root{
    --bg:#14181a; --ink:#e8eae7; --dim:#a2aaa6; --line:#2c3335;
    --accent:#3fb18e; --card:#1b2022; --warn-bg:#3a1f1b; --warn-ink:#ff9c8d;
  }
}
html{-webkit-text-size-adjust:100%}
body{
  margin:0; background:var(--bg); color:var(--ink);
  font:16px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;
}
.wrap{max-width:44rem;margin:0 auto;padding:2rem 1.15rem 5rem}
h1{font-size:1.6rem;line-height:1.25;margin:0 0 .3rem}
h2{font-size:1.12rem;margin:2.2rem 0 .5rem;color:var(--accent)}
h2::before{content:"";display:block;height:1px;background:var(--line);margin:0 0 .9rem}
h1+p em,.date{color:var(--dim);font-size:.9rem;font-style:normal}
p,li{margin:.7rem 0}
a{color:var(--accent)}
strong{font-weight:650}
code{background:var(--card);border:1px solid var(--line);border-radius:4px;padding:.05em .35em;font-size:.9em}
hr{border:0;border-top:1px solid var(--line);margin:2.4rem 0}
.tablewrap{overflow-x:auto;-webkit-overflow-scrolling:touch;margin:1rem 0}
table{border-collapse:collapse;width:100%;min-width:30rem;font-size:.93rem}
th,td{text-align:start;padding:.55rem .6rem;border-bottom:1px solid var(--line);vertical-align:top}
th{color:var(--dim);font-weight:600;font-size:.82rem;text-transform:uppercase;letter-spacing:.03em}
/* Σε οθόνη κινητού ο πίνακας γίνεται κάρτες. Με οριζόντια κύλιση η τρίτη
   στήλη — «ποιος το βλέπει», η μόνη που έχει πραγματικά σημασία — έμενε έξω
   από την οθόνη και κανείς δεν θα μάθαινε ότι υπάρχει. */
@media (max-width:34rem){
  .tablewrap{overflow-x:visible}
  table{min-width:0;display:block}
  thead{display:none}
  tbody,tr,td{display:block}
  tr{border-bottom:1px solid var(--line);padding:.7rem 0}
  tr:last-child{border-bottom:0}
  td{border:0;padding:.12rem 0}
  td::before{
    content:attr(data-label);display:block;
    color:var(--dim);font-size:.72rem;font-weight:600;
    text-transform:uppercase;letter-spacing:.04em;
  }
  td+td{margin-top:.5rem}
}
.langs{margin:0 0 1.6rem;font-size:.9rem;color:var(--dim)}
.warn{background:var(--warn-bg);color:var(--warn-ink);border-radius:8px;padding:.85rem 1rem;font-weight:600}
footer{margin-top:3.5rem;padding-top:1.2rem;border-top:1px solid var(--line);color:var(--dim);font-size:.85rem}
"""

INDEX_BODY = """
<h1>Άγιος Νεκτάριος</h1>
<p class="date">Η εφαρμογή του οικισμού, στην Πάρνηθα‑Κιθαιρώνα, Αττική.</p>

<p>Ο χάρτης του οικισμού, οι αναφορές για ό,τι χαλάει, οι ανακοινώσεις, τα
μηνύματα μεταξύ γειτόνων και ο συναγερμός για τα έκτακτα — σε ένα μέρος,
για τους 200 οικιστές.</p>

<h2>Σύνδεσμοι</h2>
<ul>
  <li><a href="privacy.html">Πολιτική απορρήτου</a> · <a href="privacy-en.html">Privacy policy</a></li>
  <li><a href="https://github.com/grigoriosdimopoulos/o-agios-nektarios-mou">Ο κώδικας, στο GitHub</a></li>
</ul>

<h2>Επικοινωνία</h2>
<p>Για οτιδήποτε αφορά την εφαρμογή ή τα δεδομένα σου, η διεύθυνση είναι στο
τέλος της <a href="privacy.html">πολιτικής απορρήτου</a>.</p>
"""


PAGE = """<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title}</title>
<meta name="description" content="{desc}">
<style>{css}</style>
</head>
<body>
<div class="wrap">
<p class="langs">{langs}</p>
{body}
<footer>{footer}</footer>
</div>
</body>
</html>
"""


def label_cells(html: str) -> str:
    """Δίνει σε κάθε κελί το όνομα της στήλης του, ως data-label.

    Το CSS το χρειάζεται για να στοιβάξει τους πίνακες σε κάρτες στο κινητό:
    χωρίς αυτό, η στοιβαγμένη γραμμή είναι τρεις προτάσεις η μία κάτω από την
    άλλη χωρίς να λέει ποια είναι ποια.
    """
    def one_table(m: "re.Match[str]") -> str:
        table = m.group(0)
        heads = [
            re.sub(r"<[^>]+>", "", h).strip()
            for h in re.findall(r"<th[^>]*>(.*?)</th>", table, re.S)
        ]
        if not heads:
            return table

        def one_row(rm: "re.Match[str]") -> str:
            row = rm.group(0)
            i = iter(range(len(heads)))

            def one_cell(cm: "re.Match[str]") -> str:
                try:
                    label = heads[next(i)]
                except StopIteration:
                    return cm.group(0)
                return f'<td data-label="{label}"' + cm.group(1)

            return re.sub(r"<td(>|\s)", one_cell, row)

        return re.sub(r"<tr>.*?</tr>", one_row, table, flags=re.S)

    return re.sub(r"<table>.*?</table>", one_table, html, flags=re.S)


def render(md_path: pathlib.Path) -> str:
    text = md_path.read_text(encoding="utf-8")
    html = markdown.markdown(text, extensions=["tables", "sane_lists"])
    # Οι πίνακες είναι το μόνο πράγμα εδώ που δεν χωράει σε οθόνη κινητού. Ας
    # κυλάει ο πίνακας μόνος του αντί να κυλάει όλη η σελίδα πλάγια.
    html = label_cells(html)
    html = html.replace("<table>", '<div class="tablewrap"><table>')
    html = html.replace("</table>", "</table></div>")
    # Το email που λείπει δεν πρέπει να περνάει απαρατήρητο σε δημοσιευμένη
    # σελίδα, οπότε φοράει κόκκινο.
    for token in PLACEHOLDERS:
        html = re.sub(
            r"<p><strong>(" + re.escape(token) + r"[^<]*)</strong></p>",
            r'<p class="warn">\1</p>',
            html,
        )
    return html


def main() -> int:
    DOCS.mkdir(exist_ok=True)
    (DOCS / ".nojekyll").write_text("")

    pages = [
        (
            "privacy.html",
            "privacy-policy.el.md",
            "el",
            "Πολιτική απορρήτου — Άγιος Νεκτάριος",
            "Τι δεδομένα κρατάει η εφαρμογή του οικισμού Άγιος Νεκτάριος Αττικής και ποιος τα βλέπει.",
            '<a href="privacy-en.html">English</a>',
            'Εφαρμογή του οικισμού Άγιος Νεκτάριος Αττικής. '
            '<a href="https://github.com/grigoriosdimopoulos/o-agios-nektarios-mou">Ο κώδικας είναι ανοιχτός.</a>',
        ),
        (
            "privacy-en.html",
            "privacy-policy.en.md",
            "en",
            "Privacy Policy — Agios Nektarios",
            "What the Agios Nektarios village app stores and who can see it.",
            '<a href="privacy.html">Ελληνικά</a>',
            'Village app for Agios Nektarios, Attica. '
            '<a href="https://github.com/grigoriosdimopoulos/o-agios-nektarios-mou">Source is public.</a>',
        ),
    ]

    # Μια σελίδα ευρετηρίου, γιατί όποιος πάρει τον σύνδεσμο της πολιτικής
    # και κόψει το τέλος του βρίσκεται στο /  και πρέπει να δει κάτι.
    (DOCS / "index.html").write_text(
        PAGE.format(
            lang="el",
            title="Άγιος Νεκτάριος — η εφαρμογή του οικισμού",
            desc="Ο χάρτης, τα προβλήματα και τα νέα του οικισμού Άγιος Νεκτάριος Αττικής.",
            css=CSS,
            langs='<a href="privacy-en.html">English</a>',
            body=INDEX_BODY,
            footer='<a href="https://github.com/grigoriosdimopoulos/o-agios-nektarios-mou">'
                   'Ο κώδικας είναι ανοιχτός.</a>',
        ),
        encoding="utf-8",
    )
    print("docs/index.html")

    unfilled = []
    for out, src, lang, title, desc, langs, footer in pages:
        src_path = ROOT / "store" / src
        body = render(src_path)
        if any(t in body for t in PLACEHOLDERS):
            unfilled.append(src)
        (DOCS / out).write_text(
            PAGE.format(lang=lang, title=title, desc=desc, css=CSS,
                        langs=langs, body=body, footer=footer),
            encoding="utf-8",
        )
        print(f"docs/{out}  ←  store/{src}")

    if unfilled:
        print()
        print("ΠΡΟΣΟΧΗ: λείπει ακόμα το email επικοινωνίας από: "
              + ", ".join(unfilled))
        print("Το Play απορρίπτει πολιτική χωρίς τρόπο επικοινωνίας.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
