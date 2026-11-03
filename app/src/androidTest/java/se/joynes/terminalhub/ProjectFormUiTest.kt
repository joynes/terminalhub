package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.ui.screen.projects.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
class ProjectFormUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var servers: ServerRepository
    private lateinit var vm: AddEditProjectViewModel
    @Before fun setup() {
        hilt.inject(); db.clearAllTables()
        vm = AddEditProjectViewModel(projects, servers)
    }
    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())
    private fun start(id: Long? = null, onBack: () -> Unit = {}) {
        compose.setContent { TerminalHubTheme { AddEditProjectScreen(null, id, onBack, vm) } }
    }
    private fun advanced() { compose.onNodeWithText("[ > ADVANCED ]").performScrollTo().performClick() }
    @Test fun localProjectCanBeCreatedThroughTheFormWithHumanReadableTitle() {
        var returned = false
        vm.update { copy(targetType = ProjectTargetType.LOCAL) }
        start(onBack = { returned = true })
        compose.onNodeWithText("[ SAVE ]").performScrollTo().assertIsNotEnabled()
        field("Folder/session name *").performScrollTo().performTextInput("my local project")
        field("Display name (optional)").performScrollTo().performTextInput("Mitt projekt 😀")
        compose.onNodeWithText("[ SAVE ]").performScrollTo().performClick()
        compose.waitUntil(5000) { returned }
        val saved = runBlocking { projects.getAll().first().single() }
        assertEquals("my-local-project", saved.name); assertEquals("Mitt projekt 😀", saved.displayName)
        assertEquals(ProjectTargetType.LOCAL, saved.targetType)
    }
    @Test fun sshTargetAdvancedSettingsAndScriptResetPersistOnSave() {
        val id = runBlocking { servers.save(Server(name = "Build server", host = "example.invalid", username = "user")) }
        vm.update { copy(targetType = ProjectTargetType.LOCAL) }
        start()
        compose.onNodeWithText("Local device").performClick()
        compose.onNodeWithText("Build server").performClick()
        field("Folder/session name *").performTextInput("web")
        advanced()
        field("Git Repo URL (optional)").performScrollTo().performTextInput("https://github.com/example/web")
        compose.onNodeWithText("GitHub HTTPS URLs are converted to SSH on save:", substring = true).assertExists()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Creates a tmux session named after the project, or attaches if it already exists.").assertExists()
        compose.onNode(hasSetTextAction() and hasText("cd {{PROJECT_PATH}}", substring = false)).performScrollTo().performTextReplacement("echo custom")
        compose.onNodeWithText("[ RESET ]").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("cd {{PROJECT_PATH}}", substring = false)).assertExists()
        compose.onNode(hasSetTextAction() and hasText("e.g. npm run dev, htop, or codex")).performScrollTo().performTextInput("npm run dev")
        compose.onNodeWithText("[ SAVE ]").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.state.value.saved }
        val saved = runBlocking { projects.getAll().first().single() }
        assertEquals(id, saved.serverId); assertTrue(saved.useTmux)
        assertEquals("git@github.com:example/web.git", saved.gitUrl); assertEquals("npm run dev", saved.aiCommand)
        assertEquals("cd {{PROJECT_PATH}}", saved.customScript)
    }
    @Test fun invalidFolderShowsValidationAndDisablesSaveWhileLocalAdvancedDisablesTmux() {
        vm.update { copy(targetType = ProjectTargetType.LOCAL) }
        start()
        field("Folder/session name *").performTextInput("../escape")
        compose.onNodeWithText("[ SAVE ]").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertNotNull(vm.state.value.nameError) }
        advanced()
        compose.onNode(isToggleable()).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Local mode currently uses a plain device shell without tmux.").assertExists()
        compose.onNodeWithText("[ v ADVANCED ]").performScrollTo().performClick()
        compose.onNodeWithText("CUSTOM SCRIPT").assertDoesNotExist()
    }
    @Test fun editingChangesTheTitleWithoutExposingARenameOfTheRemoteFolder() {
        val id = runBlocking { projects.save(Project(serverId = LOCAL_PROJECT_SERVER_ID, targetType = ProjectTargetType.LOCAL,
            name = "original-folder", displayName = "Original title")) }
        start(id)
        compose.waitUntil(5000) { vm.state.value.name == "original-folder" }
        compose.onNodeWithText("Folder/session: original-folder").assertExists()
        field("Folder/session name *").assertDoesNotExist()
        field("Display name (optional)").performScrollTo().performTextReplacement("New title")
        compose.onNodeWithText("[ SAVE ]").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.state.value.saved }
        val saved = runBlocking { projects.getById(id)!! }
        assertEquals("original-folder", saved.name); assertEquals("New title", saved.displayName)
    }
}
