package com.example.aqi.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aqi.AqiApiService
import com.example.aqi.data.AqiRepository
import com.example.aqi.viewmodel.HyperlocalAqiViewModel

class ViewModelFactory : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HyperlocalAqiViewModel::class.java)) {
            val repository = AqiRepository(AqiApiService.create())
            @Suppress("UNCHECKED_CAST")
            return HyperlocalAqiViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}