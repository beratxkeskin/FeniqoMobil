package com.feniqo.mobile.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DemoState { LOADING, READY, FAILED }

@HiltViewModel
class DemoViewModel @Inject constructor(private val repository: DemoDataRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(DemoState.LOADING)
    val state = mutableState.asStateFlow()
    init { open() }
    fun open() {
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = DemoState.LOADING
            try {
                repository.prepare()
                mutableState.value = DemoState.READY
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = DemoState.FAILED
            }
        }
    }
}
