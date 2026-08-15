package gr.agiosnektarios.village.core.geo

import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class IssueClusteringTest {

    private var nextSecond = 0L

    private fun issue(
        id: String,
        lat: Double,
        lng: Double,
        category: IssueCategory = IssueCategory.ROAD,
        status: IssueStatus = IssueStatus.OPEN,
        upvotes: Int = 0,
        comments: Int = 0,
    ): Issue = Issue(
        id = id,
        title = id,
        categoryId = category.id,
        statusId = status.id,
        lat = lat,
        lng = lng,
        upvotes = upvotes,
        score = upvotes,
        commentCount = comments,
        // Distinct, increasing timestamps so cluster anchoring is deterministic.
        createdAt = Date(1_700_000_000_000L + (nextSecond++ * 1_000L)),
    )

    @Test
    fun `empty input produces no clusters`() {
        assertTrue(IssueClustering.cluster(emptyList(), zoom = 18f).isEmpty())
    }

    @Test
    fun `same category within the same-problem radius merges even when fully zoomed in`() {
        // ~20 m apart: one pothole, reported by two neighbours.
        val issues = listOf(
            issue("a", 37.84800, 23.92000),
            issue("b", 37.84815, 23.92005),
        )

        val clusters = IssueClustering.cluster(issues, zoom = 20f)

        assertEquals(1, clusters.size)
        assertEquals(2, clusters.first().size)
        assertEquals(IssueCategory.ROAD, clusters.first().category)
    }

    @Test
    fun `different categories at the same spot stay separate when zoomed in`() {
        val issues = listOf(
            issue("road", 37.84800, 23.92000, category = IssueCategory.ROAD),
            issue("water", 37.84801, 23.92001, category = IssueCategory.WATER),
        )

        val clusters = IssueClustering.cluster(issues, zoom = 19f)

        assertEquals(2, clusters.size)
        assertTrue(clusters.all { it.isSingle })
    }

    @Test
    fun `different categories merge once zoomed out past the legibility threshold`() {
        val issues = listOf(
            issue("road", 37.84800, 23.92000, category = IssueCategory.ROAD),
            issue("water", 37.84805, 23.92003, category = IssueCategory.WATER),
        )

        val clusters = IssueClustering.cluster(issues, zoom = 14f)

        assertEquals(1, clusters.size)
        assertEquals(2, clusters.first().size)
        // Mixed clusters have no single category, which is what tells the pin
        // to fall back to the generic marker.
        assertNull(clusters.first().category)
    }

    @Test
    fun `reports far apart never merge`() {
        val issues = listOf(
            issue("a", 37.8400, 23.9100),
            issue("b", 37.8600, 23.9300),
        )

        val clusters = IssueClustering.cluster(issues, zoom = 18f)

        assertEquals(2, clusters.size)
    }

    @Test
    fun `cluster position is the centroid of its members`() {
        val issues = listOf(
            issue("a", 37.84800, 23.92000),
            issue("b", 37.84820, 23.92000),
        )

        val cluster = IssueClustering.cluster(issues, zoom = 20f).single()

        assertEquals(37.84810, cluster.position.lat, 0.00001)
        assertEquals(23.92000, cluster.position.lng, 0.00001)
    }

    @Test
    fun `cluster identity is anchored on its oldest member`() {
        // Stable ids are what keep markers from being torn down and re-dropped
        // every time a new report arrives nearby.
        val first = issue("first", 37.84800, 23.92000)
        val second = issue("second", 37.84810, 23.92002)

        val fromOneOrder = IssueClustering.cluster(listOf(first, second), zoom = 20f).single()
        val fromOtherOrder = IssueClustering.cluster(listOf(second, first), zoom = 20f).single()

        assertEquals("first", fromOneOrder.id)
        assertEquals(fromOneOrder.id, fromOtherOrder.id)
    }

    @Test
    fun `representative is the most engaged report in the cluster`() {
        val quiet = issue("quiet", 37.84800, 23.92000)
        val busy = issue("busy", 37.84805, 23.92001, upvotes = 4, comments = 2)

        val cluster = IssueClustering.cluster(listOf(quiet, busy), zoom = 20f).single()

        assertEquals("busy", cluster.representative.id)
    }

    @Test
    fun `open count ignores terminal reports`() {
        val issues = listOf(
            issue("open", 37.84800, 23.92000),
            issue("done", 37.84805, 23.92001, status = IssueStatus.RESOLVED),
        )

        val cluster = IssueClustering.cluster(issues, zoom = 20f).single()

        assertEquals(2, cluster.size)
        assertEquals(1, cluster.openCount)
    }

    @Test
    fun `single-member clusters expose their category`() {
        val cluster = IssueClustering
            .cluster(listOf(issue("only", 37.848, 23.920, IssueCategory.FIRE_RISK)), zoom = 18f)
            .single()

        assertTrue(cluster.isSingle)
        assertNotNull(cluster.category)
        assertEquals(IssueCategory.FIRE_RISK, cluster.category)
    }

    @Test
    fun `every report ends up in exactly one cluster`() {
        val issues = (0 until 40).map { index ->
            issue(
                id = "issue-$index",
                lat = 37.8400 + index * 0.0004,
                lng = 23.9100 + (index % 5) * 0.0004,
                category = IssueCategory.entries[index % IssueCategory.entries.size],
            )
        }

        val clusters = IssueClustering.cluster(issues, zoom = 16f)

        assertEquals(issues.size, clusters.sumOf { it.size })
        assertEquals(issues.size, clusters.flatMap { it.issues }.map { it.id }.distinct().size)
    }
}
