package me.abolfazl.nmock.view.mockPlayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.carto.core.ScreenPos
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.abolfazl.nmock.R
import me.abolfazl.nmock.databinding.ActivityMockPlayerBinding
import me.abolfazl.nmock.utils.managers.CameraManager
import me.abolfazl.nmock.utils.managers.LineManager
import me.abolfazl.nmock.utils.managers.MarkerManager
import me.abolfazl.nmock.utils.managers.UriManager
import me.abolfazl.nmock.utils.response.exceptions.EXCEPTION_DATABASE_GETTING_ERROR
import me.abolfazl.nmock.utils.response.exceptions.EXCEPTION_INSERTION_ERROR
import me.abolfazl.nmock.utils.showSnackBar
import me.abolfazl.nmock.view.mockDetail.MockDetailBottomSheetDialogFragment
import me.abolfazl.nmock.view.mockService.NMockService
import me.abolfazl.nmock.view.mockSpeed.MockSpeedBottomSheetDialogFragment
import org.neshan.mapsdk.model.Marker
import org.neshan.mapsdk.model.Polyline

@AndroidEntryPoint
class MockPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMockPlayerBinding
    private val viewModel: MockPlayerViewModel by viewModels()

    //Layers
    private val markerLayer = ArrayList<Marker>()
    private val polylineLayer = ArrayList<Polyline>()

    private var serviceIsRunning = false
    private var nMockBinder: NMockService.NMockBinder? = null
    private var nMockService: NMockService? = null

    companion object {
        const val KEY_MOCK_ID_PLAYER = "MOCK_PLAYER_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMockPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startService(Intent(this, NMockService::class.java))

        initViewsFromBundle()

        initObservers()

        initListeners()
    }

    private fun initViewsFromBundle() {
        val mockId = intent.getLongExtra(KEY_MOCK_ID_PLAYER, -1)
        if (mockId == -1L) {
            this.finish()
            return
        }
        showProgressbar(true)
        viewModel.getMockInformation(mockId)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(component: ComponentName?, binder: IBinder?) {
            serviceIsRunning = true
            nMockBinder = binder as NMockService.NMockBinder
            nMockBinder?.setMockSpeed(viewModel.mockPlayerState.value.mockInformation?.speed!!)
            nMockBinder?.setLineVectorForProcessing(viewModel.mockPlayerState.value.mockInformation?.lineVector!![0])
            nMockService = nMockBinder?.getService()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            serviceIsRunning = false
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
        serviceIsRunning = false
    }

    private fun initObservers() {
        lifecycleScope.launch {
            viewModel.mockPlayerState.collect { state ->

                state.mockInformation?.let { mockInformation ->
                    Intent(this@MockPlayerActivity, NMockService::class.java).also { intent ->
                        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                    }
                    showProgressbar(false)
                    binding.titleTextView.text = mockInformation.mockName
                    binding.originTextView.text =
                        locationInformationFormat(mockInformation.originAddress, true)
                    binding.destinationTextView.text =
                        locationInformationFormat(mockInformation.destinationAddress, false)
                    LineManager.drawLineOnMap(
                        mapView = binding.mapview,
                        polylineLayer = polylineLayer,
                        vector = mockInformation.lineVector!!
                    )
                    val originMarker = MarkerManager.createMarker(
                        location = mockInformation.originLocation,
                        drawableRes = R.drawable.ic_origin_marker,
                        elementId = MarkerManager.ELEMENT_ID_ORIGIN_MARKER,
                        context = this@MockPlayerActivity
                    )
                    val destinationMarker = MarkerManager.createMarker(
                        location = mockInformation.destinationLocation,
                        drawableRes = R.drawable.ic_destination_marker,
                        elementId = MarkerManager.ELEMENT_ID_DESTINATION_MARKER,
                        context = this@MockPlayerActivity
                    )
                    if (originMarker != null && destinationMarker != null) {
                        markerLayer.add(originMarker)
                        markerLayer.add(destinationMarker)
                        binding.mapview.addMarker(originMarker)
                        binding.mapview.addMarker(destinationMarker)
                    }
                    LineManager.drawLineOnMap(
                        mapView = binding.mapview,
                        polylineLayer = polylineLayer,
                        vector = mockInformation.lineVector
                    )
                    CameraManager.moveCameraToTripLine(
                        mapView = binding.mapview,
                        screenPos = ScreenPos(binding.root.x, binding.root.y),
                        origin = mockInformation.originLocation,
                        destination = mockInformation.destinationLocation
                    )
                }

            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.oneTimeEmitter.collect { response ->
                    val message = when (response.exception) {
                        EXCEPTION_INSERTION_ERROR -> getString(R.string.databaseInsertionException)
                        EXCEPTION_DATABASE_GETTING_ERROR -> getString(R.string.databaseGettingException)
                        else -> getString(R.string.unknownException)
                    }

                    showSnackBar(
                        message = message,
                        rootView = findViewById(R.id.mockPlayerRootView),
                        Snackbar.LENGTH_SHORT
                    )
                }
            }
        }
    }

    private fun initListeners() {
        binding.backImageView.setOnClickListener {
            // todo: check service is running and if, show a dialog to user
            this.finish()
        }

        binding.detailImageView.setOnClickListener {
            val detailDialog = MockDetailBottomSheetDialogFragment.newInstance(
                title = viewModel.mockPlayerState.value.mockInformation?.mockName!!,
                description = viewModel.mockPlayerState.value.mockInformation?.mockDescription!!,
                provider = viewModel.mockPlayerState.value.mockInformation?.provider!!,
                type = viewModel.mockPlayerState.value.mockInformation?.mockType!!,
                createdAt = viewModel.mockPlayerState.value.mockInformation?.createdAt!!,
                updatedAt = viewModel.mockPlayerState.value.mockInformation?.updatedAt!!
            )
            detailDialog.isCancelable = true
            detailDialog.show(supportFragmentManager.beginTransaction(), null)
        }

        binding.playPauseFloatingActionButton.setOnClickListener {
            // todo: check the state of player first!
            // todo: change the state of player in second
        }
        binding.stopFloatingActionButton.setOnClickListener {
            // todo: stop the service and close it!
        }
        binding.speedFloatingActionButton.setOnClickListener {
            val speedDialog = MockSpeedBottomSheetDialogFragment.newInstance(
                viewModel.mockPlayerState.value.mockInformation?.speed!!
            )
            speedDialog.isCancelable = false
            speedDialog.setOnSaveClickListener { newSpeed ->
                // todo: change the speed of player in service
                viewModel.changeMockSpeed(newSpeed)
                speedDialog.dismiss()
            }
            speedDialog.show(supportFragmentManager.beginTransaction(), null)
        }

        binding.shareMaterialButton.setOnClickListener {
            val uri = UriManager.createShareUri(
                origin = viewModel.mockPlayerState.value.mockInformation?.originLocation!!,
                destination = viewModel.mockPlayerState.value.mockInformation?.destinationLocation!!,
                speed = viewModel.mockPlayerState.value.mockInformation?.speed!!
            )
            startActivity(
                Intent(Intent.ACTION_SEND, uri).apply {
                    putExtra(Intent.EXTRA_TEXT, uri.toString())
                    type = "text/plain"
                }
            )
        }

        binding.navigateMaterialButton.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    UriManager.createNavigationUri(viewModel.mockPlayerState.value.mockInformation?.destinationLocation!!)
                )
            )
        }
    }

    private fun showProgressbar(show: Boolean) {
        binding.loadingProgressbar.visibility = if (show) View.VISIBLE else View.GONE
        binding.titleTextView.visibility = if (!show) View.VISIBLE else View.GONE
    }

    private fun locationInformationFormat(
        address: String,
        isOrigin: Boolean
    ): String {
        val suffix = if (isOrigin) "From:" else "To:"
        return "$suffix $address"
    }

}