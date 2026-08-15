# Setting this up with only an Android phone

No computer needed. The work splits cleanly: you do the parts that require a
browser and a Google account, and the build machine does everything that
requires a terminal.

| You, in the phone browser | The build machine |
| --- | --- |
| Create the Firebase project | Build and sign the APK |
| Register the two Android apps | Deploy the security rules |
| Turn on Email/Password sign-in | (Optional) deploy push notifications |
| Create the Firestore database | |
| Download `google-services.json` and send it | |

**Turn on "Desktop site" in Chrome** before opening the Firebase console. The
mobile layout hides most of the settings you need. Chrome menu (⋮) → *Desktop
site*.

---

## 1. Create the project

<https://console.firebase.google.com> → *Create a project*. Any name. Google
Analytics can be off.

## 2. Register two Android apps

Gear icon → *Project settings* → *Your apps* → the Android icon. Do it twice:

- `gr.agiosnektarios.village`
- `gr.agiosnektarios.village.debug`

Two, because debug builds carry a `.debug` suffix and the build fails outright
if that package has no matching client.

While registering the second one, paste this into **SHA-1 certificate
fingerprint** — it is the app's committed debug key, and Google sign-in will not
work without it:

```
95:AD:51:7D:8D:DD:60:F3:63:7F:96:54:0E:15:D9:EA:93:CD:F3:25
```

(You can also add it later: *Project settings* → *Your apps* → *Add
fingerprint*. Email/password sign-in works without it.)

## 3. Download `google-services.json` — after both apps exist

Do **not** take the download Firebase offers right after the first app: that
copy has only one client and fails the build. Instead:

*Project settings* → *General* → scroll to *Your apps* → **google-services.json**

It lands in your Downloads folder. **Send that file in the chat.**

Nothing in it is secret — it ships inside every copy of the app — but it is
specific to your project.

## 4. Turn on sign-in

*Build* → *Authentication* → *Get started* → *Sign-in method*:

- **Email/Password** → enable. This alone is enough to use the app.
- **Google** → enable, if you want that button. It also creates the OAuth client
  the app needs, so without it the Google button reports "not configured".

## 5. Create the database

*Build* → *Firestore Database* → *Create database* → **Native mode** →
location **europe-west1** → **Production mode**.

Production mode's starting rules deny everything, which is correct — the real
rules go on in the next step. Until they do, the app will sign in and then show
only permission errors.

## 6. Get the rules deployed

The rules are the difference between "signs in, then everything fails" and a
working app. Two ways, pick one.

### Option A — paste them yourself (no credentials shared)

*Firestore Database* → *Rules* tab. Tap into the editor, select all, delete, and
paste the contents of
[`firebase/firestore.rules`](../firebase/firestore.rules). *Publish*.

That is the whole step. **No composite indexes to create.** Every query the app
makes is a shape Firestore indexes on its own, precisely so this page does not
end with ten minutes of form filling on a phone — see
[`firebase/firestore.indexes.json`](../firebase/firestore.indexes.json) for why.

### Option B — send a service account key and it gets done for you

*Project settings* → *Service accounts* → *Generate new private key*. Send the
downloaded JSON in the chat.

**Understand what this is.** Unlike `google-services.json`, this one *is* a
credential: it grants administrative access to the project — all data, all
settings. Only do this for a project that holds nothing you care about, and
revoke it when setup is done: *Service accounts* → the key → delete. Option A
shares nothing.

## 7. Maps — nothing to do

The map uses OpenStreetMap through MapLibre: no API key, no Google Cloud
console, no billing account. It just works.

---

## 8. Install the APK

You get back an `app-debug.apk`. Tap it; Android will ask permission to install
from that source. It installs alongside anything else — the package is
`gr.agiosnektarios.village.debug`.

Because the signing key is committed to the repo, later builds install straight
over the top as updates.

---

## What still will not work

The app is built to run on the free Spark plan, so almost everything works with
no billing at all: sign-up, the map, filing reports, **vote and comment counts**,
chat with unread badges, announcements, and the whole administration screen.

One thing needs the Blaze plan (a card on file, though village-scale usage sits
inside the free allowance):

- **Push notifications.** Sending them requires a credential that cannot ship
  inside an app, so it needs a server. Deploy `firebase/functions` when you want
  them; nothing else changes.

Photos are *not* on that list any more. Cloud Storage would need Blaze, so
pictures are stored as bytes inside Firestore documents instead — which is free,
at the cost of the size limits described in the README.

One thing has no workaround at all: deleting a resident removes their profile
and everything they wrote, but **not their login**. Only the Admin SDK can
delete somebody else's Firebase account. Signing in with it again lands on the
"complete your profile" screen as a brand new resident.

## Becoming the administrator

Roles are a field on your own user document, so you can do this from the phone
with no server and no terminal:

1. Sign up in the app first, so the document exists.
2. Firebase console → **Firestore Database** → **Data** → `users` collection →
   your document (the id is your account's uid — if there are several, match the
   `email` field).
3. **Add field** → name `role`, type `string`, value `ADMIN` → Update.
4. Force-close the app and reopen it.

The administration screen appears in your profile. From there you can promote
others, so this is the only time you need the console.

The rules stop an administrator editing their own `role`, which means you cannot
accidentally demote yourself and leave the village with no admin — if you ever
need to undo it, do it from the console the same way.

---

## Appendix: a prompt for Gemini

Gemini cannot press the buttons — no assistant can drive the Firebase console
for you. What it is good at is walking you through one screen at a time and
reading back error messages. It does not know this project, though, so a bare
"help me set up Firebase" gets generic advice that drifts from what this app
actually needs.

Paste this instead. Every project-specific fact it needs is included.

```text
I'm setting up a Firebase backend for an Android app. I have ONLY an Android
phone — no computer, no terminal, no Android Studio. Someone else compiles the
app for me; my job is only the Firebase console in Chrome.

Walk me through it ONE STEP AT A TIME. Wait for me to say "done" before the next
step. Assume I am not a developer: tell me exactly what to tap and what to type,
and warn me when a button is easy to miss on a phone screen.

These are the exact settings. Do not substitute your own:

- Two Android apps must be registered in the SAME Firebase project:
    gr.agiosnektarios.village
    gr.agiosnektarios.village.debug
  Both are required. The build fails if the .debug one is missing.
- SHA-1 fingerprint to add to the .debug app:
    95:AD:51:7D:8D:DD:60:F3:63:7F:96:54:0E:15:D9:EA:93:CD:F3:25
- Authentication: enable Email/Password. Also Google if it's easy.
- Firestore Database: Native mode, location europe-west1, Production mode.
- I need to download google-services.json, but ONLY after BOTH apps are
  registered — the copy offered right after the first app has only one app in
  it and breaks the build. Tell me the exact path to download the complete one.

Do NOT tell me to:
- install the Firebase CLI, npm, Node, or run any terminal command
- open Android Studio or edit any code
- set up Cloud Functions or Cloud Storage (they need paid billing; I'm skipping
  them for now)

Start by telling me how to make the Firebase console usable on a phone, then
give me step 1.

If I paste an error message, explain what it means in plain language and what to
tap to fix it.
```

**What to do with the results.** One thing comes back to the person building the
app: the `google-services.json` file. Nothing else from the console is needed —
the map needs no key.

**One thing Gemini cannot help with**: deploying the Firestore security rules.
Until they are published the app signs in and then fails every read and write.
See step 6 above — either paste `firebase/firestore.rules` into the console's
Rules tab yourself, or hand a service account key to whoever builds the app.
