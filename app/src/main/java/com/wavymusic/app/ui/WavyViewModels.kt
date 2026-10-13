package com.wavymusic.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wavymusic.app.catalog.CatalogSearchDataSource
import com.wavymusic.app.catalog.CatalogSearchHit
import com.wavymusic.app.catalog.CatalogSearchState
import com.wavymusic.app.catalog.provider.CatalogContinuation
import com.wavymusic.app.catalog.provider.CatalogProviderErrorCode
import com.wavymusic.app.catalog.provider.CatalogSearchType
import com.wavymusic.app.catalog.storage.CatalogRecentSearchEntity
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    protected fun publishContentState(state: CatalogContentState) {
        _contentState.value = state
    }
}

class HomeViewModel : CatalogViewModel()

class LibraryViewModel : CatalogViewModel()

enum class CatalogSearchUiStatus {
    IDLE,
    LOADING,
    RESULTS,
    EMPTY,
    NOT_CONFIGURED,
    PROVIDER_UNAVAILABLE,
    OFFLINE,
    RATE_LIMITED,
    ERROR
}

data class SearchUiState(
    val query: String = "",
    val searchType: CatalogSearchType = CatalogSearchType.TRACKS,
    val status: CatalogSearchUiStatus = CatalogSearchUiStatus.NOT_CONFIGURED,
    val results: List<CatalogSearchHit> = emptyList(),
    val continuations: List<CatalogContinuation> = emptyList(),
    val recentSearches: List<CatalogRecentSearchEntity> = emptyList(),
    val message: String? = null,
    val retryAfterMs: Long? = null,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false
)

class SearchViewModel(
    private val dataSource: CatalogSearchDataSource
) : ViewModel() {

    private val initialStatus =
        if (dataSource.isConfigured) {
            CatalogSearchUiStatus.IDLE
        } else {
            CatalogSearchUiStatus.NOT_CONFIGURED
        }

    private val _uiState = MutableStateFlow(
        SearchUiState(status = initialStatus)
    )

    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var recentSearchJob: Job? = null
    private var requestGeneration = 0L

    init {
        refreshRecentSearches()
    }

    fun onQueryChanged(query: String) {
        val type = _uiState.value.searchType

        _uiState.update {
            it.copy(
                query = query,
                results = emptyList(),
                continuations = emptyList(),
                message = null,
                retryAfterMs = null
            )
        }

        startSearch(
            query = query,
            type = type,
            continuation = null,
            debounce = true,
            baseResults = emptyList(),
            baseContinuations = emptyList()
        )
    }

    fun onSearchTypeChanged(type: CatalogSearchType) {
        val current = _uiState.value
        if (current.searchType == type) return

        _uiState.update {
            it.copy(
                searchType = type,
                results = emptyList(),
                continuations = emptyList(),
                message = null,
                retryAfterMs = null
            )
        }

        startSearch(
            query = current.query,
            type = type,
            continuation = null,
            debounce = true,
            baseResults = emptyList(),
            baseContinuations = emptyList()
        )

        refreshRecentSearches()
    }

    fun onRecentSearchSelected(search: CatalogRecentSearchEntity) {
        val type = runCatching {
            CatalogSearchType.valueOf(search.searchType)
        }.getOrNull() ?: return

        _uiState.update {
            it.copy(
                query = search.queryText,
                searchType = type,
                results = emptyList(),
                continuations = emptyList(),
                message = null,
                retryAfterMs = null
            )
        }

        startSearch(
            query = search.queryText,
            type = type,
            continuation = null,
            debounce = false,
            baseResults = emptyList(),
            baseContinuations = emptyList()
        )
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            try {
                dataSource.clearRecentSearches()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the current screen usable if local storage fails.
            }

            refreshRecentSearches()
        }
    }

    fun loadMore() {
        val current = _uiState.value
        val continuation = current.continuations.firstOrNull() ?: return

        if (
            current.query.isBlank() ||
            current.isLoadingMore ||
            current.isRefreshing
        ) {
            return
        }

        startSearch(
            query = current.query,
            type = current.searchType,
            continuation = continuation,
            debounce = false,
            baseResults = current.results,
            baseContinuations = current.continuations.filterNot {
                it == continuation
            }
        )
    }

    private fun startSearch(
        query: String,
        type: CatalogSearchType,
        continuation: CatalogContinuation?,
        debounce: Boolean,
        baseResults: List<CatalogSearchHit>,
        baseContinuations: List<CatalogContinuation>
    ) {
        val requestId = ++requestGeneration

        searchJob?.cancel()
        searchJob = null

        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    status = initialStatus,
                    results = emptyList(),
                    continuations = emptyList(),
                    message = null,
                    retryAfterMs = null,
                    isRefreshing = false,
                    isLoadingMore = false
                )
            }
            return
        }

        if (!dataSource.isConfigured) {
            _uiState.update {
                it.copy(
                    status = CatalogSearchUiStatus.NOT_CONFIGURED,
                    results = emptyList(),
                    continuations = emptyList(),
                    message = "No catalog provider is configured. Your query " +
                        "has not been sent to a music service.",
                    retryAfterMs = null,
                    isRefreshing = false,
                    isLoadingMore = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                status = if (baseResults.isNotEmpty()) {
                    CatalogSearchUiStatus.RESULTS
                } else {
                    CatalogSearchUiStatus.LOADING
                },
                results = baseResults,
                continuations = baseContinuations,
                message = null,
                retryAfterMs = null,
                isRefreshing = false,
                isLoadingMore = continuation != null
            )
        }

        searchJob = viewModelScope.launch {
            try {
                if (debounce) delay(SEARCH_DEBOUNCE_MS)

                if (requestId != requestGeneration) return@launch

                dataSource.search(
                    query = query.trim(),
                    type = type,
                    pageSize = DEFAULT_PAGE_SIZE,
                    continuation = continuation
                ).collect { incoming ->
                    if (requestId == requestGeneration) {
                        applyRepositoryState(
                            incoming = incoming,
                            continuation = continuation,
                            baseResults = baseResults,
                            baseContinuations = baseContinuations
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId == requestGeneration) {
                    val hasVisibleResults =
                        _uiState.value.results.isNotEmpty()

                    _uiState.update {
                        it.copy(
                            status = when {
                                hasVisibleResults ->
                                    CatalogSearchUiStatus.RESULTS

                                error is IOException ->
                                    CatalogSearchUiStatus.OFFLINE

                                else -> CatalogSearchUiStatus.ERROR
                            },
                            message = error.message
                                ?: "Catalog search could not be completed.",
                            isRefreshing = false,
                            isLoadingMore = false
                        )
                    }
                }
            } finally {
                if (requestId == requestGeneration) {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isLoadingMore = false
                        )
                    }
                    refreshRecentSearches()
                }
            }
        }
    }

    private fun applyRepositoryState(
        incoming: CatalogSearchState,
        continuation: CatalogContinuation?,
        baseResults: List<CatalogSearchHit>,
        baseContinuations: List<CatalogContinuation>
    ) {
        fun mergeItems(items: List<CatalogSearchHit>) =
            (baseResults + items).distinctBy { it.stableKey }

        fun mergeContinuations(
            next: List<CatalogContinuation>
        ): List<CatalogContinuation> {
            val prefix = if (continuation == null) {
                emptyList()
            } else {
                baseContinuations
            }

            return (prefix + next).distinctBy {
                it.providerId to it.token
            }
        }

        when (incoming) {
            CatalogSearchState.Idle -> {
                _uiState.update {
                    it.copy(
                        status = initialStatus,
                        results = emptyList(),
                        continuations = emptyList(),
                        message = null,
                        isRefreshing = false,
                        isLoadingMore = false
                    )
                }
            }

            is CatalogSearchState.Loading -> {
                val results = mergeItems(incoming.items)

                _uiState.update {
                    it.copy(
                        status = if (results.isEmpty()) {
                            CatalogSearchUiStatus.LOADING
                        } else {
                            CatalogSearchUiStatus.RESULTS
                        },
                        results = results,
                        continuations = mergeContinuations(emptyList()),
                        isRefreshing = incoming.isRefreshing,
                        message = null
                    )
                }
            }

            is CatalogSearchState.Success -> {
                val results = mergeItems(incoming.items)

                _uiState.update {
                    it.copy(
                        status = if (results.isEmpty()) {
                            CatalogSearchUiStatus.EMPTY
                        } else {
                            CatalogSearchUiStatus.RESULTS
                        },
                        results = results,
                        continuations = mergeContinuations(
                            incoming.continuations
                        ),
                        message = incoming.warnings
                            .takeIf { warnings -> warnings.isNotEmpty() }
                            ?.joinToString(separator = " ") { warning ->
                                warning.message
                            },
                        retryAfterMs = null,
                        isRefreshing = false,
                        isLoadingMore = false
                    )
                }
            }

            is CatalogSearchState.Empty -> {
                val results = baseResults

                _uiState.update {
                    it.copy(
                        status = if (results.isEmpty()) {
                            CatalogSearchUiStatus.EMPTY
                        } else {
                            CatalogSearchUiStatus.RESULTS
                        },
                        results = results,
                        continuations = mergeContinuations(
                            incoming.continuations
                        ),
                        message = null,
                        isRefreshing = false,
                        isLoadingMore = false
                    )
                }
            }

            is CatalogSearchState.Offline -> {
                val results = mergeItems(incoming.items)

                _uiState.update {
                    it.copy(
                        status = if (results.isEmpty()) {
                            CatalogSearchUiStatus.OFFLINE
                        } else {
                            CatalogSearchUiStatus.RESULTS
                        },
                        results = results,
                        continuations = mergeContinuations(emptyList()),
                        message = incoming.message,
                        isRefreshing = false,
                        isLoadingMore = false
                    )
                }
            }

            is CatalogSearchState.RateLimited -> {
                val results = mergeItems(incoming.items)

                _uiState.update {
                    it.copy(
                        status = if (results.isEmpty()) {
                            CatalogSearchUiStatus.RATE_LIMITED
                        } else {
                            CatalogSearchUiStatus.RESULTS
                        },
                        results = results,
                        continuations = mergeContinuations(emptyList()),
                        message = incoming.message,
                        retryAfterMs = incoming.retryAfterMs,
                        isRefreshing = false,
                        isLoadingMore = false
                    )
                }
            }

            is CatalogSearchState.Failure -> {
                val results = mergeItems(incoming.items)
                val hasResults = results.isNotEmpty()

                val failureStatus = when (incoming.error.code) {
                    CatalogProviderErrorCode.OFFLINE,
                    CatalogProviderErrorCode.NETWORK ->
                        CatalogSearchUiStatus.OFFLINE

                    CatalogProviderErrorCode.RATE_LIMITED ->
                        CatalogSearchUiStatus.RATE_LIMITED

                    CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                    CatalogProviderErrorCode.SOURCE_UNAVAILABLE,
                    CatalogProviderErrorCode.AUTHORIZATION_REQUIRED,
                    CatalogProviderErrorCode.POLICY_BLOCKED ->
                        CatalogSearchUiStatus.PROVIDER_UNAVAILABLE

                    else -> CatalogSearchUiStatus.ERROR
                }

                _uiState.update {
                    it.copy(
                        status = if (hasResults) {
                            CatalogSearchUiStatus.RESULTS
                        } else {
                            failureStatus
                        },
                        results = results,
                        continuations = mergeContinuations(emptyList()),
                        message = incoming.error.message,
                        retryAfterMs = incoming.error.retryAfterMs,
                        isRefreshing = false,
                        isLoadingMore = false
                    )
                }
            }
        }
    }

    private fun refreshRecentSearches() {
        recentSearchJob?.cancel()

        recentSearchJob = viewModelScope.launch {
            val currentType = _uiState.value.searchType

            val recent = try {
                dataSource.recentSearches(
                    type = currentType,
                    limit = RECENT_SEARCH_LIMIT
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }

            _uiState.update {
                it.copy(recentSearches = recent)
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        private const val DEFAULT_PAGE_SIZE = 25
        private const val RECENT_SEARCH_LIMIT = 10
    }
}

class SearchViewModelFactory(
    private val dataSource: CatalogSearchDataSource
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(dataSource) as T
        }

        throw IllegalArgumentException(
            "Unsupported ViewModel type: ${modelClass.name}"
        )
    }
}

/**
 * Presentation state for the shared playback UI.
 * Playback remains owned by the playback service/controller.
 */
data class MiniPlayerUiState(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false
) {
    val isVisible: Boolean
        get() = !title.isNullOrBlank()
}