package com.wavymusic.app.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface CatalogContentState {

    data object Loading : CatalogContentState

    data object NotConfigured : CatalogContentState

    data object Empty : CatalogContentState

    data object Offline : CatalogContentState

    data class Error(
        val message: String
    ) : CatalogContentState
}

open class CatalogViewModel : ViewModel() {

    private val _contentState =
        MutableStateFlow<CatalogContentState>(
            CatalogContentState.NotConfigured
        )

    val contentState: StateFlow<CatalogContentState> =
        _contentState.asStateFlow()

    protected fun publishContentState(
        state: CatalogContentState
    ) {
        _contentState.value = state
    }
}

class HomeViewModel : CatalogViewModel()

class LibraryViewModel : CatalogViewModel()

data class SearchUiState(
    val query: String = "",
    val contentState: CatalogContentState =
        CatalogContentState.NotConfigured
)

class SearchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())

    val uiState: StateFlow<SearchUiState> =
        _uiState.asStateFlow()

    fun onQueryChanged(query: String) {
        _uiState.update { current ->
            current.copy(query = query)
        }
    }
}

/**
 * Presentation state only.
 *
 * The playback service will become the authoritative source
 * for this data when real playback is implemented.
 */
data class MiniPlayerUiState(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false
) {
    val isVisible: Boolean
        get() = !title.isNullOrBlank()
}