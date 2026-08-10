package com.baxailab.cadebot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baxailab.cadebot.ui.ai.AiScreen
import com.baxailab.cadebot.ui.callstaff.CallStaffScreen
import com.baxailab.cadebot.ui.cart.CartScreen
import com.baxailab.cadebot.ui.cart.CartViewModel
import com.baxailab.cadebot.ui.detail.DetailScreen
import com.baxailab.cadebot.ui.home.HomeScreen
import com.baxailab.cadebot.ui.menu.MenuScreen
import com.baxailab.cadebot.ui.ordersuccess.OrderSuccessScreen
import com.baxailab.cadebot.ui.payment.PaymentScreen

object Routes {
    const val HOME = "home"
    const val MENU = "menu"
    const val DETAIL = "detail/{menuItemId}"
    const val CART = "cart"
    const val PAYMENT = "payment"
    const val ORDER_SUCCESS = "order_success"
    const val AI = "ai"
    const val CALL_STAFF = "call_staff"

    fun detail(menuItemId: String) = "detail/$menuItemId"
}

@Composable
fun CadebotNavGraph(navController: NavHostController = rememberNavController()) {
    // Shared CartViewModel scoped to the nav graph
    val cartViewModel: CartViewModel = hiltViewModel()
    val cartUiState by cartViewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onStartOrder = { navController.navigate(Routes.MENU) },
                onAskCadebot = { navController.navigate(Routes.AI) },
                onCallStaff = { navController.navigate(Routes.CALL_STAFF) }
            )
        }

        composable(Routes.MENU) {
            MenuScreen(
                cartItemCount = cartUiState.items.size,
                onBack = { navController.popBackStack() },
                onItemClick = { id -> navController.navigate(Routes.detail(id)) },
                onCartClick = { navController.navigate(Routes.CART) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("menuItemId") { type = NavType.StringType })
        ) {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onAddToCart = { cartItem ->
                    cartViewModel.addItem(cartItem)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.CART) {
            CartScreen(
                uiState = cartUiState,
                onBack = { navController.popBackStack() },
                onCheckout = {
                    cartViewModel.checkout()
                    navController.navigate(Routes.PAYMENT)
                },
                onRemoveItem = { id -> cartViewModel.removeItem(id) },
                onSelectTable = { tableId -> cartViewModel.selectTable(tableId) },
                onContinueShopping = { navController.navigate(Routes.MENU) }
            )
        }

        composable(Routes.PAYMENT) {
            PaymentScreen(
                totalAmount = cartUiState.totalAmount,
                qrUrl = cartUiState.qrUrl,
                transferContent = cartUiState.transferContent,
                secondsRemaining = cartUiState.secondsRemaining,
                paymentStatus = cartUiState.paymentStatus,
                errorMessage = cartUiState.errorMessage,
                onRetry = { cartViewModel.checkout() },
                onSuccess = {
                    navController.navigate(Routes.ORDER_SUCCESS) {
                        popUpTo(Routes.CART) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ORDER_SUCCESS) {
            OrderSuccessScreen(
                orderId = cartUiState.orderCode,
                tableId = cartUiState.selectedTableId,
                totalAmount = cartUiState.totalAmount,
                onBackHome = {
                    cartViewModel.clearCart()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onOrderMore = {
                    cartViewModel.clearCart()
                    navController.navigate(Routes.MENU) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        composable(Routes.AI) {
            AiScreen(
                onBack = { navController.popBackStack() },
                onAddToCart = { menuItemId ->
                    // Navigate to detail screen for proper customization
                    navController.navigate(Routes.detail(menuItemId))
                }
            )
        }

        composable(Routes.CALL_STAFF) {
            CallStaffScreen(onBack = { navController.popBackStack() })
        }
    }
}
