package com.Saujyun.dollyzoom
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.hardware.camera2.*
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class DollyZoomActivity : AppCompatActivity() {
    private lateinit var textureView: AutoFitTextureView
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private var initialZoom = 1.0f
    private var initialFocalLength: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dolly_zoom)

        textureView = findViewById(R.id.texture_view)
        setupCamera()
        setupTouchListener()
    }

    private fun setupCamera() {
        if (checkCameraPermission()) {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0] // 使用主摄像头

            // 打开相机
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, null)
        }
    }

    private fun createCameraPreviewSession() {
        val surface = Surface(textureView.surfaceTexture)

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)

        cameraDevice.createCaptureSession(listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    updatePreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    // 处理配置失败
                }
            }, null)
    }

    private fun setupTouchListener() {
        var startY = 0f

        textureView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = (event.y - startY) / textureView.height
                    updateDollyZoom(deltaY)
                    true
                }
                else -> false
            }
        }
    }

    // 添加防抖动处理
    private val zoomUpdateDebouncer = object {
        private var lastUpdate = 0L
        private val minUpdateInterval = 16L // 约60fps

        fun shouldUpdate(): Boolean {
            val current = System.currentTimeMillis()
            if (current - lastUpdate >= minUpdateInterval) {
                lastUpdate = current
                return true
            }
            return false
        }
    }

    private fun updateDollyZoom(delta: Float) {
        if (!zoomUpdateDebouncer.shouldUpdate()){
            return
        }
        // 计算新的变焦值
        val newZoom = (initialZoom * (1 + delta)).coerceIn(1f, 5f)

        // 更新相机参数
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, newZoom)
        }

        // 计算相机位置补偿
        val compensation = 1f / newZoom
        updateCameraPosition(compensation)

        // 更新预览
        updatePreview()
    }

    private fun updateCameraPosition(compensation: Float) {
        // 在实际设备中，这里需要控制相机的物理移动
        // 这里我们通过修改预览变换来模拟
        val matrix = Matrix()
        matrix.setScale(compensation, compensation, textureView.width / 2f, textureView.height / 2f)
        textureView.setTransform(matrix)
    }

    private fun updatePreview() {
        cameraCaptureSession.setRepeatingRequest(
            captureRequestBuilder.build(),
            null,
            null
        )
    }

    private fun checkCameraPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
            return false
        }
        return true
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }
}
