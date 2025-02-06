package com.Saujyun.dollyzoom

import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Bundle
import android.view.TextureView
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.MeteringRectangle
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * desc:
 * Created by Auntieli on 2025/1/24
 * Copyright (c) 2025 TENCENT. All rights reserved.
 */
class CameraRecordActivity : AppCompatActivity() {
    private lateinit var textureView: TextureView
    private lateinit var recordButton: Button
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var focusView: FocusView
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var cameraViewModel: CameraViewModel

    private var isRecording = false
    private var videoFile: File? = null
    private val TAG = "CameraRecordActivity"
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var sensorInfoTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_record)

        textureView = findViewById(R.id.texture_view)
        recordButton = findViewById(R.id.record_button)
        focusView = FocusView(this)
        zoomSeekBar = findViewById(R.id.zoom_seekbar)
        cameraViewModel = ViewModelProvider(this, CameraViewModelFactory(applicationContext)).get(CameraViewModel::class.java)

        val frameLayout = findViewById<FrameLayout>(R.id.camera_frame_layout)
        frameLayout.addView(focusView)

        if (checkPermissions()) {
            setupCamera()
            setupMediaRecorder()
        }

        setupRecordButton()
        // 获取 TextView 的引用
        sensorInfoTextView = findViewById(R.id.sensor_info_text)
        // 监听 SeekBar 的变化
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 更新 ViewModel 中的焦距
                val zoomLevel =
                    1.0f + (progress / 100.0f) * (10.0f - 1.0f) // Assuming 1x to 10x zoom
                cameraViewModel.adjustZoomBasedOnMovement(zoomLevel)

                // 确保 captureRequestBuilder 和 cameraCaptureSession 已初始化
                if (::captureRequestBuilder.isInitialized && ::cameraCaptureSession.isInitialized) {
                    cameraViewModel.applyZoom(captureRequestBuilder, cameraCaptureSession)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        motionTracker = MotionTracker(this)
        // 定期更新UI显示位置信息
        lifecycleScope.launch {
               while (true){
                   updateMotionInfo()
                   delay(10) // 每100ms更新一次
               }
        }
    }
    private lateinit var motionTracker: MotionTracker
    private fun updateMotionInfo() {
        val motion = motionTracker.getCurrentMotion()
        sensorInfoTextView.text = """
            位置: 
            X: %.3f m
            Y: %.3f m
            Z: %.3f m
            
            速度:
            X: %.3f m/s
            Y: %.3f m/s
            Z: %.3f m/s
            
            方向:
            X: %.2f°
            Y: %.2f°
            Z: %.2f°
        """.trimIndent().format(
            motion.position.x, motion.position.y, motion.position.z,
            motion.velocity.x, motion.velocity.y, motion.velocity.z,
            Math.toDegrees(motion.orientation.x.toDouble()),
            Math.toDegrees(motion.orientation.y.toDouble()),
            Math.toDegrees(motion.orientation.z.toDouble())
        )
    }


    override fun onResume() {
        super.onResume()
        motionTracker.start()
    }

    override fun onPause() {
        super.onPause()
        motionTracker.stop()
    }



    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                notGrantedPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
            return false
        }
        return true
    }

    private fun setupCamera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0] // 使用默认后置摄像头

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        cameraViewModel.setCameraId(cameraId) // 设置相机 ID
                        createCameraPreviewSession()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        finish()
                    }
                }, null)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access error: ${e.message}")
        }
    }

    private fun setupMediaRecorder() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // 设置视频编码参数
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(30)
            setVideoSize(1920, 1080)

            // 设置音频编码参数
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)

            // 设置输出方向
            setOrientationHint(90)

            // 错误监听
            setOnErrorListener { mr, what, extra ->
                Log.e(TAG, "MediaRecorder Error: $what, $extra")
                handleRecordingError("录制错误: $what")
            }
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(1920, 1080)

            val previewSurface = Surface(texture)
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(previewSurface)

            val surfaces = mutableListOf(previewSurface)

            if (isRecording) {
                surfaces.add(mediaRecorder.surface)
            }

            cameraDevice.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    updatePreview()

                    // 设置初始对焦区域
                    setFocusArea(textureView.width / 2, textureView.height / 2)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Failed to configure camera session")
                }
            }, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error creating preview session: ${e.message}")
        }
    }

    private fun setFocusArea(x: Int, y: Int) {
        val focusRect = calculateFocusRect(x, y)
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX)))
        cameraCaptureSession.capture(captureRequestBuilder.build(), null, null)
        focusView.setFocusArea(focusRect)
    }

    private fun calculateFocusRect(x: Int, y: Int): Rect {
        val halfTouchWidth = 100
        val halfTouchHeight = 100
        val rect = Rect(
            (x - halfTouchWidth).coerceIn(0, textureView.width - 1),
            (y - halfTouchHeight).coerceIn(0, textureView.height - 1),
            (x + halfTouchWidth).coerceIn(0, textureView.width - 1),
            (y + halfTouchHeight).coerceIn(0, textureView.height - 1)
        )
        return rect
    }

    private fun updatePreview() {
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            cameraCaptureSession.setRepeatingRequest(
                captureRequestBuilder.build(),
                null,
                null
            )

            // 设置初始对焦区域
            setFocusArea(textureView.width / 2, textureView.height / 2)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error updating preview: ${e.message}")
        }
    }

    private fun setupRecordButton() {
        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    private fun startRecording() {
        videoFile = createVideoFile()
        videoFile?.let { file ->
            try {
                mediaRecorder.setOutputFile(file.absolutePath)
                mediaRecorder.prepare()

                // 重新创建录制会话
                createRecordingSession()

                mediaRecorder.start()
                isRecording = true
                recordButton.text = "停止录制"
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording: ${e.message}")
                handleRecordingError("启动录制失败")
            }
        }
    }

    private fun createRecordingSession() {
        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(1920, 1080)

            val previewSurface = Surface(texture)
            val recorderSurface = mediaRecorder.surface

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            captureRequestBuilder.addTarget(previewSurface)
            captureRequestBuilder.addTarget(recorderSurface)

            cameraDevice.createCaptureSession(
                listOf(previewSurface, recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure recording session")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error creating recording session: ${e.message}")
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder.stop()
            mediaRecorder.reset()
            isRecording = false
            recordButton.text = "开始录制"

            // 保存视频到相册
            scope.launch(Dispatchers.IO) {
                videoFile?.let { saveToGallery(it) }
            }

            // 重新创建预览会话
            createCameraPreviewSession()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
            handleRecordingError("停止录制失败")
        }
    }

    private fun createVideoFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        val fileName = "VIDEO_${timestamp}.mp4"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), fileName)
        }
    }

    private suspend fun saveToGallery(videoFile: File) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                } else {
                    put(MediaStore.Video.Media.DATA, videoFile.absolutePath)
                }
            }

            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    videoFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@CameraRecordActivity, "视频已保存", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to gallery: ${e.message}")
            withContext(Dispatchers.Main) {
                handleRecordingError("保存视频失败")
            }
        }
    }

    private fun handleRecordingError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (isRecording) {
            isRecording = false
            recordButton.text = "开始录制"
            mediaRecorder.reset()
            createCameraPreviewSession()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            try {
                mediaRecorder.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording on destroy: ${e.message}")
            }
        }
        mediaRecorder.release()
        // 注销传感器监听器
        scope.cancel()
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
    }
}

// 对焦框的自定义视图
class FocusView(context: Context) : View(context) {
    private val paint: Paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private var focusRect: Rect? = null

    fun setFocusArea(area: Rect) {
        focusRect = area
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        focusRect?.let {
            canvas.drawRect(it, paint)
        }
    }
}