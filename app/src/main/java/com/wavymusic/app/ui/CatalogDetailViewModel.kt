
package com.wavymusic.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wavymusic.app.catalog.CatalogDetailRepository
import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CatalogDetailKind {
    ARTIST,
    ALBUM
}

sealed interface CatalogDetailUiState {
    data object Loading : CatalogDetailUiState
    data object NotFound : CatalogDetailUiState

    data class ArtistLoaded(
        val artist: CatalogArtist
    ) : CatalogDetailUiState

    data class AlbumLoaded(
        val album: CatalogAlbum
    ) : CatalogDetailUiState

    data class Error(
        val message: String
    ) : CatalogDetailUiState
}

class CatalogDetailViewModel(
    private val repository: CatalogDetailRepository,
    private val kind: CatalogDetailKind,
    private val catalogItemId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow<CatalogDetailUiState>(
        CatalogDetailUiState.Loading
    )

    val uiState: StateFlow<CatalogDetailUiState> =
        _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        val id = catalogItemId?.takeIf { it.isNotBlank() }

        if (id == null) {
            _uiState.value = CatalogDetailUiState.NotFound
            return
        }

        viewModelScope.launch {
            try {
                val result = when (kind) {
                    CatalogDetailKind.ARTIST ->
                        repository.artist(id)?.let {
                            CatalogDetailUiState.ArtistLoaded(it)
                        }

                    CatalogDetailKind.ALBUM ->
                        repository.album(id)?.let {
                            CatalogDetailUiState.AlbumLoaded(it)
                        }
                }

                _uiState.value = result ?: CatalogDetailUiState.NotFound
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = CatalogDetailUiState.Error(
                    error.message ?: "Catalog details could not be loaded."
                )
            }
        }
    }
}

class CatalogDetailViewModelFactory(
    private val repository: CatalogDetailRepository,
    private val kind: CatalogDetailKind,
    private val catalogItemId: String?
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CatalogDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CatalogDetailViewModel(
                repository = repository,
                kind = kind,
                catalogItemId = catalogItemId
            ) as T
        }

        throw IllegalArgumentException(
            "Unsupported ViewModel type: ${modelClass.name}"
        )
    }
}
