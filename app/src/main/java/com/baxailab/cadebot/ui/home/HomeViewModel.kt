package com.baxailab.cadebot.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class HomeUiState(
    val campaigns: List<HomeCampaign> = emptyList(),
    val currentBannerIndex: Int = 0
)

data class HomeCampaign(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String
)

@Serializable
private data class CampaignResponse(val campaigns: List<CampaignEntry>)

@Serializable
private data class CampaignEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val active: Boolean
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadCampaigns()
    }

    private fun loadCampaigns() {
        try {
            val raw = context.assets.open("config/campaign.json").bufferedReader().readText()
            val resp = Json { ignoreUnknownKeys = true }.decodeFromString<CampaignResponse>(raw)
            _uiState.value = HomeUiState(
                campaigns = resp.campaigns.filter { it.active }.map {
                    HomeCampaign(it.id, it.title, it.subtitle, it.description)
                }
            )
        } catch (_: Exception) { }
    }

    fun onBannerChanged(index: Int) {
        _uiState.value = _uiState.value.copy(currentBannerIndex = index)
    }
}
