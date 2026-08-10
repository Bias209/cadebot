package com.baxailab.cadebot.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxailab.cadebot.data.mock.MockMenuService
import com.baxailab.cadebot.data.model.CartItem
import com.baxailab.cadebot.data.model.TableInfo
import com.baxailab.cadebot.data.remote.PaymentApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PaymentStatus { IDLE, CREATING, AWAITING_PAYMENT, PAID, EXPIRED, ERROR }

data class CartUiState(
    val items: List<CartItem> = emptyList(),
    val tables: List<TableInfo> = emptyList(),
    val selectedTableId: String = "",
    val paymentStatus: PaymentStatus = PaymentStatus.IDLE,
    val orderCode: String = "",
    val qrUrl: String = "",
    val transferContent: String = "",
    val secondsRemaining: Int = 0,
    val errorMessage: String = ""
) {
    val totalAmount: Int get() = items.sumOf { it.totalPrice }
    val isEmpty: Boolean get() = items.isEmpty()
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val paymentApiService: PaymentApiService,
    private val menuService: MockMenuService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        val tables = menuService.getTables()
        _uiState.value = _uiState.value.copy(
            tables = tables,
            selectedTableId = tables.firstOrNull()?.tableId ?: ""
        )
    }

    fun addItem(item: CartItem) {
        _uiState.value = _uiState.value.copy(items = _uiState.value.items + item)
    }

    fun removeItem(itemId: String) {
        _uiState.value = _uiState.value.copy(items = _uiState.value.items.filter { it.id != itemId })
    }

    fun selectTable(tableId: String) {
        _uiState.value = _uiState.value.copy(selectedTableId = tableId)
    }

    fun checkout() {
        pollingJob?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(paymentStatus = PaymentStatus.CREATING, errorMessage = "")
            val result = paymentApiService.createOrder(_uiState.value.selectedTableId, _uiState.value.items)
            result.fold(
                onSuccess = { order ->
                    _uiState.value = _uiState.value.copy(
                        paymentStatus = PaymentStatus.AWAITING_PAYMENT,
                        orderCode = order.orderCode,
                        qrUrl = order.qrUrl,
                        transferContent = order.transferContent,
                        secondsRemaining = order.secondsRemaining
                    )
                    startPolling(order.orderCode)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        paymentStatus = PaymentStatus.ERROR,
                        errorMessage = e.message ?: "Không kết nối được server thanh toán"
                    )
                }
            )
        }
    }

    private fun startPolling(orderCode: String) {
        pollingJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                delay(2000)
                val result = paymentApiService.getOrderStatus(orderCode)
                result.fold(
                    onSuccess = { status ->
                        consecutiveFailures = 0
                        when (status.status) {
                            "PAID" -> {
                                _uiState.value = _uiState.value.copy(
                                    paymentStatus = PaymentStatus.PAID,
                                    secondsRemaining = status.secondsRemaining
                                )
                                return@launch
                            }
                            "CANCELLED" -> {
                                _uiState.value = _uiState.value.copy(paymentStatus = PaymentStatus.EXPIRED)
                                return@launch
                            }
                            else -> {
                                _uiState.value = _uiState.value.copy(secondsRemaining = status.secondsRemaining)
                            }
                        }
                    },
                    onFailure = {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            _uiState.value = _uiState.value.copy(
                                paymentStatus = PaymentStatus.ERROR,
                                errorMessage = "Mất kết nối tới server thanh toán"
                            )
                            return@launch
                        }
                    }
                )
            }
        }
    }

    fun clearCart() {
        pollingJob?.cancel()
        _uiState.value = CartUiState(
            tables = _uiState.value.tables,
            selectedTableId = _uiState.value.selectedTableId
        )
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}
