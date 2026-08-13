package gr.agiosnektarios.village.core

/**
 * [runCatching] for Firebase calls whose result value is discarded.
 *
 * A Firebase `Task<Void>` awaits to `Void!`, so a plain
 * `runCatching { doc.delete().await() }` infers `Result<Void!>` and will not
 * satisfy a `Result<Unit>` return type. Declaring the block as `() -> Unit`
 * makes Kotlin coerce the final expression, which keeps every repository
 * signature honest without a trailing `Unit` at two dozen call sites.
 */
internal inline fun runCatchingUnit(block: () -> Unit): Result<Unit> = runCatching(block)
