package com.baxailab.cadebot.ui.ordersuccess

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baxailab.cadebot.ui.components.VivaPrimaryButton
import com.baxailab.cadebot.ui.components.VivaSecondaryButton
import com.baxailab.cadebot.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun OrderSuccessScreen(
    orderId: String,
    tableId: String,
    totalAmount: Int,
    onBackHome: () -> Unit,
    onOrderMore: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VivaFoam),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn() + fadeIn()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(VivaSuccess.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = VivaSuccess,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Đặt hàng thành công!",
                    style = MaterialTheme.typography.headlineLarge,
                    color = VivaEspresso,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Cảm ơn bạn đã chọn Viva Reserve ☕",
                    style = MaterialTheme.typography.bodyLarge,
                    color = VivaGray,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                // Order info card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = VivaSurface),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OrderInfoRow(label = "Mã đơn", value = "#${orderId.takeLast(6).uppercase()}")
                        HorizontalDivider(color = VivaLatte)
                        OrderInfoRow(label = "Bàn", value = tableId)
                        HorizontalDivider(color = VivaLatte)
                        OrderInfoRow(label = "Tổng tiền", value = "${String.format("%,d", totalAmount)}đ")
                        HorizontalDivider(color = VivaLatte)
                        OrderInfoRow(label = "Trạng thái", value = "⏳ Đang pha chế")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Robot delivery notice
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = VivaCaramel.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = VivaCoffee,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Cadebot sẽ giao món đến bàn bạn sau khi pha chế xong!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VivaCoffee
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VivaSecondaryButton(
                        text = "Gọi thêm món",
                        onClick = onOrderMore,
                        modifier = Modifier.fillMaxWidth()
                    )
                    VivaPrimaryButton(
                        text = "Về trang chủ",
                        onClick = onBackHome,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = VivaGray)
        Text(value, style = MaterialTheme.typography.titleMedium, color = VivaEspresso)
    }
}
