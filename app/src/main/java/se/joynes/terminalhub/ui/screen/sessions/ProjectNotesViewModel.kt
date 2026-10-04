package se.joynes.terminalhub.ui.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import se.joynes.terminalhub.data.notes.ProjectNotesRepository

@HiltViewModel
class ProjectNotesViewModel @Inject constructor(val notes: ProjectNotesRepository) : ViewModel() {
    fun open(id: Long) { viewModelScope.launch { notes.load(id) } }
    fun flush() { viewModelScope.launch { notes.flush() } }
}
