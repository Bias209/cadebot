package com.baxailab.cadebot.ui.menu

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.baxailab.cadebot.data.model.MenuItem
import com.baxailab.cadebot.ui.components.PriceText
import com.baxailab.cadebot.ui.components.VivaTag
import com.baxailab.cadebot.ui.theme.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp

@Composable
fun MenuScreen(
    cartItemCount: Int,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    onCartClick: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VivaFoam)
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(VivaEspresso, VivaCoffee)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = VivaOnDark)
            }
            Text(
                text = "Thực đơn",
                style = MaterialTheme.typography.headlineSmall,
                color = VivaOnDark,
                modifier = Modifier.align(Alignment.Center)
            )
            BadgedBox(
                badge = {
                    if (cartItemCount > 0) Badge { Text(cartItemCount.toString()) }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                IconButton(onClick = onCartClick) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "Giỏ hàng", tint = VivaOnDark)
                }
            }
        }

        // Category tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(VivaCoffee)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.categories) { category ->
                val selected = category.id == uiState.selectedCategoryId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) VivaCaramel else VivaEspresso)
                        .clickable { viewModel.selectCategory(category.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${category.iconEmoji} ${category.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) VivaEspresso else VivaLatte
                    )
                }
            }
        }

        // Menu items
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.filteredItems) { item ->
                MenuItemCard(item = item, onClick = { onItemClick(item.menuItemId) })
            }
        }
    }
}

@Composable
private fun MenuItemCard(item: MenuItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VivaSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Placeholder image
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(VivaCoffee, VivaCaramel))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(item.category),
                    contentDescription = null,
                    tint = VivaOnDark,
                    modifier = Modifier.size(40.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = VivaEspresso,
                        modifier = Modifier.weight(1f)
                    )
                    if (!item.available) {
                        VivaTag(text = "Hết", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VivaGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriceText(
                        amount = item.price,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.tags.contains("best_seller")) VivaTag("⭐ Best")
                        if (!item.attributes.caffeine) VivaTag("No caffeine")
                    }
                }
            }
        }
    }
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "coffee" -> Icons.Default.LocalCafe
    "tea" -> Icons.Default.EmojiFoodBeverage
    "ice_blended" -> Icons.Default.AcUnit
    "pastry" -> Icons.Default.BakeryDining
    "combo" -> Icons.Default.CardGiftcard
    else -> Icons.Default.LocalCafe
}

