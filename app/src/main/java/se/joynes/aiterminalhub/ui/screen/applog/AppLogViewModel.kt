package se.joynes.aiterminalhub.ui.screen.applog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import se.joynes.aiterminalhub.data.model.AppLogEntry
import se.joynes.aiterminalhub.data.repository.AppLogRepository
import se.joynes.aiterminalhub.domain.usecase.ExportSessionLog
import javax.inject.Inject

@HiltViewModel
class AppLogViewModel @Inject constructor(
    private val appLogRepository: AppLogRepository,
    private val exportSessionLog: ExportSessionLog
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLevel = MutableStateFlow("ALL")
    val selectedLevel: StateFlow<String> = _selectedLevel.asStateFlow()

    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    val logs: StateFlow<List<AppLogEntry>> = combine(
        _searchQuery.debounce(300),
        _selectedLevel
    ) { query, level -> Pair(query, level) }
        .flatMapLatest { (query, level) ->
            when {
                query.isNotBlank() -> appLogRepository.search(query)
                level != "ALL" -> appLogRepository.logsByLevel(level)
                else -> appLogRepository.recentLogs()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(query: String) { _searchQuery.value = query }
    fun setLevel(level: String) { _selectedLevel.value = level }
    fun toggleAutoScroll() { _autoScroll.value = !_autoScroll.value }

    fun export() {
        viewModelScope.launch(Dispatchers.IO) {
            try { exportSessionLog() } catch (_: Exception) {}
        }
    }
}
