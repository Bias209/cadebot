package com.baxailab.cadebot.ui.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.baxailab.cadebot.ui.cart.PaymentStatus
import com.baxailab.cadebot.ui.components.VivaPrimaryButton
import com.baxailab.cadebot.ui.theme.*
import kotlinx.coroutines.delay

private fun formatCountdown(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PaymentScreen(
    totalAmount: Int,
    qrUrl: String,
    transferContent: String,
    secondsRemaining: Int,
    paymentStatus: PaymentStatus,
    errorMessage: String,
    onRetry: () -> Unit,
    onSuccess: () -> Unit
) {
    LaunchedEffect(paymentStatus) {
        if (paymentStatus == PaymentStatus.PAID) {
            delay(500)
            onSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VivaFoam),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(VivaEspresso, VivaCoffee)))
                .statusBarsPadding()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Thanh toán", style = MaterialTheme.typography.headlineSmall, color = VivaOnDark)
        }

        Spacer(Modifier.height(32.dp))

        Text("Tổng thanh toán", style = MaterialTheme.typography.titleMedium, color = VivaGray)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${String.format("%,d", totalAmount)}đ",
            style = MaterialTheme.typography.displayMedium,
            color = VivaEspresso
        )

        Spacer(Modifier.height(32.dp))

        when (paymentStatus) {
            PaymentStatus.CREATING, PaymentStatus.IDLE -> {
                CircularProgressIndicator(color = VivaEspresso, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Đang tạo đơn hàng...", style = MaterialTheme.typography.bodyMedium, color = VivaGray)
            }

            PaymentStatus.AWAITING_PAYMENT, PaymentStatus.PAID -> {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(VivaSurface),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = qrUrl,
                        contentDescription = "Mã QR thanh toán",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = transferContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VivaCoffee,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Quét mã QR bằng app ngân hàng bất kỳ để thanh toán\nMã hết hạn sau ${formatCountdown(secondsRemaining)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VivaGray,
                    textAlign = TextAlign.Center
                )
            }

            PaymentStatus.EXPIRED -> {
                Text(
                    text = "Mã QR đã hết hạn",
                    style = MaterialTheme.typography.titleMedium,
                    color = VivaError,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                VivaPrimaryButton(
                    text = "Tạo lại mã QR",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
            }

            PaymentStatus.ERROR -> {
                Text(
                    text = errorMessage.ifBlank { "Có lỗi xảy ra, vui lòng thử lại" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VivaError,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                VivaPrimaryButton(
                    text = "Thử lại",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}
