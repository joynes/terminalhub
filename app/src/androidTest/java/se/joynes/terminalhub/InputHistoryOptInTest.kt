package se.joynes.terminalhub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.data.repository.InputActionsRepository
import se.joynes.terminalhub.data.settings.AppSettingsRepository

@RunWith(AndroidJUnit4::class)
class InputHistoryOptInTest {
    @Test fun optInControlsEverySourceClearsDraftsAndDeleteKeepsPins() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("input_history_consent", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = AppSettingsRepository(context)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val actions = InputActionsRepository(db.textInputHistoryDao(), db.pinnedActionDao(), settings)
        try {
            assertFalse(settings.settings.value.inputHistoryEnabled)
            actions.recorder.input(1, "super-secret-password\r")
            actions.recorder.paste(2, "voice secret")
            actions.recorder.complete(3, "text panel or pin secret")
            assertEquals(0, actions.recorder.retainedDraftCount())
            delay(150)
            for (project in 1L..3L) assertTrue(actions.history(project).first().isEmpty())

            actions.setHistoryEnabled(true)
            assertTrue(AppSettingsRepository(context).settings.value.inputHistoryEnabled)
            actions.recorder.input(1, "git status\r")
            withTimeout(5_000) { actions.history(1).first { it.isNotEmpty() } }
            actions.recorder.input(1, "partial-secret")
            actions.recorder.paste(2, "other secret")
            actions.setHistoryEnabled(false)
            assertEquals(0, actions.recorder.retainedDraftCount())
            assertEquals("git status", actions.history(1).first().single().text)
            actions.setHistoryEnabled(true)
            actions.recorder.input(2, "hello\r")
            val second = withTimeout(5_000) { actions.history(2).first { it.isNotEmpty() } }
            assertEquals("hello", second.single().text)

            actions.setHistoryEnabled(false)
            actions.savePin(PinnedActionEntity(name = "Status", text = "git status", scope = "GLOBAL"))
            actions.markUsed(actions.pins(99).first().single().id)
            actions.recorder.complete(1, "executed pin while off")
            assertEquals("git status", actions.history(1).first().single().text)
            actions.clearHistory()
            assertTrue(actions.history(1).first().isEmpty())
            assertTrue(actions.history(2).first().isEmpty())
            assertEquals(1L, actions.pins(99).first().single().usageCount)
        } finally {
            actions.setHistoryEnabled(false)
            db.close()
            context.getSharedPreferences("input_history_consent", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun queuedInputCannotReturnAfterDisableOrDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AppSettingsRepository(context)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val actions = InputActionsRepository(db.textInputHistoryDao(), db.pinnedActionDao(), settings)
        try {
            actions.setHistoryEnabled(true)
            repeat(1_000) { actions.recorder.complete(1, "queued $it") }
            actions.setHistoryEnabled(false)
            actions.clearHistory()
            actions.setHistoryEnabled(true)
            actions.recorder.complete(1, "only new input")
            withTimeout(5_000) { actions.history(1).first { it.isNotEmpty() } }
            assertEquals(listOf("only new input"), actions.history(1).first().map { it.text })
        } finally { actions.setHistoryEnabled(false); db.close() }
    }
}
