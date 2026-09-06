package se.joynes.terminalhub.ui.screen.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadBatchTest {

    @Test
    fun `batch remains active while another selected file is queued`() {
        val progress = advanceUploadBatch(emptyList(), "first.txt", remainingFiles = 2)

        assertEquals(listOf("first.txt"), progress.completedFileNames)
        assertFalse(progress.batchComplete)
    }

    @Test
    fun `final result contains every uploaded filename in selection order`() {
        val first = advanceUploadBatch(emptyList(), "first.txt", remainingFiles = 1)
        val final = advanceUploadBatch(first.completedFileNames, "second.txt", remainingFiles = 0)

        assertEquals(listOf("first.txt", "second.txt"), final.completedFileNames)
        assertTrue(final.batchComplete)
    }
}
