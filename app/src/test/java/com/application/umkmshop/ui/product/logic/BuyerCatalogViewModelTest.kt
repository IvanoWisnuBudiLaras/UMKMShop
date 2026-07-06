package com.application.umkmshop.ui.product.logic

import com.application.umkmshop.data.product.BuyerProduct
import com.application.umkmshop.data.product.BuyerProductPage
import com.application.umkmshop.data.product.ProductRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BuyerCatalogViewModelTest {
    private val mockRepository: ProductRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: BuyerCatalogViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = BuyerCatalogViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `refreshCatalog - success`() = runTest {
        val mockPage = BuyerProductPage(
            products = listOf(mockk(relaxed = true)),
            page = 0,
            hasPreviousPage = false,
            hasNextPage = true
        )
        coEvery { mockRepository.listBuyerProducts(any(), any(), any()) } returns mockPage
        
        viewModel.refreshCatalog(resetPage = true)
        advanceUntilIdle()
        
        val state = viewModel.catalogState.value
        assertEquals(mockPage.products, state.products)
        assertFalse(state.isLoading)
        assertTrue(state.hasNextPage)
    }

    @Test
    fun `refreshCatalog - failure`() = runTest {
        coEvery { mockRepository.listBuyerProducts(any(), any(), any()) } throws Exception("Network Error")
        
        viewModel.refreshCatalog(resetPage = true)
        advanceUntilIdle()
        
        val state = viewModel.catalogState.value
        assertEquals("Network Error", state.message)
        assertFalse(state.isLoading)
    }

    @Test
    fun `nextPage - triggers refresh if hasNextPage`() = runTest {
        // Initial state from mock repository in init
        val mockPage = BuyerProductPage(
            products = emptyList(),
            page = 0,
            hasPreviousPage = false,
            hasNextPage = true
        )
        coEvery { mockRepository.listBuyerProducts(any(), page = 0, any()) } returns mockPage
        coEvery { mockRepository.listBuyerProducts(any(), page = 1, any()) } returns mockPage.copy(page = 1, hasPreviousPage = true, hasNextPage = false)
        
        viewModel.refreshCatalog(resetPage = true)
        advanceUntilIdle()
        
        viewModel.nextPage()
        advanceUntilIdle()
        
        coVerify { mockRepository.listBuyerProducts(any(), page = 1, any()) }
        assertEquals(1, viewModel.catalogState.value.page)
    }

    @Test
    fun `previousPage - triggers refresh if hasPreviousPage`() = runTest {
        val mockPage = BuyerProductPage(
            products = emptyList(),
            page = 1,
            hasPreviousPage = true,
            hasNextPage = false
        )
        coEvery { mockRepository.listBuyerProducts(any(), page = 1, any()) } returns mockPage
        coEvery { mockRepository.listBuyerProducts(any(), page = 0, any()) } returns mockPage.copy(page = 0, hasPreviousPage = false, hasNextPage = true)
        
        // Force state to page 1
        viewModel.refreshCatalog(page = 1)
        advanceUntilIdle()
        
        viewModel.previousPage()
        advanceUntilIdle()
        
        coVerify { mockRepository.listBuyerProducts(any(), page = 0, any()) }
        assertEquals(0, viewModel.catalogState.value.page)
    }

    @Test
    fun `filter updates - triggers catalog refresh`() = runTest {
        viewModel.setSearchQuery("shoes")
        viewModel.setCategory("fashion")
        viewModel.setCity("Jakarta")
        viewModel.setMinPrice("1000")
        viewModel.setMaxPrice("5000")
        
        // Wait for debounce and distinctUntilChanged
        advanceTimeBy(600)
        advanceUntilIdle()
        
        coVerify(atLeast = 1) { 
            mockRepository.listBuyerProducts(
                filter = match { 
                    it.searchQuery == "shoes" && 
                    it.category == "fashion" && 
                    it.city == "Jakarta" &&
                    it.minPrice == 1000.0 &&
                    it.maxPrice == 5000.0
                },
                page = 0,
                any()
            ) 
        }
    }

    @Test
    fun `clearFilters - resets state and refreshes`() = runTest {
        viewModel.setSearchQuery("to be cleared")
        advanceTimeBy(600)
        advanceUntilIdle()
        
        viewModel.clearFilters()
        advanceUntilIdle()
        
        val state = viewModel.catalogState.value
        assertEquals("", state.searchQuery)
        assertNull(state.message)
        coVerify { mockRepository.listBuyerProducts(match { it.searchQuery == "" }, any(), any()) }
    }

    @Test
    fun `loadDetail - success`() = runTest {
        val mockProduct: BuyerProduct = mockk(relaxed = true) {
            every { id } returns "p1"
        }
        coEvery { mockRepository.getBuyerProductDetail("p1") } returns mockProduct
        
        viewModel.loadDetail("p1")
        advanceUntilIdle()
        
        val state = viewModel.detailState.value
        assertEquals(mockProduct, state.product)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadDetail - failure`() = runTest {
        coEvery { mockRepository.getBuyerProductDetail("p1") } throws Exception("Not Found")
        
        viewModel.loadDetail("p1")
        advanceUntilIdle()
        
        val state = viewModel.detailState.value
        assertEquals("Not Found", state.message)
        assertFalse(state.isLoading)
    }

    @Test
    fun `reportProduct - success`() = runTest {
        coEvery { mockRepository.reportProduct(any(), any()) } returns mockk()
        
        viewModel.reportProduct("p1", "spam")
        advanceUntilIdle()
        
        val state = viewModel.detailState.value
        assertEquals("Laporan produk terkirim untuk ditinjau.", state.message)
        assertFalse(state.isReporting)
    }

    @Test
    fun `reportProduct - handle duplicate error`() = runTest {
        coEvery { mockRepository.reportProduct(any(), any()) } throws Exception("duplicate key value")
        
        viewModel.reportProduct("p1", "spam")
        advanceUntilIdle()
        
        assertEquals("Laporan yang sama sudah pernah dikirim.", viewModel.detailState.value.message)
    }

    @Test
    fun `refreshFavorites - success and syncs flags`() = runTest {
        val favProduct: BuyerProduct = mockk(relaxed = true) {
            every { id } returns "f1"
            every { isFavorite } returns true
        }
        val catalogProduct: BuyerProduct = mockk(relaxed = true) {
            every { id } returns "f1"
            every { isFavorite } returns false
            every { copy(isFavorite = any()) } returns mockk {
                every { id } returns "f1"
                every { isFavorite } returns true
            }
        }
        
        // Pre-fill catalog with the product
        coEvery { mockRepository.listBuyerProducts(any(), any(), any()) } returns BuyerProductPage(listOf(catalogProduct), 0, false, false)
        viewModel.refreshCatalog(resetPage = true)
        advanceUntilIdle()
        
        coEvery { mockRepository.listFavoriteProducts() } returns listOf(favProduct)
        
        viewModel.refreshFavorites()
        advanceUntilIdle()
        
        val favState = viewModel.favoriteState.value
        assertEquals(listOf(favProduct), favState.products)
        
        val catalogState = viewModel.catalogState.value
        assertTrue(catalogState.products.first { it.id == "f1" }.isFavorite)
    }

    @Test
    fun `toggleFavorite - optimistic update and sync`() = runTest {
        val product: BuyerProduct = mockk(relaxed = true) {
            every { id } returns "p1"
            every { isFavorite } returns false
            every { copy(isFavorite = true) } returns mockk {
                every { id } returns "p1"
                every { isFavorite } returns true
            }
        }
        coEvery { mockRepository.setProductFavorite("p1", true) } returns mockk()
        
        viewModel.toggleFavorite(product, true)
        
        // Optimistic check
        assertTrue(viewModel.favoriteState.value.products.any { it.id == "p1" })
        
        advanceUntilIdle()
        
        coVerify { mockRepository.setProductFavorite("p1", true) }
    }

    @Test
    fun `toggleFavorite - rollback on error`() = runTest {
        val product: BuyerProduct = mockk(relaxed = true) {
            every { id } returns "p1"
            every { isFavorite } returns false
            every { copy(isFavorite = true) } returns mockk {
                every { id } returns "p1"
                every { isFavorite } returns true
            }
        }
        coEvery { mockRepository.setProductFavorite("p1", true) } throws Exception("Sync Error")
        
        viewModel.toggleFavorite(product, true)
        advanceUntilIdle()
        
        // Should be rolled back (removed from favorite list if it wasn't there)
        assertFalse(viewModel.favoriteState.value.products.any { it.id == "p1" })
        assertEquals("Sync Error", viewModel.favoriteState.value.message)
    }

    @Test
    fun `refreshCatalog - validation price bounds`() {
        viewModel.setMinPrice("10.0")
        viewModel.setMaxPrice("5.0")
        viewModel.refreshCatalog()
        
        assertEquals("Harga minimum tidak boleh lebih besar dari maksimum.", viewModel.catalogState.value.message)
    }

    @Test
    fun `refreshCatalog - invalid price input`() {
        viewModel.setMinPrice("abc")
        // minPrice becomes empty string because of priceInput() filter
        viewModel.refreshCatalog()
        assertNull(viewModel.catalogState.value.message)
    }

    @Test
    fun `refreshCityOptions - success`() = runTest {
        val cities = listOf("Jakarta", "Bandung")
        coEvery { mockRepository.listAvailableCities() } returns cities
        
        // Since it's called in init, we might need a new instance to catch the mock if it wasn't caught
        val newViewModel = BuyerCatalogViewModel(mockRepository)
        advanceUntilIdle()
        
        assertEquals(cities, newViewModel.catalogState.value.cityOptions)
    }
}
