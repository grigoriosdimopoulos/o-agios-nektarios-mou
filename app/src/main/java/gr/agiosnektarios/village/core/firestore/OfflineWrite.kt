package gr.agiosnektarios.village.core.firestore

/**
 * Writing something that must survive having no signal.
 *
 * The app already had an offline queue and nobody could reach it. Firestore is
 * configured here with unlimited persistent cache, so a `set` issued with no
 * network is written to disk immediately, shown to every listener on the device
 * at once, and sent when a connection returns — the data is safe the moment the
 * call is made.
 *
 * What is *not* immediate is the `Task` those calls return: it completes only
 * when the server acknowledges. Every repository here awaited it, so on a
 * hillside with one bar a resident filing a report watched a spinner until they
 * gave up and pressed back — and the report had in fact been saved and would
 * have arrived. The queue worked; the screen lied about it.
 *
 * So the wait is bounded. Inside the window a real failure — a rule that says
 * no, a malformed document — still comes back as a failure, which is the whole
 * reason not to simply fire and forget. Past it, the write is treated as done,
 * because on disk and on its way is done.
 *
 * A queued write is not an error and must never be shown as one. It is the
 * difference between "we lost it" and "it will go", and in a village where the
 * signal comes and goes that distinction is most of whether the app is
 * trusted — which is why the callers return success either way and let the
 * report open immediately from the local cache.
 */

/** How long to wait for the server before deciding the queue can have it. */
const val SERVER_ACK_MS = 4_000L
