package com.application.umkmshop.ui.product.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.umkmshop.data.auth.AuthErrorParser
import com.application.umkmshop.data.product.ProductImageUpload
import com.application.umkmshop.data.product.ProductRepository
import com.application.umkmshop.data.product.SellerProduct
import com.application.umkmshop.data.product.SellerProductInput
import com.application.umkmshop.data.product.isPhase3ProductCategory
import com.application.umkmshop.data.shipping.ShippingRepository
import com.application.umkmshop.data.shipping.WilayahItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SellerProductUiState(
    val products: List<SellerProduct> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val editingProductId: String? = null,
    val name: String = "",
    val price: String = "",
    val description: String = "",
    val category: String = "",
    val city: String = "",
    
    // Wilayah.id Integration
    val provinces: List<WilayahItem> = emptyList(),
    val regencies: List<WilayahItem> = emptyList(),
    val selectedProvince: WilayahItem? = null,
    val selectedRegency: WilayahItem? = null,

    val selectedImage: ProductImageUpload? = null,
    val selectedImageName: String? = null,
) {
    val editingProduct: SellerProduct? =
        products.firstOrNull { it.id == editingProductId }

    val isEditing: Boolean = editingProductId != null
}

open class SellerProductViewModel(
    private val repository: ProductRepository = ProductRepository(),
    private val shippingRepository: ShippingRepository = ShippingRepository(),
) : ViewModel() {
    protected val _state = MutableStateFlow(SellerProductUiState())
    val state: StateFlow<SellerProductUiState> = _state.asStateFlow()

    init {
        loadProvinces()
    }

    private fun loadProvinces() {
        viewModelScope.launch {
            val provinces = shippingRepository.getProvinces()
            _state.update { it.copy(provinces = provinces) }
        }
    }

    fun selectProvince(province: WilayahItem) {
        _state.update { 
            it.copy(
                selectedProvince = province,
                selectedRegency = null,
                regencies = emptyList(),
                city = ""
            )
        }
        viewModelScope.launch {
            val regencies = shippingRepository.getRegencies(province.code)
            _state.update { it.copy(regencies = regencies) }
        }
    }

    fun selectRegency(regency: WilayahItem) {
        _state.update { 
            it.copy(
                selectedRegency = regency,
                city = regency.name
            )
        }
    }

    fun refreshProducts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            runCatching {
                Pair(
                    repository.listOwnProducts(),
                    repository.getOwnCity(),
                )
            }.onSuccess { (products, city) ->
                    _state.update {
                        it.copy(
                            products = products,
                            city = city,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = AuthErrorParser.mapThrowableToMessage(error),
                        )
                    }
                }
        }
    }

    fun startCreate() {
        _state.update {
            it.copy(
                editingProductId = null,
                name = "",
                price = "",
                description = "",
                category = "",
                selectedImage = null,
                selectedImageName = null,
                message = null,
            )
        }
    }

    fun startEdit(productId: String) {
        val product = _state.value.products.firstOrNull { it.id == productId } ?: return
        _state.update {
            it.copy(
                editingProductId = product.id,
                name = product.name,
                price = product.price.toPlainPrice(),
                description = product.description.orEmpty(),
                category = product.category.orEmpty(),
                selectedImage = null,
                selectedImageName = product.imageUrls.firstOrNull()?.substringAfterLast('/'),
                message = null,
            )
        }
    }

    fun setName(value: String) {
        _state.update { it.copy(name = value, message = null) }
    }

    fun setPrice(value: String) {
        _state.update { it.copy(price = value.filter { char -> char.isDigit() || char == '.' }, message = null) }
    }

    fun setDescription(value: String) {
        _state.update { it.copy(description = value, message = null) }
    }

    fun setCategory(value: String) {
        _state.update { it.copy(category = value, message = null) }
    }

    fun setSelectedImage(upload: ProductImageUpload, displayName: String) {
        _state.update {
            it.copy(
                selectedImage = upload,
                selectedImageName = displayName,
                message = null,
            )
        }
    }

    fun saveProduct(onSaved: () -> Unit) {
        val current = _state.value
        val price = current.price.toDoubleOrNull()
        val validationMessage = when {
            current.name.isBlank() -> "Nama produk wajib diisi."
            price == null || price < 0.0 -> "Harga produk tidak valid."
            !isPhase3ProductCategory(current.category) -> "Silakan pilih kategori produk."
            !current.isEditing && current.selectedImage == null -> "Minimal satu foto produk wajib dipilih."
            else -> null
        }
        if (validationMessage != null) {
            _state.update { it.copy(message = validationMessage) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            val input = SellerProductInput(
                name = current.name,
                price = requireNotNull(price),
                description = current.description,
                category = current.category,
                imageUpload = current.selectedImage,
            )
            runCatching {
                if (current.isEditing) {
                    repository.updateProduct(
                        productId = requireNotNull(current.editingProductId),
                        input = input,
                    )
                } else {
                    repository.createProduct(input)
                }
            }.onSuccess { savedProduct ->
                _state.update { state ->
                    val products = state.products
                        .filterNot { it.id == savedProduct.id } + savedProduct
                    state.copy(
                        products = products.sortedByDescending { it.id },
                        isSaving = false,
                        message = "Produk tersimpan.",
                    )
                }
                onSaved()
                refreshProducts()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        message = AuthErrorParser.mapThrowableToMessage(error),
                    )
                }
            }
        }
    }

    fun deactivate(productId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            runCatching { repository.deactivateProduct(productId) }
                .onSuccess { updatedProduct ->
                    _state.update { state ->
                        state.copy(
                            products = state.products.map {
                                if (it.id == updatedProduct.id) updatedProduct else it
                            },
                            isSaving = false,
                            message = "Produk telah disembunyikan dari katalog.",
                        )
                    }
                    refreshProducts()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            message = AuthErrorParser.mapThrowableToMessage(error),
                        )
                    }
                }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            runCatching { repository.deleteProduct(productId) }
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            products = state.products.filterNot { it.id == productId },
                            isSaving = false,
                            message = "Produk berhasil dihapus secara permanen.",
                        )
                    }
                    refreshProducts()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            message = AuthErrorParser.mapThrowableToMessage(error),
                        )
                    }
                }
        }
    }

    fun saveCity() {
        val city = _state.value.city
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            runCatching { repository.updateOwnCity(city) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            message = "Kota toko tersimpan.",
                        )
                    }
                    refreshProducts()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            message = AuthErrorParser.mapThrowableToMessage(error),
                        )
                    }
                }
        }
    }

    private fun Double.toPlainPrice(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()
}
