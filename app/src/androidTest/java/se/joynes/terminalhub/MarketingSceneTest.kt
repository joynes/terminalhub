package se.joynes.terminalhub

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.marketing.MarketingPreviewActivity

/** Store-capture fixtures must remain launchable and show consistent demo state. */
@HiltAndroidTest
class MarketingSceneTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MarketingPreviewActivity>? = null
    @Before fun setup() { hilt.inject() }
    @After fun cleanup() { scenario?.close() }
    private fun launch(scene: String? = null) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MarketingPreviewActivity::class.java)
        scene?.let { intent.putExtra("scene", it) }
        scenario = ActivityScenario.launch(intent)
    }
    @Test fun defaultSceneShowsFiveProjects() {
        launch()
        compose.onNodeWithText("5 ACTIVE").assertIsDisplayed()
        compose.onNodeWithText("WORKSTATION / MOBILE").assertIsDisplayed()
    }
    @Test fun twoProjectSceneShowsExactlyTwoActiveProjects() {
        launch("sessions2")
        compose.onNodeWithText("2 ACTIVE").assertIsDisplayed()
        compose.onNodeWithText("DOCS").assertDoesNotExist()
    }
    @Test fun tenProjectSceneKeepsTheProjectStripScrollable() {
        launch("sessions10")
        compose.onNodeWithText("10 ACTIVE").assertIsDisplayed()
        compose.onNodeWithText("MOBILE").assertExists()
    }
    @Test fun restoredSceneIdentifiesTheRestoredServerAndProject() {
        launch("resume")
        compose.onNodeWithText("VPS / API").assertIsDisplayed()
        compose.onNodeWithText("TMUX RESTORED").assertIsDisplayed()
    }
    @Test fun multiUploadSceneClearlyShowsCountAndCurrentFile() {
        launch("upload-multiple")
        compose.onNodeWithText("FILE UPLOAD").assertIsDisplayed()
        compose.onNodeWithText("UPLOADING (2/3)...").assertIsDisplayed()
        compose.onNodeWithText("3 files selected · README.md · architecture.png · release-notes.pdf").assertIsDisplayed()
    }
    @Test fun singleUploadSceneKeepsTheSelectedFileVisible() {
        launch("files")
        compose.onNodeWithText("requirements.md", substring = true).assertExists()
    }
    @Test fun downloadSceneShowsTheCompletedBatch() {
        launch("download-multiple")
        compose.onNodeWithText("README.md", substring = true).assertExists()
        compose.onNodeWithText("release-notes.pdf", substring = true).assertExists()
    }
    @Test fun promptSceneContainsEditableMultilineExample() {
        launch("prompt")
        compose.onNode(hasSetTextAction()).assertTextContains("git switch -c feature/mobile-auth\n./gradlew test\ngit status --short")
    }
    @Test fun keybarSceneShowsCustomizableControls() {
        launch("keybar-settings")
        compose.onNodeWithText("CUSTOMIZE YOUR TERMINAL CONTROLS").assertIsDisplayed()
        compose.onNodeWithText("Replace, highlight and reorder the keys you use most.").assertIsDisplayed()
    }
    @Test fun serverSceneShowsTheThreeDemonstrationEndpoints() {
        launch("servers")
        for (endpoint in listOf("developer@workstation.example", "admin@homelab.example", "ops@vps.example")) {
            compose.onNodeWithText(endpoint).assertIsDisplayed()
        }
        compose.onNodeWithText("OPEN SOURCE").assertIsDisplayed()
    }
    @Test fun openSourceSceneIncludesTheLicenseAndVersion() {
        launch("opensource")
        compose.onNodeWithText("GPL-3.0", substring = true).assertExists()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME.removeSuffix("-diag")}", substring = true).assertExists()
    }
}
