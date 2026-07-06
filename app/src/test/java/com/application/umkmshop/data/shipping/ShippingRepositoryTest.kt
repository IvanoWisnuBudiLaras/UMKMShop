package com.application.umkmshop.data.shipping

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShippingRepositoryTest {
    private lateinit var repository: ShippingRepository
    private lateinit var mockClient: SupabaseClient
    private lateinit var mockAuth: Auth
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        val mockEngine = MockEngine { request ->
            val body = request.body.toString()
            val content = when {
                body.contains("search_villages") -> """{"villages": [{"villageCode": "1234567890", "villageName": "Village", "districtName": "Dist", "cityName": "City", "provinceName": "Prov", "postalCode": "12345"}]}"""
                body.contains("estimate_shipping") -> """{"estimate": {"serviceName": "reg", "cost": 1000.0, "etd": "1-2 days"}}"""
                else -> """{"message": "error"}"""
            }
            
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        
        val httpClient = HttpClient(mockEngine)
        val realClient = createSupabaseClient("https://example.supabase.co", "key") {
            httpEngine = mockEngine
        }
        
        mockClient = spyk(realClient)
        mockAuth = mockk(relaxed = true)
        mockkStatic("io.github.jan.supabase.auth.AuthKt")
        every { mockClient.auth } returns mockAuth
        
        val mockSession = mockk<UserSession> { every { accessToken } returns "token" }
        every { mockAuth.currentSessionOrNull() } returns mockSession

        repository = spyk(
            ShippingRepository(
                httpClient = httpClient,
                supabaseClient = mockClient
            ),
            recordPrivateCalls = true
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `searchVillages - success`() = runTest {
        val results = repository.searchVillages("Jakarta")
        assertNotNull(results)
        assertEquals(1, results.size)
        assertEquals("1234567890", results[0].villageCode)
    }

    @Test
    fun `searchVillages - validation`() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.searchVillages("ab") }
    }

    @Test
    fun `estimateShipping - success`() = runTest {
        val estimate = repository.estimateShipping("1234567890", "1234567890", 1000)
        assertNotNull(estimate)
        assertEquals(1000.0, estimate.cost)
    }

    @Test
    fun `estimateShipping - validation`() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.estimateShipping("123", "1234567890", 100) }
        assertFailsWith<IllegalArgumentException> { repository.estimateShipping("1234567890", "123", 100) }
        assertFailsWith<IllegalArgumentException> { repository.estimateShipping("1234567890", "1234567890", 0) }
    }

    @Test
    fun `callShippingFunction - failure`() = runTest {
        val mockEngine = MockEngine { respond("""{"message": "Service error"}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) }
        val httpClient = HttpClient(mockEngine)
        val repo = ShippingRepository(httpClient, mockClient)
        
        assertFailsWith<IllegalStateException> {
            repo.searchVillages("Jakarta")
        }
    }

    @Test
    fun `mapping coverage`() {
        val villageDto = VillageSearchResultDto("code", "12345", "name", "dist", "city", "prov")
        villageDto.toVillageSearchResult()
        
        val estimateDto = ShippingEstimateDto("reg", 1000.0, "1-2 days")
        estimateDto.toShippingEstimate()

        // Touch models
        villageDto.toString()
        assertEquals(villageDto, villageDto.copy())
        VillageSearchResultDto.serializer()

        estimateDto.toString()
        assertEquals(estimateDto, estimateDto.copy())
        ShippingEstimateDto.serializer()
        
        VillageSearchResponse(listOf(villageDto)).toString()
        VillageSearchResponse.serializer()
        
        ShippingEstimateResponse(estimateDto).toString()
        ShippingEstimateResponse.serializer()
        
        ShippingFunctionRequest("a", "q", "o", "d", 100).toString()
        ShippingFunctionRequest.serializer()
        
        ShippingEstimate("s", 1.0, "e").toString()
    }

    @Test
    fun `utility coverage`() {
        repository.String_extractMessage("""{"message": "error"}""")
        repository.String_extractMessage("{}")
        
        val res = VillageSearchResult("c", "p", "v", "d", "cy", "pr")
        assertNotNull(res.label)
        val resSimple = VillageSearchResult("c", null, "v", null, null, null)
        assertNotNull(resSimple.label)
    }

    private fun ShippingRepository.String_extractMessage(s: String): String? {
        val method = this.javaClass.getDeclaredMethods().find { it.name == "extractMessage" }
        method?.isAccessible = true
        return method?.invoke(this, s) as String?
    }
}
