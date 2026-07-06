package com.application.umkmshop.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.umkmshop.R
import coil3.compose.SubcomposeAsyncImage
import com.application.umkmshop.data.product.BuyerProduct
import com.application.umkmshop.data.product.productCategoryDisplayLabel
import com.application.umkmshop.ui.components.*
import com.application.umkmshop.ui.notification.InboxSummaryButton
import com.application.umkmshop.ui.theme.ErrorRed
import com.application.umkmshop.ui.theme.Terracotta
import com.application.umkmshop.ui.product.logic.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BuyerCatalogScreen(
    viewModel: BuyerCatalogViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.catalogState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    Text(
                        text = "Katalog Bahan & Komponen",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CatalogFilters(
                        state = state,
                        onSearchChange = viewModel::setSearchQuery,
                        onCategoryChange = viewModel::setCategory,
                        onCityChange = viewModel::setCity,
                        onMinPriceChange = viewModel::setMinPrice,
                        onMaxPriceChange = viewModel::setMaxPrice,
                        onClear = viewModel::clearFilters,
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.products.isEmpty() && !state.isLoading) {
                item(span = { GridItemSpan(2) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "🔍",
                                    fontSize = 48.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.searchQuery.isNotEmpty()) 
                                "Tidak ada hasil untuk \"${state.searchQuery}\"" 
                            else 
                                "Belum ada bahan atau komponen aktif.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        if (state.searchQuery.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearFilters) {
                                Text("Bersihkan Filter")
                            }
                        }
                    }
                }
            } else {
                items(
                    items = state.products,
                    key = { it.id },
                ) { product ->
                    BuyerGridProductCard(
                        product = product,
                        onToggleFavorite = {
                            viewModel.toggleFavorite(
                                product = product,
                                nextFavorite = !product.isFavorite,
                            )
                        },
                        onClick = { onOpenDetail(product.id) }
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                PaginationControls(
                    page = state.page,
                    canGoBack = state.hasPreviousPage && !state.isLoading,
                    canGoForward = state.hasNextPage && !state.isLoading,
                    onPrevious = viewModel::previousPage,
                    onNext = viewModel::nextPage,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun BuyerGridProductCard(
    product: BuyerProduct,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.2f)) {
                SubcomposeAsyncImage(
                    model = product.imageUrls.firstOrNull(),
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { 
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.scale(0.5f), strokeWidth = 2.dp)
                        }
                    },
                    error = { ImageStatusText("!") }
                )
                FavoriteButton(
                    isFavorite = product.isFavorite,
                    onToggle = onToggleFavorite,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.heightIn(min = 40.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.price.formatRupiah(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_mylocation),
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = product.sellerCity ?: "Lokasi tdk diketahui",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                SellerRatingBadge(
                    ratingAvg = product.sellerRatingAvg,
                    ratingCount = product.sellerRatingCount,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun CatalogFilters(
    state: BuyerCatalogUiState,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onMinPriceChange: (String) -> Unit,
    onMaxPriceChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFilterExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Cari nama produk") },
            placeholder = { Text("Ketik minimal 2 karakter...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { isFilterExpanded = !isFilterExpanded }) {
                    Icon(
                        imageVector = if (isFilterExpanded) Icons.Default.FilterListOff else Icons.Default.FilterList,
                        contentDescription = "Toggle Filters",
                        tint = if (state.category.isNotEmpty() || state.city.isNotEmpty() || state.minPrice.isNotEmpty() || state.maxPrice.isNotEmpty())
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        if (isFilterExpanded) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProductCategoryDropdown(
                        selectedCategory = state.category,
                        onCategorySelected = onCategoryChange,
                        enabled = !state.isLoading,
                        includeAllOption = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CityDropdown(
                        selectedCity = state.city,
                        cityOptions = state.cityOptions,
                        onCitySelected = onCityChange,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.minPrice,
                            onValueChange = onMinPriceChange,
                            label = { Text("Harga min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = state.maxPrice,
                            onValueChange = onMaxPriceChange,
                            label = { Text("Harga max") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UMKMSecondaryButton(
                            onClick = {
                                onClear()
                                isFilterExpanded = false
                            },
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Filter")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityDropdown(
    selectedCity: String,
    cityOptions: List<String>,
    onCitySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Kota",
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(cityOptions) {
        listOf("") + cityOptions
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) expanded = !expanded
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedCity.ifBlank { "Semua kota" },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { city ->
                DropdownMenuItem(
                    text = { Text(if (city.isBlank()) "Semua kota" else city) },
                    onClick = {
                        onCitySelected(city)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PaginationControls(
    page: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Sebelumnya")
        }
        Text(
            text = "Halaman ${page + 1}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Berikutnya")
        }
    }
}

@Composable
fun BuyerProductDetailScreen(
    viewModel: BuyerCatalogViewModel,
    productId: String,
    onBack: () -> Unit,
    onChatSeller: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.detailState.collectAsState()
    var isReportDialogOpen by remember(productId) { mutableStateOf(false) }

    LaunchedEffect(productId) {
        viewModel.loadDetail(productId)
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            state.product?.let { product ->
                item {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                        ProductImageGallery(product = product)
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.TopStart)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                        FavoriteButton(
                            isFavorite = product.isFavorite,
                            onToggle = {
                                viewModel.toggleFavorite(
                                    product = product,
                                    nextFavorite = !product.isFavorite,
                                )
                            },
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.TopEnd),
                        )
                    }
                }
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = product.price.formatRupiah(),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = productCategoryDisplayLabel(product.category),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SellerRatingBadge(
                            ratingAvg = product.sellerRatingAvg,
                            ratingCount = product.sellerRatingCount,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Deskripsi",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = product.description?.takeIf { it.isNotBlank() } ?: "Belum ada deskripsi.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        SellerInfoCard(product = product)
                        Spacer(modifier = Modifier.height(16.dp))
                        ReportProductAction(
                            onClick = { isReportDialogOpen = true },
                            enabled = !state.isReporting,
                        )
                        state.message?.let { message ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }

        state.product?.let { product ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UMKMPrimaryButton(
                        onClick = { onChatSeller(product.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Chat Penjual")
                    }
                }
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }

    state.product?.let { product ->
        if (isReportDialogOpen) {
            ReportProductDialog(
                productName = product.name,
                isSubmitting = state.isReporting,
                onDismiss = {
                    if (!state.isReporting) isReportDialogOpen = false
                },
                onSubmit = { reason ->
                    viewModel.reportProduct(product.id, reason)
                    isReportDialogOpen = false
                },
            )
        }
    }
}

@Composable
fun FavoriteProductsScreen(
    viewModel: BuyerCatalogViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.favoriteState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshFavorites()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Favorit Saya",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            item(span = { GridItemSpan(2) }) {
                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.products.isEmpty() && !state.isLoading) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Belum ada produk favorit.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            } else {
                items(
                    items = state.products,
                    key = { it.id },
                ) { product ->
                    BuyerGridProductCard(
                        product = product,
                        onToggleFavorite = {
                            viewModel.toggleFavorite(
                                product = product,
                                nextFavorite = false,
                            )
                        },
                        onClick = { onOpenDetail(product.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportProductAction(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ErrorRed,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
    ) {
        Text("Laporkan Produk")
    }
}

@Composable
private fun ReportProductDialog(
    productName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    val canSubmit = reason.trim().isNotEmpty() && !isSubmitting

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Laporkan produk") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan laporan") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reason) },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (isSubmitting) "Mengirim..." else "Kirim Laporan")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun SellerInfoCard(product: BuyerProduct) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = product.sellerName?.take(1) ?: "P",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = product.sellerName ?: "Penjual",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    SellerRatingBadge(
                        ratingAvg = product.sellerRatingAvg,
                        ratingCount = product.sellerRatingCount,
                        compact = true,
                    )
                }
                Text(
                    text = "Penjual Aktif",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                product.sellerCity?.takeIf { it.isNotBlank() }?.let { city ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_material_symbol_location_on_24),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = city,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerRatingBadge(
    ratingAvg: Double,
    ratingCount: Int,
    compact: Boolean = false,
) {
    val text = if (ratingCount > 0) {
        "${String.format(Locale.US, "%.1f", ratingAvg)} ($ratingCount)"
    } else {
        "Baru"
    }
    Surface(
        color = Terracotta.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "★",
                color = Terracotta,
                fontSize = if (compact) 10.sp else 12.sp,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 10.sp else 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProductImageGallery(
    product: BuyerProduct,
    modifier: Modifier = Modifier,
) {
    if (product.imageUrls.isEmpty()) {
        ProductImagePreview(
            imageUrl = null,
            contentDescription = product.name,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
    } else {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(product.imageUrls) { imageUrl ->
                ProductImagePreview(
                    imageUrl = imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .aspectRatio(1f),
                )
            }
        }
    }
}

@Composable
private fun ProductImagePreview(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    ImageStatusText("...")
                },
                error = {
                    ImageStatusText("!")
                },
            )
        } else {
            ImageStatusText("No Image")
        }
    }
}

@Composable
private fun ImageStatusText(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Double.formatRupiah(): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
        .format(this)
