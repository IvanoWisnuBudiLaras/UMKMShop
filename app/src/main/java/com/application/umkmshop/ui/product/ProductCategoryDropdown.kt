package com.application.umkmshop.ui.product

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.application.umkmshop.data.product.PHASE3_PRODUCT_CATEGORIES
import com.application.umkmshop.data.product.productCategoryDisplayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCategoryDropdown(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includeAllOption: Boolean = false,
    label: String = "Kategori",
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = when {
        includeAllOption && selectedCategory.isBlank() -> "Semua kategori"
        selectedCategory.isBlank() -> "Pilih kategori"
        else -> productCategoryDisplayLabel(selectedCategory)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) expanded = !expanded
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
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
            if (includeAllOption) {
                DropdownMenuItem(
                    text = { Text("Semua kategori") },
                    onClick = {
                        onCategorySelected("")
                        expanded = false
                    },
                )
            }
            PHASE3_PRODUCT_CATEGORIES.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onCategorySelected(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}
