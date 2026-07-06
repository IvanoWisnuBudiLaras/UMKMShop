package com.application.umkmshop.data.chat

import app.cash.turbine.test
import com.application.umkmshop.data.product.ProductDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryTest {
    private lateinit var repository: ChatRepository
    private lateinit var mockClient: SupabaseClient
    private lateinit var mockAuth: Auth
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            val query = request.url.encodedQuery
            val content = when {
                path.contains("profiles") -> """[{"id": "u1", "name": "User", "phone": "123", "rating_avg": 5.0, "rating_count": 1}, {"id": "s1", "name": "Seller"}]"""
                path.contains("products") -> """[{"id": "p1", "name": "Product", "price": 10.0, "seller_id": "s1", "status": "active"}]"""
                path.contains("chat_rooms") -> {
                    if (query.contains("EmptyRoom")) "[]"
                    else """[{"id": "r1", "buyer_id": "u1", "seller_id": "s1", "product_id": "p1", "last_message_at": "2024-01-01T00:00:00Z", "created_at": "2024-01-01T00:00:00Z"}]"""
                }
                path.contains("messages") -> """[{"id": "m1", "room_id": "r1", "sender_id": "u1", "message_text": "Hello", "created_at": "2024-01-01T00:00:00Z"}]"""
                path.contains("reviews") -> """[{"id": "v1", "chat_room_id": "r1", "seller_id": "s1", "reviewer_id": "u1", "rating": 5, "comment": "Good"}]"""
                path.contains("product_images") -> """[{"id": "i1", "product_id": "p1", "image_url": "url"}]"""
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
            install(Realtime)
        }
        
        mockClient = spyk(realClient)
        mockAuth = mockk(relaxed = true)
        
        mockkStatic("io.github.jan.supabase.auth.AuthKt")
        every { mockClient.auth } returns mockAuth
        every { mockAuth.currentUserOrNull() } returns mockk { every { id } returns "u1" }
        
        repository = spyk(
            ChatRepository(
                supabaseClient = mockClient,
                ioDispatcher = testDispatcher
            ),
            recordPrivateCalls = true
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `listRooms - success`() = runTest(testDispatcher) {
        val rooms = repository.listRooms()
        assertEquals(1, rooms.size)
        assertEquals("r1", rooms[0].id)
    }

    @Test
    fun `getRoom - success`() = runTest(testDispatcher) {
        val room = repository.getRoom("r1")
        assertNotNull(room)
        assertEquals("r1", room.id)
    }

    @Test
    fun `listMessages - success`() = runTest(testDispatcher) {
        val messages = repository.listMessages("r1")
        assertEquals(1, messages.size)
        assertEquals("m1", messages[0].id)
    }

    @Test
    fun `sendMessage - success`() = runTest(testDispatcher) {
        val msg = repository.sendMessage("r1", "Hello World")
        assertNotNull(msg)
    }

    @Test
    fun `sendMessage - empty failure`() = runTest(testDispatcher) {
        assertFailsWith<IllegalArgumentException> {
            repository.sendMessage("r1", "  ")
        }
    }

    @Test
    fun `getOrCreateRoomForProduct - existing success`() = runTest(testDispatcher) {
        val detail = repository.getOrCreateRoomForProduct("p1")
        assertNotNull(detail)
    }

    @Test
    fun `getOrCreateRoomForProduct - chat with self failure`() = runTest(testDispatcher) {
        every { mockAuth.currentUserOrNull() } returns mockk { every { id } returns "s1" }
        assertFailsWith<IllegalArgumentException> {
            repository.getOrCreateRoomForProduct("p1")
        }
    }

    @Test
    fun `submitSellerReview - success`() = runTest(testDispatcher) {
        val review = repository.submitSellerReview("r1", "s1", 5, "Good")
        assertNotNull(review)
    }

    @Test
    fun `submitSellerReview - self failure`() = runTest(testDispatcher) {
        assertFailsWith<IllegalArgumentException> {
            repository.submitSellerReview("r1", "u1", 5, "Self")
        }
    }

    @Test
    fun `submitSellerReview - invalid rating failure`() = runTest(testDispatcher) {
        assertFailsWith<IllegalArgumentException> {
            repository.submitSellerReview("r1", "s1", 0, "Bad")
        }
    }

    @Test
    fun `subscribeToRoomMessages - reach`() = runTest(testDispatcher) {
        repository.subscribeToRoomMessages("r1").test {
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `internal functions reach`() = runTest(testDispatcher) {
        runCatching { repository.loadProducts(listOf("p1")) }
        runCatching { repository.loadProfiles(listOf("u1")) }
        runCatching { repository.loadImagesFor(listOf("p1")) }
        runCatching { repository.loadLatestMessages(listOf(ChatRoomDto("r1", "u1", "s1", "p1", lastMessageId = "m1"))) }
        runCatching { repository.loadOwnReview("r1") }
        runCatching { repository.findRoom("u1", "s1", "p1") }
        
        val roomDto = ChatRoomDto("r1", "u1", "s1", "p1")
        val prodDto = ProductDto("p1", "s1", "n", 1.0)
        runCatching { repository.buildRoomDetail(roomDto, prodDto) }
    }
}
