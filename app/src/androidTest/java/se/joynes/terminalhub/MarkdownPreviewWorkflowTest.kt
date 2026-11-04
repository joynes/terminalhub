package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.ui.screen.download.RemoteTextPreviewDialog
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class MarkdownPreviewWorkflowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun formattedDocumentShowsQuotesCodeListsAndDividerWithoutDownloading() {
        var closed = false
        val markdown = "# Report\n\n> quoted result\n\n```sh\nprintf example\n```\n\n---\n\n1. first item\n2. second item\n\nEnd of report"
        compose.setContent { TerminalHubTheme { RemoteTextPreviewDialog("report.md", markdown, true, { closed = true }) } }
        compose.onNodeWithTag("remote-text-preview").performScrollToNode(hasText("quoted result"))
        compose.onNodeWithText("quoted result").assertIsDisplayed()
        compose.onNodeWithTag("remote-text-preview").performScrollToNode(hasText("printf example"))
        compose.onNodeWithText("printf example").assertIsDisplayed()
        compose.onNodeWithTag("remote-text-preview").performScrollToNode(hasText("End of report"))
        compose.onNodeWithText("End of report").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close file browser").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun emptyMarkdownHasClearEmptyFileMessage() {
        compose.setContent { TerminalHubTheme { RemoteTextPreviewDialog("empty.md", "", true, {}) } }
        compose.onNodeWithText("(empty file)").assertIsDisplayed()
    }
}
