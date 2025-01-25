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

class CameraViewModel(context: Context) : ViewModel() {
    private val _zoomLevel = MutableLiveData<Float>()
    val zoomLevel: LiveData<Float> get() = _zoomLevel

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private lateinit var cameraCharacteristics: CameraCharacteristics

    fun setCameraId(cameraId: String) {
        cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
    }

    fun setZoomLevel(zoom: Float) {
        _zoomLevel.value = zoom
    }

    fun applyZoom(captureRequestBuilder: CaptureRequest.Builder, cameraCaptureSession: CameraCaptureSession) {
        try {
            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, calculateZoomRect(zoomLevel.value ?: 1.0f))
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun calculateZoomRect(zoom: Float): Rect {
        val maxZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
        val sensorArraySize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return Rect()

        val zoomFactor = if (zoom < 1.0f) 1.0f else if (zoom > maxZoom) maxZoom else zoom

        val cropWidth = (sensorArraySize.width() / zoomFactor).toInt()
        val cropHeight = (sensorArraySize.height() / zoomFactor).toInt()
        val cropLeft = (sensorArraySize.width() - cropWidth) / 2
        val cropTop = (sensorArraySize.height() - cropHeight) / 2

        return Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
    }
}