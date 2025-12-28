package com.example.aqi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aqi.data.AqiRepository
import com.example.aqi.AqiResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AqiUiState {
    object Loading : AqiUiState()
    data class Success(val data: AqiResponse) : AqiUiState()
    data class Error(val message: String) : AqiUiState()
}

class HyperlocalAqiViewModel(private val repository: AqiRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AqiUiState>(AqiUiState.Loading)
    val uiState: StateFlow<AqiUiState> = _uiState

    fun fetchAqiData(lat: Double, lon: Double, token: String) {
        viewModelScope.launch {
            _uiState.value = AqiUiState.Loading
            repository.getHyperlocalAqi(lat, lon, token)
                .onSuccess { response ->
                    _uiState.value = AqiUiState.Success(response)
                }
                .onFailure { error ->
                    _uiState.value = AqiUiState.Error(error.message ?: "An unknown error occurred")
                }
        }
    }
}