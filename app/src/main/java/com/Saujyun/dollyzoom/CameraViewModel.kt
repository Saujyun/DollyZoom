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
import com.google.mlkit.vision.common.InputImage

import kotlinx.coroutines.*

class CameraViewModel(context: Context) : ViewModel() {
    private val ZOOM_DEFAULT = 3.0f
    private val _zoomLevel = MutableLiveData<Float>().apply { value = ZOOM_DEFAULT }
    val zoomLevel: LiveData<Float> get() = _zoomLevel

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private lateinit var cameraCharacteristics: CameraCharacteristics
    var maxZoom: Float = 1.0f
    private var zoomJob: Job? = null
    private val objectTracker = ObjectTracker(context)

    fun processFrame(image: InputImage) {
        objectTracker.trackObject(image) { result ->
            if (result.objectDetected) {
                // 根据物体大小比例调整焦距
                adjustZoomBasedOnObjectSize(result.sizeRatio)
            }
        }
    }

    private fun adjustZoomBasedOnObjectSize(sizeRatio: Float) {
        // 计算目标焦距
        // 当物体变大时（相机靠近），减小焦距
        // 当物体变小时（相机远离），增加焦距
        val targetZoom = (1.0f / sizeRatio).coerceIn(1.0f, maxZoom)
        smoothZoomTo(targetZoom)
    }

    fun setCameraId(cameraId: String) {
        cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        maxZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
    }

    fun adjustZoomBasedOnMovement(movement: Float) {
        val targetZoom = (ZOOM_DEFAULT - movement * 10.0f).coerceIn(1.0f, maxZoom)
        if (targetZoom - (_zoomLevel.value ?: 1.0f) < 0.1) {
            return
        }
        smoothZoomTo(targetZoom)
    }

    private fun smoothZoomTo(targetZoom: Float) {
        zoomJob?.cancel()
        zoomJob = CoroutineScope(Dispatchers.Main).launch {
            val startZoom = _zoomLevel.value ?: ZOOM_DEFAULT
            val steps = 20
            val stepZoom = (targetZoom - startZoom) / steps
            for (i in 0..steps) {
                _zoomLevel.value = startZoom + stepZoom * i
//                delay(10) // 每步之间的延迟，确保平滑过渡
            }
        }
    }

    fun applyZoom(captureRequestBuilder: CaptureRequest.Builder, cameraCaptureSession: CameraCaptureSession) {
        try {
            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, calculateZoomRect(_zoomLevel.value ?: ZOOM_DEFAULT))
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun calculateZoomRect(zoom: Float): Rect {
        val sensorArraySize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return Rect()

        val zoomFactor = if (zoom < 1.0f) ZOOM_DEFAULT else if (zoom > maxZoom) maxZoom else zoom

        val cropWidth = (sensorArraySize.width() / zoomFactor).toInt()
        val cropHeight = (sensorArraySize.height() / zoomFactor).toInt()
        val cropLeft = (sensorArraySize.width() - cropWidth) / 2
        val cropTop = (sensorArraySize.height() - cropHeight) / 2

        return Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
    }
}