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

**Reports.** Title, description, category, photos, a pin. Upvote and downvote,
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
| Backend | Firebase Auth, Firestore, Storage, Cloud Messaging, Cloud Functions |
| Maps | Google Maps Compose |
| Local prefs | DataStore |

```
app/src/main/java/gr/agiosnektarios/village/
├── core/           models, geo maths, validation, DI, Firestore helpers
├── data/           repositories — the only things that touch Firebase
├── notifications/  FCM service, channels, token synchronisation
└── ui/             theme, shared components, one package per feature
firebase/
├── firestore.rules  storage.rules  firestore.indexes.json
└── functions/       counters, notifications, privileged operations
```

### Three decisions worth knowing about

**Every counter is server-owned.** Vote tallies, comment totals and unread
badges are written only by Cloud Functions, and the security rules reject a
client write that touches them. A client casts a vote by writing one document at
`issues/{id}/votes/{uid}` — the path itself makes double-voting impossible — and
the function recounts from the subcollection rather than incrementing. That makes
the totals self-healing: a lost trigger is corrected by the next vote instead of
leaving a permanently wrong number on screen.

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

Cloud Functions are left out of the script because they need the Blaze billing
plan — the app runs without them, minus the server-owned counters and push
notifications. Full instructions, troubleshooting, and how to make yourself the
first administrator are in **[docs/SETUP.md](docs/SETUP.md)**.

Neither `google-services.json` nor `local.properties` is committed; both are
git-ignored.

## Tests

```bash
./gradlew testDebugUnitTest            # 37 tests: geohashing, clustering, validation, permissions
./gradlew assembleRelease              # also runs R8 and lint's fatal checks
cd firebase/functions && npx tsc --noEmit   # Cloud Functions typecheck
npm --prefix firebase/functions run test:rules  # 30 security-rule tests, in the emulator
```

The unit tests cover the parts where a subtle mistake would be invisible in the
UI: geohash encoding, the clustering rules, phone and password validation, and
who is allowed to edit what.

`firebase/rules.test.mjs` runs the security rules against the Firestore
emulator using the exact payloads the repositories write. It asserts both
halves: that an ordinary resident can sign up, file a report, vote and comment —
and that nobody can promote themselves to admin, forge a vote tally, write under
someone else's name, or read a conversation they are not in. Rules are the one
place where a mistake is silent in development and serious in production.
