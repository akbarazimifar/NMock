package me.abolfazl.nmock.repository.models

import me.abolfazl.nmock.model.database.MockProvider
import me.abolfazl.nmock.model.database.MockType
import org.neshan.common.model.LatLng

data class MockDataClass(
    val id: Long? = null,
    val mockName: String,
    val mockDescription: String,
    val originLocation: LatLng,
    val destinationLocation: LatLng,
    val originAddress: String,
    val destinationAddress: String,
    @MockType
    val mockType: String,
    var speed: Int = 0,
    val lineVector: ArrayList<List<LatLng>>? = null,
    val bearing: Float,
    val accuracy: Float,
    @MockProvider
    val provider: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)