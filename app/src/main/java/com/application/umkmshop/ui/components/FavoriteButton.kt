package com.application.umkmshop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.application.umkmshop.R

@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.08f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "favoriteFillState",
    )

    Surface(
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isFavorite) {
                            R.drawable.ic_material_symbol_favorite_24
                        } else {
                            R.drawable.ic_material_symbol_favorite_outline_24
                        },
                    ),
                    contentDescription = if (isFavorite) {
                        "Hapus dari favorit"
                    } else {
                        "Tambah ke favorit"
                    },
                    modifier = Modifier
                        .size(20.dp)
                        .scale(scale),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
