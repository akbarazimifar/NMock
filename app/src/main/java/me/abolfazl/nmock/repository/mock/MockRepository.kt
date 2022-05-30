package me.abolfazl.nmock.repository.mock

import kotlinx.coroutines.flow.Flow
import me.abolfazl.nmock.model.database.MockProvider
import me.abolfazl.nmock.model.database.MockType
import me.abolfazl.nmock.repository.models.MockDataClass
import me.abolfazl.nmock.utils.response.Response
import org.neshan.common.model.LatLng

interface MockRepository {

    fun saveMockInformation(
        name: String,
        description: String,
        originLocation: LatLng,
        destinationLocation: LatLng,
        originAddress: String?,
        destinationAddress: String?,
        @MockType type: String,
        speed: Int,
        lineVector: ArrayList<List<LatLng>>?,
        bearing: Float,
        accuracy: Float,
        @MockProvider provider: String,
    ): Flow<Response<Long, Int>>

    fun updateMockInformation(
        mockDataClass: MockDataClass
    ): Flow<Response<Long, Int>>

    suspend fun deleteMock(
        mockDataClass: MockDataClass
    )

    suspend fun deleteAllMocks()

    suspend fun getMocks(): List<MockDataClass>

    suspend fun getMock(
        mockId: Long
    ): Flow<Response<MockDataClass, Int>>
}