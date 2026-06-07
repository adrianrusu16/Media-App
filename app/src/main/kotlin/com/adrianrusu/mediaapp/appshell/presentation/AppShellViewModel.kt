package com.adrianrusu.mediaapp.appshell.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.adrianrusu.mediaapp.appshell.data.InMemoryAppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.DispatchAppShellIntentUseCase
import com.adrianrusu.mediaapp.appshell.domain.ObserveAppShellStateUseCase
import com.adrianrusu.mediaapp.core.automotive.ux.PlatformAutomotiveUxRestrictionObserver

class AppShellViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository: AppShellRepository =
        InMemoryAppShellRepository(
            uxRestrictionObserver = PlatformAutomotiveUxRestrictionObserver(application),
        )
    private val observeState = ObserveAppShellStateUseCase(repository)
    private val dispatchIntent = DispatchAppShellIntentUseCase(repository)

    val state = observeState()

    init {
        repository.start()
    }

    fun onIntent(intent: AppShellIntent) {
        dispatchIntent(intent)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
