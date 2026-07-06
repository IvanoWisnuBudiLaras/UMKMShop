package com.application.umkmshop.ui.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.application.umkmshop.data.chat.ChatRoomSummary
import com.application.umkmshop.ui.theme.UMKMShopTheme
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test

class FakeChatViewModel : ChatViewModel() {
    fun updateListState(newState: ChatListUiState) {
        _listState.update { newState }
    }
}

class ChatScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testEmptyChatList() {
        val fakeViewModel = FakeChatViewModel()
        fakeViewModel.updateListState(ChatListUiState(rooms = emptyList(), isLoading = false))

        composeTestRule.setContent {
            UMKMShopTheme {
                ChatListScreen(
                    viewModel = fakeViewModel,
                    onOpenRoom = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Belum ada chat.").assertExists()
    }

    @Test
    fun testChatRoomDisplay() {
        val rooms = listOf(
            ChatRoomSummary("r1", "b1", "s1", "p1", "Product A", null, "Buyer A", null, null, false)
        )
        val fakeViewModel = FakeChatViewModel()
        fakeViewModel.updateListState(ChatListUiState(rooms = rooms, isLoading = false))

        composeTestRule.setContent {
            UMKMShopTheme {
                ChatListScreen(
                    viewModel = fakeViewModel,
                    onOpenRoom = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Product A").assertExists()
        composeTestRule.onNodeWithText("Buyer A").assertExists()
    }
}
