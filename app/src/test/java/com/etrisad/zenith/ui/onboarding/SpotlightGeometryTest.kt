package com.etrisad.zenith.ui.onboarding

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightGeometryTest {

    private val density = Density(1f)
    private val viewport = Size(400f, 800f)

    @Test
    fun `wide row is fully contained and becomes a pill`() {
        val row = Rect(left = 16f, top = 300f, right = 384f, bottom = 380f)
        val hole = row.toSpotlightGeometry(density = density, container = viewport)

        assertTrue(hole.rect.left <= row.left)
        assertTrue(hole.rect.top <= row.top)
        assertTrue(hole.rect.right >= row.right)
        assertTrue(hole.rect.bottom >= row.bottom)
        assertEquals(hole.rect.height / 2f, hole.cornerRadius, 0.01f)
    }

    @Test
    fun `compact square target becomes a circle around it`() {
        val fab = Rect(left = 300f, top = 660f, right = 380f, bottom = 740f)
        val hole = fab.toSpotlightGeometry(density = density, container = viewport)

        assertEquals(hole.rect.width, hole.rect.height, 0.01f)
        assertEquals(hole.rect.width / 2f, hole.cornerRadius, 0.01f)
        assertEquals(fab.center.x, hole.center.x, 0.01f)
        assertEquals(fab.center.y, hole.center.y, 0.01f)
    }

    @Test
    fun `tall target is capped and starts at its visible top`() {
        val card = Rect(left = 16f, top = 100f, right = 384f, bottom = 760f)
        val hole = card.toSpotlightGeometry(density = density, container = viewport)

        assertTrue(hole.rect.height <= viewport.height * GuideTokens.HoleMaxHeightFraction)
        assertTrue(hole.rect.top >= card.top)
        assertTrue(hole.rect.top <= card.top + 24f)
    }

    @Test
    fun `hole is clamped inside the viewport`() {
        val offScreen = Rect(left = 16f, top = -120f, right = 384f, bottom = -20f)
        val hole = offScreen.toSpotlightGeometry(density = density, container = viewport)

        assertTrue(hole.rect.top >= 0f)
        assertTrue(hole.rect.bottom <= viewport.height)
        assertTrue(hole.rect.left >= 0f)
        assertTrue(hole.rect.right <= viewport.width)
    }

    @Test
    fun `corner radius shrinks as the highlight grows`() {
        val small = Rect(left = 16f, top = 300f, right = 216f, bottom = 360f)
            .toSpotlightGeometry(density = density, container = viewport)
        val large = Rect(left = 16f, top = 200f, right = 384f, bottom = 500f)
            .toSpotlightGeometry(density = density, container = viewport)

        assertTrue(small.cornerRadius > large.cornerRadius)
        assertEquals(GuideTokens.RectCornerDp, large.cornerRadius, 0.01f)
    }

    @Test
    fun `big card is a rounded rectangle instead of a blob`() {
        val card = Rect(left = 16f, top = 120f, right = 384f, bottom = 560f)
        val hole = card.toSpotlightGeometry(density = density, container = viewport)

        assertTrue(hole.cornerRadius <= GuideTokens.RectCornerDp)
        assertTrue(hole.cornerRadius < hole.rect.height / 4f)
    }

    @Test
    fun `long section is highlighted where it is visible`() {
        val section = Rect(left = 16f, top = 120f, right = 384f, bottom = 1600f)
        val hole = section.toSpotlightGeometry(density = density, container = viewport)

        assertTrue(hole.rect.top >= 0f)
        assertTrue(hole.rect.bottom <= viewport.height)
        assertTrue(hole.center.y < viewport.height * 0.75f)
        assertTrue(hole.rect.top <= section.top + 200f)
    }

    @Test
    fun `tiny target still gets a usable hole`() {
        val dot = Rect(center = androidx.compose.ui.geometry.Offset(200f, 400f), radius = 2f)
        val hole = dot.toSpotlightGeometry(density = density, container = viewport)

        assertTrue(hole.rect.width >= 64f)
        assertTrue(hole.rect.height >= 64f)
        assertEquals(200f, hole.center.x, 0.01f)
    }
}
