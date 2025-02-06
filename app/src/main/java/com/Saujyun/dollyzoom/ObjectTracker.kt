package com.Saujyun.dollyzoom

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * desc:
 * Created by Auntieli on 2025/2/6
 * Copyright (c) 2025 TENCENT. All rights reserved.
 */
class ObjectTracker(private val context: Context) {
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    )

    private var initialObjectSize: Float? = null
    private var targetObject: DetectedObject? = null

    // 跟踪目标物体的大小变化
    fun trackObject(image: InputImage, onResult: (TrackingResult) -> Unit) {
        objectDetector.process(image)
            .addOnSuccessListener { detectedObjects ->
                // 找到最接近中心的物体作为目标
                val centerObject = findCenterObject(detectedObjects, image.width, image.height)

                centerObject?.let { detected ->
                    // 计算物体的大小（使用边界框面积）
                    val currentSize = calculateObjectSize(detected.boundingBox)

                    // 如果是首次检测到物体，记录初始大小
                    if (initialObjectSize == null) {
                        initialObjectSize = currentSize
                        targetObject = detected
                    }

                    // 计算大小变化比例
                    val sizeRatio = initialObjectSize?.let { initial ->
                        currentSize / initial
                    } ?: 1.0f

                    onResult(TrackingResult(
                        objectDetected = true,
                        sizeRatio = sizeRatio,
                        boundingBox = detected.boundingBox
                    ))
                } ?: onResult(TrackingResult(objectDetected = false))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Object detection failed: ${e.message}")
            }
    }

    private fun findCenterObject(
        detectedObjects: List<DetectedObject>,
        imageWidth: Int,
        imageHeight: Int
    ): DetectedObject? {
        val centerX = (imageWidth / 2).toDouble()
        val centerY = (imageHeight / 2).toDouble()

        return detectedObjects.minByOrNull { detected ->
            val box = detected.boundingBox
            val objectCenterX = box.centerX()
            val objectCenterY = box.centerY()

            // 计算到图像中心的距离
            sqrt(
                (centerX - objectCenterX).pow(2) +
                        (centerY - objectCenterY).pow(2)
            )
        }
    }



    private fun calculateObjectSize(boundingBox: Rect): Float {
        return (boundingBox.width() * boundingBox.height()).toFloat()
    }

    data class TrackingResult(
        val objectDetected: Boolean,
        val sizeRatio: Float = 1.0f,
        val boundingBox: Rect? = null
    )

    companion object {
        private const val TAG = "ObjectTracker"
    }
}