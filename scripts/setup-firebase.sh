#!/usr/bin/env bash
#
# Sets up the Firebase side of the village app.
#
# Everything the Firebase CLI is able to do is done here: creating the project,
# registering both Android apps, downloading google-services.json, creating the
# Firestore database, and deploying rules and indexes. Three things genuinely
# cannot be automated — they are printed at the end with exact click paths.
#
# Safe to re-run: every step checks for what already exists first, and nothing
# here deletes or overwrites remote data.
#
# Usage:
#   ./scripts/setup-firebase.sh                 # create a new project, prompts for an id
#   ./scripts/setup-firebase.sh my-project-id   # use an existing project

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

PACKAGE="gr.agiosnektarios.village"
DEBUG_PACKAGE="gr.agiosnektarios.village.debug"
# Must match the region the Cloud Functions and the client both pin.
REGION="europe-west1"

bold() { printf '\033[1m%s\033[0m\n' "$1"; }
info() { printf '  %s\n' "$1"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1"; }
die()  { printf '\033[31mx %s\033[0m\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------- prerequisites

bold "Checking prerequisites"

command -v firebase >/dev/null 2>&1 || die \
  "firebase CLI not found. Install it with: npm install -g firebase-tools"
ok "firebase CLI $(firebase --version)"

if ! firebase login:list 2>/dev/null | grep -q '@'; then
  warn "Not signed in — opening a browser to log in."
  firebase login
fi
ok "signed in as $(firebase login:list 2>/dev/null | grep -o '[^ ]*@[^ ]*' | head -1)"

# ------------------------------------------------------------------- project

bold "Project"

PROJECT_ID="${1:-}"
if [ -z "$PROJECT_ID" ]; then
  read -r -p "  Project id to create (lowercase, e.g. agios-nektarios-village): " PROJECT_ID
  [ -n "$PROJECT_ID" ] || die "A project id is required."
fi

if firebase projects:list 2>/dev/null | grep -q "\b${PROJECT_ID}\b"; then
  ok "using existing project $PROJECT_ID"
else
  info "creating project $PROJECT_ID…"
  # Project creation is the one step that can fail for reasons outside this
  # script's control (quota, an id already taken globally), so say so clearly.
  firebase projects:create "$PROJECT_ID" --display-name "Agios Nektarios" \
    || die "Could not create the project. The id may be taken — try another, or create it at https://console.firebase.google.com and re-run with: ./scripts/setup-firebase.sh <project-id>"
  ok "created $PROJECT_ID"
fi

# --------------------------------------------------------------- android apps

bold "Android apps"

# Two apps: debug builds carry the '.debug' applicationId suffix, and the
# google-services plugin refuses to build a variant with no matching client.
existing_apps="$(firebase apps:list ANDROID --project "$PROJECT_ID" 2>/dev/null || true)"

for pkg in "$PACKAGE" "$DEBUG_PACKAGE"; do
  if grep -q "$pkg" <<<"$existing_apps"; then
    ok "$pkg already registered"
  else
    info "registering $pkg…"
    firebase apps:create ANDROID "Agios Nektarios ${pkg##*.}" \
      --package-name "$pkg" --project "$PROJECT_ID" >/dev/null \
      || die "Could not register $pkg"
    ok "registered $pkg"
  fi
done

# ------------------------------------------------------------- google-services

bold "google-services.json"

APP_ID="$(firebase apps:list ANDROID --project "$PROJECT_ID" 2>/dev/null \
  | grep "$PACKAGE " | grep -oE '1:[0-9]+:android:[a-f0-9]+' | head -1 || true)"
[ -n "$APP_ID" ] || die "Could not determine the Android app id. Run: firebase apps:list ANDROID --project $PROJECT_ID"

if [ -f app/google-services.json ] && ! grep -q "REPLACE_WITH_YOUR" app/google-services.json; then
  warn "app/google-services.json already exists and is not the placeholder — leaving it alone."
  info "Delete it and re-run if you want it refreshed."
else
  firebase apps:sdkconfig ANDROID "$APP_ID" --project "$PROJECT_ID" --out app/google-services.json >/dev/null
  ok "wrote app/google-services.json (git-ignored)"
fi

# ------------------------------------------------------------------ firestore

bold "Firestore"

if firebase firestore:databases:list --project "$PROJECT_ID" 2>/dev/null | grep -q '(default)'; then
  ok "database already exists"
else
  info "creating the default database in $REGION…"
  firebase firestore:databases:create "(default)" \
    --location "$REGION" --project "$PROJECT_ID" >/dev/null \
    || die "Could not create the Firestore database. Create it once in the console (Build → Firestore Database → Create database, Native mode, $REGION) and re-run."
  ok "created the database in $REGION"
fi

# ------------------------------------------------------------ rules & indexes

bold "Rules and indexes"

info "deploying firestore rules and indexes…"
(cd firebase && firebase deploy --only firestore:rules,firestore:indexes --project "$PROJECT_ID") >/dev/null \
  || die "Rules/index deploy failed. Run it manually from firebase/ to see why."
ok "firestore rules and indexes deployed (indexes take a few minutes to build)"

# Storage rules need the bucket to exist, which needs Blaze on projects created
# after Oct 2024 — so a failure here is expected and not fatal.
if (cd firebase && firebase deploy --only storage --project "$PROJECT_ID") >/dev/null 2>&1; then
  ok "storage rules deployed"
else
  warn "storage rules not deployed — the bucket does not exist yet."
  info "Photo upload is the only feature that needs it. Enable Storage in the"
  info "console (Build → Storage → Get started), then re-run this script."
fi

# ------------------------------------------------------------------ functions

bold "Cloud Functions"

warn "not deployed — they are optional and require the Blaze billing plan."
info "The app is built to run without them: counters, chat badges and the"
info "whole admin screen are client transactions that the rules police."
info "Functions add push notifications and nothing else. To add them later:"
info ""
info "    cd firebase && npm --prefix functions install && firebase deploy --only functions"

# ---------------------------------------------------------------- signing cert

bold "Debug signing certificate"

info "Google sign-in will not work until this SHA-1 is registered in Firebase:"
echo
if ./gradlew -q signingReport 2>/dev/null | grep -A1 'Variant: debug' | grep -oE 'SHA1: [A-F0-9:]+' | head -1; then
  :
else
  warn "could not read it — run ./gradlew signingReport and look for the debug variant's SHA1."
fi

# ------------------------------------------------------------------- manual

echo
bold "Two things left, both in the browser"
cat <<EOF

  1. Turn on sign-in providers
     https://console.firebase.google.com/project/$PROJECT_ID/authentication/providers
     Enable "Email/Password" and "Google". Enabling Google is also what creates
     the OAuth web client that Google sign-in on Android needs.

  2. Register the debug SHA-1 printed above
     https://console.firebase.google.com/project/$PROJECT_ID/settings/general
     Under "Your apps" → $DEBUG_PACKAGE → "Add fingerprint".
     Skip this if you only care about email/password sign-in for now.

  Then build:

      ./gradlew assembleDebug

EOF
ok "done"
