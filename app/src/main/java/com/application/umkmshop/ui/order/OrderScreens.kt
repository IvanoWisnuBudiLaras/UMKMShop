package com.application.umkmshop.ui.order

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.umkmshop.data.chat.ChatRoomDetail
import com.application.umkmshop.data.order.ORDER_STATUS_CANCELLED
import com.application.umkmshop.data.order.ORDER_STATUS_EXPIRED
import com.application.umkmshop.data.order.ORDER_STATUS_PAID
import com.application.umkmshop.data.order.ORDER_STATUS_PENDING
import com.application.umkmshop.data.order.OrderInput
import com.application.umkmshop.data.order.OrderTransaction
import com.application.umkmshop.ui.components.UMKMPrimaryButton
import com.application.umkmshop.ui.components.UMKMTopBar
import com.application.umkmshop.ui.order.logic.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CreateOrderPanel(
    room: ChatRoomDetail,
    viewModel: OrderViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.createState.collectAsState()
    val isSeller = room.currentUserId == room.sellerId
    if (!isSeller) return

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
            Text(
                text = "Buat Invoice",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = state.itemNote,
                onValueChange = viewModel::setItemNote,
                label = { Text("Catatan item") },
                placeholder = { Text("Contoh: 50kg bahan premium") },
                maxLines = 2,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.subtotal,
                    onValueChange = viewModel::setSubtotal,
                    label = { Text("Subtotal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = state.weightGrams,
                    onValueChange = viewModel::setWeightGrams,
                    label = { Text("Gram") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                )
            }
            state.estimatedShippingCost?.let { shippingCost ->
                Text(
                    text = "Ongkir ${state.shippingServiceName.orEmpty()}: ${shippingCost.formatRupiah()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (state.requiresManualShipping) {
                OutlinedTextField(
                    value = state.manualShippingCost,
                    onValueChange = viewModel::setManualShippingCost,
                    label = { Text("Ongkir manual") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.startsWith("Invoice")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            UMKMPrimaryButton(
                onClick = {
                    viewModel.createOrder(
                        OrderInput(
                            chatRoomId = room.id,
                            buyerId = room.buyerId,
                            sellerId = room.sellerId,
                            productId = room.productId,
                            itemNote = state.itemNote,
                            weightGrams = null,
                            subtotal = 0.0,
                        ),
                    )
                },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        state.isEstimatingShipping -> "Menghitung ongkir..."
                        state.isSaving -> "Menyimpan..."
                        else -> "Buat Invoice Pending"
                    },
                )
            }
        }
    }
}

@Composable
fun OrderHistoryScreen(
    viewModel: OrderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.historyState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshOrders()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        UMKMTopBar(
            title = "Riwayat Transaksi",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            },
        )
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (state.orders.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Belum ada transaksi.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = state.orders,
                    key = { it.id },
                ) { order ->
                    OrderHistoryCard(
                        order = order,
                        onOpenInvoice = { invoiceUrl ->
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(invoiceUrl)),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderHistoryCard(
    order: OrderTransaction,
    onOpenInvoice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.productName,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (order.isSellerView) "Sebagai penjual" else "Sebagai pembeli",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                OrderStatusBadge(status = order.status)
            }
            order.itemNote?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OrderAmountRow(label = "Subtotal", amount = order.subtotal)
            OrderAmountRow(label = "Ongkir", amount = order.shippingCost)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            OrderAmountRow(
                label = "Total",
                amount = order.totalAmount,
                emphasized = true,
            )
            Text(
                text = "Chat room: ${order.chatRoomId.take(8)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            if (order.status == ORDER_STATUS_PENDING && !order.xenditInvoiceUrl.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { onOpenInvoice(order.xenditInvoiceUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Buka Invoice Xendit")
                }
            }
        }
    }
}

@Composable
private fun OrderAmountRow(
    label: String,
    amount: Double,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = amount.formatRupiah(),
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OrderStatusBadge(status: String) {
    val (label, color) = when (status) {
        ORDER_STATUS_PAID -> "DIBAYAR" to MaterialTheme.colorScheme.primary
        ORDER_STATUS_EXPIRED -> "EXPIRED" to MaterialTheme.colorScheme.outline
        ORDER_STATUS_CANCELLED -> "BATAL" to MaterialTheme.colorScheme.error
        ORDER_STATUS_PENDING -> "PENDING" to MaterialTheme.colorScheme.tertiary
        else -> status.uppercase() to MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun Double.formatRupiah(): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).format(this)
