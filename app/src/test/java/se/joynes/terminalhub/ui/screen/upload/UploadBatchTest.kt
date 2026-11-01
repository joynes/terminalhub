package se.joynes.terminalhub.ui.screen.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadBatchTest {

    @Test
    fun `batch remains active while another selected file is queued`() {
        val progress = advanceUploadBatch(emptyList(), "/srv/projects/demo/first.txt", remainingFiles = 2)

        assertEquals(listOf("/srv/projects/demo/first.txt"), progress.completedRemotePaths)
        assertFalse(progress.batchComplete)
    }

    @Test
    fun `final result contains every uploaded remote path in selection order`() {
        val first = advanceUploadBatch(emptyList(), "/srv/projects/demo/first.txt", remainingFiles = 1)
        val final = advanceUploadBatch(first.completedRemotePaths, "/srv/projects/demo/second.txt", remainingFiles = 0)

        assertEquals(listOf("/srv/projects/demo/first.txt", "/srv/projects/demo/second.txt"), final.completedRemotePaths)
        assertTrue(final.batchComplete)
    }
}
