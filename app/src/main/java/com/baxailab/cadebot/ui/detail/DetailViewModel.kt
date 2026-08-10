package com.baxailab.cadebot.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.baxailab.cadebot.data.mock.MockMenuService
import com.baxailab.cadebot.data.model.CartItem
import com.baxailab.cadebot.data.model.MenuItem
import com.baxailab.cadebot.data.model.SIZE_PRICE_DELTA
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DetailUiState(
    val item: MenuItem? = null,
    val quantity: Int = 1,
    val selectedSize: String = "",
    val selectedSweetness: String = "",
    val selectedIce: String = "",
    val selectedTemperature: String = "",
    val selectedToppings: List<String> = emptyList(),
    val note: String = ""
) {
    val isHot: Boolean get() = selectedTemperature == "hot" || selectedTemperature == "warm"

    val unitPrice: Int get() {
        val base = item?.price ?: 0
        val sizeDelta = SIZE_PRICE_DELTA[selectedSize] ?: 0
        val toppingExtra = selectedToppings.size * 5000
        return base + sizeDelta + toppingExtra
    }

    val totalPrice: Int get() = unitPrice * quantity
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val menuService: MockMenuService
) : ViewModel() {

    private val menuItemId: String = savedStateHandle["menuItemId"] ?: ""

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        val menu = menuService.getMenu()
        val item = menu.items.find { it.menuItemId == menuItemId }
        if (item != null) {
            _uiState.value = DetailUiState(
                item = item,
                selectedSize = item.attributes.defaultSize,
                selectedSweetness = item.attributes.defaultSweetness,
                selectedIce = item.attributes.defaultIce,
                selectedTemperature = item.attributes.defaultTemperature
            )
        }
    }

    fun setQuantity(qty: Int) {
        if (qty >= 1) _uiState.value = _uiState.value.copy(quantity = qty)
    }

    fun setSize(size: String) { _uiState.value = _uiState.value.copy(selectedSize = size) }
    fun setSweetness(s: String) { _uiState.value = _uiState.value.copy(selectedSweetness = s) }
    fun setIce(ice: String) { _uiState.value = _uiState.value.copy(selectedIce = ice) }
    fun setNote(note: String) { _uiState.value = _uiState.value.copy(note = note) }

    fun setTemperature(temp: String) {
        // Khi chọn nóng, reset ice về no_ice
        val newIce = if (temp == "hot" || temp == "warm") "no_ice" else _uiState.value.selectedIce
        _uiState.value = _uiState.value.copy(selectedTemperature = temp, selectedIce = newIce)
    }

    fun toggleTopping(topping: String) {
        val current = _uiState.value.selectedToppings.toMutableList()
        if (current.contains(topping)) current.remove(topping) else current.add(topping)
        _uiState.value = _uiState.value.copy(selectedToppings = current)
    }

    fun buildCartItem(): CartItem? {
        val item = _uiState.value.item ?: return null
        return CartItem(
            menuItem = item,
            quantity = _uiState.value.quantity,
            selectedSize = _uiState.value.selectedSize,
            selectedSweetness = _uiState.value.selectedSweetness,
            selectedIce = _uiState.value.selectedIce,
            selectedTemperature = _uiState.value.selectedTemperature,
            selectedToppings = _uiState.value.selectedToppings,
            note = _uiState.value.note
        )
    }
}
