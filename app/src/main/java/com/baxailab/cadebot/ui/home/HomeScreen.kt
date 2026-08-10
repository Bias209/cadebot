package com.baxailab.cadebot.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.baxailab.cadebot.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp as composeSp

@Composable
fun HomeScreen(
    onStartOrder: () -> Unit,
    onAskCadebot: () -> Unit,
    onCallStaff: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VivaFoam)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(VivaEspresso, VivaCoffee)))
                    .statusBarsPadding()
                    .padding(vertical = 32.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "☕ VIVA RESERVE",
                        style = MaterialTheme.typography.labelLarge,
                        color = VivaCaramel,
                        letterSpacing = 3.composeSp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Xin chào!",
                        style = MaterialTheme.typography.displayMedium,
                        color = VivaOnDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Bạn muốn dùng gì hôm nay?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = VivaLatte,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalCafe, contentDescription = null,
                            tint = VivaCaramel, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Được phục vụ bởi Cadebot L100",
                            style = MaterialTheme.typography.labelSmall,
                            color = VivaCaramel
                        )
                    }
                }
            }

            // Banner carousel
            if (uiState.campaigns.isNotEmpty()) {
                BannerCarousel(
                    campaigns = uiState.campaigns,
                    onPageChanged = viewModel::onBannerChanged
                )
            }

            Spacer(Modifier.height(24.dp))

            // CTA buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeCTAButton(
                    icon = Icons.Default.LocalCafe,
                    title = "Bắt đầu đặt món",
                    subtitle = "Chọn từ thực đơn của Viva",
                    backgroundColor = VivaEspresso,
                    textColor = VivaOnDark,
                    subtitleColor = VivaLatte,
                    onClick = onStartOrder
                )
                HomeCTAButton(
                    icon = Icons.Default.MicNone,
                    title = "Hỏi Cadebot",
                    subtitle = "Tư vấn AI theo khẩu vị của bạn",
                    backgroundColor = VivaCaramel,
                    textColor = VivaEspresso,
                    subtitleColor = VivaCoffee,
                    onClick = onAskCadebot
                )
                HomeCTAButton(
                    icon = Icons.Default.SupportAgent,
                    title = "Gọi nhân viên",
                    subtitle = "Nhân viên Viva sẽ đến hỗ trợ bạn",
                    backgroundColor = VivaLightGray,
                    textColor = VivaEspresso,
                    subtitleColor = VivaGray,
                    onClick = onCallStaff
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BannerCarousel(
    campaigns: List<HomeCampaign>,
    onPageChanged: (Int) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { campaigns.size })

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            val next = (pagerState.currentPage + 1) % campaigns.size
            pagerState.animateScrollToPage(next)
            onPageChanged(next)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) { page ->
            BannerCard(campaign = campaigns[page])
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(campaigns.size) { index ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 20.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (pagerState.currentPage == index) VivaEspresso else VivaLatte)
                )
            }
        }
    }
}

@Composable
private fun BannerCard(campaign: HomeCampaign) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(VivaCoffee, VivaCaramel))
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = campaign.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = VivaOnDark
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = campaign.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VivaCream
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Xem ngay →",
                    style = MaterialTheme.typography.labelMedium,
                    color = VivaLatte
                )
            }
        }
    }
}

@Composable
private fun HomeCTAButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    backgroundColor: Color,
    textColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(textColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, color = textColor)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
            }
            Text(text = "›", style = MaterialTheme.typography.headlineLarge, color = textColor.copy(alpha = 0.5f))
        }
    }
}

