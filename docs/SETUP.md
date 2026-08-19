# Setup

## The short way

Most of this is automated. Install the Firebase CLI, then run the script:

```bash
npm install -g firebase-tools
./scripts/setup-firebase.sh
```

It creates the project, registers both Android apps, downloads
`google-services.json`, creates the Firestore database, deploys the rules and
indexes, and prints your debug SHA-1. Three things it cannot do — enabling the
sign-in providers, registering that SHA-1, and creating a Maps key — are printed
at the end with direct links. It is safe to re-run.

**Cloud Functions are deliberately left out**, and the app no longer needs them:
counters, chat badges, cascading deletes and administration are all client
transactions constrained by the security rules. Functions add push notifications
and nothing else, and they require the Blaze billing plan. Photos do not need
them either: they live in Firestore rather than Cloud Storage.

The rest of this document is the same thing done by hand, plus troubleshooting.

---

## The long way

Everything needed to get the app building, running and talking to a real
backend. Expect the first pass to take about half an hour, most of it waiting
for Firebase.

## Prerequisites

- Android Studio Ladybug or newer, or a standalone Android SDK with API 35
- JDK 17
- Node.js 20 (only for the optional Cloud Functions and the rules tests)
- `firebase-tools` (`npm install -g firebase-tools`)
- A Google account. **No billing needed** — the app is built to run on the free
  Spark plan. Blaze buys push notifications and nothing else.

---

## 1. Firebase project

Create a project at <https://console.firebase.google.com>, then:

**Add TWO Android apps**, with package names `gr.agiosnektarios.village` and
`gr.agiosnektarios.village.debug`. Debug builds carry the `.debug` applicationId
suffix, and the google-services plugin fails the build outright when a variant's
applicationId has no matching client. (The alternative is dropping
`applicationIdSuffix` from `app/build.gradle.kts` and registering only the
first — but then a debug build shares a Firebase app with your release one.)

> **Register both before downloading anything.** Firebase offers you
> `google-services.json` the moment the first app is registered, and *that* copy
> contains only one client. Downloading it there is the single most common way to
> end up with a file that fails the build. Add both apps, then take the file from
> Project settings → General → "Your apps" → `google-services.json`.

Save it to `app/google-services.json`. It is git-ignored;
`app/google-services.json.example` shows the shape.

**Enable Authentication** → Sign-in method → **Email/Password** and **Google**.
Enabling Google creates the OAuth *web* client that Google sign-in on Android
needs; its id becomes the generated `default_web_client_id` string resource that
`GoogleCredentialClient` reads. If Google sign-in fails with "not configured",
this is what is missing.

Add your debug signing certificate's SHA-1 to the Android app
(Project settings → Your apps → Add fingerprint) — Google sign-in will not work
without it:

```bash
./gradlew signingReport      # copy the SHA1 of the debug variant
```

**Create Firestore** in Native mode, in a region near the village
(`europe-west1` matches the region the functions are pinned to).

> Pick **production mode** when asked, then deploy this repo's rules in step 4.
> Production mode's starting rules deny every read and write, so until that
> deploy the app will sign in and then show nothing but permission errors —
> which looks exactly like a broken configuration. Test mode instead allows
> everything for 30 days and then starts denying everything, which is a worse
> surprise later. Either way, **the rules deploy is not optional.**

**Enable Cloud Messaging** (it needs no configuration).

**Cloud Storage is not used at all.** It requires the Blaze plan on projects
created after October 2024, so photos are stored as bytes inside Firestore
documents instead — see *How photos are stored* below.

---

## 2. Maps — nothing to do

The map uses MapLibre with OpenStreetMap tiles. No API key, no Google Cloud
project, no billing account. It works the moment the app starts.

Tiles come from OpenFreeMap's public endpoint; the style URLs are
`MAP_STYLE_LIGHT` / `MAP_STYLE_DARK` in `VillageConfig.kt` if you ever want to
point at your own tile server.

---

## 3. Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

---

## 4. Deploy the backend

```bash
cd firebase
firebase use --add                 # pick the project created above
npm --prefix functions install
firebase deploy --only firestore:rules,firestore:indexes
```

That is the whole backend. `functions` is an optional extra that needs Blaze —
add it to the list once you have it. There is no `storage` target: the app does
not use Cloud Storage.

There are no composite indexes to wait on: the app only issues queries
Firestore indexes automatically, so the rules deploy is the whole story. The
`firestore:indexes` target above is kept in the command because
`firestore.indexes.json` still carries field overrides.

---

## 5. Make yourself an administrator

Roles are a `role` field on the user document, not a custom claim, so this needs
no server and no terminal:

1. Sign up in the app first, so your document exists.
2. Firebase console → Firestore Database → Data → `users` → your document
   (match the `email` field if several exist).
3. Add a field `role`, type string, value `ADMIN`.
4. Restart the app.

The security rules read that field with `get()`, so it takes effect immediately —
no token refresh, no sign-out. From then on you can promote others in the app.

The rules refuse to let an administrator edit *their own* `role` or `disabled`,
so the village cannot be locked out by a mis-tap; undo it in the console the
same way.

### The passphrase, and the hidden way in

There is a second route that needs no console at all: a passphrase that turns
whoever types it into an administrator.

**Setting it.** Firebase console → Firestore Database → Data → create a
collection `config` with a document id `admin`, holding one string field
`secret`. Until that document exists, every attempt fails — which is the right
default for a village that has not chosen one.

**Using it.** In the app: Settings → tap the version line at the bottom **seven
times** → type the passphrase. Nothing marks that line as tappable; the
hiddenness is the point, the same way Android hides developer options.

**How it is checked.** Never in the app. The passphrase document is unreadable
by every client, but a `get()` inside a security rule runs on the server and is
not itself subject to the rules — so the app writes what was typed to
`adminClaims/{uid}`, and the rule accepts that write only if it matches. The
accepted document is then proof the passphrase was known, which is what lets
the rule allow raising your own role. The app deletes it immediately afterwards
so the passphrase does not sit in the database.

**What you are choosing when you set one.** The passphrase *is* the
administrator credential for the village:

- Anyone who learns it can take administrator rights. Demoting them does not
  help — they can do it again. **Revoking someone means changing the
  passphrase**, not demoting them.
- Nothing rate-limits guesses. Make it long — a phrase of several unrelated
  words, not a word and a number.
- It is stored in plain text in Firestore. No client can read it; anyone with
  console access can.

If you would rather not have this route at all, simply never create
`config/admin`. The elevation path then refuses everyone, and the console
remains the only way to make an administrator.

---

## How photos are stored

There is no Cloud Storage bucket. Cloud Storage requires the Blaze plan on
projects created after October 2024, so every picture in this app is stored as
bytes inside a Firestore document — as a native `Blob` field rather than base64
text, which would add a third to the size for nothing.

Firestore hard-caps a document at **1 MiB**, and that shapes the whole design:

| Picture | Where it lives | Ceiling |
| --- | --- | --- |
| Report photo | `issues/{id}/photos/{photoId}`, one per document | 500 KB |
| Report card thumbnail | `thumbnail` on the report itself | 7 KB |
| Avatar | `avatar` on the user document | 12 KB |
| Announcement image | `image` on the announcement | 90 KB |
| Chat image | `image` on the message | 90 KB |

The split matters more than the numbers. The map reads **every** report in the
village on every launch, so anything living on a report document is multiplied
by the number of reports — a 100 KB thumbnail would mean tens of megabytes per
open. Full photos are therefore their own documents, read only when somebody
opens that report.

`ImageCodec` enforces the ceilings by stepping quality down and then dimensions
down until the encoded image fits, so a large photo is never rejected — only
made smaller. The rules enforce them again server-side, because a client is not
the right place for the only copy of a limit.

## The street network, and where its names come from

`app/src/main/assets/village_roads.json` holds the village's roads, extracted
from OpenStreetMap and drawn by the app itself on top of whichever basemap is
showing. It exists because satellite and terrain imagery contain no road lines
at all, and finding a pothole on an aerial photo without them is guesswork.

**Only one way in the village has a name.** OpenStreetMap records 82 ways here
and names exactly one — the Vilia–Porto Germeno road. The 33 residential
streets, 6 living streets and 8 service roads inside the settlement are
unnamed, and so is every one of them in Nominatim and in every public source
that can be reached. Inventing names for the streets of a real village would
put words in its mouth, and the app would state them with the same confidence
as facts.

Names were tried three times from the Greek state's valuation map
(maps.gsis.gr), which does list this settlement's streets, by matching that list
to ways by position — the WSW-ENE rows by north-to-south order, the cross
streets by west-to-east order. All three attempts were wrong, and the reason is
geometric: the settlement grid is rotated about 73 degrees, so "north to south"
is not a well-defined order over these ways at all. Measured on the geometry,
eight pairs of rows overlap the wrong way round, with a southern row's east end
sitting north of a northern row's west end. Ordering cannot identify a street
here, and a wrong street name is worse than a blank one — it is rendered with
the same authority as a surveyed coordinate.

So the names come from the people who live on them. Every feature in the asset
carries its OpenStreetMap `wayId`; tapping a road on the map opens a sheet where
a resident writes what the street is called, and neighbours confirm it. Those
names live at `streetNames/{wayId}` in Firestore — see `StreetNameRepository`
and the `streetNames` block in `firestore.rules`, one of the few places in this
app an ordinary resident writes to a document that is not their own, and
policed accordingly. Names the village confirms are also the ones worth
contributing back to OpenStreetMap, which fixes this everywhere at once; anyone
can, from a phone, with StreetComplete or Vespucci.

Regenerate the asset after OpenStreetMap changes, with the query the extraction
used:

```
[out:json][timeout:120];
way["highway"](38.1555,23.2810,38.1725,23.3020);
out geom;
```

Feed the result through the same filter: keep `secondary`/`tertiary`/
`unclassified` as `main`, `residential`/`living_street`/`service` as `street`,
`track` as `track`, drop everything else, and carry `name` across only where
OpenStreetMap itself has one. Keep the `wayId` — it is what resident-supplied
names are keyed on, and regenerating without it orphans every name in
Firestore.

The data is © OpenStreetMap contributors under ODbL; the map already carries
the attribution, which must stay.

---

## 6. Adjusting the geography

The app is set up for Άγιος Νεκτάριος Αττικής — the settlement in the Vilia
municipal unit of Mandra-Eidyllia, West Attica, tagged `place=village` in
OpenStreetMap at 38.16304 N, 23.28997 E. The extent is taken from the
OpenStreetMap road network rather than estimated: 1240 m north-south by
1139 m east-west.

Two files hold all of it, and nothing else in the app hardcodes a coordinate:

**`app/src/main/java/gr/agiosnektarios/village/core/VillageConfig.kt`** — the
settlement extent, the camera fence around it, and the zoom range.

**`app/src/main/assets/village_blocks.json`** — the neighbourhood outlines:

```json
{
  "blocks": [
    {
      "id": "sector-mc",
      "nameEl": "Κέντρο",
      "nameEn": "Village centre",
      "polygon": [
        { "lat": 38.162115, "lng": 23.289995 },
        { "lat": 38.162115, "lng": 23.294333 }
      ]
    }
  ]
}
```

The nine shipped sectors are named by compass direction because OpenStreetMap
records no named roads or quarters here. They are meant to be replaced with the
divisions residents actually use — see the header comment in the asset.

`VillageGeographyTest` fails the build if the two files stop describing the same
place: every polygon vertex must fall inside the camera bounds, and the mapped
area must stay village-sized. That test exists because they once did not, and
the app opened 40 km away.

Polygons may have any number of vertices and need not be rectangles. **Keep the
ids stable** once residents have neighbourhoods attached to their profiles —
renaming an id orphans every profile pointing at it.

Report counts are computed from each report's coordinates rather than its stored
`blockId`, so redrawing the outlines re-buckets historical reports correctly
with no data migration.

---

## Running against the emulators

```bash
cd firebase
firebase emulators:start
```

The app points at production Firebase by default. To use the emulator suite, add
the connection calls in `AppModule` — `useEmulator("10.0.2.2", 8080)` on
Firestore, `9099` on Auth, `5001` on Functions — guarded by
`BuildConfig.DEBUG`.

---

## Troubleshooting

**Google sign-in immediately fails.** Almost always a missing SHA-1 fingerprint
or a Firebase project without the Google provider enabled. Check that
`default_web_client_id` exists in the merged resources after a build.

**The map is blank.** The tile endpoint is unreachable — check the device has
a connection. There is no key to get wrong.

**Notifications never arrive.** Check in order: the POST_NOTIFICATIONS runtime
permission (Android 13+), the category switch in Settings — the server checks it
before sending — and whether the user document has any `fcmTokens`.

**A query fails with `FAILED_PRECONDITION`.** A composite index is missing,
which means a query was added in a shape the app deliberately avoids — an
equality or range filter combined with an `orderBy` on another field. Prefer
reshaping it to a single-key query and sorting the capped result locally, as
`observeChats` and `observeIssuesByAuthor` do. If the index is genuinely
unavoidable, the error message contains a link that creates it; add it to
`firebase/firestore.indexes.json` too, and note in `docs/PHONE-SETUP.md` that
setup now requires a step that cannot be done from a phone.
