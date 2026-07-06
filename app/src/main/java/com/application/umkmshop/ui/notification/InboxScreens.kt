package com.application.umkmshop.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.umkmshop.data.notification.*
import com.application.umkmshop.ui.components.*
import com.application.umkmshop.data.notification.*
import com.application.umkmshop.ui.components.*
import com.application.umkmshop.ui.theme.ErrorRed
import com.application.umkmshop.ui.theme.Terracotta
import com.application.umkmshop.ui.notification.logic.*

@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.markAllRead()
        viewModel.startRealtime()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        UMKMTopBar(
            title = "Inbox",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            }
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Notifikasi pesanan dan pembayaran",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )
        }

        if (state.isLoading) {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (state.notifications.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Belum ada notifikasi pesanan.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(
                    items = state.notifications,
                    key = { it.id },
                ) { notification ->
                    InboxNotificationCard(notification = notification)
                }
            }
        }
    }
}

@Composable
fun InboxSummaryButton(
    unreadCount: Int,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UMKMSecondaryButton(
        onClick = onOpenInbox,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Inbox")
            if (unreadCount > 0) {
                Badge(
                    containerColor = ErrorRed,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) {
                    Text(unreadCount.coerceAtMost(99).toString())
                }
            }
        }
    }
}

@Composable
private fun InboxNotificationCard(
    notification: InboxNotification,
    modifier: Modifier = Modifier,
) {
    val accentColor = notification.type.accentColor()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = if (notification.isRead) 0.16f else 0.24f),
            ) {
                Text(
                    text = notification.type.shortLabel(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                        fontWeight = if (notification.isRead) FontWeight.SemiBold else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!notification.isRead) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp),
                        ) {}
                    }
                }
                notification.body?.takeIf { it.isNotBlank() }?.let { body ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = notification.createdAt.toDisplayTimestamp(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
    }
}

private fun String.shortLabel(): String =
    when (this) {
        NOTIFICATION_TYPE_ORDER_CREATED -> "INV"
        NOTIFICATION_TYPE_PAYMENT_PAID -> "PAID"
        NOTIFICATION_TYPE_PAYMENT_EXPIRED -> "EXP"
        NOTIFICATION_TYPE_PAYMENT_CANCELLED -> "VOID"
        NOTIFICATION_TYPE_REPLY_REMINDER -> "CHAT"
        else -> "INFO"
    }

@Composable
private fun String.accentColor() =
    when (this) {
        NOTIFICATION_TYPE_PAYMENT_PAID -> MaterialTheme.colorScheme.primary
        NOTIFICATION_TYPE_PAYMENT_EXPIRED,
        NOTIFICATION_TYPE_PAYMENT_CANCELLED -> ErrorRed
        NOTIFICATION_TYPE_REPLY_REMINDER -> Terracotta
        else -> MaterialTheme.colorScheme.secondary
    }

private fun String.toDisplayTimestamp(): String =
    replace("T", " ")
        .replace("Z", "")
        .substringBefore(".")
