package com.Saujyun.dollyzoom
// DollyZoomActivity.kt
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class DollyZoomActivity : AppCompatActivity() {
    private lateinit var textureView: AutoFitTextureView
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var recordButton: Button
    private val TAG = "DollyZoomActivity"

    private var isRecording = false
    private var videoFile: File? = null
    private var previewSize: Size? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    // 添加状态追踪
    private var recordingStartTime: Long = 0L
    private var isPrepared = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dolly_zoom)

        textureView = findViewById(R.id.texture_view)
        recordButton = findViewById(R.id.record_button)

        if (checkPermissions()) {
            setupCamera()
            setupMediaRecorder()
        }

        setupRecordButton()
        setupTouchListener()
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        // 只在 Android 9 (API 28) 及以下版本请求存储权限
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

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
        val cameraId = cameraManager.cameraIdList[0]

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        // 获取相机支持的预览尺寸
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        // 获取设备方向
        val rotation = windowManager.defaultDisplay.rotation

        // 选择最合适的预览尺寸
        previewSize = chooseOptimalSize(
            map?.getOutputSizes(SurfaceTexture::class.java) ?: arrayOf(),
            rotation,
            windowManager.defaultDisplay.width,
            windowManager.defaultDisplay.height
        )

        // 根据预览尺寸设置TextureView的宽高比
        previewSize?.let { size ->
            // 根据设备方向调整宽高比
            if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                textureView.setAspectRatio(size.height, size.width)
            } else {
                textureView.setAspectRatio(size.width, size.height)
            }
        }


            if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
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

    private fun chooseOptimalSize(
        choices: Array<Size>,
        rotation: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Size {
        // 确保宽度和高度正确对应设备方向
        val targetWidth = if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            screenHeight
        } else {
            screenWidth
        }
        val targetHeight = if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            screenWidth
        } else {
            screenHeight
        }

        // 寻找最接近屏幕宽高比的预览尺寸
        return choices
            .filter { it.height <= targetHeight && it.width <= targetWidth }
            .minByOrNull {
                Math.abs(it.width.toFloat() / it.height - targetWidth.toFloat() / targetHeight)
            } ?: choices[0]
    }

    private fun setupMediaRecorder() {
        mediaRecorder = MediaRecorder().apply {
            // 注意设置顺序，这个顺序很重要
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)

            // 设置输出格式必须在设置编码器之前
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // 设置视频编码参数
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(4000000) // 4Mbps
            setVideoFrameRate(30)

            // 确保分辨率是16的倍数，这对某些编码器很重要
            val width = (previewSize?.width ?: 1280) / 16 * 16
            val height = (previewSize?.height ?: 720) / 16 * 16
            setVideoSize(width, height)

            // 音频编码参数
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)

            // 设置最大文件大小和时长限制
            setMaxFileSize(1024 * 1024 * 500) // 500MB
            setMaxDuration(30 * 60 * 1000)    // 30分钟

            // 设置方向
            setOrientationHint(getVideoOrientation())

            // 设置错误监听
            setOnErrorListener { mr, what, extra ->
                Log.e(TAG, "Error: $what, $extra")
                scope.launch(Dispatchers.Main)  {
                    handleRecorderError(what, extra)
                }
            }
        }
    }


    private fun handleRecorderError(what: Int, extra: Int) {
        val errorMessage = when (what) {
            MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN -> "未知错误"
            MediaRecorder.MEDIA_ERROR_SERVER_DIED -> "服务异常"
            else -> "录制错误: $what"
        }
        handleRecordingError(errorMessage)
    }

    private fun getVideoOrientation(): Int {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]
        val rotation = windowManager.defaultDisplay.rotation
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        return when (rotation) {
            Surface.ROTATION_0 -> sensorOrientation
            Surface.ROTATION_90 -> (sensorOrientation + 270) % 360
            Surface.ROTATION_180 -> (sensorOrientation + 180) % 360
            Surface.ROTATION_270 -> (sensorOrientation + 90) % 360
            else -> sensorOrientation
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(previewSize?.width ?: 1920, previewSize?.height ?: 1080)

            val previewSurface = Surface(texture)
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(previewSurface)

            val surfaces = mutableListOf(previewSurface)
            if (isRecording) {
                surfaces.add(mediaRecorder.surface)
            }

            cameraDevice.createCaptureSession(surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        // Handle configuration failure
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG,"error=$e")
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
            Log.i(TAG,"[startRecording] videoFile=${file.absolutePath}")
            try {
                mediaRecorder.setOutputFile(file.absolutePath)
                mediaRecorder.prepare()
                mediaRecorder.start()
                recordingStartTime = System.currentTimeMillis()
                isRecording = true
                isPrepared = true
                recordButton.text = "停止录制"
            } catch (e: Exception) {
                Log.e(TAG,"error=$e")
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        try {
            // 检查录制时长
            val recordingDuration = System.currentTimeMillis() - recordingStartTime
            if (recordingDuration < 1000) {
                showToast("录制时间太短")
                cleanupRecording()
                return
            }

            if (isPrepared) {
                try {
                    mediaRecorder.setOnErrorListener(null)
                    mediaRecorder.setOnInfoListener(null)
                    mediaRecorder.setPreviewDisplay(null)
                    mediaRecorder.stop()
                } catch (e: RuntimeException) {
                    Log.e(TAG,"error=$e")
                    cleanupRecording()
                    return
                }
            }

            mediaRecorder.reset()
            isPrepared = false
            isRecording = false
            recordButton.text = "开始录制"

            // 在后台线程保存视频
            scope.launch(Dispatchers.IO) {
                try {
                    videoFile?.let { file ->
                        if (file.exists() && file.length() > 0) {
                            saveToGallery(file)
                            withContext(Dispatchers.Main) {
                                showToast("视频已保存")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                showToast("录制失败")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG,"error=$e")
                    withContext(Dispatchers.Main) {
                        handleRecordingError("保存视频失败")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG,"error=$e")
            handleRecordingError("停止录制失败")
        } finally {
            setupMediaRecorder()
        }
    }

    private fun handleRecordingError(message: String) {
        runOnUiThread {
            showToast(message)
            cleanupRecording()
        }
    }

    private fun cleanupRecording() {
        try {
            if (isPrepared) {
                mediaRecorder.reset()
            }
            isPrepared = false
            isRecording = false
            recordButton.text = "开始录制"

            // 删除可能存在的不完整文件
            videoFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
            }

            // 重新初始化MediaRecorder
            setupMediaRecorder()
        } catch (e: Exception) {
            Log.e(TAG,"error=$e")
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
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                showToast("保存到相册失败: ${e.localizedMessage}")
            }
            throw e
        }
    }

    private fun showToast(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else {
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
        scope.cancel() // 取消所有协程
        try {
            mediaRecorder.release()
        } catch (e: Exception) {
            Log.e(TAG,"error=$e")
        }
    }

    private fun createVideoFile(): File {
//        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
//        val fileName = "VIDEO_${timestamp}.mp4"
//
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            // Android 10 及以上版本使用应用专属目录
//            File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)
//        } else {
//            // Android 9 及以下版本可以使用公共目录
//            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), fileName)
//        }
        val saveDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "CameraRecorder"
        ).apply {
            // 确保目录存在且可写
            if (!exists() && !mkdirs()) {
                throw IOException("Failed to create directory")
            }
            // 检查可用空间
            if (freeSpace < 10 * 1024 * 1024) { // 至少10MB可用空间
                throw IOException("Insufficient storage space")
            }
        }
        saveDirectory.mkdirs()
        val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        val fileName = simpleDateFormat.format(Date(System.currentTimeMillis())) + ".mp4"
        val outputFile = File(saveDirectory, fileName)
        return outputFile


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

    private var initialZoom = 1.0f
    private fun updateDollyZoom(delta: Float) {
        // 计算新的变焦值
        val newZoom = (initialZoom * (1 + delta)).coerceIn(1f, 5f)

        // 更新相机参数
        captureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, newZoom)

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
        try {
            cameraCaptureSession.setRepeatingRequest(
                captureRequestBuilder.build(),
                null,
                null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG,"error=$e")
        }
    }


    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
    }
}