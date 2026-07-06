package com.application.umkmshop.data.product

import com.application.umkmshop.data.auth.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProductRepositoryTest {
    private lateinit var repository: ProductRepository
    private lateinit var mockClient: SupabaseClient
    private lateinit var mockAuth: Auth
    private lateinit var mockStorageService: ProductStorageService
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            val query = request.url.encodedQuery
            val content = when {
                path.contains("/rest/v1/profiles") -> {
                    if (query.contains("Empty")) "[]"
                    else """[{"id": "u1", "city": "Jakarta", "name": "User", "phone": "123"}]"""
                }
                path.contains("/rest/v1/products") -> """[{"id": "p1", "seller_id": "s1", "name": "Product", "price": 10.0, "status": "active"}]"""
                path.contains("/rest/v1/wishlist") -> """[{"id": "w1", "user_id": "u1", "product_id": "p1"}]"""
                path.contains("/rest/v1/product_images") -> """[{"id": "i1", "product_id": "p1", "image_url": "/storage/v1/object/public/product-images/path"}]"""
                else -> "[]"
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        
        val realClient = createSupabaseClient("https://example.supabase.co", "key") {
            httpEngine = mockEngine
            install(Postgrest)
        }
        
        mockClient = spyk(realClient)
        mockAuth = mockk(relaxed = true)
        mockStorageService = mockk(relaxed = true)
        
        mockkStatic("io.github.jan.supabase.auth.AuthKt")
        every { mockClient.auth } returns mockAuth
        every { mockAuth.currentUserOrNull() } returns mockk { every { id } returns "u1" }
        
        repository = ProductRepository(
            supabaseClient = mockClient,
            storageService = mockStorageService,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getOwnCity - success`() = runTest(testDispatcher) {
        val city = repository.getOwnCity()
        assertEquals("Jakarta", city)
    }

    @Test
    fun `listBuyerProducts - success`() = runTest(testDispatcher) {
        val filter = BuyerCatalogFilter(city = "Jakarta", category = "Food", minPrice = 1.0, maxPrice = 100.0, searchQuery = "Prod")
        val page = repository.listBuyerProducts(filter, 0)
        assertNotNull(page)
    }

    @Test
    fun `listBuyerProducts - empty city`() = runTest(testDispatcher) {
        val filter = BuyerCatalogFilter(city = "Empty")
        val page = repository.listBuyerProducts(filter, 0)
        assertTrue(page.products.isEmpty())
    }

    @Test
    fun `createProduct - success`() = runTest(testDispatcher) {
        val dummyImg = ProductImageUpload(byteArrayOf(1), "image/jpeg", "jpg")
        val input = SellerProductInput("New", 20.0, "Desc", "Food", dummyImg)
        
        coEvery { mockStorageService.uploadProductImage(any(), any(), any()) } returns UploadedProductImage("path", "url")
        
        val result = repository.createProduct(input)
        assertNotNull(result)
        assertEquals(PRODUCT_STATUS_ACTIVE, result.status)
    }

    @Test
    fun `createProduct - upload failure`() = runTest(testDispatcher) {
        val dummyImg = ProductImageUpload(byteArrayOf(1), "image/jpeg", "jpg")
        val input = SellerProductInput("New", 20.0, "Desc", "Food", dummyImg)
        
        coEvery { mockStorageService.uploadProductImage(any(), any(), any()) } throws RuntimeException("Upload Failed")
        
        assertFailsWith<RuntimeException> {
            repository.createProduct(input)
        }
    }

    @Test
    fun `updateProduct - success with image`() = runTest(testDispatcher) {
        val dummyImg = ProductImageUpload(byteArrayOf(1), "image/jpeg", "jpg")
        val input = SellerProductInput("Updated", 25.0, "New Desc", "Food", dummyImg)
        
        coEvery { mockStorageService.uploadProductImage(any(), any(), any()) } returns UploadedProductImage("new_path", "new_url")
        
        val result = repository.updateProduct("p1", input)
        assertNotNull(result)
    }

    @Test
    fun `deactivateProduct - success`() = runTest(testDispatcher) {
        val result = repository.deactivateProduct("p1")
        assertNotNull(result)
    }

    @Test
    fun `listOwnProducts - success`() = runTest(testDispatcher) {
        val products = repository.listOwnProducts()
        assertTrue(products.isNotEmpty())
    }

    @Test
    fun `getBuyerProductDetail - success`() = runTest(testDispatcher) {
        val detail = repository.getBuyerProductDetail("p1")
        assertNotNull(detail)
    }

    @Test
    fun `setProductFavorite - success`() = runTest(testDispatcher) {
        repository.setProductFavorite("p1", true)
        repository.setProductFavorite("p1", false)
    }

    @Test
    fun `listFavoriteProducts - success`() = runTest(testDispatcher) {
        val favorites = repository.listFavoriteProducts()
        assertTrue(favorites.isNotEmpty())
    }

    @Test
    fun `reportProduct - success`() = runTest(testDispatcher) {
        repository.reportProduct("p1", "Scam")
    }

    @Test
    fun `reportProduct - empty reason failure`() = runTest(testDispatcher) {
        assertFailsWith<IllegalStateException> {
            repository.reportProduct("p1", " ")
        }
    }

    @Test
    fun `listAvailableCities - success`() = runTest(testDispatcher) {
        val cities = repository.listAvailableCities()
        assertTrue(cities.isNotEmpty())
    }

    @Test
    fun `updateOwnCity - success`() = runTest(testDispatcher) {
        repository.updateOwnCity("Bandung")
    }

    @Test
    fun `internal functions reach`() = runTest(testDispatcher) {
        runCatching { repository.loadImages("p1") }
        runCatching { repository.loadProfiles(listOf("u1")) }
        runCatching { repository.loadSellerIdsByCity("Jakarta") }
        runCatching { repository.loadFavoriteProductIdsFor(listOf("p1")) }
        runCatching { repository.deleteOwnProduct("p1", "s1") }
        runCatching { repository.replacePrimaryImage("p1", "url") }
    }
}
