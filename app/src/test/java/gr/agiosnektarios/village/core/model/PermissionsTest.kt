package gr.agiosnektarios.village.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client-side half of authorisation. The Firestore rules are the half that
 * actually enforces it; these checks decide what the UI offers, and the two are
 * meant to agree.
 */
class PermissionsTest {

    private val author = UserProfile(id = "author", firstName = "Maria", lastName = "Papadopoulou")
    private val neighbour = UserProfile(id = "neighbour", firstName = "Giorgos", lastName = "Nikou")
    private val moderator = neighbour.copy(id = "mod", role = Role.MODERATOR.id)
    private val admin = neighbour.copy(id = "admin", role = Role.ADMIN.id)

    private val issue = Issue(id = "issue-1", authorId = "author", title = "Fallen branch")

    @Test
    fun `author may edit and delete their own report`() {
        assertTrue(issue.canEdit(author))
        assertTrue(issue.canDelete(author))
        assertTrue(issue.canChangeStatus(author))
    }

    @Test
    fun `a plain neighbour may not`() {
        assertFalse(issue.canEdit(neighbour))
        assertFalse(issue.canDelete(neighbour))
        assertFalse(issue.canChangeStatus(neighbour))
    }

    @Test
    fun `moderators and admins may act on anyone's report`() {
        assertTrue(issue.canEdit(moderator))
        assertTrue(issue.canDelete(moderator))
        assertTrue(issue.canEdit(admin))
        assertTrue(issue.canDelete(admin))
    }

    @Test
    fun `a signed-out viewer may do nothing`() {
        assertFalse(issue.canEdit(null))
        assertFalse(issue.canDelete(null))
        assertFalse(issue.canChangeStatus(null))
    }

    @Test
    fun `only administrators count as admin, moderators do not`() {
        assertTrue(admin.isAdmin)
        assertFalse(moderator.isAdmin)
        // …but moderators do get every issue-level power.
        assertTrue(moderator.canModerate)
        assertTrue(admin.canModerate)
        assertFalse(neighbour.canModerate)
    }

    @Test
    fun `unknown role ids degrade to plain resident`() {
        val strange = neighbour.copy(role = "SUPERUSER")
        assertEquals(Role.USER, strange.roleType)
        assertFalse(strange.canModerate)
    }

    @Test
    fun `comment deletion follows the same rule as issues`() {
        val comment = Comment(id = "c1", authorId = "author", text = "Still there")
        assertTrue(comment.canDelete(author))
        assertTrue(comment.canDelete(moderator))
        assertFalse(comment.canDelete(neighbour))
        assertFalse(comment.canDelete(null))
    }

    @Test
    fun `unknown category and status ids degrade instead of throwing`() {
        val odd = Issue(categoryId = "TELEPORTER_FAULT", statusId = "PENDING_MAGIC")
        assertEquals(IssueCategory.OTHER, odd.category)
        assertEquals(IssueStatus.OPEN, odd.status)
        assertTrue(odd.isOpen)
    }

    @Test
    fun `terminal statuses stop counting as open`() {
        assertFalse(Issue(statusId = IssueStatus.RESOLVED.id).isOpen)
        assertFalse(Issue(statusId = IssueStatus.WONT_DO.id).isOpen)
        assertTrue(Issue(statusId = IssueStatus.IN_PROGRESS.id).isOpen)
    }

    @Test
    fun `display name falls back to the email local part`() {
        val halfFilled = UserProfile(id = "x", email = "maria@example.gr")
        assertEquals("maria", halfFilled.displayName)
        assertEquals("MP", author.initials)
    }

    @Test
    fun `direct chat titles show the other person`() {
        val chat = Chat(
            id = "author_neighbour",
            type = ChatType.DIRECT.id,
            memberIds = listOf("author", "neighbour"),
            memberNames = mapOf("author" to "Maria", "neighbour" to "Giorgos"),
        )
        assertEquals("Giorgos", chat.displayTitle("author"))
        assertEquals("Maria", chat.displayTitle("neighbour"))
    }
}
