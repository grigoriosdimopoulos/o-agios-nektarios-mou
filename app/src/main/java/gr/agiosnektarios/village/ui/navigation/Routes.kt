package gr.agiosnektarios.village.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.ui.graphics.vector.ImageVector
import gr.agiosnektarios.village.R

/** Scheme used by push notifications to deep-link into a screen. */
const val DEEP_LINK_SCHEME = "agiosnektarios://open"

object Routes {
    // Authentication
    const val SIGN_IN = "auth/sign-in"
    const val SIGN_UP = "auth/sign-up"
    const val FORGOT_PASSWORD = "auth/forgot-password"

    /** Reached when authentication succeeded but the village details are missing. */
    const val COMPLETE_PROFILE = "auth/complete-profile"

    // Bottom-bar destinations
    const val MAP = "map"
    const val ISSUES = "issues"
    const val ANNOUNCEMENTS = "announcements"
    const val CHATS = "chats"
    const val PROFILE = "profile"

    // Detail destinations
    const val ISSUE_DETAIL = "issue/{issueId}"
    const val ISSUE_COMPOSE = "issue-compose?issueId={issueId}&lat={lat}&lng={lng}"
    const val CHAT_ROOM = "chat/{chatId}"
    const val NEW_CHAT = "chat-new"
    const val EDIT_PROFILE = "profile/edit"
    const val SETTINGS = "settings"
    const val CHANGE_PASSWORD = "settings/password"
    const val ADMIN = "admin"
    const val ADMIN_USER_DETAIL = "admin/user/{userId}"
    const val ANNOUNCEMENT_COMPOSE = "announcement-compose?announcementId={announcementId}"
    const val EVENT_COMPOSE = "event-compose?eventId={eventId}"
    const val CONTACTS = "contacts"
    const val ALERT = "alert"
    const val HOME_PIN = "profile/home"

    fun issueDetail(issueId: String) = "issue/$issueId"

    fun newIssueAt(lat: Double, lng: Double) =
        "issue-compose?issueId=&lat=$lat&lng=$lng"

    fun editIssue(issueId: String) = "issue-compose?issueId=$issueId&lat=0.0&lng=0.0"

    fun chatRoom(chatId: String) = "chat/$chatId"

    fun adminUser(userId: String) = "admin/user/$userId"

    fun announcementCompose(announcementId: String? = null) =
        "announcement-compose?announcementId=${announcementId.orEmpty()}"

    fun eventCompose(eventId: String? = null) = "event-compose?eventId=${eventId.orEmpty()}"
}

/**
 * The links carried by notifications.
 *
 * Kept beside [Routes] because a notice and the screen it opens have to agree,
 * and they are written by one device and opened on another — a mismatch would
 * show up as a notification that goes nowhere.
 */
object DeepLinks {
    fun issue(issueId: String) = "$DEEP_LINK_SCHEME/issue/$issueId"
    fun chat(chatId: String) = "$DEEP_LINK_SCHEME/chat/$chatId"
    const val ANNOUNCEMENTS = "$DEEP_LINK_SCHEME/announcements"
}

/** The five tabs, in bar order. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    MAP(Routes.MAP, R.string.nav_map, Icons.Filled.Map, Icons.Outlined.Map),
    ISSUES(Routes.ISSUES, R.string.nav_issues, Icons.Filled.ViewList, Icons.Outlined.ViewList),
    /**
     * Notices and the calendar, behind one tab.
     *
     * The route keeps its old name so existing deep links from notifications
     * still land, but the label and the icon are the village rather than the
     * megaphone: what lives here now is both what has been announced and what
     * is coming up.
     */
    ANNOUNCEMENTS(
        Routes.ANNOUNCEMENTS,
        R.string.nav_village,
        Icons.Filled.Cottage,
        Icons.Outlined.Cottage,
    ),
    CHATS(Routes.CHATS, R.string.nav_chat, Icons.Filled.Forum, Icons.Outlined.Forum),
    PROFILE(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person),
    ;

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}
