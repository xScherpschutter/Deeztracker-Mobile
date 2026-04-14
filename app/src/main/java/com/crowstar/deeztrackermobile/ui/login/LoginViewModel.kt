package com.crowstar.deeztrackermobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val rustService: RustDeezerService
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun login(arl: String, onLoginSuccess: () -> Unit, errorMsg: String) {
        if (arl.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val success = rustService.login(arl)
            _isLoading.value = false
            if (success) {
                onLoginSuccess()
            } else {
                _errorMessage.value = errorMsg
            }
        }
    }
}
