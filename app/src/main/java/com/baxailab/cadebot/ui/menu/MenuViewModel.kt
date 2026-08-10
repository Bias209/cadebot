package com.baxailab.cadebot.ui.menu

import androidx.lifecycle.ViewModel
import com.baxailab.cadebot.data.mock.MockMenuService
import com.baxailab.cadebot.data.model.Category
import com.baxailab.cadebot.data.model.MenuItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class MenuUiState(
    val categories: List<Category> = emptyList(),
    val items: List<MenuItem> = emptyList(),
    val selectedCategoryId: String = "",
    val filteredItems: List<MenuItem> = emptyList()
)

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val menuService: MockMenuService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    init {
        val menu = menuService.getMenu()
        val firstCat = menu.categories.firstOrNull()?.id ?: ""
        _uiState.value = MenuUiState(
            categories = menu.categories,
            items = menu.items,
            selectedCategoryId = firstCat,
            filteredItems = menu.items.filter { it.category == firstCat }
        )
    }

    fun selectCategory(categoryId: String) {
        _uiState.value = _uiState.value.copy(
            selectedCategoryId = categoryId,
            filteredItems = _uiState.value.items.filter { it.category == categoryId }
        )
    }
}
