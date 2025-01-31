package me.abolfazl.nmock.view.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.abolfazl.nmock.model.database.DATABASE_TYPE_IMPORTED
import me.abolfazl.nmock.model.database.DATABASE_TYPE_NORMAL
import me.abolfazl.nmock.repository.mock.MockRepository
import me.abolfazl.nmock.repository.mock.MockRepositoryImpl
import me.abolfazl.nmock.repository.mock.models.viewModels.MockDataClass
import me.abolfazl.nmock.utils.logger.NMockLogger
import me.abolfazl.nmock.utils.response.OneTimeEmitter
import me.abolfazl.nmock.utils.response.ifNotSuccessful
import me.abolfazl.nmock.utils.response.ifSuccessful
import javax.inject.Inject

@HiltViewModel
class MockPlayerViewModel @Inject constructor(
    private val mockRepository: MockRepository,
    private val logger: NMockLogger
) : ViewModel() {

    companion object {
        const val ACTION_UNKNOWN = "UNKNOWN_EXCEPTION"
        const val ACTION_GET_MOCK_INFORMATION = "GET_MOCK_INFORMATION"
        const val ACTION_UPDATE_MOCK_INFORMATION = "UPDATE_MOCK_INFORMATION"
    }

    // for handling states
    private val _mockPlayerState = MutableStateFlow(MockPlayerState())
    val mockPlayerState = _mockPlayerState.asStateFlow()

    // for errors..
    private val _oneTimeEmitter = MutableSharedFlow<OneTimeEmitter>()
    val oneTimeEmitter = _oneTimeEmitter.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.captureExceptionWithLogFile(
            message = "Exception thrown in MockPlayerViewModel: ${throwable.message}",
        )
        viewModelScope.launch {
            _oneTimeEmitter.emit(
                OneTimeEmitter(
                    actionId = ACTION_UNKNOWN,
                    message = actionMapper(0)
                )
            )
        }
    }

    init {
        logger.disableLogHeaderForThisClass()
        logger.setClassInformationForEveryLog(javaClass.simpleName)
    }

    fun getMockInformation(
        mockId: Long,
        mockIsImported: Boolean
    ) = viewModelScope.launch(exceptionHandler) {
        val mockDatabaseType = if (mockIsImported) DATABASE_TYPE_IMPORTED else DATABASE_TYPE_NORMAL
        mockRepository.getMockInformationFromId(
            id = mockId,
            mockDatabaseType = mockDatabaseType
        ).collect { response ->
            response.ifSuccessful { mockInformation ->
                _mockPlayerState.value = _mockPlayerState.value.copy(
                    mockInformation = mockInformation
                )
            }
            response.ifNotSuccessful { exceptionType ->
                _oneTimeEmitter.emit(
                    OneTimeEmitter(
                        actionId = ACTION_GET_MOCK_INFORMATION,
                        message = actionMapper(exceptionType)
                    )
                )
            }
        }
    }

    fun changeMockSpeed(mockSpeed: Int) = viewModelScope.launch(exceptionHandler) {
        val mock = _mockPlayerState.value.mockInformation!!
        mock.speed = mockSpeed
        _mockPlayerState.value = _mockPlayerState.value.copy(
            mockInformation = mock
        )
        val mockData = _mockPlayerState.value.mockInformation!!
        mockRepository.updateMockInformation(
            MockDataClass(
                id = mockData.id!!,
                name = mockData.name,
                description = mockData.description,
                originLocation = mockData.originLocation,
                destinationLocation = mockData.destinationLocation,
                originAddress = mockData.originAddress,
                destinationAddress = mockData.destinationAddress,
                creationType = mockData.creationType,
                speed = mockData.speed,
                lineVector = mockData.lineVector,
                bearing = mockData.bearing,
                accuracy = mockData.bearing,
                provider = mockData.provider,
                createdAt = mockData.createdAt!!,
                mockDatabaseType = mockData.mockDatabaseType,
                updatedAt = mockData.updatedAt,
                fileCreatedAt = mockData.fileCreatedAt,
                fileOwner = mockData.fileOwner,
                applicationVersionCode = mockData.applicationVersionCode
            )
        ).collect { response ->
            response.ifNotSuccessful { exceptionType ->
                _oneTimeEmitter.emit(
                    OneTimeEmitter(
                        actionId = ACTION_UPDATE_MOCK_INFORMATION,
                        message = actionMapper(exceptionType)
                    )
                )
            }
        }
    }

    private fun actionMapper(exceptionType: Int): Int {
        return when (exceptionType) {
            MockRepositoryImpl.LINE_VECTOR_NULL_EXCEPTION,
            MockRepositoryImpl.DATABASE_EMPTY_LINE_EXCEPTION,
            MockRepositoryImpl.DATABASE_INSERTION_EXCEPTION,
            MockRepositoryImpl.DATABASE_EMPTY_MOCK_INFORMATION,
            MockRepositoryImpl.DATABASE_WRONG_TYPE_EXCEPTION -> MockPlayerActivity.MOCK_INFORMATION_IS_WRONG_MESSAGE
            else -> MockPlayerActivity.UNKNOWN_ERROR_MESSAGE
        }
    }
}