package se.joynes.terminalhub.ui.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTabDragTargetTest {
    private val bounds = mapOf(
        1L to Rect(0f, 0f, 70f, 28f),
        2L to Rect(70f, 0f, 140f, 28f),
        3L to Rect(0f, 28f, 70f, 56f)
    )

    @Test
    fun `drag chooses nearest tab on the same row`() {
        assertEquals(1, tabDropTargetIndex(listOf(1L, 2L, 3L), bounds, Offset(120f, 14f)))
    }

    @Test
    fun `drag can move a tab across wrapped rows`() {
        assertEquals(2, tabDropTargetIndex(listOf(1L, 2L, 3L), bounds, Offset(30f, 48f)))
    }

    @Test
    fun `drag target follows current persisted order`() {
        assertEquals(0, tabDropTargetIndex(listOf(3L, 1L, 2L), bounds, Offset(30f, 48f)))
    }

    @Test
    fun `preview move changes the draft order`() {
        assertEquals(listOf(2L, 3L, 1L), moveTabId(listOf(1L, 2L, 3L), 0, 2))
    }

    @Test
    fun `invalid preview move preserves the draft order`() {
        assertEquals(listOf(1L, 2L, 3L), moveTabId(listOf(1L, 2L, 3L), 0, 4))
    }
}
