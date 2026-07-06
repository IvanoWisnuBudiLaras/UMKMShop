package com.application.umkmshop.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import com.application.umkmshop.ui.components.ModeToggle
import com.application.umkmshop.data.auth.AuthSessionState
import com.application.umkmshop.data.shipping.WilayahItem
import com.application.umkmshop.ui.components.UMKMPrimaryButton
import com.application.umkmshop.ui.components.UMKMSecondaryButton
import com.application.umkmshop.ui.notification.InboxSummaryButton
import com.application.umkmshop.ui.profile.logic.*

@Composable
fun ProfileScreen(
    session: AuthSessionState,
    viewModel: ProfileViewModel,
    unreadInboxCount: Int,
    isSellerMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    onOpenInbox: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenTransactions: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(session.userId) {
        if (session.isSignedIn) {
            viewModel.loadAddress()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                ModeToggle(
                    isSellerMode = isSellerMode,
                    onModeChange = onModeChange
                )
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = session.profile?.name?.trim()?.take(1)?.uppercase()
                                ?: session.email?.take(1)?.uppercase()
                                ?: "U",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.profile?.name?.takeIf { it.isNotBlank() } ?: "Pengguna",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.SemiBold,
                        )
                        session.email?.takeIf { it.isNotBlank() }?.let { email ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }

        item {
            AddressPanel(
                state = state,
                onProvinceSelected = viewModel::selectProvince,
                onRegencySelected = viewModel::selectRegency,
                onDistrictSelected = viewModel::selectDistrict,
                onVillageSelected = viewModel::selectVillageItem,
                onPostalCodeChange = viewModel::setPostalCode,
                onSaveAddress = viewModel::saveAddress,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InboxSummaryButton(
                    unreadCount = unreadInboxCount,
                    onOpenInbox = onOpenInbox,
                    modifier = Modifier.fillMaxWidth(),
                )
                UMKMSecondaryButton(
                    onClick = onOpenFavorites,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Produk Favorit")
                }
                UMKMSecondaryButton(
                    onClick = onOpenTransactions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Riwayat Transaksi")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                UMKMSecondaryButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressPanel(
    state: ProfileUiState,
    onProvinceSelected: (WilayahItem) -> Unit,
    onRegencySelected: (WilayahItem) -> Unit,
    onDistrictSelected: (WilayahItem) -> Unit,
    onVillageSelected: (WilayahItem) -> Unit,
    onPostalCodeChange: (String) -> Unit,
    onSaveAddress: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Alamat Ongkir",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                fontWeight = FontWeight.SemiBold,
            )
            
            WilayahDropdown(
                label = "Provinsi",
                items = state.provinces,
                selectedItem = state.selectedProvince,
                onItemSelected = onProvinceSelected
            )

            WilayahDropdown(
                label = "Kabupaten / Kota",
                items = state.regencies,
                selectedItem = state.selectedRegency,
                onItemSelected = onRegencySelected,
                enabled = state.selectedProvince != null
            )

            WilayahDropdown(
                label = "Kecamatan",
                items = state.districts,
                selectedItem = state.selectedDistrict,
                onItemSelected = onDistrictSelected,
                enabled = state.selectedRegency != null
            )

            WilayahDropdown(
                label = "Kelurahan / Desa",
                items = state.villages,
                selectedItem = state.selectedVillage,
                onItemSelected = onVillageSelected,
                enabled = state.selectedDistrict != null
            )

            OutlinedTextField(
                value = state.postalCode,
                onValueChange = onPostalCodeChange,
                label = { Text("Kode Pos") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message == "Alamat ongkir tersimpan.") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            UMKMPrimaryButton(
                onClick = onSaveAddress,
                enabled = !state.isSavingAddress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSavingAddress) "Menyimpan..." else "Simpan Alamat Ongkir")
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
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

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
                .menuAnchor()
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
