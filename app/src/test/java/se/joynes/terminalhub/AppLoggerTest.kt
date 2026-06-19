package se.joynes.terminalhub

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import se.joynes.terminalhub.data.db.dao.AppLogDao
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel

class AppLoggerTest {
    private lateinit var logger: AppLogger
    private val dao = mock(AppLogDao::class.java)

    @Before
    fun setup() {
        logger = AppLogger(dao)
    }

    @Test
    fun `log emits to flow`() = runTest {
        logger.log(LogLevel.INFO, "TestTag", "Hello world")
        val entry = logger.logFlow.first()
        assertEquals("INFO", entry.level)
        assertEquals("TestTag", entry.tag)
        assertEquals("Hello world", entry.message)
    }

    @Test
    fun `log stores correct level`() = runTest {
        logger.log(LogLevel.ERROR, "Tag", "Error msg")
        val entry = logger.logFlow.first()
        assertEquals("ERROR", entry.level)
    }
}
