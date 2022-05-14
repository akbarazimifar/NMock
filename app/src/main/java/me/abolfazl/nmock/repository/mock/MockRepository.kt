package me.abolfazl.nmock.repository.mock

import kotlinx.coroutines.flow.Flow
import me.abolfazl.nmock.model.database.models.MockEntity
import me.abolfazl.nmock.repository.models.MockDataClass
import me.abolfazl.nmock.utils.response.Response
import me.abolfazl.nmock.utils.response.exceptions.NMockException

interface MockRepository {

    fun saveMock(
        mockDataClass: MockDataClass
    ): Flow<Response<Boolean, NMockException>>

    suspend fun deleteMock(
        mockDataClass: MockDataClass
    )

    suspend fun deleteAllMocks()

    suspend fun getMocks(): Flow<Response<List<MockDataClass>, NMockException>>

    suspend fun getMock(
        mockId: Long
    ): Flow<Response<MockDataClass, NMockException>>
}