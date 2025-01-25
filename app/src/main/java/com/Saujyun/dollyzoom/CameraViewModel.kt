package com.Saujyun.dollyzoom

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

import kotlinx.coroutines.*

class CameraViewModel(context: Context) : ViewModel() {
    private val _zoomLevel = MutableLiveData<Float>().apply { value = 1.0f }
    val zoomLevel: LiveData<Float> get() = _zoomLevel

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private lateinit var cameraCharacteristics: CameraCharacteristics
    var maxZoom: Float = 1.0f
    private var zoomJob: Job? = null

    fun setCameraId(cameraId: String) {
        cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        maxZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
    }

    fun adjustZoomBasedOnMovement(movement: Float) {
        val targetZoom = (1.0f + movement / 10.0f).coerceIn(1.0f, maxZoom)
        smoothZoomTo(targetZoom)
    }

    private fun smoothZoomTo(targetZoom: Float) {
        zoomJob?.cancel()
        zoomJob = CoroutineScope(Dispatchers.Main).launch {
            val startZoom = _zoomLevel.value ?: 1.0f
            val steps = 20
            val stepZoom = (targetZoom - startZoom) / steps
            for (i in 0..steps) {
                _zoomLevel.value = startZoom + stepZoom * i
                delay(10) // 每步之间的延迟，确保平滑过渡
            }
        }
    }

    fun applyZoom(captureRequestBuilder: CaptureRequest.Builder, cameraCaptureSession: CameraCaptureSession) {
        try {
            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, calculateZoomRect(_zoomLevel.value ?: 1.0f))
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun calculateZoomRect(zoom: Float): Rect {
        val sensorArraySize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return Rect()

        val zoomFactor = if (zoom < 1.0f) 1.0f else if (zoom > maxZoom) maxZoom else zoom

        val cropWidth = (sensorArraySize.width() / zoomFactor).toInt()
        val cropHeight = (sensorArraySize.height() / zoomFactor).toInt()
        val cropLeft = (sensorArraySize.width() - cropWidth) / 2
        val cropTop = (sensorArraySize.height() - cropHeight) / 2

        return Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
    }
}