package com.application.umkmshop.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.umkmshop.data.chat.ChatMessage
import com.application.umkmshop.data.chat.ChatRoomSummary
import com.application.umkmshop.ui.order.CreateOrderPanel
import com.application.umkmshop.ui.order.logic.OrderViewModel
import com.application.umkmshop.ui.chat.logic.*
import com.application.umkmshop.ui.components.*

@Composable
fun ChatListScreen(
    viewModel: ChatViewModel,
    onOpenRoom: (roomId: String, productId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.listState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshRooms()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        UMKMTopBar(title = "Pesan")
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.rooms.isEmpty() && !state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Belum ada chat.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                items(
                    items = state.rooms,
                    key = { it.id },
                ) { room ->
                    ChatRoomCard(
                        room = room,
                        onOpen = { onOpenRoom(room.id, room.productId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRoomCard(
    room: ChatRoomSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = room.otherUserName.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = room.otherUserName,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Baru", // Placeholder for timestamp
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = room.productName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = room.lastMessagePreview ?: "Belum ada pesan.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


@Composable
fun ChatRoomScreen(
    viewModel: ChatViewModel,
    orderViewModel: OrderViewModel,
    productId: String?,
    roomId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.roomState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(productId, roomId) {
        viewModel.openRoom(productId = productId, roomId = roomId)
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val room = state.room
        val otherName = if (room != null) {
            if (room.currentUserId == room.buyerId) room.sellerName else room.buyerName
        } else "Chat"

        UMKMTopBar(
            title = otherName,
            actions = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }
        )

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message == "Rating terkirim.") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.messages.isEmpty() && !state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Mulai percakapan...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            items(
                items = state.messages,
                key = { it.id },
            ) { message ->
                val isMine = message.isMine(state.room?.currentUserId.orEmpty())
                ChatBubble(
                    message = message.messageText,
                    timestamp = "Hari ini", // Placeholder
                    isFromMe = isMine
                )
            }
        }

        room?.let { roomDetail ->
            CreateOrderPanel(
                room = roomDetail,
                viewModel = orderViewModel,
            )
            SellerReviewPrompt(
                room = roomDetail,
                rating = state.reviewRating,
                comment = state.reviewComment,
                confirmed = state.hasConfirmedTransaction,
                isSubmitting = state.isSubmittingReview,
                onRatingChange = viewModel::setReviewRating,
                onCommentChange = viewModel::setReviewComment,
                onConfirmedChange = viewModel::setHasConfirmedTransaction,
                onSubmit = viewModel::submitReview,
            )
        }

        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.draftMessage,
                    onValueChange = viewModel::setDraftMessage,
                    placeholder = { Text("Ketik pesan...") },
                    maxLines = 4,
                    enabled = state.room != null && !state.isSending,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                UMKMPrimaryButton(
                    onClick = viewModel::sendMessage,
                    enabled = state.room != null && !state.isSending && state.draftMessage.isNotBlank(),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kirim")
                }
            }
        }
    }
}

@Composable
private fun SellerReviewPrompt(
    room: com.application.umkmshop.data.chat.ChatRoomDetail,
    rating: Int,
    comment: String,
    confirmed: Boolean,
    isSubmitting: Boolean,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onConfirmedChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBuyer = room.currentUserId == room.buyerId
    if (!isBuyer) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (room.myReview != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RatingStars(rating = room.myReview.rating, onRatingChange = {}, enabled = false)
                    Text(
                        text = "Rating sudah terkirim",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                room.myReview.comment?.let { reviewComment ->
                    Text(
                        text = reviewComment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                return@Column
            }

            Text(
                text = "Sudah transaksi dengan ${room.sellerName}?",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = confirmed,
                    onCheckedChange = onConfirmedChange,
                    enabled = !isSubmitting,
                )
                Text(
                    text = "Ya, transaksi dilakukan di luar aplikasi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
            RatingStars(
                rating = rating,
                onRatingChange = onRatingChange,
                enabled = confirmed && !isSubmitting,
            )
            OutlinedTextField(
                value = comment,
                onValueChange = onCommentChange,
                placeholder = { Text("Komentar opsional") },
                minLines = 1,
                maxLines = 3,
                enabled = confirmed && !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            )
            UMKMPrimaryButton(
                onClick = onSubmit,
                enabled = confirmed && rating in 1..5 && !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSubmitting) "Mengirim..." else "Kirim Rating")
            }
        }
    }
}

@Composable
private fun RatingStars(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..5).forEach { value ->
            IconButton(
                onClick = { onRatingChange(value) },
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (value <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (value <= rating) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(28.sp.value.dp)
                )
            }
        }
    }
}
