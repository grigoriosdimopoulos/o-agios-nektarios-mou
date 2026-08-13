# Setup

Everything needed to get the app building, running and talking to a real
backend. Expect the first pass to take about half an hour, most of it waiting
for Firebase.

## Prerequisites

- Android Studio Ladybug or newer, or a standalone Android SDK with API 35
- JDK 17
- Node.js 20 (for the Cloud Functions)
- `firebase-tools` (`npm install -g firebase-tools`)
- A Google account with billing enabled on the Firebase project — Cloud
  Functions require the Blaze plan; at village scale the actual cost sits inside
  the free allowance.

---

## 1. Firebase project

Create a project at <https://console.firebase.google.com>, then:

**Add an Android app** with package name `gr.agiosnektarios.village`.

> Debug builds use the applicationId `gr.agiosnektarios.village.debug`. Either
> register that as a second Android app in the same Firebase project, or drop
> the `applicationIdSuffix` from `app/build.gradle.kts`. Without one of the two,
> debug builds cannot reach Firebase.

Download `google-services.json` and save it to `app/google-services.json`. It is
git-ignored; `app/google-services.json.example` shows the shape.

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

**Enable Storage** and **Cloud Messaging** (Messaging needs no configuration).

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
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

The indexes take a few minutes to build. Until they finish, the profile screen,
the chat list and the duplicate-detection query will fail with a
`FAILED_PRECONDITION` error naming the missing index — that is expected, not a
bug.

---

## 5. Make yourself an administrator

Roles are stored in a Firebase Auth custom claim, which no client can write. To
create the very first administrator, either:

**Option A — the bootstrap function.** Set the address on the function and call
it once from the app while signed in with that address:

```bash
firebase functions:config:unset bootstrap    # if a previous value exists
firebase deploy --only functions \
  --set-env-vars BOOTSTRAP_ADMIN_EMAIL=you@example.com
```

`bootstrapFirstAdmin` refuses to run once any administrator exists, so it closes
itself behind you.

**Option B — set the claim by hand,** from a machine with the Admin SDK
credentials:

```js
await admin.auth().setCustomUserClaims(uid, { role: "ADMIN" });
await admin.firestore().collection("users").doc(uid).update({ role: "ADMIN" });
```

Either way, **sign out and back in** afterwards. Custom claims are baked into the
ID token, so a promotion only takes effect when the token is renewed — within the
hour automatically, or immediately on a fresh sign-in. Until then the app shows
the admin tools (it reads the user document) but Firestore still rejects
admin-only writes (it reads the claim). That gap is expected and short.

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
