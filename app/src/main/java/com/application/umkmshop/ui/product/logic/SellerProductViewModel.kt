package com.application.umkmshop.ui.product.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.umkmshop.data.product.ProductImageUpload
import com.application.umkmshop.data.product.ProductRepository
import com.application.umkmshop.data.product.SellerProduct
import com.application.umkmshop.data.product.SellerProductInput
import com.application.umkmshop.data.product.isPhase3ProductCategory
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
    val cityOptions: List<String> = emptyList(),
    val selectedImage: ProductImageUpload? = null,
    val selectedImageName: String? = null,
) {
    val editingProduct: SellerProduct? =
        products.firstOrNull { it.id == editingProductId }

    val isEditing: Boolean = editingProductId != null
}

open class SellerProductViewModel(
    private val repository: ProductRepository = ProductRepository(),
) : ViewModel() {
    protected val _state = MutableStateFlow(SellerProductUiState())
    val state: StateFlow<SellerProductUiState> = _state.asStateFlow()

    fun refreshProducts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            runCatching {
                Triple(
                    repository.listOwnProducts(),
                    repository.getOwnCity(),
                    repository.listAvailableCities(),
                )
            }.onSuccess { (products, city, cityOptions) ->
                    _state.update {
                        it.copy(
                            products = products,
                            city = city,
                            cityOptions = cityOptions,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = error.userMessage(),
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

    fun setCity(value: String) {
        _state.update { it.copy(city = value, message = null) }
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
            !isPhase3ProductCategory(current.category) -> "Pilih kategori Fase 3."
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
                        message = error.userMessage(),
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
                            message = "Produk dinonaktifkan.",
                        )
                    }
                    refreshProducts()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            message = error.userMessage(),
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
                            message = error.userMessage(),
                        )
                    }
                }
        }
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Operasi produk gagal."

    private fun Double.toPlainPrice(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()
}
