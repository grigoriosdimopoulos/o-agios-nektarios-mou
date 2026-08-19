package gr.agiosnektarios.village.ui.issue

import android.content.Context
import android.content.Intent
import android.net.Uri
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.Issue
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Getting a report out of the app and in front of somebody who can fix it.
 *
 * This is the thing whose absence made the rest of the app a diary. Twelve
 * residents can agree that a culvert is blocked and it changes nothing until
 * one of them tells the municipality — and until now the app could not help
 * with that at all. There was no export, no message, no way to hand over the
 * photograph and the coordinate together.
 *
 * Two channels, because a village office answers to different things on
 * different days: an e-mail with everything laid out, or the same text as a
 * message. Both go through the phone's own apps with the text prepared and
 * nothing sent, so the person forwarding it reads what goes out over their own
 * name — which is the only honest way for an app to write on somebody's behalf.
 *
 * The photograph is deliberately not attached. Report photos live inside
 * Firestore documents as bytes rather than as files, so attaching one would
 * mean writing it to disk and granting a content permission to whichever app
 * the resident picks; the map link and the coordinate identify the spot without
 * any of that, and the office can be sent the picture in a reply if it asks.
 */
object CouncilHandoff {

    /** The whole report as a piece of prose an office can act on. */
    fun body(context: Context, issue: Issue, locale: Locale): String {
        val dateFormat = DateFormat.getDateInstance(DateFormat.LONG, locale)
        val lines = buildList {
            add(context.getString(R.string.council_intro))
            add("")
            add(context.getString(R.string.council_what, issue.title))
            if (issue.description.isNotBlank()) add(issue.description)
            add("")
            if (issue.placeLabel.isNotBlank()) {
                add(context.getString(R.string.council_where, issue.placeLabel))
            }
            add(
                context.getString(
                    R.string.council_coordinates,
                    String.format(Locale.US, "%.6f", issue.lat),
                    String.format(Locale.US, "%.6f", issue.lng),
                ),
            )
            add(mapLink(issue))
            add("")
            issue.createdAt?.let {
                add(context.getString(R.string.council_reported, dateFormat.format(it)))
            }
            // The number of neighbours behind it, which is the one piece of
            // information a village has that an office does not.
            if (issue.upvotes > 0) {
                add(context.getString(R.string.council_supporters, issue.upvotes))
            }
            add("")
            add(context.getString(R.string.council_sign))
        }
        return lines.joinToString("\n")
    }

    fun subject(context: Context, issue: Issue): String =
        context.getString(R.string.council_subject, issue.title)

    fun mapLink(issue: Issue): String =
        "https://maps.google.com/?q=${issue.lat},${issue.lng}"

    /**
     * Opens a mail composer, or falls back to anything that will take text.
     *
     * `ACTION_SENDTO` with a `mailto:` is the only form that guarantees a mail
     * app rather than a share sheet full of social networks — but a phone with
     * no mail app configured has nothing to answer it, which on a village phone
     * is common. The fallback is a plain share, which always resolves.
     */
    fun email(context: Context, to: String, subject: String, body: String): Boolean {
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (mail.resolveActivity(context.packageManager) != null) {
            return runCatching { context.startActivity(mail) }.isSuccess
        }
        return share(context, subject, body)
    }

    fun share(context: Context, subject: String, body: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    /** How long a report has been sitting with the municipality, in days. */
    fun daysSince(sentAt: Date?, now: Long = System.currentTimeMillis()): Int? {
        val sent = sentAt?.time ?: return null
        return ((now - sent) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
    }
}
