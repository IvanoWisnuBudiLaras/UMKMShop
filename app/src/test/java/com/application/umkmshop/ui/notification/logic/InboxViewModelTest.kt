package com.application.umkmshop.ui.notification.logic

import com.application.umkmshop.data.notification.InboxNotification
import com.application.umkmshop.data.notification.InboxRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    private lateinit var viewModel: InboxViewModel
    private lateinit var repository: InboxRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = InboxViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `refresh - success`() = runTest {
        val mockNotifications = listOf<InboxNotification>(mockk(relaxed = true))
        coEvery { repository.listNotifications() } returns mockNotifications
        coEvery { repository.unreadCount() } returns 1
        
        viewModel.refresh()
        
        val state = viewModel.state.value
        assertEquals(mockNotifications, state.notifications)
        assertEquals(1, state.unreadCount)
        assertFalse(state.isLoading)
    }

    @Test
    fun `markAllRead - success`() = runTest {
        coEvery { repository.markAllRead() } returns 1
        
        viewModel.markAllRead()
        
        val state = viewModel.state.value
        assertEquals(0, state.unreadCount)
    }

    @Test
    fun `realtime - collect new notifications`() = runTest {
        val newNotif: InboxNotification = mockk(relaxed = true) {
            every { id } returns "n1"
            every { isRead } returns false
        }
        every { repository.subscribeToInbox() } returns flowOf(newNotif)
        
        viewModel.startRealtime()
        
        val state = viewModel.state.value
        assertEquals(1, state.notifications.size)
        assertEquals("n1", state.notifications[0].id)
    }
}
