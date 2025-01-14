package me.abolfazl.nmock.view.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.abolfazl.nmock.model.database.DATABASE_TYPE_IMPORTED
import me.abolfazl.nmock.model.database.DATABASE_TYPE_NORMAL
import me.abolfazl.nmock.model.database.mocks.TYPE_CUSTOM_CREATION
import me.abolfazl.nmock.repository.locationInfo.LocationInfoRepository
import me.abolfazl.nmock.repository.locationInfo.LocationInfoRepositoryImpl
import me.abolfazl.nmock.repository.mock.MockRepository
import me.abolfazl.nmock.repository.mock.MockRepositoryImpl
import me.abolfazl.nmock.repository.mock.models.viewModels.MockDataClass
import me.abolfazl.nmock.repository.routingInfo.RoutingInfoRepository
import me.abolfazl.nmock.repository.routingInfo.RoutingInfoRepositoryImpl
import me.abolfazl.nmock.utils.Constant
import me.abolfazl.nmock.utils.locationFormat
import me.abolfazl.nmock.utils.logger.NMockLogger
import me.abolfazl.nmock.utils.response.OneTimeEmitter
import me.abolfazl.nmock.utils.response.SingleEvent
import me.abolfazl.nmock.utils.response.ifNotSuccessful
import me.abolfazl.nmock.utils.response.ifSuccessful
import javax.inject.Inject

@HiltViewModel
class MockEditorViewModel @Inject constructor(
    private val locationInfoRepository: LocationInfoRepository,
    private val routingInfoRepository: RoutingInfoRepository,
    private val mockRepository: MockRepository,
    private val logger: NMockLogger
) : ViewModel() {

    companion object {
        const val ACTION_UNKNOWN = "UNKNOWN_EXCEPTION"
        const val ACTION_LOCATION_INFORMATION = "ACTION_LOCATION_INFORMATION"
        const val ACTION_ROUTE_INFORMATION = "ROUTE_INFORMATION_REQUEST"
        const val ACTION_SAVE_MOCK_INFORMATION = "SAVE_MOCK_INFORMATION"
        const val ACTION_GET_MOCK_INFORMATION = "GET_MOCK_INFORMATION"
        const val ACTION_MOCK_SAVED = "MOCK_SAVED!"
    }

    // for handling states
    private val _mockEditorState = MutableStateFlow(MockEditorState())
    val mockEditorState = _mockEditorState.asStateFlow()

    // for errors..
    private val _oneTimeEmitter = MutableSharedFlow<OneTimeEmitter>()
    val oneTimeEmitter = _oneTimeEmitter.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.captureExceptionWithLogFile(
            message = "Exception thrown in MockEditorViewModel: ${throwable.message}",
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

    fun getLocationInformation(
        location: LatLng,
        isOrigin: Boolean
    ) = viewModelScope.launch(exceptionHandler) {
        logger.writeLog(
            value = "getLocationInformation function called!" +
                    " isOrigin: $isOrigin," +
                    " latitude: ${location.latitude}," +
                    " longitude: ${location.longitude}"
        )
        setLoadingState(true)
        locationInfoRepository.getLocationInformation(
            location.latitude,
            location.longitude
        ).collect { response ->
            setLoadingState(false)
            response.ifSuccessful { result ->
                logger.writeLog(
                    value = "locationInformationReceived!" +
                            "isOrigin: $isOrigin," +
                            "address: ${result.fullAddress}"
                )
                if (isOrigin) {
                    _mockEditorState.value = _mockEditorState.value.copy(
                        originAddress = SingleEvent(result.fullAddress),
                        originLocation = SingleEvent(location)
                    )
                } else {
                    _mockEditorState.value = _mockEditorState.value.copy(
                        destinationAddress = SingleEvent(result.fullAddress),
                        destinationLocation = SingleEvent(location)
                    )
                }
            }
            response.ifNotSuccessful { exceptionType ->
                _oneTimeEmitter.emit(
                    OneTimeEmitter(
                        actionId = ACTION_LOCATION_INFORMATION,
                        message = actionMapper(exceptionType)
                    )
                )
            }
        }
    }

    fun getRouteInformation() = viewModelScope.launch(exceptionHandler) {
        val originLocation = _mockEditorState.value.originLocation?.getRawValue()
        val destinationLocation = _mockEditorState.value.destinationLocation?.getRawValue()
        if (originLocation == null || destinationLocation == null) {
            logger.writeLog(
                value = "getRouteInformation was failed. origin or destination location was null!"
            )
            _oneTimeEmitter.emit(
                OneTimeEmitter(
                    actionId = ACTION_ROUTE_INFORMATION,
                    message = actionMapper(RoutingInfoRepositoryImpl.UNKNOWN_EXCEPTION)
                )
            )
            return@launch
        }
        setLoadingState(true)
        routingInfoRepository.getRoutingInformation(
            origin = originLocation.locationFormat(),
            destination = destinationLocation.locationFormat()
        ).collect { response ->
            setLoadingState(false)
            response.ifSuccessful { result ->
                logger.writeLog(
                    value = "getRouteInformation was successful!"
                )
                _mockEditorState.value = _mockEditorState.value.copy(
                    lineVector = SingleEvent(result.routeModels[0].getRouteLineVector())
                )
            }
            response.ifNotSuccessful { exceptionType ->
                _oneTimeEmitter.emit(
                    OneTimeEmitter(
                        actionId = ACTION_ROUTE_INFORMATION,
                        message = actionMapper(exceptionType)
                    )
                )
            }
        }
    }

    fun saveMockInformation(
        name: String,
        description: String,
        speed: Int
    ) = viewModelScope.launch(exceptionHandler) {
        val originLocation = _mockEditorState.value.originLocation?.getRawValue()
        val destinationLocation = _mockEditorState.value.destinationLocation?.getRawValue()
        val id = _mockEditorState.value.id?.getRawValue()
        if (originLocation == null || destinationLocation == null) {
            logger.writeLog(
                value = "saveMockInformation was failed. origin or destination location was null!"
            )
            _oneTimeEmitter.emit(
                OneTimeEmitter(
                    actionId = ACTION_ROUTE_INFORMATION,
                    message = actionMapper(RoutingInfoRepositoryImpl.UNKNOWN_EXCEPTION)
                )
            )
            return@launch
        }
        setLoadingState(true)
        // For updating mock:
        if (id != null) {
            mockRepository.updateMockInformation(
                mockDataClass = MockDataClass(
                    id = id,
                    name = name,
                    description = description,
                    originLocation = originLocation,
                    destinationLocation = destinationLocation,
                    originAddress = _mockEditorState.value.originAddress?.getRawValue()!!,
                    destinationAddress = _mockEditorState.value.destinationAddress?.getRawValue()!!,
                    creationType = TYPE_CUSTOM_CREATION,
                    speed = speed,
                    lineVector = _mockEditorState.value.lineVector?.getRawValue(),
                    bearing = 0f,
                    accuracy = 1f,
                    provider = Constant.PROVIDER_GPS,
                    createdAt = _mockEditorState.value.createdAt!!,
                    updatedAt = _mockEditorState.value.updatedAt,
                    mockDatabaseType = _mockEditorState.value.mockDatabaseType!!,
                    fileCreatedAt = _mockEditorState.value.fileCreatedAt,
                    fileOwner = _mockEditorState.value.fileOwner,
                    applicationVersionCode = _mockEditorState.value.applicationVersionCode
                )
            ).collect { response ->
                setLoadingState(false)
                response.ifSuccessful { mockId ->
                    _mockEditorState.value = _mockEditorState.value.copy(
                        id = SingleEvent(mockId)
                    )
                    _oneTimeEmitter.emit(OneTimeEmitter(actionId = ACTION_MOCK_SAVED, message = 0))
                }
                response.ifNotSuccessful { exceptionType ->
                    _oneTimeEmitter.emit(
                        OneTimeEmitter(
                            actionId = ACTION_SAVE_MOCK_INFORMATION,
                            message = actionMapper(exceptionType)
                        )
                    )
                }
            }
        } /*For inserting new mock:*/ else {
            mockRepository.saveMockInformation(
                mockDataClass = MockDataClass(
                    name = name,
                    description = description,
                    creationType = TYPE_CUSTOM_CREATION,
                    originLocation = originLocation,
                    destinationLocation = destinationLocation,
                    originAddress = _mockEditorState.value.originAddress?.getRawValue(),
                    destinationAddress = _mockEditorState.value.destinationAddress?.getRawValue(),
                    speed = speed,
                    lineVector = mockEditorState.value.lineVector?.getRawValue(),
                    bearing = 0f,
                    accuracy = 1F,
                    provider = Constant.PROVIDER_GPS,
                    mockDatabaseType = DATABASE_TYPE_NORMAL
                )
            ).collect { response ->
                setLoadingState(false)
                response.ifSuccessful { mockId ->
                    _mockEditorState.value = _mockEditorState.value.copy(
                        id = SingleEvent(mockId)
                    )
                    _oneTimeEmitter.emit(OneTimeEmitter(actionId = ACTION_MOCK_SAVED, message = 0))
                }
                response.ifNotSuccessful { exceptionType ->
                    _oneTimeEmitter.emit(
                        OneTimeEmitter(
                            actionId = ACTION_SAVE_MOCK_INFORMATION,
                            message = actionMapper(exceptionType)
                        )
                    )
                }
            }
        }
    }

    fun clearMockInformation(
        clearOrigin: Boolean
    ) {
        logger.writeLog(
            value = "clearMockInformation called. clearOrigin: $clearOrigin"
        )
        if (clearOrigin) {
            _mockEditorState.value = _mockEditorState.value.copy(
                destinationAddress = null,
                originAddress = null,
                lineVector = null,
                originLocation = null,
                destinationLocation = null,
                speed = 0,
                id = null,
            )
        } else {
            _mockEditorState.value = _mockEditorState.value.copy(
                destinationAddress = null,
                lineVector = null,
                destinationLocation = null
            )
        }
    }

    fun getMockInformationFromId(
        id: Long,
        mockIsImported: Boolean
    ) = viewModelScope.launch(exceptionHandler) {
        val mockDatabaseType = if (mockIsImported) DATABASE_TYPE_IMPORTED else DATABASE_TYPE_NORMAL
        setLoadingState(true)
        mockRepository.getMockInformationFromId(
            id = id,
            mockDatabaseType = mockDatabaseType
        ).collect { response ->
            setLoadingState(false)
            response.ifSuccessful { mockData ->
                _mockEditorState.value = _mockEditorState.value.copy(
                    id = SingleEvent(mockData.id!!),
                    name = SingleEvent(mockData.name),
                    description = SingleEvent(mockData.description),
                    originLocation = SingleEvent(mockData.originLocation),
                    destinationLocation = SingleEvent(mockData.destinationLocation),
                    originAddress = SingleEvent(mockData.originAddress),
                    destinationAddress = SingleEvent(mockData.destinationAddress),
                    lineVector = SingleEvent(mockData.lineVector!!),
                    speed = mockData.speed,
                    createdAt = mockData.createdAt,
                    updatedAt = mockData.updatedAt,
                    mockDatabaseType = mockData.mockDatabaseType,
                    fileCreatedAt = mockData.fileCreatedAt,
                    fileOwner = mockData.fileOwner,
                    applicationVersionCode = mockData.applicationVersionCode,
                    openingReason = EDITOR_REASON_BUNDLE
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

    fun deleteMock() = viewModelScope.launch(exceptionHandler) {
        mockRepository.deleteMock(
            mockId = _mockEditorState.value.id?.getRawValue()!!,
            // todo: it's doesn't good solution for deleting! we should have capacity for all database types!
            mockDatabaseType = _mockEditorState.value.mockDatabaseType ?: DATABASE_TYPE_NORMAL
        )
        clearMockInformation(true)
    }

    fun loadMockWithOriginAndDestinationLocation(
        originLocation: String,
        destinationLocation: String,
        speed: String
    ) = viewModelScope.launch {
        val realOrigin = originLocation.locationFormat()
        val realDestination = destinationLocation.locationFormat()
        val realSpeed = speed.toInt()
        _mockEditorState.value = _mockEditorState.value.copy(
            speed = realSpeed,
            openingReason = EDITOR_REASON_INTENT
        )
        withContext(Dispatchers.Default) { getLocationInformation(realOrigin, true) }
        withContext(Dispatchers.Default) { getLocationInformation(realDestination, false) }
        withContext(Dispatchers.Default) { getRouteInformation() }
    }

    fun hasMockData(): Boolean {
        return _mockEditorState.value.lineVector != null
    }

    private fun setLoadingState(loading: Boolean) {
        _mockEditorState.value = _mockEditorState.value.copy(
            loadingState = SingleEvent(loading)
        )
    }

    private fun actionMapper(errorType: Int): Int {
        return when (errorType) {
            LocationInfoRepositoryImpl.OUT_OF_IRAN_EXCEPTION -> 0
            LocationInfoRepositoryImpl.UNKNOWN_EXCEPTION -> MockEditorActivity.LOCATION_INFORMATION_EXCEPTION_MESSAGE
            RoutingInfoRepositoryImpl.UNKNOWN_EXCEPTION -> MockEditorActivity.ROUTE_INFORMATION_EXCEPTION_MESSAGE
            MockRepositoryImpl.LINE_VECTOR_NULL_EXCEPTION,
            MockRepositoryImpl.DATABASE_EMPTY_LINE_EXCEPTION,
            MockRepositoryImpl.DATABASE_WRONG_TYPE_EXCEPTION,
            MockRepositoryImpl.DATABASE_EMPTY_MOCK_INFORMATION,
            MockRepositoryImpl.DATABASE_INSERTION_EXCEPTION -> MockEditorActivity.MOCK_INFORMATION_IS_WRONG_MESSAGE
            else -> MockEditorActivity.UNKNOWN_ERROR_MESSAGE
        }
    }
}