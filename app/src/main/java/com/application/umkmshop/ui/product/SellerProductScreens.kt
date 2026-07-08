package com.application.umkmshop.ui.product

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.umkmshop.R
import com.application.umkmshop.data.product.ProductImageUpload
import com.application.umkmshop.data.product.SellerProduct
import com.application.umkmshop.data.product.productCategoryDisplayLabel
import com.application.umkmshop.data.shipping.WilayahItem
import com.application.umkmshop.ui.components.*
import com.application.umkmshop.ui.notification.InboxSummaryButton
import com.application.umkmshop.ui.product.logic.*

@Composable
fun SellerDashboardScreen(
    viewModel: SellerProductViewModel,
    onAddProduct: () -> Unit,
    onEditProduct: (String) -> Unit,
    onOpenChats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Dashboard Toko",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        SellerStatsRow(state = state)
        SellerWilayahEditor(
            state = state,
            onProvinceSelected = viewModel::selectProvince,
            onRegencySelected = viewModel::selectRegency,
            onSaveCity = viewModel::saveCity,
        )

        if (state.isLoading || state.isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UMKMPrimaryButton(
                onClick = {
                    viewModel.startCreate()
                    onAddProduct()
                },
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text("Tambah Produk")
            }
            UMKMSecondaryButton(
                onClick = onOpenChats,
                modifier = Modifier.weight(1f),
            ) {
                Text("Chat Masuk")
            }
        }

        if (state.products.isEmpty() && !state.isLoading) {
            UMKMEmptyState(
                message = "Belum ada produk",
                description = "Mulai tambahkan produk Anda agar pembeli bisa menemukannya.",
                icon = Icons.Default.AddBusiness,
                actionLabel = "Tambah Produk Pertama",
                onAction = {
                    viewModel.startCreate()
                    onAddProduct()
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = state.products,
                    key = { it.id },
                ) { product ->
                    SellerProductCard(
                        product = product,
                        enabled = !state.isSaving,
                        onEdit = {
                            viewModel.startEdit(product.id)
                            onEditProduct(product.id)
                        },
                        onDeactivate = { viewModel.deactivate(product.id) },
                        onDelete = { viewModel.deleteProduct(product.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerWilayahEditor(
    state: SellerProductUiState,
    onProvinceSelected: (WilayahItem) -> Unit,
    onRegencySelected: (WilayahItem) -> Unit,
    onSaveCity: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_material_symbol_location_on_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Lokasi Toko (wilayah.id)",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            WilayahDropdown(
                label = "Provinsi",
                items = state.provinces,
                selectedItem = state.selectedProvince,
                onItemSelected = onProvinceSelected,
                enabled = !state.isSaving
            )

            WilayahDropdown(
                label = "Kabupaten / Kota",
                items = state.regencies,
                selectedItem = state.selectedRegency,
                onItemSelected = onRegencySelected,
                enabled = state.selectedProvince != null && !state.isSaving
            )

            UMKMSecondaryButton(
                onClick = onSaveCity,
                enabled = !state.isSaving && state.selectedRegency != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Simpan Lokasi")
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.contains("tersimpan", ignoreCase = true)) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WilayahDropdown(
    label: String,
    items: List<WilayahItem>,
    selectedItem: WilayahItem?,
    onItemSelected: (WilayahItem) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedItem?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SellerStatsRow(state: SellerProductUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = "Produk",
            value = state.products.size.toString(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Aktif",
            value = state.products.count { it.isActive }.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}


@Composable
private fun SellerProductCard(
    product: SellerProduct,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = productCategoryDisplayLabel(product.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "Rp ${product.price.toLong()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    color = if (product.isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (product.isActive) "AKTIF" else "NONAKTIF",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = if (product.isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UMKMSecondaryButton(
                    onClick = onEdit,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                ) {
                    Text("Edit", style = MaterialTheme.typography.labelLarge)
                }
                if (product.isActive) {
                    UMKMSecondaryButton(
                        onClick = onDeactivate,
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                    ) {
                        Text("Sembunyikan", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = onDelete,
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Text("Hapus Permanen", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}


@Composable
fun ProductFormScreen(
    viewModel: SellerProductViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            readProductImageUpload(context, uri)?.let { (upload, displayName) ->
                viewModel.setSelectedImage(upload, displayName)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        UMKMTopBar(title = if (state.isEditing) "Edit Produk" else "Tambah Produk")
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Nama produk") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = state.price,
                    onValueChange = viewModel::setPrice,
                    label = { Text("Harga") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            item {
                ProductCategoryDropdown(
                    selectedCategory = state.category,
                    onCategorySelected = viewModel::setCategory,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text("Deskripsi") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.selectedImageName ?: "Belum ada foto dipilih.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        UMKMSecondaryButton(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (state.isEditing) "Ganti Foto" else "Pilih Foto")
                        }
                    }
                }
            }
            item {
                if (state.isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.message?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                UMKMPrimaryButton(
                    onClick = { viewModel.saveProduct(onSaved) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isEditing) "Simpan Perubahan" else "Simpan Produk")
                }
                UMKMSecondaryButton(
                    onClick = onCancel,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("Batal")
                }
            }
        }
    }
}


private fun readProductImageUpload(
    context: Context,
    uri: Uri,
): Pair<ProductImageUpload, String>? {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: return null
    val extension = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> return null
    }
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > MAX_PRODUCT_IMAGE_BYTES) return null

    val displayName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    } ?: "foto-produk.$extension"

    return ProductImageUpload(
        bytes = bytes,
        mimeType = mimeType,
        extension = extension,
    ) to displayName
}

private const val MAX_PRODUCT_IMAGE_BYTES = 5 * 1024 * 1024
