package com.baxailab.cadebot.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.baxailab.cadebot.data.model.CartItem
import com.baxailab.cadebot.data.model.SIZE_PRICE_DELTA
import com.baxailab.cadebot.ui.components.*
import com.baxailab.cadebot.ui.theme.*

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onAddToCart: (CartItem) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.item ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VivaFoam)
    ) {
        // LazyColumn để scroll dọc hoạt động đúng dù có row scroll ngang bên trong
        LazyColumn(modifier = Modifier.weight(1f)) {

            // Image header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Brush.verticalGradient(listOf(VivaCoffee, VivaCaramel)))
                        .statusBarsPadding()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = VivaOnDark
                        )
                    }
                    Icon(
                        imageVector = categoryIcon(item.category),
                        contentDescription = null,
                        tint = VivaOnDark,
                        modifier = Modifier.align(Alignment.Center).size(64.dp)
                    )
                }
            }

            // Tên + giá + mô tả + tags
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.headlineLarge,
                            color = VivaEspresso,
                            modifier = Modifier.weight(1f)
                        )
                        PriceText(amount = uiState.unitPrice, style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(text = item.description, style = MaterialTheme.typography.bodyLarge, color = VivaGray)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.attributes.caffeine) VivaTag("Có caffeine") else VivaTag("Không caffeine")
                        if (item.tags.contains("best_seller")) VivaTag("⭐ Best Seller")
                    }
                }
                HorizontalDivider(color = VivaLatte, modifier = Modifier.padding(horizontal = 20.dp))
            }

            // Phục vụ (Nóng / Lạnh)
            if (item.attributes.temperatureOptions.size > 1) {
                item {
                    OptionSection(label = "Phục vụ") {
                        item.attributes.temperatureOptions.forEach { temp ->
                            OptionChip(
                                label = tempLabel(temp),
                                selected = uiState.selectedTemperature == temp,
                                onClick = { viewModel.setTemperature(temp) }
                            )
                        }
                    }
                }
            }

            // Size
            if (item.attributes.sizeOptions.isNotEmpty()) {
                item {
                    OptionSection(label = "Size") {
                        item.attributes.sizeOptions.forEach { size ->
                            val delta = SIZE_PRICE_DELTA[size] ?: 0
                            val label = when {
                                delta > 0 -> "$size  +${String.format("%,d", delta)}đ"
                                delta < 0 -> "$size  ${String.format("%,d", delta)}đ"
                                else -> size
                            }
                            OptionChip(
                                label = label,
                                selected = uiState.selectedSize == size,
                                onClick = { viewModel.setSize(size) }
                            )
                        }
                    }
                }
            }

            // Độ ngọt
            if (item.attributes.sweetnessOptions.isNotEmpty()) {
                item {
                    OptionSection(label = "Độ ngọt") {
                        item.attributes.sweetnessOptions.forEach { s ->
                            OptionChip(
                                label = s,
                                selected = uiState.selectedSweetness == s,
                                onClick = { viewModel.setSweetness(s) }
                            )
                        }
                    }
                }
            }

            // Đá — chỉ hiện khi Lạnh
            if (item.attributes.iceOptions.isNotEmpty() && !uiState.isHot) {
                item {
                    OptionSection(label = "Đá  (0% = không đá)") {
                        item.attributes.iceOptions.forEach { ice ->
                            OptionChip(
                                label = iceLabel(ice),
                                selected = uiState.selectedIce == ice,
                                onClick = { viewModel.setIce(ice) }
                            )
                        }
                    }
                }
            }

            // Topping
            if (item.attributes.toppings.isNotEmpty()) {
                item {
                    OptionSection(label = "Topping  (+5.000đ mỗi loại)") {
                        item.attributes.toppings.forEach { topping ->
                            OptionChip(
                                label = toppingLabel(topping),
                                selected = uiState.selectedToppings.contains(topping),
                                onClick = { viewModel.toggleTopping(topping) }
                            )
                        }
                    }
                }
            }

            // Ghi chú
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    HorizontalDivider(color = VivaLatte)
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Ghi chú (tuỳ chọn)")
                    OutlinedTextField(
                        value = uiState.note,
                        onValueChange = viewModel::setNote,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ví dụ: không sữa, dị ứng đậu phộng...", color = VivaGray) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VivaEspresso,
                            unfocusedBorderColor = VivaLatte
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        // Bottom bar cố định
        Surface(shadowElevation = 8.dp, color = Color.White) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuantityStepper(
                    quantity = uiState.quantity,
                    onDecrease = { viewModel.setQuantity(uiState.quantity - 1) },
                    onIncrease = { viewModel.setQuantity(uiState.quantity + 1) }
                )
                VivaPrimaryButton(
                    text = "Thêm  ${String.format("%,d", uiState.totalPrice)}đ",
                    onClick = { viewModel.buildCartItem()?.let { onAddToCart(it) } },
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                    icon = Icons.Default.Add
                )
            }
        }
    }
}

@Composable
private fun OptionSection(label: String, content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Spacer(Modifier.height(12.dp))
        SectionLabel(label)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            content = content
        )
        Spacer(Modifier.height(4.dp))
    }
}

fun categoryIcon(c: String): ImageVector = when (c) {
    "coffee" -> Icons.Default.LocalCafe
    "tea" -> Icons.Default.EmojiFoodBeverage
    "ice_blended" -> Icons.Default.AcUnit
    "pastry" -> Icons.Default.BakeryDining
    "combo" -> Icons.Default.CardGiftcard
    else -> Icons.Default.LocalCafe
}
fun tempLabel(t: String) = when (t) { "hot" -> "🔥 Nóng"; "iced" -> "❄️ Lạnh"; "warm" -> "♨️ Ấm"; "cold" -> "❄️ Lạnh"; else -> t }
fun iceLabel(i: String) = when (i) { "no_ice" -> "0%"; "less_ice" -> "30%"; "normal_ice" -> "70%"; "extra_ice" -> "100%"; else -> i }
fun toppingLabel(t: String) = when (t) {
    "extra_shot" -> "Extra Shot"; "oat_milk" -> "Sữa Yến Mạch"; "pearl" -> "Trân Châu"; "jelly" -> "Thạch"
    "whipped_cream" -> "Kem Tươi"; "chocolate_sauce" -> "Sốt Socola"; "strawberry_sauce" -> "Sốt Dâu"
    "cinnamon" -> "Quế"; "extra_matcha" -> "Thêm Matcha"; "lychee_jelly" -> "Thạch Vải"; "aloe_vera" -> "Nha Đam"; else -> t
}
