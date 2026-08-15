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
and nothing else, and they require the Blaze billing plan.

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
  Spark plan. Blaze buys push notifications and photo upload, nothing else.

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

**Enable Storage** and **Cloud Messaging** (Messaging needs no configuration).
Storage is only used for photo uploads; on projects created after October 2024
it requires the Blaze plan, and the app runs fine without it.

---

## 2. Google Maps key

In the [Google Cloud console](https://console.cloud.google.com/apis/credentials)
for the *same* project, enable **Maps SDK for Android** and create an API key.
Restrict it to Android apps, with the package name and SHA-1 above.

```bash
echo "MAPS_API_KEY=AIza..." >> local.properties
```

`local.properties` is git-ignored. CI can supply the key as a `MAPS_API_KEY`
environment variable or Gradle property instead. An empty key still builds — the
map simply renders blank, which is a useful way to tell "no key" apart from "no
data".

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

That is the whole backend. `storage` and `functions` are optional extras that
need Blaze — add them to the list once you have it.

The indexes take a few minutes to build. Until they finish, the profile screen,
the chat list and the duplicate-detection query will fail with a
`FAILED_PRECONDITION` error naming the missing index — that is expected, not a
bug.

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

---

## 6. Replace the placeholder geography

Two files, and nothing else in the app hardcodes a coordinate:

**`app/src/main/java/gr/agiosnektarios/village/core/VillageConfig.kt`** — the
village centre and the camera bounds.

**`app/src/main/assets/village_blocks.json`** — the neighbourhood outlines:

```json
{
  "version": 1,
  "blocks": [
    {
      "id": "block-01",
      "nameEl": "Κέντρο",
      "nameEn": "Village Centre",
      "polygon": [
        { "lat": 37.8355, "lng": 23.9040 },
        { "lat": 37.8355, "lng": 23.9120 }
      ]
    }
  ]
}
```

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
Firestore, `9099` on Auth, `9199` on Storage, `5001` on Functions — guarded by
`BuildConfig.DEBUG`.

---

## Troubleshooting

**Google sign-in immediately fails.** Almost always a missing SHA-1 fingerprint
or a Firebase project without the Google provider enabled. Check that
`default_web_client_id` exists in the merged resources after a build.

**The map is grey.** No Maps API key, or the key is not authorised for
Maps SDK for Android, or its restriction does not match the package name and
SHA-1 of the build you are running.

**Notifications never arrive.** Check in order: the POST_NOTIFICATIONS runtime
permission (Android 13+), the category switch in Settings — the server checks it
before sending — and whether the user document has any `fcmTokens`.

**A query fails with `FAILED_PRECONDITION`.** A composite index is missing. The
error message contains a link that creates it; also add it to
`firebase/firestore.indexes.json` so it survives the next deploy.
