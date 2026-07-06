package com.application.umkmshop.ui.product.logic

import com.application.umkmshop.data.product.ProductRepository
import com.application.umkmshop.data.product.SellerProduct
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SellerProductViewModelTest {
    private val mockRepository: ProductRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SellerProductViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SellerProductViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `refreshProducts - success`() = runTest {
        val mockProducts = listOf<SellerProduct>(mockk(relaxed = true))
        coEvery { mockRepository.listOwnProducts() } returns mockProducts
        
        viewModel.refreshProducts()
        
        val state = viewModel.state.value
        assertEquals(mockProducts, state.products)
        assertFalse(state.isLoading)
    }

    @Test
    fun `saveProduct - validation and repository call`() = runTest {
        viewModel.setName("Test Product")
        viewModel.setPrice("1000")
        viewModel.setCategory("Makanan (bahan makanan)")
        
        val mockUpload = mockk<com.application.umkmshop.data.product.ProductImageUpload>()
        viewModel.setSelectedImage(mockUpload, "image.jpg")
        
        coEvery { mockRepository.createProduct(any()) } returns mockk(relaxed = true)
        
        viewModel.saveProduct { }
        
        coVerify { mockRepository.createProduct(any()) }
    }

    @Test
    fun `startEdit - populate state`() = runTest {
        val mockProduct: SellerProduct = mockk(relaxed = true) {
            every { id } returns "p1"
            every { name } returns "Existing"
            every { price } returns 5000.0
        }
        val stateFlow = viewModel.getPrivateField<MutableStateFlow<SellerProductUiState>>("_state")
        stateFlow.update { it.copy(products = listOf(mockProduct)) }
        
        viewModel.startEdit("p1")
        
        val state = viewModel.state.value
        assertEquals("Existing", state.name)
        assertEquals("5000", state.price)
    }

    @Test
    fun `deactivate - success`() = runTest {
        val product: SellerProduct = mockk(relaxed = true) { every { id } returns "p1" }
        coEvery { mockRepository.deactivateProduct("p1") } returns product
        
        viewModel.deactivate("p1")
        
        coVerify { mockRepository.deactivateProduct("p1") }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.getPrivateField(name: String): T {
        val field = this.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this) as T
    }
}
