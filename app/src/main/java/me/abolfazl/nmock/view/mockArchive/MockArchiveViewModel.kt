package me.abolfazl.nmock.view.mockArchive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.abolfazl.nmock.repository.mock.MockRepository
import me.abolfazl.nmock.utils.response.exceptions.EXCEPTION_FORCE_CLOSE
import me.abolfazl.nmock.utils.response.ifNotSuccessful
import me.abolfazl.nmock.utils.response.ifSuccessful
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MockArchiveViewModel @Inject constructor(
    private val mockRepository: MockRepository
) : ViewModel() {

    private val _mockArchiveState = MutableStateFlow(MockArchiveState())
    val mockArchiveState = _mockArchiveState.asStateFlow()

    private val _oneTimeEmitter = MutableSharedFlow<String>()
    val oneTimeEmitter = _oneTimeEmitter.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e("Exception thrown in MockArchiveViewModel: " + throwable.message)
        viewModelScope.launch {
            _oneTimeEmitter.emit("$EXCEPTION_FORCE_CLOSE thrown. please check the logcat!")
        }
    }

    fun getMocks() = viewModelScope.launch(exceptionHandler) {
        mockRepository.getMocks().collect { response ->
            response.ifSuccessful { mockList ->
                _mockArchiveState.value = _mockArchiveState.value.copy(
                    mockList = mockList
                )
            }
            response.ifNotSuccessful { exception ->
                _oneTimeEmitter.emit(exception.type)
                Timber.e(exception.type)
            }
        }
    }

    fun deleteAllMocks() = viewModelScope.launch(exceptionHandler) {
        mockRepository.deleteAllMocks()
        _mockArchiveState.value = _mockArchiveState.value.copy(
            mockList = null
        )
    }
}