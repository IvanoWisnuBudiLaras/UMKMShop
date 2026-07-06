package com.application.umkmshop.ui.chat.logic

import com.application.umkmshop.data.chat.ChatMessage
import com.application.umkmshop.data.chat.ChatRepository
import com.application.umkmshop.data.chat.ChatRoomDetail
import com.application.umkmshop.data.chat.ChatRoomSummary
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private lateinit var viewModel: ChatViewModel
    private lateinit var repository: ChatRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = ChatViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `refreshRooms - success`() = runTest {
        val mockRooms = listOf(
            ChatRoomSummary("r1", "b1", "s1", "p1", "Prod", null, "Other", null, "Hello", false)
        )
        coEvery { repository.listRooms() } returns mockRooms
        
        viewModel.refreshRooms()
        
        val state = viewModel.listState.value
        assertEquals(mockRooms, state.rooms)
        assertFalse(state.isLoading)
    }

    @Test
    fun `openRoom - validation error when no args`() = runTest {
        viewModel.openRoom(null, null)
        val state = viewModel.roomState.value
        assertEquals("Ruang chat harus dibuka dari produk atau daftar percakapan.", state.message)
    }

    @Test
    fun `openRoom - success with product id`() = runTest {
        val mockDetail = ChatRoomDetail(
            id = "r1", 
            buyerId = "b1", 
            sellerId = "s1", 
            productId = "p1", 
            productName = "Prod", 
            productImageUrl = null, 
            buyerName = "B", 
            sellerName = "S", 
            currentUserId = "b1", 
            sellerRatingAvg = 5.0, 
            sellerRatingCount = 1, 
            myReview = null
        )
        coEvery { repository.getOrCreateRoomForProduct("p1") } returns mockDetail
        coEvery { repository.listMessages("r1") } returns emptyList()
        
        viewModel.openRoom(productId = "p1", roomId = null)
        
        val state = viewModel.roomState.value
        assertEquals(mockDetail, state.room)
        assertFalse(state.isLoading)
    }

    @Test
    fun `sendMessage - success`() = runTest {
        val mockDetail = ChatRoomDetail("r1", "b1", "s1", "p1", "Prod", null, "B", "S", "b1", 5.0, 1, null)
        coEvery { repository.getOrCreateRoomForProduct("p1") } returns mockDetail
        viewModel.openRoom(productId = "p1", roomId = null)
        
        viewModel.setDraftMessage("Test message")
        coEvery { repository.sendMessage("r1", "Test message") } returns mockk(relaxed = true)
        
        viewModel.sendMessage()
        
        val state = viewModel.roomState.value
        assertEquals("", state.draftMessage) // Cleared after success
        assertFalse(state.isSending)
    }

    @Test
    fun `submitReview - validation error when not buyer`() = runTest {
        val mockDetail = ChatRoomDetail("r1", "b1", "s1", "p1", "Prod", null, "B", "S", "s1", 5.0, 1, null)
        coEvery { repository.getRoom("r1") } returns mockDetail
        viewModel.openRoom(null, "r1")
        
        viewModel.submitReview()
        
        val state = viewModel.roomState.value
        assertEquals("Hanya pembeli di chat ini yang bisa memberi rating.", state.message)
    }

    @Test
    fun `submitReview - success`() = runTest {
        val mockDetail = ChatRoomDetail("r1", "b1", "s1", "p1", "Prod", null, "B", "S", "b1", 5.0, 1, null)
        coEvery { repository.getRoom("r1") } returns mockDetail
        viewModel.openRoom(null, "r1")
        
        viewModel.setReviewRating(5)
        viewModel.setReviewComment("Excellent")
        viewModel.setHasConfirmedTransaction(true)
        coEvery { repository.submitSellerReview("r1", "s1", 5, "Excellent") } returns mockk(relaxed = true)
        
        viewModel.submitReview()
        
        val state = viewModel.roomState.value
        assertEquals("Rating terkirim.", state.message)
        assertFalse(state.isSubmittingReview)
    }
}
