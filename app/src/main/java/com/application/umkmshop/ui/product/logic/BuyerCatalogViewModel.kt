package com.application.umkmshop.ui.product.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.umkmshop.data.product.BuyerCatalogFilter
import com.application.umkmshop.data.product.BuyerProduct
import com.application.umkmshop.data.product.ProductRepository
import com.application.umkmshop.ui.components.debounceSearch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BuyerCatalogUiState(
    val products: List<BuyerProduct> = emptyList(),
    val page: Int = 0,
    val hasPreviousPage: Boolean = false,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
    val searchQuery: String = "",
    val category: String = "",
    val city: String = "",
    val cityOptions: List<String> = emptyList(),
    val minPrice: String = "",
    val maxPrice: String = "",
)

data class BuyerProductDetailUiState(
    val product: BuyerProduct? = null,
    val isLoading: Boolean = false,
    val isReporting: Boolean = false,
    val message: String? = null,
)

data class FavoriteProductsUiState(
    val products: List<BuyerProduct> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

@OptIn(FlowPreview::class)
open class BuyerCatalogViewModel(
    private val repository: ProductRepository = ProductRepository(),
) : ViewModel() {
    protected val _catalogState = MutableStateFlow(BuyerCatalogUiState())
    val catalogState: StateFlow<BuyerCatalogUiState> = _catalogState.asStateFlow()

    protected val _detailState = MutableStateFlow(BuyerProductDetailUiState())
    val detailState: StateFlow<BuyerProductDetailUiState> = _detailState.asStateFlow()

    protected val _favoriteState = MutableStateFlow(FavoriteProductsUiState())
    val favoriteState: StateFlow<FavoriteProductsUiState> = _favoriteState.asStateFlow()

    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var favoriteJob: Job? = null
    private var catalogRequestId = 0
    private var detailRequestId = 0
    private var favoriteRequestId = 0
    private val favoriteSyncJobs = mutableMapOf<String, Job>()
    private val favoriteDesiredStates = mutableMapOf<String, Boolean>()
    private val favoriteConfirmedStates = mutableMapOf<String, Boolean>()

    init {
        refreshCityOptions()
        observeFilters()
    }

    private fun observeFilters() {
        viewModelScope.launch {
            combine(
                _catalogState.map { it.searchQuery }.debounceSearch(500L),
                _catalogState.map { it.category }.distinctUntilChanged(),
                _catalogState.map { it.city }.distinctUntilChanged(),
                _catalogState.map { it.minPrice }.distinctUntilChanged(),
                _catalogState.map { it.maxPrice }.distinctUntilChanged()
            ) { _, _, _, _, _ ->
                refreshCatalog(resetPage = true)
            }.collect()
        }
    }

    fun setSearchQuery(value: String) {
        _catalogState.update { it.copy(searchQuery = value, message = null) }
    }

    fun setCategory(value: String) {
        _catalogState.update { it.copy(category = value, message = null) }
    }

    fun setCity(value: String) {
        _catalogState.update { it.copy(city = value, message = null) }
    }

    fun setMinPrice(value: String) {
        _catalogState.update { it.copy(minPrice = value.priceInput(), message = null) }
    }

    fun setMaxPrice(value: String) {
        _catalogState.update { it.copy(maxPrice = value.priceInput(), message = null) }
    }

    fun applyFilters() {
        refreshCatalog(resetPage = true)
    }

    fun clearFilters() {
        _catalogState.update {
            it.copy(
                searchQuery = "",
                category = "",
                city = "",
                minPrice = "",
                maxPrice = "",
                message = null,
            )
        }
        refreshCatalog(resetPage = true)
    }

    fun nextPage() {
        if (_catalogState.value.hasNextPage) {
            refreshCatalog(page = _catalogState.value.page + 1)
        }
    }

    fun previousPage() {
        if (_catalogState.value.hasPreviousPage) {
            refreshCatalog(page = _catalogState.value.page - 1)
        }
    }

    fun refreshCatalog(resetPage: Boolean = false, page: Int = _catalogState.value.page) {
        val current = _catalogState.value
        val targetPage = if (resetPage) 0 else page.coerceAtLeast(0)
        val minPrice = current.minPrice.toDoubleOrNull()
        val maxPrice = current.maxPrice.toDoubleOrNull()
        val validationMessage = when {
            current.minPrice.isNotBlank() && minPrice == null -> "Harga minimum tidak valid."
            current.maxPrice.isNotBlank() && maxPrice == null -> "Harga maksimum tidak valid."
            minPrice != null && maxPrice != null && minPrice > maxPrice -> "Harga minimum tidak boleh lebih besar dari maksimum."
            else -> null
        }
        if (validationMessage != null) {
            _catalogState.update { it.copy(message = validationMessage) }
            return
        }

        val requestId = ++catalogRequestId
        val requestFilter = BuyerCatalogFilter(
            searchQuery = current.searchQuery,
            category = current.category,
            city = current.city,
            minPrice = minPrice,
            maxPrice = maxPrice,
        )
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            _catalogState.update { it.copy(isLoading = true, message = null) }
            runCatching {
                repository.listBuyerProducts(
                    filter = requestFilter,
                    page = targetPage,
                )
            }.onSuccess { result ->
                if (requestId != catalogRequestId) return@onSuccess
                _catalogState.update {
                    it.copy(
                        products = result.products,
                        page = result.page,
                        hasPreviousPage = result.hasPreviousPage,
                        hasNextPage = result.hasNextPage,
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                if (requestId != catalogRequestId) return@onFailure
                _catalogState.update {
                    it.copy(
                        isLoading = false,
                        message = error.userMessage("Gagal memuat katalog."),
                    )
                }
            }
        }
    }

    private fun refreshCityOptions() {
        viewModelScope.launch {
            runCatching { repository.listAvailableCities() }
                .onSuccess { cities ->
                    _catalogState.update { it.copy(cityOptions = cities) }
                }
        }
    }

    fun loadDetail(productId: String) {
        if (_detailState.value.product?.id == productId) return

        val requestId = ++detailRequestId
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detailState.update {
                BuyerProductDetailUiState(isLoading = true)
            }
            runCatching { repository.getBuyerProductDetail(productId) }
                .onSuccess { product ->
                    if (requestId != detailRequestId) return@onSuccess
                    _detailState.update {
                        BuyerProductDetailUiState(product = product)
                    }
                }
                .onFailure { error ->
                    if (requestId != detailRequestId) return@onFailure
                    _detailState.update {
                        BuyerProductDetailUiState(
                            isLoading = false,
                            message = error.userMessage("Produk tidak tersedia di katalog."),
                        )
                    }
                }
        }
    }

    fun reportProduct(productId: String, reason: String) {
        if (_detailState.value.isReporting) return

        viewModelScope.launch {
            _detailState.update { it.copy(isReporting = true, message = null) }
            runCatching {
                repository.reportProduct(productId = productId, reason = reason)
            }.onSuccess {
                _detailState.update {
                    it.copy(
                        isReporting = false,
                        message = "Laporan produk terkirim untuk ditinjau.",
                    )
                }
            }.onFailure { error ->
                _detailState.update {
                    it.copy(
                        isReporting = false,
                        message = error.reportUserMessage(),
                    )
                }
            }
        }
    }

    fun refreshFavorites() {
        val requestId = ++favoriteRequestId
        favoriteJob?.cancel()
        favoriteJob = viewModelScope.launch {
            _favoriteState.update { it.copy(isLoading = true, message = null) }
            runCatching { repository.listFavoriteProducts() }
                .onSuccess { products ->
                    if (requestId != favoriteRequestId) return@onSuccess
                    _favoriteState.update {
                        it.copy(
                            products = products,
                            isLoading = false,
                        )
                    }
                    syncFavoriteFlags(products.map { it.id }.toSet())
                }
                .onFailure { error ->
                    if (requestId != favoriteRequestId) return@onFailure
                    _favoriteState.update {
                        it.copy(
                            isLoading = false,
                            message = error.userMessage("Gagal memuat favorit."),
                        )
                    }
                }
        }
    }

    fun toggleFavorite(product: BuyerProduct, nextFavorite: Boolean) {
        val productId = product.id
        favoriteConfirmedStates.putIfAbsent(productId, product.isFavorite)
        favoriteDesiredStates[productId] = nextFavorite
        applyFavoriteOptimistically(
            product = product,
            isFavorite = nextFavorite,
        )

        if (favoriteSyncJobs[productId]?.isActive == true) {
            return
        }

        favoriteSyncJobs[productId] = viewModelScope.launch {
            syncFavoriteIntent(productId)
        }
    }

    private suspend fun syncFavoriteIntent(productId: String) {
        try {
            while (true) {
                val desiredState = favoriteDesiredStates[productId] ?: return
                val confirmedState = favoriteConfirmedStates[productId]
                if (confirmedState == desiredState) {
                    favoriteDesiredStates.remove(productId)
                    return
                }

                runCatching {
                    repository.setProductFavorite(
                        productId = productId,
                        isFavorite = desiredState,
                    )
                }.onSuccess {
                    favoriteConfirmedStates[productId] = desiredState
                    if (favoriteDesiredStates[productId] == desiredState) {
                        favoriteDesiredStates.remove(productId)
                        return
                    }
                }.onFailure { error ->
                    favoriteDesiredStates.remove(productId)
                    val rollbackState = favoriteConfirmedStates[productId] ?: currentFavoriteState(productId)
                    applyFavoriteOptimistically(
                        product = currentProductSnapshot(productId),
                        productId = productId,
                        isFavorite = rollbackState,
                    )
                    applyFavoriteError(error.userMessage("Gagal memperbarui favorit."))
                    return
                }
            }
        } finally {
            favoriteSyncJobs.remove(productId)
            if (favoriteDesiredStates.containsKey(productId)) {
                favoriteSyncJobs[productId] = viewModelScope.launch {
                    syncFavoriteIntent(productId)
                }
            } else {
                favoriteConfirmedStates.remove(productId)
            }
        }
    }

    private fun applyFavoriteOptimistically(
        product: BuyerProduct?,
        productId: String = product?.id.orEmpty(),
        isFavorite: Boolean,
    ) {
        _catalogState.update { state ->
            state.copy(
                products = state.products.map { product ->
                    if (product.id == productId) product.copy(isFavorite = isFavorite) else product
                },
                message = null,
            )
        }
        _detailState.update { state ->
            state.copy(
                product = state.product?.let { product ->
                    if (product.id == productId) product.copy(isFavorite = isFavorite) else product
                },
                message = null,
            )
        }
        _favoriteState.update { state ->
            state.copy(
                products = if (isFavorite) {
                    val optimisticProduct = product?.copy(isFavorite = true)
                    val updatedProducts = state.products.map { favoriteProduct ->
                        if (favoriteProduct.id == productId) {
                            favoriteProduct.copy(isFavorite = true)
                        } else {
                            favoriteProduct
                        }
                    }
                    if (optimisticProduct != null && state.products.none { it.id == productId }) {
                        listOf(optimisticProduct) + updatedProducts
                    } else {
                        updatedProducts
                    }
                } else {
                    state.products.filterNot { it.id == productId }
                },
                message = null,
            )
        }
    }

    private fun applyFavoriteError(message: String) {
        _catalogState.update { it.copy(message = message) }
        _detailState.update { it.copy(message = message) }
        _favoriteState.update { it.copy(message = message) }
    }

    private fun currentFavoriteState(productId: String): Boolean {
        _detailState.value.product
            ?.takeIf { it.id == productId }
            ?.let { return it.isFavorite }

        _catalogState.value.products
            .firstOrNull { it.id == productId }
            ?.let { return it.isFavorite }

        return _favoriteState.value.products.any { it.id == productId && it.isFavorite }
    }

    private fun currentProductSnapshot(productId: String): BuyerProduct? =
        _catalogState.value.products.firstOrNull { it.id == productId }
            ?: _detailState.value.product?.takeIf { it.id == productId }
            ?: _favoriteState.value.products.firstOrNull { it.id == productId }

    private fun syncFavoriteFlags(favoriteProductIds: Set<String>) {
        _catalogState.update { state ->
            state.copy(
                products = state.products.map { product ->
                    product.copy(isFavorite = product.id in favoriteProductIds)
                },
            )
        }
        _detailState.update { state ->
            state.copy(
                product = state.product?.let { product ->
                    product.copy(isFavorite = product.id in favoriteProductIds)
                },
            )
        }
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback

    private fun Throwable.reportUserMessage(): String {
        val rawMessage = message.orEmpty()
        return when {
            rawMessage.contains("duplicate", ignoreCase = true) ||
                rawMessage.contains("unique", ignoreCase = true) ->
                "Laporan yang sama sudah pernah dikirim."
            rawMessage.contains("row-level security", ignoreCase = true) ||
                rawMessage.contains("violates row-level security", ignoreCase = true) ->
                "Produk ini tidak bisa dilaporkan."
            else -> userMessage("Gagal mengirim laporan produk.")
        }
    }

    private fun String.priceInput(): String =
        filter { it.isDigit() || it == '.' }
}
