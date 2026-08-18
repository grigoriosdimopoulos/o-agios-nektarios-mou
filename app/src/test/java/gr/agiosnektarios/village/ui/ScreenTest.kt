package gr.agiosnektarios.village.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import gr.agiosnektarios.village.core.model.Chat
import gr.agiosnektarios.village.core.model.ChatMessage
import gr.agiosnektarios.village.core.model.ChatType
import gr.agiosnektarios.village.core.model.Role
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.data.settings.ThemeMode
import gr.agiosnektarios.village.ui.chat.ChatRoomContent
import gr.agiosnektarios.village.ui.chat.ChatRoomUiState
import gr.agiosnektarios.village.ui.chat.ChatsContent
import gr.agiosnektarios.village.ui.chat.ChatsUiState
import gr.agiosnektarios.village.ui.issue.FixState
import gr.agiosnektarios.village.ui.issue.IssueDetailContent
import gr.agiosnektarios.village.ui.issue.QuickReportSheet
import gr.agiosnektarios.village.ui.issue.QuickReportUiState
import gr.agiosnektarios.village.ui.issue.IssueListContent
import gr.agiosnektarios.village.ui.issue.IssueListUiState
import gr.agiosnektarios.village.ui.profile.ProfileContent
import gr.agiosnektarios.village.ui.profile.ProfileUiState
import gr.agiosnektarios.village.ui.theme.VillageTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import java.util.Date
import org.junit.Rule
import org.junit.Test

/**
 * Whole screens, rendered.
 *
 * Components were being looked at one at a time while the thing anyone
 * actually uses — a screen, with its header, its spacing, its density and its
 * empty room at the bottom — was never seen at all. A gallery of good
 * components does not add up to a good screen, and every complaint so far has
 * been about screens.
 */
class ScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private fun render(dark: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.snapshot {
            VillageTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { content() }
            }
        }
    }

    @Test fun issue_list_light() = render { IssueList() }

    @Test fun issue_list_dark() = render(dark = true) { IssueList() }

    @Test fun issue_detail_light() = render { IssueDetail() }

    @Test fun issue_detail_dark() = render(dark = true) { IssueDetail() }

    @Test fun chats_light() = render { Chats() }

    @Test fun chats_dark() = render(dark = true) { Chats() }

    @Test fun chat_room_light() = render { ChatRoom() }

    @Test fun chat_room_dark() = render(dark = true) { ChatRoom() }

    @Test fun quick_report_light() = render { QuickReport() }

    @Test fun quick_report_dark() = render(dark = true) { QuickReport() }

    @Test fun quick_report_no_photo_light() = render { QuickReportEmpty() }

    @Test fun profile_light() = render { Profile() }

    @Test fun profile_dark() = render(dark = true) { Profile() }
}

@Composable
private fun IssueList() = IssueListContent(
    state = IssueListUiState(issues = sampleIssues, loading = false),
    onOpenIssue = {},
    onQueryChange = {},
    onSortChange = {},
    onToggleStatus = {},
    onToggleCategory = {},
)

@Composable
private fun IssueDetail() = IssueDetailContent(
    issue = sampleIssues[1],
    comments = sampleComments,
    photos = emptyList(),
    viewer = me,
    myVote = 1,
    onVote = {},
    onDeleteComment = {},
    contentPadding = PaddingValues(top = 0.dp, bottom = 0.dp),
)

@Composable
private fun Chats() = ChatsContent(
    state = ChatsUiState(chats = sampleChats, currentUserId = me.id, loading = false),
    onOpenChat = {},
    onNewChat = {},
)

@Composable
private fun ChatRoom() = ChatRoomContent(
    state = ChatRoomUiState(
        chat = sampleChats[0],
        messages = sampleMessages,
        currentUserId = me.id,
        draft = "",
        loading = false,
    ),
    onBack = {},
    onDraftChange = {},
    onSend = {},
    onLeave = {},
)

@Composable
private fun QuickReport() = QuickReportSheet(
    state = QuickReportUiState(
        photo = null,
        text = "Πεσμένο δέντρο κλείνει τον δρόμο κάτω από τη στροφή",
        position = gr.agiosnektarios.village.core.geo.GeoPoint(38.1640, 23.2920),
        fix = FixState.FOUND,
    ),
    onTextChange = {},
    onRetakePhoto = {},
    onRetryLocation = {},
    onPickOnMap = {},
    onSubmit = {},
    onOpenFullForm = {},
)

@Composable
private fun QuickReportEmpty() = QuickReportSheet(
    state = QuickReportUiState(fix = FixState.LOCATING),
    onTextChange = {},
    onRetakePhoto = {},
    onRetryLocation = {},
    onPickOnMap = {},
    onSubmit = {},
    onOpenFullForm = {},
)

@Composable
private fun Profile() = ProfileContent(
    state = ProfileUiState(
        profile = me,
        myIssues = sampleIssues.take(3),
        blockNameEl = "Κέντρο",
        blockNameEn = "Centre",
        loading = false,
    ),
    onOpenIssue = {},
    onEditProfile = {},
    onOpenSettings = {},
    onOpenAdmin = {},
    onSignOut = {},
)

private val me = UserProfile(
    id = "me",
    firstName = "Γρηγόρης",
    lastName = "Δημόπουλος",
    email = "g@example.gr",
    blockId = "block-01",
    role = Role.USER.id,
)

private fun ago(minutes: Long) = Date(System.currentTimeMillis() - minutes * 60_000L)

private val sampleComments = listOf(
    gr.agiosnektarios.village.core.model.Comment(
        id = "c1",
        authorId = "other",
        authorName = "Μαρία Κ.",
        text = "Το είδα κι εγώ χθες. Έχει κλείσει εντελώς το πέρασμα.",
        createdAt = ago(90),
    ),
    gr.agiosnektarios.village.core.model.Comment(
        id = "c2",
        authorId = "me",
        authorName = "Γρηγόρης Δ.",
        text = "Το ανέφερα και στον δήμο σήμερα το πρωί.",
        createdAt = ago(20),
    ),
)

private val sampleChats = listOf(
    Chat(
        id = "chat1",
        type = ChatType.DIRECT.id,
        memberIds = listOf("me", "maria"),
        memberNames = mapOf("me" to "Γρηγόρης", "maria" to "Μαρία Κ."),
        lastMessage = "Ναι, το είδα. Θα περάσω το απόγευμα.",
        lastMessageSenderId = "maria",
        lastMessageAt = ago(12),
        unreadCounts = mapOf("me" to 2),
    ),
    Chat(
        id = "chat2",
        type = ChatType.GROUP.id,
        title = "Πυροπροστασία",
        memberIds = listOf("me", "maria", "dimitris"),
        memberNames = mapOf("me" to "Γρηγόρης", "maria" to "Μαρία", "dimitris" to "Δημήτρης"),
        lastMessage = "Ραντεβού Σάββατο στις 9 στην πλατεία.",
        lastMessageSenderId = "dimitris",
        lastMessageAt = ago(60 * 5),
    ),
)

private val sampleMessages = listOf(
    ChatMessage(id = "m1", senderId = "maria", senderName = "Μαρία Κ.",
        text = "Είδες το δέντρο στην κάτω στροφή;", createdAt = ago(48)),
    ChatMessage(id = "m2", senderId = "me", senderName = "Γρηγόρης",
        text = "Ναι, το ανέφερα στην εφαρμογή χθες βράδυ.", createdAt = ago(44)),
    ChatMessage(id = "m3", senderId = "maria", senderName = "Μαρία Κ.",
        text = "Ωραία. Ο Δημήτρης λέει ότι έχει αλυσοπρίονο αν χρειαστεί.",
        createdAt = ago(30)),
    ChatMessage(id = "m4", senderId = "me", senderName = "Γρηγόρης",
        text = "Τέλεια, θα του γράψω.", createdAt = ago(12)),
)
