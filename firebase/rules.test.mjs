import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import {
  doc, setDoc, getDoc, updateDoc, deleteDoc, collection, addDoc, serverTimestamp,
} from "firebase/firestore";

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
await check("open a direct chat", assertSucceeds(setDoc(doc(maria, "chats/giorgos_maria"), chat)));
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
await check("outsider cannot send into the conversation",
  assertFails(addDoc(collection(outsider, "chats/giorgos_maria/messages"),
    { senderId: "outsider", senderName: "X", senderPhotoUrl: "", text: "intruding",
      imageUrl: "", systemEvent: "", createdAt: serverTimestamp() })));

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

await env.cleanup();

const failed = results.filter(([ok]) => !ok);
for (const [ok, name] of results) console.log(`${ok ? "  PASS" : "  FAIL"}  ${name}`);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length ? 1 : 0);
