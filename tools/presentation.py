"""
Η επίσημη παρουσίαση της εφαρμογής για τους οικιστές, σε PDF.

Γράφεται σε HTML και τυπώνεται από τον Chromium, ώστε να χρησιμοποιεί τις
πραγματικές γραμματοσειρές και την παλέτα της εφαρμογής — και ξαναφτιάχνεται
με μία εντολή όταν αλλάξει κάτι, αντί να είναι ένα αρχείο που κανείς δεν
θυμάται πώς έγινε.

    python3 tools/presentation.py
"""

import base64
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "store" / "Agios-Nektarios-parousiasi.pdf"
HTML = ROOT / "store" / ".presentation.html"
CHROME = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"


def data_uri(path: pathlib.Path, mime: str) -> str:
    return f"data:{mime};base64," + base64.b64encode(path.read_bytes()).decode()


def font(name: str) -> str:
    return data_uri(ROOT / "app/src/main/res/font" / name, "font/ttf")


def shot(name: str) -> str:
    return data_uri(ROOT / "store/screenshots" / name, "image/png")


def build_html() -> str:
    alegreya = font("alegreya_variable.ttf")
    inter = font("inter_variable.ttf")
    banner = data_uri(ROOT / "store/feature-graphic.png", "image/png")

    def gallery(rows, kicker, title, page, lead=""):
        """Several screens to a page, for the ones that need showing but not
        explaining. A presentation that gives every screen its own spread is a
        presentation nobody reaches the end of."""
        cells = "".join(
            f'<figure><img src="{shot(f)}" alt=""><figcaption>{c}</figcaption></figure>'
            for f, c in rows
        )
        return f"""
        <div class="page">
          <div class="kicker">{kicker}</div>
          <h2>{title}</h2>
          {f'<p>{lead}</p>' if lead else ''}
          <div class="grid">{cells}</div>
          <div class="foot"><span>Άγιος Νεκτάριος</span><span>{page}</span></div>
        </div>"""

    def mapshot(page):
        """The map, if somebody has put a photograph of it here.

        MapLibre draws into a native view, which cannot be rendered off a
        device — so this page cannot be produced from the code the way every
        other page can. Rather than draw something map-shaped and let a reader
        assume it is a screenshot, the page says what is missing until the file
        appears, and completes itself when it does."""
        real = ROOT / "store/screenshots/hartis.png"
        if real.exists():
            body = f'<div class="mapwrap"><img src="{shot("hartis.png")}" alt=""></div>'
        else:
            body = ('<div class="missing"><b>Λείπει το στιγμιότυπο του χάρτη.</b>'
                    '<br>Τράβα ένα από το κινητό σου με την αρχική οθόνη, '
                    'αποθήκευσέ το ως <code>store/screenshots/hartis.png</code>, '
                    'και ξανατρέξε <code>python3 tools/presentation.py</code>.</div>')
        return f"""
        <div class="page">
          <div class="kicker">Η αρχική οθόνη</div>
          <h2>Ο χάρτης του οικισμού</h2>
          <p>Κάθε αναφορά μπαίνει στο σημείο της. Επάνω αριστερά το κουμπί για
          τα έκτακτα, επάνω δεξιά ο καιρός και ο κίνδυνος πυρκαγιάς, και από
          κάτω η λίστα με ό,τι τρέχει αυτή τη στιγμή.</p>
          {body}
          <div class="foot"><span>Άγιος Νεκτάριος</span><span>{page}</span></div>
        </div>"""

    def feature(img, kicker, title, body, points, page):
        lis = "".join(f"<li>{p}</li>" for p in points)
        return f"""
        <section class="feature">
          <div class="shot"><img src="{shot(img)}" alt=""></div>
          <div class="copy">
            <div class="kicker">{kicker}</div>
            <h2>{title}</h2>
            <p>{body}</p>
            <ul>{lis}</ul>
          </div>
          <div class="foot"><span>Άγιος Νεκτάριος</span><span>{page}</span></div>
        </section>"""

    features = mapshot(3) + "".join([
        feature(
            "anafores.png",
            "Καθημερινά",
            "Ο χάρτης και οι αναφορές",
            "Κάθε πρόβλημα μπαίνει στο σημείο του πάνω στον χάρτη του χωριού, "
            "με φωτογραφία αν χρειάζεται. Δεν χάνεται σε ένα group και δεν "
            "ξεχνιέται.",
            [
                "Πεσμένο δέντρο, σπασμένος στύλος, σκουπίδια, ξερά χόρτα δίπλα σε σπίτι.",
                "Οι γείτονες το στηρίζουν· φαίνεται πόσοι το θεωρούν σοβαρό.",
                "Κάποιος το αναλαμβάνει, και φαίνεται ποιος.",
                "Φαίνεται πότε στάλθηκε στον δήμο και πόσες μέρες εκκρεμεί.",
            ],
            4,
        ),
        feature(
            "ektakto.png",
            "Όταν βιάζεσαι",
            "Έκτακτα",
            "Φωτιά, ασθενοφόρο, κάποιος που λείπει — με τα τηλέφωνα της "
            "Πυροσβεστικής και του ΕΚΑΒ ένα πάτημα μακριά.",
            [
                "Πρώτα το τηλέφωνο: η εφαρμογή ειδοποιεί τους γείτονες, δεν καλεί για σένα.",
                "Το σημείο μπαίνει αυτόματα, ή από το σπίτι σου αν το έχεις σημειώσει.",
                "Διακοπές ρεύματος και νερού, όπου μετράει το πόσα σπίτια το έχουν.",
                "Λέει καθαρά ποιος θα το δει αμέσως και ποιος όχι.",
            ],
            5,
        ),
        feature(
            "kairos.png",
            "Το καλοκαίρι",
            "Καιρός και κίνδυνος πυρκαγιάς",
            "Πρόγνωση για το σημείο του χωριού, και ένδειξη κινδύνου πυρκαγιάς "
            "βαθμονομημένη στα δικά μας δεδομένα.",
            [
                "Δείχνει πότε απαγορεύεται η καύση, με τον λόγο δίπλα.",
                "Η κλίμακα φτιάχτηκε από τρία χρόνια πραγματικών μετρήσεων εδώ.",
                "Ο αέρας και η βροχή φαίνονται πάνω στον χάρτη.",
            ],
            6,
        ),
        feature(
            "anakoinoseis.png",
            "Το χωριό μαζί",
            "Ανακοινώσεις και ημερολόγιο",
            "Ό,τι πρέπει να ξέρει το χωριό, σε ένα μέρος — και τι έρχεται.",
            [
                "Ανακοινώσεις από τον διαχειριστή, με καρφίτσωμα των σημαντικών.",
                "Εκδηλώσεις, καθαρισμοί, συνελεύσεις· δηλώνεις αν θα πας.",
                "Υπενθύμιση το βράδυ πριν.",
            ],
            7,
        ),
        feature(
            "minymata.png",
            "Μεταξύ μας",
            "Μηνύματα",
            "Προσωπικές συνομιλίες και ομάδες μεταξύ κατοίκων, μέσα στην ίδια "
            "εφαρμογή — χωρίς να χρειάζεται να έχει ο άλλος το τηλέφωνό σου.",
            [
                "Ένας προς έναν ή ομάδα, π.χ. για την πυροπροστασία.",
                "Ειδοποίηση όταν έρθει μήνυμα, και μέτρηση των αδιάβαστων.",
                "Δεν διαβάζει κανείς άλλος τη συνομιλία, ούτε ο διαχειριστής.",
                "Τα ονόματα των δρόμων τα γράφουν και τα επιβεβαιώνουν οι κάτοικοι.",
            ],
            8,
        ),
        feature(
            "tilefona.png",
            "Όταν χρειαστεί",
            "Χρήσιμα τηλέφωνα",
            "Τα νούμερα που ψάχνει κανείς βιαστικά, σε ένα μέρος — και τα "
            "επείγοντα δουλεύουν ακόμα και χωρίς σήμα δεδομένων.",
            [
                "112, Πυροσβεστική, ΕΚΑΒ, Αστυνομία — γραμμένα μέσα στην εφαρμογή.",
                "ΔΕΔΔΗΕ, δήμος, δασαρχείο, αγροτικό ιατρείο.",
                "Ένα πάτημα για κλήση, ένα για αντιγραφή.",
            ],
            9,
        ),
    ]) + gallery(
        [
            ("nea-anafora.png", "Νέα αναφορά: φωτογραφία, κατηγορία, και το σημείο μπαίνει μόνο του."),
            ("fotia.png", "Φωτιά: πρώτα το τηλέφωνο της Πυροσβεστικής, μετά η ειδοποίηση."),
            ("diakopi.png", "Διακοπή νερού, και πόσα σπίτια την έχουν δηλώσει."),
        ],
        "Και μερικές ακόμα", "Πώς φαίνονται στην πράξη", 10,
        "Οι οθόνες που θα δεις πιο συχνά, χωρίς πολλά λόγια.",
    ) + gallery(
        [
            ("synomilies.png", "Οι συνομιλίες σου, με τα αδιάβαστα."),
            ("imerologio.png", "Το ημερολόγιο: τι έρχεται και ποιος θα πάει."),
            ("profil.png", "Το προφίλ σου και οι ρυθμίσεις σου."),
        ],
        "Και μερικές ακόμα", "Το υπόλοιπο", 11,
    )

    return f"""<!doctype html>
<html lang="el"><head><meta charset="utf-8">
<style>
  @font-face {{ font-family: Alegreya; src: url({alegreya}) format('truetype');
               font-weight: 400 700; }}
  @font-face {{ font-family: Inter; src: url({inter}) format('truetype');
               font-weight: 300 700; }}

  @page {{ size: A4; margin: 0; }}
  * {{ box-sizing: border-box; }}
  body {{ margin: 0; font-family: Inter, sans-serif; color: #17211E;
          background: #FBF7F2; -webkit-print-color-adjust: exact;
          print-color-adjust: exact; }}

  .page {{ width: 210mm; height: 297mm; padding: 18mm 20mm; position: relative;
           page-break-after: always; overflow: hidden; }}
  .page:last-child {{ page-break-after: auto; }}

  /* ------------------------------------------------------------ εξώφυλλο */
  .cover {{ padding: 0; background: #0E2E27; color: #FBF7F2; }}
  .cover img.banner {{ width: 100%; display: block; }}
  .cover .inner {{ padding: 22mm 20mm 0; }}
  .cover h1 {{ font-family: Alegreya, serif; font-size: 46pt; font-weight: 700;
               margin: 0 0 6mm; line-height: 1.02; }}
  .cover .lead {{ font-size: 13.5pt; line-height: 1.6; color: #CFE0DA;
                  max-width: 140mm; }}
  .cover .rule {{ width: 26mm; height: 1mm; background: #E2724B;
                  margin: 0 0 8mm; border-radius: 1mm; }}
  .cover .meta {{ position: absolute; bottom: 16mm; left: 20mm; right: 20mm;
                  font-size: 9.5pt; color: #8FB0A6; border-top: 1px solid #24564A;
                  padding-top: 4mm; display: flex; justify-content: space-between; }}

  h2 {{ font-family: Alegreya, serif; font-size: 22pt; font-weight: 700;
        margin: 0 0 3mm; color: #1F6F5C; }}
  h3 {{ font-family: Alegreya, serif; font-size: 15pt; margin: 0 0 2mm; }}
  p {{ font-size: 10.5pt; line-height: 1.55; margin: 0 0 3mm; }}
  ul {{ font-size: 10pt; line-height: 1.55; margin: 0; padding-left: 5mm; }}
  li {{ margin-bottom: 1.6mm; }}

  .kicker {{ font-size: 8.5pt; letter-spacing: .12em; text-transform: uppercase;
             color: #7C8B84; margin-bottom: 3mm; }}

  /* ---------------------------------------------------- σελίδα λειτουργίας */
  .feature {{ display: flex; gap: 13mm; align-items: center; position: relative;
              page-break-after: always; padding: 22mm 20mm; height: 297mm; }}
  .feature:last-of-type {{ page-break-after: auto; }}
  .feature .shot {{ flex: 0 0 88mm; }}
  .feature .shot img {{ width: 88mm; border-radius: 4mm; border: 1px solid #E4DCD1;
                        box-shadow: 0 2mm 6mm rgba(23,33,30,.10); }}
  .feature .copy {{ flex: 1; }}
  .feature p {{ font-size: 11pt; }}
  .feature ul {{ font-size: 10.5pt; margin-top: 5mm; }}
  .feature li {{ margin-bottom: 3mm; }}

  .grid {{ display: flex; gap: 6mm; margin-top: 8mm;
           align-items: flex-start; }}
  .grid figure {{ margin: 0; flex: 1; min-width: 0; }}
  .grid img {{ width: 100%; border-radius: 3mm; border: 1px solid #E4DCD1;
               box-shadow: 0 1.5mm 4mm rgba(23,33,30,.09); display: block; }}
  .grid figcaption {{ font-size: 9pt; color: #5D6B64; margin-top: 3.5mm;
                      line-height: 1.4; }}

  .mapwrap {{ margin-top: 6mm; }}
  .mapwrap img {{ width: 88mm; display: block; margin: 0 auto;
                  border-radius: 4mm; border: 1px solid #E4DCD1;
                  box-shadow: 0 2mm 6mm rgba(23,33,30,.10); }}
  .missing {{ margin-top: 8mm; padding: 14mm 10mm; text-align: center;
              border: 1.5px dashed #C9BFB2; border-radius: 4mm;
              color: #7C6F60; font-size: 10pt; line-height: 1.6;
              background: #F6F0E8; }}
  .missing code {{ font-family: ui-monospace, monospace; font-size: 9pt; }}

  .box {{ background: #FFFFFF; border: 1px solid #E4DCD1; border-radius: 3mm;
          padding: 6mm 7mm; margin-bottom: 5mm; }}
  .box.warn {{ border-color: #E7C9C9; background: #FDF5F4; }}
  .box h3 {{ color: #1F6F5C; }}
  .box.warn h3 {{ color: #B53434; }}

  table {{ width: 100%; border-collapse: collapse; font-size: 9.5pt; }}
  th, td {{ text-align: left; padding: 2.4mm 3mm; border-bottom: 1px solid #EDE6DC; }}
  th {{ color: #7C8B84; font-weight: 600; font-size: 8.5pt;
        text-transform: uppercase; letter-spacing: .06em; }}
  td:first-child, th:first-child {{ font-weight: 600; }}
  td:last-child, th:last-child {{ text-align: right; }}
  .yes {{ color: #1F6F5C; font-weight: 600; }}
  .no  {{ color: #B53434; font-weight: 600; }}

  .steps {{ counter-reset: s; padding: 0; list-style: none; }}
  .steps li {{ counter-increment: s; position: relative; padding-left: 11mm;
               margin-bottom: 4mm; font-size: 10.5pt; line-height: 1.5; }}
  .steps li::before {{ content: counter(s); position: absolute; left: 0; top: -0.5mm;
      width: 7mm; height: 7mm; border-radius: 50%; background: #1F6F5C;
      color: #FBF7F2; font-size: 9pt; font-weight: 700;
      display: flex; align-items: center; justify-content: center; }}

  .foot {{ position: absolute; bottom: 12mm; left: 20mm; right: 20mm;
           font-size: 8pt; color: #9AA6A0; border-top: 1px solid #EDE6DC;
           padding-top: 3mm; display: flex; justify-content: space-between; }}
</style></head><body>

<div class="page cover">
  <img class="banner" src="{banner}" alt="">
  <div class="inner">
    <div class="rule"></div>
    <h1>Η εφαρμογή<br>του χωριού μας</h1>
    <p class="lead">Τρία πράγματα: να επικοινωνούμε, να υπάρχει ένα κουμπί για
    την έκτακτη ανάγκη, και να προωθούνται και να παρακολουθούνται τα προβλήματα
    του οικισμού μέχρι να λυθούν.</p>
  </div>
  <div class="meta"><span>Άγιος Νεκτάριος Αττικής · 200 σπίτια · 640 μ.</span>
    <span>Παρουσίαση για τους οικιστές</span></div>
</div>

<div class="page">
  <div class="kicker">Γιατί</div>
  <h2>Τρία προβλήματα</h2>

  <div class="box">
    <h3>1 · Να επικοινωνούμε</h3>
    <p style="margin:0">Οι ανακοινώσεις, οι εκδηλώσεις και τα χρήσιμα τηλέφωνα
    είναι σκόρπια — σε μηνύματα, σε χαρτιά, στη μνήμη κάποιου. Η εφαρμογή τα
    βάζει σε ένα μέρος όπου μένουν και βρίσκονται.</p>
  </div>

  <div class="box">
    <h3>2 · Η έκτακτη ανάγκη</h3>
    <p style="margin:0">Όταν βλέπεις καπνό, δεν ψάχνεις ποιον να πάρεις και δεν
    γράφεις σε group. Ένα κουμπί βάζει μπροστά σου το τηλέφωνο της Πυροσβεστικής
    ή του ΕΚΑΒ, στέλνει το σημείο σε όσους έχουν την εφαρμογή, και λέει καθαρά
    τι <i>δεν</i> μπορεί να κάνει — που είναι εξίσου σημαντικό. Το ίδιο και για
    διακοπές ρεύματος ή νερού, όπου μετράει πόσα σπίτια τα έχουν.</p>
  </div>

  <div class="box">
    <h3>3 · Τα προβλήματα, μέχρι να λυθούν</h3>
    <p>Ένα πεσμένο δέντρο συζητιέται σε ένα group, η φωτογραφία κατεβαίνει μέσα
    σε μια μέρα, και τρεις μήνες μετά κανείς δεν ξέρει αν ειπώθηκε σε κάποιον,
    αν το ανέλαβε κάποιος, ή αν έγινε ποτέ τίποτα.</p>
    <p style="margin:0">Η εφαρμογή κρατάει τα τρία που το group δεν κρατάει:
    <b>πού</b> είναι, <b>ποιος</b> το ανέλαβε, και <b>τι απέγινε</b> — μαζί με
    την αποστολή στον δήμο και το πόσες μέρες εκκρεμεί.</p>
  </div>

  <div class="kicker" style="margin-top:8mm">Τι χρειάζεται</div>
  <h2>Για να τη χρησιμοποιήσεις</h2>
  <ul>
    <li>Κινητό με Android (έκδοση 8 και πάνω — δηλαδή σχεδόν κάθε κινητό από το 2017).</li>
    <li>Έναν λογαριασμό: όνομα, email, και η γειτονιά σου.</li>
    <li>Σύνδεση στο διαδίκτυο — αλλά η εφαρμογή δουλεύει και χωρίς: ό,τι γράψεις
        αποθηκεύεται και φεύγει μόλις υπάρξει σήμα.</li>
  </ul>
  <div class="foot"><span>Άγιος Νεκτάριος</span><span>2</span></div>
</div>

{features}

<div class="page">
  <div class="kicker">Ιδιωτικότητα</div>
  <h2>Ποιος βλέπει τι</h2>
  <p>Αυτό είναι το κομμάτι που αξίζει να διαβαστεί προσεκτικά. Δεν είναι
  υπόσχεση της εφαρμογής· είναι κανόνες στον διακομιστή, που ισχύουν ακόμα κι
  αν κάποιος πειράξει την εφαρμογή στο κινητό του.</p>

  <table>
    <tr><th>Στοιχείο</th><th>Ποιος το βλέπει</th></tr>
    <tr><td>Όνομα και γειτονιά</td><td>Οι συνδεδεμένοι κάτοικοι</td></tr>
    <tr><td>Email</td><td><span class="yes">Μόνο εσύ</span> και ο διαχειριστής</td></tr>
    <tr><td>Τηλέφωνο</td><td><span class="yes">Μόνο εσύ</span> και ο διαχειριστής</td></tr>
    <tr><td>Το σπίτι σου στον χάρτη</td><td><span class="yes">Μόνο εσύ</span> — ούτε ο διαχειριστής</td></tr>
    <tr><td>Θέση αναφοράς ή συναγερμού</td><td>Οι συνδεδεμένοι κάτοικοι</td></tr>
    <tr><td>Προσωπικά μηνύματα</td><td>Όσοι είναι στη συνομιλία</td></tr>
    <tr><td>Πού βρίσκεσαι, γενικά</td><td><span class="no">Κανείς</span> — δεν καταγράφεται ποτέ</td></tr>
  </table>

  <div class="box warn" style="margin-top:6mm">
    <h3>Το τηλέφωνό σου, και η μία εξαίρεση</h3>
    <p>Το τηλέφωνό σου δεν το διαβάζει κανένας άλλος κάτοικος. Υπάρχει μία
    εξαίρεση και τη διαλέγεις εσύ: αν ενεργοποιήσεις το «Να μπορούν οι γείτονες
    να μου στείλουν SMS σε έκτακτο», γίνεται αναγνώσιμο από τα κινητά των
    υπολοίπων, ώστε η οθόνη του συναγερμού να στέλνει μήνυμα σε όλους μαζί.</p>
    <p style="margin:0">Ξεκινάει <b>κλειστό</b>. Το παίρνεις πίσω όποτε θέλεις,
    και σβήνεται αμέσως. Τα SMS φεύγουν από το δικό σου κινητό, με τη δική σου
    εφαρμογή μηνυμάτων — όχι από την εφαρμογή.</p>
  </div>

  <ul>
    <li>Δεν υπάρχουν διαφημίσεις και δεν πουλάει τίποτα.</li>
    <li>Δεν διαβάζει τις επαφές σου, το ημερολόγιό σου ή τα μηνύματά σου.</li>
    <li>Η τοποθεσία ζητείται μόνο όταν πατήσεις κάτι που τη χρειάζεται.</li>
    <li>Μπορείς να διαγράψεις τον λογαριασμό σου μέσα από την εφαρμογή.</li>
  </ul>
  <div class="foot"><span>Άγιος Νεκτάριος</span><span>12</span></div>
</div>

<div class="page">
  <div class="kicker">Ξεκίνημα</div>
  <h2>Πώς μπαίνεις</h2>
  <ol class="steps">
    <li>Θα λάβεις έναν σύνδεσμο για να κατεβάσεις την εφαρμογή.</li>
    <li>Άνοιξέ την και φτιάξε λογαριασμό με το email σου — ή με τον λογαριασμό
        Google που ήδη έχεις στο κινητό.</li>
    <li>Συμπλήρωσε όνομα και γειτονιά. Το τηλέφωνο είναι προαιρετικό.</li>
    <li>Σημείωσε πού είναι το σπίτι σου στον χάρτη. Το βλέπεις μόνο εσύ, και
        είναι αυτό που θα διαβάσεις στο ασθενοφόρο.</li>
    <li>Επίτρεψε τις ειδοποιήσεις, αλλιώς δεν θα μάθεις για ένα έκτακτο.</li>
  </ol>

  <div class="box" style="margin-top:6mm">
    <h3>Αν κάτι δεν δουλεύει</h3>
    <p style="margin:0">Μίλα στον διαχειριστή της εφαρμογής του χωριού. Μπορεί
    να διορθώσει λογαριασμούς, να σβήσει κάτι που δεν έπρεπε να γραφτεί, και να
    κλείσει λειτουργίες που το χωριό αποφασίσει ότι δεν θέλει.</p>
  </div>

  <div class="box">
    <h3>Τι αποφασίζει το χωριό</h3>
    <p style="margin:0">Κάθε λειτουργία — έκτακτα, μηνύματα, ημερολόγιο,
    τηλέφωνα, καιρός, ονόματα δρόμων — ανοίγει και κλείνει από τον διαχειριστή
    για όλους. Ό,τι κλείσει δεν διαγράφεται· απλώς παύει να χρησιμοποιείται.</p>
  </div>

  <div class="meta" style="position:absolute;bottom:16mm;left:20mm;right:20mm;
      border-top:1px solid #EDE6DC;padding-top:4mm;font-size:9pt;color:#7C8B84;
      display:flex;justify-content:space-between">
    <span>Ερωτήσεις: ο διαχειριστής της εφαρμογής</span>
    <span>Άγιος Νεκτάριος Αττικής</span>
  </div>
</div>

</body></html>"""


def main():
    HTML.write_text(build_html(), encoding="utf-8")
    result = subprocess.run(
        [CHROME, "--headless", "--disable-gpu", "--no-sandbox",
         "--no-pdf-header-footer", f"--print-to-pdf={OUT}", HTML.as_uri()],
        capture_output=True, text=True,
    )
    if not OUT.exists():
        print(result.stderr[-2000:], file=sys.stderr)
        sys.exit("Ο Chromium δεν έβγαλε PDF.")
    HTML.unlink()
    print(f"{OUT}  {OUT.stat().st_size} bytes")


if __name__ == "__main__":
    main()
