package se.joynes.terminalhub.ui.screen.projects

import org.junit.Assert.assertEquals
import org.junit.Test
import se.joynes.terminalhub.data.model.ProjectTargetType

class AddEditProjectViewModelTest {
    @Test
    fun normalizeGitUrlConvertsGithubHttpsToSshForServerProjects() {
        val normalized = AddEditProjectViewModel.normalizeGitUrl(
            "https://github.com/example/sample-project",
            ProjectTargetType.SSH
        )

        assertEquals("git@github.com:example/sample-project.git", normalized)
    }

    @Test
    fun normalizeGitUrlLeavesLocalProjectsUntouched() {
        val normalized = AddEditProjectViewModel.normalizeGitUrl(
            "https://github.com/example/sample-project",
            ProjectTargetType.LOCAL
        )

        assertEquals("https://github.com/example/sample-project", normalized)
    }
}
