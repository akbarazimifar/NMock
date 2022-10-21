package me.abolfazl.nmock.repository.mock.models.viewModels

import me.abolfazl.nmock.model.database.MockDatabaseType
import me.abolfazl.nmock.model.database.mocks.MockCreationType
import me.abolfazl.nmock.model.database.mocks.MockProvider
import org.neshan.common.model.LatLng

data class MockDataClass(
    var id: Long? = null,
    @MockDatabaseType val mockDatabaseType: String?,
    var name: String,
    val description: String,
    val originLocation: com.google.android.gms.maps.model.LatLng,
    val destinationLocation: com.google.android.gms.maps.model.LatLng,
    val originAddress: String?,
    val destinationAddress: String?,
    @MockCreationType val creationType: String,
    var speed: Int = 0,
    var lineVector: ArrayList<List<com.google.android.gms.maps.model.LatLng>>? = null,
    val bearing: Float,
    val accuracy: Float,
    @MockProvider val provider: String,
    var createdAt: String? = null,
    var updatedAt: String? = null,
    var showShareLoading: Boolean = false,
    val fileCreatedAt: String? = null,
    val fileOwner: String? = null,
    val applicationVersionCode: Int = 0
)
