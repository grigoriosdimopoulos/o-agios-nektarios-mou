import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import {
  doc, setDoc, getDoc, updateDoc, deleteDoc, collection, addDoc, serverTimestamp,
  writeBatch, increment, query, where, getDocs, limit, Bytes, deleteField,
} from "firebase/firestore";

/** Stand-in for JPEG bytes; only the length matters to the rules. */
const image = (n) => Bytes.fromUint8Array(new Uint8Array(n));

const env = await initializeTestEnvironment({
  projectId: "demo-rules-check",
  firestore: {
    host: "127.0.0.1",
    port: 8080,
    rules: readFileSync(
      new URL("./firestore.rules", import.meta.url),
      "utf8",
    ),
  },
});

const results = [];
async function check(name, promise) {
  try {
    await promise;
    results.push([true, name]);
  } catch (e) {
    results.push([false, `${name}  →  ${e.message.split("\n")[0]}`]);
  }
}

const maria = env.authenticatedContext("maria").firestore();
const giorgos = env.authenticatedContext("giorgos").firestore();
const anon = env.unauthenticatedContext().firestore();

// The exact payload UserRepository.createProfile writes.
const profileSeed = (name) => ({
  firstName: name, lastName: "Test", nameLower: `${name} test`.toLowerCase(),
  email: `${name}@example.gr`, phone: "6971234567", address: "Marathonos 12",
  blockId: "block-01", photoUrl: "", role: "USER", disabled: false,
  issueCount: 0, resolvedCount: 0, upvotesReceived: 0,
  notificationPrefs: { comments: true, statusChanges: true, votes: true, announcements: true, chat: true },
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
});

// An administrator is now just a user document with role: ADMIN, seeded here
// through the privileged context so the rules themselves are what we test.
await env.withSecurityRulesDisabled(async (ctx) => {
  await ctx.firestore().doc("users/boss").set({ ...profileSeed("boss"), role: "ADMIN" });
});
const boss = env.authenticatedContext("boss").firestore();

// ---- the sign-up flow, which is what has to work first
await check("sign-up: create own profile",
  assertSucceeds(setDoc(doc(maria, "users/maria"), profileSeed("maria"))));
await check("sign-up: cannot create someone else's profile",
  assertFails(setDoc(doc(maria, "users/giorgos"), profileSeed("giorgos"))));
await check("sign-up: cannot self-promote to ADMIN",
  assertFails(setDoc(doc(giorgos, "users/giorgos"), { ...profileSeed("giorgos"), role: "ADMIN" })));

await check("sign-up: second resident",
  assertSucceeds(setDoc(doc(giorgos, "users/giorgos"), profileSeed("giorgos"))));

// ---- reading the directory
await check("read another resident's profile", assertSucceeds(getDoc(doc(maria, "users/giorgos"))));
await check("anonymous cannot read the directory", assertFails(getDoc(doc(anon, "users/maria"))));

// ---- profile editing
await check("edit own phone number",
  assertSucceeds(updateDoc(doc(maria, "users/maria"), { phone: "2109999999" })));
await check("cannot grant self a role via update",
  assertFails(updateDoc(doc(maria, "users/maria"), { role: "ADMIN" })));
await check("cannot rewrite own join date",
  assertFails(updateDoc(doc(maria, "users/maria"), { createdAt: new Date(0) })));
// Writing the value it already holds is a no-op that changed() correctly
// ignores, so the meaningful test is flipping it.
await check("cannot change own suspension state",
  assertFails(updateDoc(doc(maria, "users/maria"), { disabled: true })));

// ---- filing a report: the exact payload IssueRepository.createIssue writes
const issue = {
  title: "Fallen branch on Marathonos", description: "Blocking the pavement.",
  categoryId: "FALLEN_TREE", statusId: "OPEN",
  lat: 37.848, lng: 23.92, geohash: "swbb1234", blockId: "block-01",
  photoUrls: [], authorId: "maria", authorName: "Maria Test", authorPhotoUrl: "",
  upvotes: 0, downvotes: 0, score: 0, commentCount: 0,
  resolutionNote: "", resolvedById: "", resolvedByName: "",
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
};
await check("file a report", assertSucceeds(setDoc(doc(maria, "issues/issue1"), issue)));
await check("cannot file a report as someone else",
  assertFails(setDoc(doc(giorgos, "issues/issue2"), { ...issue, authorId: "maria" })));
await check("cannot file a report pre-loaded with upvotes",
  assertFails(setDoc(doc(maria, "issues/issue3"), { ...issue, upvotes: 500, score: 500 })));
await check("neighbour can read reports", assertSucceeds(getDoc(doc(giorgos, "issues/issue1"))));

// ---- editing and moderation
await check("author edits own report",
  assertSucceeds(updateDoc(doc(maria, "issues/issue1"), { title: "Fallen branch (still there)" })));
await check("neighbour cannot edit someone else's report",
  assertFails(updateDoc(doc(giorgos, "issues/issue1"), { title: "hijacked" })));
await check("nobody can forge the vote tally",
  assertFails(updateDoc(doc(maria, "issues/issue1"), { upvotes: 999 })));
await check("neighbour cannot delete someone else's report",
  assertFails(deleteDoc(doc(giorgos, "issues/issue1"))));

// ---- voting
await check("cast an upvote",
  assertSucceeds(setDoc(doc(giorgos, "issues/issue1/votes/giorgos"),
    { userId: "giorgos", value: 1, createdAt: serverTimestamp() })));
await check("cannot vote in someone else's name",
  assertFails(setDoc(doc(giorgos, "issues/issue1/votes/maria"),
    { userId: "maria", value: 1, createdAt: serverTimestamp() })));
await check("cannot cast a vote worth 50",
  assertFails(setDoc(doc(giorgos, "issues/issue1/votes/giorgos"),
    { userId: "giorgos", value: 50, createdAt: serverTimestamp() })));
await check("can retract own vote",
  assertSucceeds(deleteDoc(doc(giorgos, "issues/issue1/votes/giorgos"))));

// ---- comments
await check("post a comment",
  assertSucceeds(addDoc(collection(giorgos, "issues/issue1/comments"),
    { issueId: "issue1", authorId: "giorgos", authorName: "Giorgos Test",
      authorPhotoUrl: "", text: "Still blocking it today.", createdAt: serverTimestamp() })));
await check("cannot post a comment under another name",
  assertFails(addDoc(collection(giorgos, "issues/issue1/comments"),
    { issueId: "issue1", authorId: "maria", authorName: "Maria", authorPhotoUrl: "",
      text: "not me", createdAt: serverTimestamp() })));

// ---- announcements
await check("resident reads announcements",
  assertSucceeds(getDoc(doc(maria, "announcements/a1"))));
await check("non-admin cannot publish an announcement",
  assertFails(setDoc(doc(maria, "announcements/a1"),
    { title: "x", body: "y", authorId: "maria", authorName: "Maria",
      pinned: false, imageUrl: "", createdAt: serverTimestamp() })));

// ---- chat
const chat = {
  type: "DIRECT", title: "", photoUrl: "",
  memberIds: ["giorgos", "maria"],
  memberNames: { maria: "Maria Test", giorgos: "Giorgos Test" },
  memberPhotos: { maria: "", giorgos: "" },
  createdById: "maria", lastMessage: "", lastMessageSenderId: "",
  lastMessageAt: serverTimestamp(), unreadCounts: { maria: 0, giorgos: 0 },
  createdAt: serverTimestamp(),
};
// ChatRepository.openDirectChat reads the deterministic id first, to decide
// whether to create it. Every earlier test here jumped straight to setDoc and
// so never covered that read — which is how a suite this size stayed green
// while starting a direct message was impossible.
await check("a member may read their direct chat before it exists",
  assertSucceeds(getDoc(doc(maria, "chats/giorgos_maria"))));
await check("open a direct chat", assertSucceeds(setDoc(doc(maria, "chats/giorgos_maria"), chat)));
await check("and may still read it once it does",
  assertSucceeds(getDoc(doc(maria, "chats/giorgos_maria"))));
await check("cannot create a chat you are not in",
  assertFails(setDoc(doc(maria, "chats/other"), { ...chat, memberIds: ["giorgos", "someone"] })));
await check("member sends a message",
  assertSucceeds(addDoc(collection(maria, "chats/giorgos_maria/messages"),
    { senderId: "maria", senderName: "Maria", senderPhotoUrl: "", text: "hello",
      imageUrl: "", systemEvent: "", createdAt: serverTimestamp() })));

const outsider = env.authenticatedContext("outsider").firestore();
await setDoc(doc(outsider, "users/outsider"), profileSeed("outsider"));
await check("outsider cannot read the conversation",
  assertFails(getDoc(doc(outsider, "chats/giorgos_maria"))));
// The absent-document branch must not become a way to read anyone's chat.
await check("outsider cannot probe a conversation that does not exist",
  assertFails(getDoc(doc(outsider, "chats/giorgos_maria_missing"))));
await check("outsider cannot read a random absent chat id",
  assertFails(getDoc(doc(outsider, "chats/anything"))));
await check("a resident may read an absent direct id that includes them",
  assertSucceeds(getDoc(doc(outsider, "chats/maria_outsider"))));
await check("outsider cannot send into the conversation",
  assertFails(addDoc(collection(outsider, "chats/giorgos_maria/messages"),
    { senderId: "outsider", senderName: "X", senderPhotoUrl: "", text: "intruding",
      imageUrl: "", systemEvent: "", createdAt: serverTimestamp() })));

// ---- the whole conversation, in the order ChatRepository performs it
//
// The assertions above check rules one clause at a time. This one replays the
// actual sequence a resident triggers by tapping "message" and typing, because
// the bug that broke direct messages lived in the seam between two steps that
// were each fine on their own.
{
  const nikos = env.authenticatedContext("nikos").firestore();
  await setDoc(doc(nikos, "users/nikos"), profileSeed("nikos"));
  const id = ["nikos", "maria"].sort().join("_");

  // openDirectChat: read the deterministic id, then create it if absent.
  await check("flow: read the id before creating",
    assertSucceeds(getDoc(doc(nikos, `chats/${id}`))));
  await check("flow: create the conversation",
    assertSucceeds(setDoc(doc(nikos, `chats/${id}`), {
      ...chat, memberIds: ["maria", "nikos"], createdById: "nikos",
      memberNames: { maria: "Maria Test", nikos: "Nikos Test" },
      memberPhotos: { maria: "", nikos: "" },
      unreadCounts: { maria: 0, nikos: 0 },
    })));

  // sendMessage: read members, then one batch for the message and the preview.
  await check("flow: read members before sending",
    assertSucceeds(getDoc(doc(nikos, `chats/${id}`))));
  await check("flow: send the message and bump the preview in one batch",
    assertSucceeds((() => {
      const b = writeBatch(nikos);
      b.set(doc(collection(nikos, `chats/${id}/messages`)), {
        senderId: "nikos", senderName: "Nikos", senderPhotoUrl: "",
        text: "καλησπέρα", imageUrl: "", systemEvent: "",
        createdAt: serverTimestamp(),
      });
      b.update(doc(nikos, `chats/${id}`), {
        lastMessage: "καλησπέρα", lastMessageSenderId: "nikos",
        lastMessageAt: serverTimestamp(), "unreadCounts.maria": increment(1),
      });
      return b.commit();
    })()));

  // The recipient's chat list, exactly as observeChats queries it.
  await check("flow: recipient lists their conversations",
    assertSucceeds(getDocs(query(
      collection(maria, "chats"),
      where("memberIds", "array-contains", "maria"),
      limit(200),
    ))));
  await check("flow: recipient reads the thread",
    assertSucceeds(getDocs(collection(maria, `chats/${id}/messages`))));
  await check("flow: recipient clears their own badge",
    assertSucceeds(updateDoc(doc(maria, `chats/${id}`), { "unreadCounts.maria": 0 })));
  await check("flow: recipient replies",
    assertSucceeds(addDoc(collection(maria, `chats/${id}/messages`), {
      senderId: "maria", senderName: "Maria", senderPhotoUrl: "",
      text: "γεια", imageUrl: "", systemEvent: "", createdAt: serverTimestamp(),
    })));
}

// ---- photos, which live in documents because there is no Storage bucket
{
  await check("a report may carry a small inline thumbnail",
    assertSucceeds(setDoc(doc(maria, "issues/withthumb"), {
      ...issue, thumbnail: image(5_000),
    })));
  await check("but not a full photo smuggled into the report itself",
    assertFails(setDoc(doc(maria, "issues/fatthumb"), {
      ...issue, thumbnail: image(400_000),
    })));

  await check("the author attaches a photo",
    assertSucceeds(addDoc(collection(maria, "issues/withthumb/photos"), {
      data: image(200_000), authorId: "maria", createdAt: serverTimestamp(),
    })));
  await check("a photo over the document budget is refused",
    assertFails(addDoc(collection(maria, "issues/withthumb/photos"), {
      data: image(900_000), authorId: "maria", createdAt: serverTimestamp(),
    })));
  await check("nobody may attach a photo in someone else's name",
    assertFails(addDoc(collection(giorgos, "issues/withthumb/photos"), {
      data: image(1_000), authorId: "maria", createdAt: serverTimestamp(),
    })));
  await check("any resident may look at the photos",
    assertSucceeds(getDocs(collection(giorgos, "issues/withthumb/photos"))));

  await check("an avatar must stay small enough to ride in the user document",
    assertFails(updateDoc(doc(maria, "users/maria"), { avatar: image(200_000) })));
  await check("a reasonable avatar is fine",
    assertSucceeds(updateDoc(doc(maria, "users/maria"), { avatar: image(10_000) })));
}

// ---- notification inbox, the free-plan stand-in for server push
{
  const notice = {
    type: "COMMENT", title: "Fallen branch", body: "", bodyKey: "notif_new_comment",
    bodyArg: "", deepLink: "agiosnektarios://open/issue/issue1",
    actorId: "giorgos", collapseKey: "COMMENT:issue1", seen: false,
    createdAt: serverTimestamp(),
  };

  await check("a resident may put a notice in a neighbour's inbox",
    assertSucceeds(addDoc(collection(giorgos, "users/maria/notifications"), notice)));
  await check("but must sign it honestly",
    assertFails(addDoc(collection(giorgos, "users/maria/notifications"),
      { ...notice, actorId: "maria" })));
  await check("and cannot deliver it pre-read",
    assertFails(addDoc(collection(giorgos, "users/maria/notifications"),
      { ...notice, seen: true })));
  await check("an inbox is not bulk storage",
    assertFails(addDoc(collection(giorgos, "users/maria/notifications"),
      { ...notice, body: "x".repeat(600) })));

  await check("the owner reads their own inbox",
    assertSucceeds(getDocs(collection(maria, "users/maria/notifications"))));
  await check("nobody reads someone else's inbox",
    assertFails(getDocs(collection(giorgos, "users/maria/notifications"))));

  const suspended = env.authenticatedContext("banned").firestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/banned"),
      { ...profileSeed("banned"), disabled: true });
  });
  await check("a suspended resident cannot spam an inbox",
    assertFails(addDoc(collection(suspended, "users/maria/notifications"),
      { ...notice, actorId: "banned" })));
}

// ---- hidden administrator elevation
{
  // The village passphrase, as an admin would set it in the console. No client
  // may read this document; only a rule's get() can.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "config/admin"), { secret: "correct horse battery staple" });
  });

  const nikos = env.authenticatedContext("nikos").firestore();

  await check("the passphrase document is unreadable by anyone",
    assertFails(getDoc(doc(nikos, "config/admin"))));

  await check("a wrong passphrase is rejected outright",
    assertFails(setDoc(doc(nikos, "adminClaims/nikos"),
      { secret: "hunter2", userId: "nikos", createdAt: serverTimestamp() })));

  // The whole point: no claim, no elevation.
  await check("without a claim, nobody can make themselves an admin",
    assertFails(updateDoc(doc(nikos, "users/nikos"), { role: "ADMIN" })));

  await check("the right passphrase is accepted",
    assertSucceeds(setDoc(doc(nikos, "adminClaims/nikos"),
      { secret: "correct horse battery staple", userId: "nikos", createdAt: serverTimestamp() })));

  await check("a claim cannot be read back to recover the passphrase",
    assertFails(getDoc(doc(nikos, "adminClaims/nikos"))));

  await check("nobody can forge a claim in someone else's name",
    assertFails(setDoc(doc(nikos, "adminClaims/maria"),
      { secret: "correct horse battery staple", userId: "maria", createdAt: serverTimestamp() })));

  await check("with a claim, elevation is allowed",
    assertSucceeds(updateDoc(doc(nikos, "users/nikos"), { role: "ADMIN" })));

  await check("but elevation cannot smuggle anything else through",
    assertFails(updateDoc(doc(maria, "users/maria"), { role: "ADMIN", disabled: false, phone: "666" })));

  // Writing a field the value it already holds is a no-op that affectedKeys
  // correctly ignores, so this needs a resident who really is suspended and a
  // claim already sitting there — the state left behind if the app died between
  // proving the passphrase and deleting the claim.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users/exiled"), { ...profileSeed("exiled"), disabled: true });
    await setDoc(doc(db, "adminClaims/exiled"),
      { secret: "correct horse battery staple", userId: "exiled" });
  });
  const exiled = env.authenticatedContext("exiled").firestore();

  await check("a lingering claim cannot be used to un-suspend yourself",
    assertFails(updateDoc(doc(exiled, "users/exiled"), { role: "ADMIN", disabled: false })));
  await check("nor to re-admin yourself after being suspended",
    assertFails(updateDoc(doc(exiled, "users/exiled"), { role: "ADMIN" })));

  await check("the claim is deletable so the passphrase does not linger",
    assertSucceeds(deleteDoc(doc(nikos, "adminClaims/nikos"))));
}

// ---- counters the client now owns, which the rules have to police
async function seedIssue(id, authorId, fields = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`issues/${id}`).set({
      ...issue, authorId, upvotes: 0, downvotes: 0, score: 0, commentCount: 0, ...fields,
    });
  });
}

await seedIssue("counters", "maria");
await check("counter: a vote may move the tally by one",
  assertSucceeds(updateDoc(doc(giorgos, "issues/counters"),
    { upvotes: 1, downvotes: 0, score: 1 })));
await check("counter: cannot jump the tally to a thousand",
  assertFails(updateDoc(doc(giorgos, "issues/counters"),
    { upvotes: 1000, downvotes: 0, score: 1000 })));
await check("counter: score must equal upvotes minus downvotes",
  assertFails(updateDoc(doc(giorgos, "issues/counters"),
    { upvotes: 2, downvotes: 0, score: 99 })));
await check("counter: cannot go negative",
  assertFails(updateDoc(doc(giorgos, "issues/counters"),
    { upvotes: -1, downvotes: 0, score: -1 })));
await check("counter: cannot smuggle a title edit alongside a tally change",
  assertFails(updateDoc(doc(giorgos, "issues/counters"),
    { upvotes: 2, downvotes: 0, score: 2, title: "hijacked" })));
await check("counter: comment total may move by one",
  assertSucceeds(updateDoc(doc(giorgos, "issues/counters"), { commentCount: 1 })));
await check("counter: comment total cannot jump",
  assertFails(updateDoc(doc(giorgos, "issues/counters"), { commentCount: 50 })));

// ---- cascading delete, now done by the client
await seedIssue("cascade", "maria");
await env.withSecurityRulesDisabled(async (ctx) => {
  await ctx.firestore().doc("issues/cascade/votes/giorgos")
    .set({ userId: "giorgos", value: 1 });
  await ctx.firestore().doc("issues/cascade/comments/c1")
    .set({ authorId: "giorgos", text: "hi", issueId: "cascade" });
});
await check("author sweeps votes under their own report",
  assertSucceeds(deleteDoc(doc(maria, "issues/cascade/votes/giorgos"))));
await check("author sweeps comments under their own report",
  assertSucceeds(deleteDoc(doc(maria, "issues/cascade/comments/c1"))));
await check("a stranger cannot sweep another report's votes",
  assertFails(deleteDoc(doc(outsider, "issues/counters/votes/giorgos"))));

// ---- roles held in Firestore rather than a custom claim
await check("admin may change someone's role",
  assertSucceeds(updateDoc(doc(boss, "users/giorgos"), { role: "MODERATOR" })));
await check("admin may suspend someone",
  assertSucceeds(updateDoc(doc(boss, "users/giorgos"), { disabled: true })));
await check("admin cannot demote themselves and orphan the village",
  assertFails(updateDoc(doc(boss, "users/boss"), { role: "USER" })));
await check("ordinary resident cannot change anyone's role",
  assertFails(updateDoc(doc(maria, "users/giorgos"), { role: "ADMIN" })));
await env.withSecurityRulesDisabled(async (ctx) => {
  await ctx.firestore().doc("users/giorgos").update({ disabled: false, role: "USER" });
});

// ---- a suspended resident is frozen out entirely
await env.withSecurityRulesDisabled(async (ctx) => {
  await ctx.firestore().doc("users/banned").set({ ...profileSeed("banned"), disabled: true });
});
const banned = env.authenticatedContext("banned").firestore();
await check("suspended resident cannot read reports",
  assertFails(getDoc(doc(banned, "issues/counters"))));
await check("suspended resident cannot file a report",
  assertFails(setDoc(doc(banned, "issues/nope"), { ...issue, authorId: "banned" })));

// ---- admins publish announcements, residents do not
await check("admin publishes an announcement",
  assertSucceeds(setDoc(doc(boss, "announcements/a2"),
    { title: "Water off Tuesday", body: "09:00-14:00", authorId: "boss",
      authorName: "Boss", pinned: false, imageUrl: "", createdAt: serverTimestamp() })));

// ---- closing your own account
await check("resident may delete their own profile",
  assertSucceeds(deleteDoc(doc(banned, "users/banned"))));
await check("resident cannot delete a neighbour's profile",
  assertFails(deleteDoc(doc(maria, "users/giorgos"))));

// ---- chat previews, now written by the sender
await check("member updates the conversation preview",
  assertSucceeds(updateDoc(doc(maria, "chats/giorgos_maria"),
    { lastMessage: "hello", lastMessageSenderId: "maria", "unreadCounts.giorgos": 1 })));
await check("outsider cannot touch the conversation preview",
  assertFails(updateDoc(doc(outsider, "chats/giorgos_maria"), { lastMessage: "spam" })));

// ---- residents naming their own streets
//
// This is the one collection an ordinary resident may write to that is not
// theirs, so it gets the most adversarial run in this file: every way someone
// could name a street on a neighbour's behalf, fake agreement with it, or
// quietly rewrite a name other people had already confirmed.
const streetProposal = (uid, name) => ({
  name, proposedById: uid, proposedByName: uid, confirmedBy: [uid],
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
});

await check("resident names an unnamed street",
  assertSucceeds(setDoc(doc(maria, "streetNames/852314600"),
    streetProposal("maria", "Ελατιάς"))));
await check("resident cannot propose under a neighbour's name",
  assertFails(setDoc(doc(giorgos, "streetNames/852314601"),
    streetProposal("maria", "Διονύσου"))));
await check("a proposal cannot arrive pre-confirmed by others",
  assertFails(setDoc(doc(giorgos, "streetNames/852314602"),
    { ...streetProposal("giorgos", "Ζήρα"), confirmedBy: ["giorgos", "maria", "boss"] })));
await check("an empty street name is refused",
  assertFails(setDoc(doc(giorgos, "streetNames/852314603"),
    streetProposal("giorgos", ""))));
await check("an overlong street name is refused",
  assertFails(setDoc(doc(giorgos, "streetNames/852314604"),
    streetProposal("giorgos", "Ελ".repeat(40)))));
await check("signed-out visitor cannot read street names",
  assertFails(getDoc(doc(anon, "streetNames/852314600"))));

await check("neighbour confirms the name",
  assertSucceeds(updateDoc(doc(giorgos, "streetNames/852314600"),
    { confirmedBy: ["maria", "giorgos"], updatedAt: serverTimestamp() })));
await check("neighbour withdraws their confirmation",
  assertSucceeds(updateDoc(doc(giorgos, "streetNames/852314600"),
    { confirmedBy: ["maria"], updatedAt: serverTimestamp() })));
await check("nobody may confirm on someone else's behalf",
  assertFails(updateDoc(doc(giorgos, "streetNames/852314600"),
    { confirmedBy: ["maria", "boss"], updatedAt: serverTimestamp() })));
await check("nobody may strike out a neighbour's confirmation",
  assertFails(updateDoc(doc(giorgos, "streetNames/852314600"),
    { confirmedBy: [], updatedAt: serverTimestamp() })));
await check("a confirmation cannot smuggle in a new name",
  assertFails(updateDoc(doc(giorgos, "streetNames/852314600"),
    { name: "Σκρα", confirmedBy: ["maria", "giorgos"], updatedAt: serverTimestamp() })));

await check("a neighbour cannot rewrite a confirmed name",
  assertFails(setDoc(doc(giorgos, "streetNames/852314600"),
    streetProposal("giorgos", "Σκρα"))));
await check("the proposer may correct their own street name",
  assertSucceeds(setDoc(doc(maria, "streetNames/852314600"),
    streetProposal("maria", "Ελατείας"))));
await check("a moderator may correct any street name",
  assertSucceeds(setDoc(doc(boss, "streetNames/852314600"),
    streetProposal("boss", "Ελατιάς"))));
await check("a resident cannot delete a street name",
  assertFails(deleteDoc(doc(maria, "streetNames/852314600"))));
await check("a moderator may delete a street name",
  assertSucceeds(deleteDoc(doc(boss, "streetNames/852314600"))));

// ---- residents taking a report on
//
// The only write an ordinary resident may make to a neighbour's issue, so it
// gets the same adversarial treatment as the street names.
await env.withSecurityRulesDisabled(async (ctx) => {
  await ctx.firestore().doc("issues/takeable").set({
    // Authored by neither maria nor giorgos, so that "can a bystander do X"
    // actually tests the take/release rule rather than the author's own edit
    // rights — which is what made three of these pass when they should not.
    ...issue, authorId: "boss", authorName: "Boss", assigneeId: "", assigneeName: "",
    upvotes: 0, downvotes: 0, score: 0, commentCount: 0,
  });
});

await check("a resident takes an unclaimed report",
  assertSucceeds(updateDoc(doc(maria, "issues/takeable"),
    { assigneeId: "maria", assigneeName: "Maria Test", assignedAt: serverTimestamp(),
      updatedAt: serverTimestamp() })));
await check("nobody may put a neighbour's name on a job",
  assertFails(updateDoc(doc(giorgos, "issues/takeable"),
    { assigneeId: "maria", assigneeName: "Maria Test", updatedAt: serverTimestamp() })));
await check("a second resident cannot take an already-claimed report",
  assertFails(updateDoc(doc(giorgos, "issues/takeable"),
    { assigneeId: "giorgos", assigneeName: "Giorgos Test", updatedAt: serverTimestamp() })));
await check("taking cannot smuggle a title edit through",
  assertFails(updateDoc(doc(giorgos, "issues/takeable"),
    { assigneeId: "giorgos", assigneeName: "G", title: "hijacked", updatedAt: serverTimestamp() })));
await check("taking cannot smuggle a vote through",
  assertFails(updateDoc(doc(giorgos, "issues/takeable"),
    { assigneeId: "giorgos", assigneeName: "G", upvotes: 99, updatedAt: serverTimestamp() })));
await check("a bystander cannot release someone else's claim",
  assertFails(updateDoc(doc(giorgos, "issues/takeable"),
    { assigneeId: "", assigneeName: "", updatedAt: serverTimestamp() })));
await check("the holder may give it back",
  assertSucceeds(updateDoc(doc(maria, "issues/takeable"),
    { assigneeId: "", assigneeName: "", updatedAt: serverTimestamp() })));
// A report filed before assignment existed: no assigneeId key at all. Every
// other fixture here seeds the field, which is exactly why the suite stayed
// green while this case was denied on every real document in the database.
await env.withSecurityRulesDisabled(async (ctx) => {
  const { assigneeId, assigneeName, ...legacy } = { ...issue, authorId: "boss", assigneeId: "", assigneeName: "" };
  await ctx.firestore().doc("issues/legacy").set(legacy);
});
await check("a report filed before assignment existed can still be taken",
  assertSucceeds(updateDoc(doc(maria, "issues/legacy"),
    { assigneeId: "maria", assigneeName: "Maria Test", assignedAt: serverTimestamp(),
      updatedAt: serverTimestamp() })));

// Releasing must clear the name with the id, or a resident's name stays
// printed against a job nobody holds.
await check("releasing cannot leave the name behind",
  assertFails(updateDoc(doc(maria, "issues/legacy"),
    { assigneeId: "", updatedAt: serverTimestamp() })));
await check("releasing clears both",
  assertSucceeds(updateDoc(doc(maria, "issues/legacy"),
    { assigneeId: "", assigneeName: "", updatedAt: serverTimestamp() })));

// A finished report is not work anyone can pick up.
await env.withSecurityRulesDisabled(async (ctx) => {
  await ctx.firestore().doc("issues/done").set({
    ...issue, authorId: "boss", statusId: "RESOLVED", assigneeId: "", assigneeName: "",
  });
});
await check("a resolved report cannot be taken on",
  assertFails(updateDoc(doc(maria, "issues/done"),
    { assigneeId: "maria", assigneeName: "Maria Test", updatedAt: serverTimestamp() })));

await check("a moderator may unstick any claim",
  assertSucceeds(updateDoc(doc(boss, "issues/takeable"),
    { assigneeId: "", assigneeName: "", updatedAt: serverTimestamp() })));

// ---- the village calendar
//
// The point of the calendar is that anybody may put something on it. The point
// of these tests is that "anybody may say they are coming" does not quietly
// become "anybody may edit anything".
const eventSeed = (uid, name, extra = {}) => ({
  title: "Καθαρισμός δασικού δρόμου",
  description: "Φέρτε γάντια.",
  place: "Πλατεία",
  kind: "WORK_DAY",
  startAt: new Date("2026-09-05T07:00:00Z"),
  endAt: null,
  allDay: false,
  authorId: uid,
  authorName: name,
  attendees: { [uid]: name },
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  ...extra,
});

await check("calendar: an ordinary resident may add an event",
  assertSucceeds(setDoc(doc(maria, "events/workday"), eventSeed("maria", "Maria Test"))));
await check("calendar: an event must credit its real author",
  assertFails(setDoc(doc(giorgos, "events/forged"), eventSeed("maria", "Maria Test"))));
await check("calendar: an event cannot open with a crowd already attending",
  assertFails(setDoc(doc(giorgos, "events/stuffed"),
    eventSeed("giorgos", "Giorgos Test", { attendees: { giorgos: "Giorgos Test", maria: "Maria Test" } }))));
await check("calendar: a title is required",
  assertFails(setDoc(doc(giorgos, "events/untitled"),
    eventSeed("giorgos", "Giorgos Test", { title: "" }))));
await check("calendar: a start time is required",
  assertFails(setDoc(doc(giorgos, "events/undated"),
    eventSeed("giorgos", "Giorgos Test", { startAt: "next Tuesday" }))));
await check("calendar: everyone reads it",
  assertSucceeds(getDoc(doc(giorgos, "events/workday"))));
await check("calendar: a suspended resident does not",
  assertFails(getDoc(doc(banned, "events/workday"))));

await check("calendar: a neighbour says they are coming",
  assertSucceeds(updateDoc(doc(giorgos, "events/workday"),
    { "attendees.giorgos": "Giorgos Test", updatedAt: serverTimestamp() })));
await check("calendar: and takes it back",
  assertSucceeds(updateDoc(doc(giorgos, "events/workday"),
    { "attendees.giorgos": deleteField(), updatedAt: serverTimestamp() })));

// The whole reason attendance is a map and not two parallel arrays: with
// arrays no rule can tell whose name was added, so anyone could sign up a
// neighbour. With a map the diff names the key, and the key must be your own.
// Writing a neighbour's *existing* entry back unchanged is a no-op and is
// allowed, because it alters nothing — the diff is empty. These are the writes
// that actually change somebody else's row, and they are the ones that matter.
await check("calendar: nobody signs a neighbour up",
  assertFails(updateDoc(doc(giorgos, "events/workday"),
    { "attendees.boss": "Boss Test", updatedAt: serverTimestamp() })));
await check("calendar: nobody rewrites a neighbour's name",
  assertFails(updateDoc(doc(giorgos, "events/workday"),
    { "attendees.maria": "Κάποιος άλλος", updatedAt: serverTimestamp() })));
await check("calendar: nobody strikes a neighbour off",
  assertFails(updateDoc(doc(giorgos, "events/workday"),
    { "attendees.maria": deleteField(), updatedAt: serverTimestamp() })));
await check("calendar: attending is not a way to edit the event",
  assertFails(updateDoc(doc(giorgos, "events/workday"),
    { "attendees.giorgos": "Giorgos Test", title: "Ακυρώθηκε", updatedAt: serverTimestamp() })));
await check("calendar: an attendee name cannot be blank",
  assertFails(updateDoc(doc(giorgos, "events/workday"),
    { "attendees.giorgos": "", updatedAt: serverTimestamp() })));

await check("calendar: a neighbour cannot rewrite the event",
  assertFails(updateDoc(doc(giorgos, "events/workday"),
    { title: "Ακυρώθηκε", updatedAt: serverTimestamp() })));
await check("calendar: the organiser can",
  assertSucceeds(updateDoc(doc(maria, "events/workday"),
    { title: "Καθαρισμός — νέα ώρα", updatedAt: serverTimestamp() })));
await check("calendar: the organiser cannot hand over authorship",
  assertFails(updateDoc(doc(maria, "events/workday"),
    { authorId: "giorgos", updatedAt: serverTimestamp() })));
await check("calendar: a neighbour cannot delete it",
  assertFails(deleteDoc(doc(giorgos, "events/workday"))));
// The hole that the whole map-instead-of-arrays design was supposed to close,
// and did not: the organiser could not add a neighbour one key at a time, but
// could rewrite the entire attendee map in an ordinary edit.
await check("calendar: the organiser cannot rewrite the attendee list",
  assertFails(updateDoc(doc(maria, "events/workday"),
    { attendees: { maria: "Maria Test", giorgos: "Giorgos Test", boss: "Boss Test" },
      updatedAt: serverTimestamp() })));
// A moderator writing their *own* key is just a moderator saying they will be
// there, and the toggle rightly allows it. What must fail is signing up a
// third party, which is the same forgery with a badge on.
await check("calendar: a moderator cannot sign up a third party either",
  assertFails(updateDoc(doc(boss, "events/workday"),
    { attendees: { maria: "Maria Test", giorgos: "Giorgos Test" },
      updatedAt: serverTimestamp() })));
// Emptying a list that holds nobody but you is just leaving, and the toggle
// allows it. Emptying one with a neighbour in it is not.
await check("calendar: a neighbour joins so there is somebody to strike off",
  assertSucceeds(updateDoc(doc(giorgos, "events/workday"),
    { "attendees.giorgos": "Giorgos Test", updatedAt: serverTimestamp() })));
await check("calendar: the organiser cannot clear the list",
  assertFails(updateDoc(doc(maria, "events/workday"),
    { attendees: {}, updatedAt: serverTimestamp() })));
await check("calendar: leaving a list you are alone on is allowed",
  assertSucceeds(updateDoc(doc(giorgos, "events/workday"),
    { "attendees.giorgos": deleteField(), updatedAt: serverTimestamp() })));

// Bounds. Sixty events are shown by start time, so a handful dated far ahead
// is enough to fill everyone's calendar — and the card prints no year.
await check("calendar: no events in 2099",
  assertFails(setDoc(doc(giorgos, "events/far"),
    eventSeed("giorgos", "Giorgos Test", { startAt: new Date("2099-12-31T10:00:00Z") }))));
await check("calendar: no events in 1900",
  assertFails(setDoc(doc(giorgos, "events/ancient"),
    eventSeed("giorgos", "Giorgos Test", { startAt: new Date("1900-01-01T10:00:00Z") }))));
await check("calendar: next summer is fine",
  assertSucceeds(setDoc(doc(giorgos, "events/panigyri"),
    eventSeed("giorgos", "Giorgos Test",
      { startAt: new Date(Date.now() + 300 * 24 * 3600 * 1000) }))));
await check("calendar: allDay must be a boolean",
  assertFails(setDoc(doc(giorgos, "events/yes"),
    eventSeed("giorgos", "Giorgos Test", { allDay: "yes" }))));
await check("calendar: endAt must be a timestamp",
  assertFails(setDoc(doc(giorgos, "events/whenever"),
    eventSeed("giorgos", "Giorgos Test", { endAt: "whenever" }))));
await check("calendar: an attendee name must be a string",
  assertFails(setDoc(doc(giorgos, "events/numbered"),
    eventSeed("giorgos", "Giorgos Test", { attendees: { giorgos: 12345 } }))));
await check("calendar: no arbitrary extra fields",
  assertFails(setDoc(doc(giorgos, "events/payload"),
    eventSeed("giorgos", "Giorgos Test", { payload: "x".repeat(40000) }))));
await check("calendar: an author name has a ceiling",
  assertFails(setDoc(doc(giorgos, "events/longname"),
    eventSeed("giorgos", "x".repeat(40000)))));
await check("calendar: a kind has a ceiling",
  assertFails(setDoc(doc(giorgos, "events/longkind"),
    eventSeed("giorgos", "Giorgos Test", { kind: "x".repeat(400) }))));

await check("calendar: a moderator can",
  assertSucceeds(updateDoc(doc(boss, "events/workday"),
    { title: "Αναβλήθηκε", updatedAt: serverTimestamp() })));

// An event written before attendance existed must still be joinable. Reading a
// field that is not there is an evaluation error, and it would deny the write.
await env.withSecurityRulesDisabled(async (ctx) => {
  await ctx.firestore().doc("events/legacy").set({
    title: "Παλιά εκδήλωση", startAt: new Date("2026-09-20T07:00:00Z"),
    authorId: "boss", authorName: "Boss Test",
  });
});
await check("calendar: an event with no attendee field can still be joined",
  assertSucceeds(updateDoc(doc(maria, "events/legacy"),
    { "attendees.maria": "Maria Test", updatedAt: serverTimestamp() })));

// ---- the village's own telephone numbers
const contactSeed = {
  name: "Αγροτικό Ιατρείο Βιλίων",
  number: "22630 00000",
  note: "Δευτέρα-Παρασκευή",
  kind: "HEALTH",
  order: 0,
  createdById: "boss",
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
};
await check("contacts: an administrator adds one",
  assertSucceeds(setDoc(doc(boss, "contacts/surgery"), contactSeed)));
await check("contacts: an ordinary resident does not",
  assertFails(setDoc(doc(maria, "contacts/invented"), contactSeed)));
await check("contacts: residents read them",
  assertSucceeds(getDoc(doc(maria, "contacts/surgery"))));
await check("contacts: a suspended resident does not",
  assertFails(getDoc(doc(banned, "contacts/surgery"))));
await check("contacts: a number is required",
  assertFails(setDoc(doc(boss, "contacts/nameless"), { ...contactSeed, number: "" })));
await check("contacts: a resident cannot edit one",
  assertFails(updateDoc(doc(maria, "contacts/surgery"), { number: "6900000000" })));
await check("contacts: a resident cannot delete one",
  assertFails(deleteDoc(doc(maria, "contacts/surgery"))));
await check("contacts: an administrator can",
  assertSucceeds(deleteDoc(doc(boss, "contacts/surgery"))));

await env.cleanup();

const failed = results.filter(([ok]) => !ok);
for (const [ok, name] of results) console.log(`${ok ? "  PASS" : "  FAIL"}  ${name}`);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length ? 1 : 0);
