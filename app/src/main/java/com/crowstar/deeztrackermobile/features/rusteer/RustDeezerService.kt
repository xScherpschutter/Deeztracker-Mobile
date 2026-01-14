package com.crowstar.deeztrackermobile.features.rusteer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rusteer.RusteerService

class RustDeezerService {
    private val service = RusteerService()
    private var currentArl: String? = null

    suspend fun login(arl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("RustDeezerService", "Verifying ARL: ${arl.take(10)}...")
                val isValid = service.verifyArl(arl)
                if (isValid) {
                    currentArl = arl
                    Log.d("RustDeezerService", "ARL is valid, login successful")
                } else {
                    Log.w("RustDeezerService", "ARL is invalid")
                }
                isValid
            } catch (e: Exception) {
                Log.e("RustDeezerService", "Login verification failed", e)
                false
            }
        }
    }

    fun isLoggedIn(): Boolean {
        return currentArl != null
    }

    fun getArl(): String? {
        return currentArl
    }
}
