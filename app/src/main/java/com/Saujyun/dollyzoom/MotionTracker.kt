package com.Saujyun.dollyzoom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * desc:
 * Created by Auntieli on 2025/1/26
 * Copyright (c) 2025 TENCENT. All rights reserved.
 */
class MotionTracker(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // 传感器
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // 位置和运动状态
    data class MotionState(
        var position: Vector3D = Vector3D(),    // 位置
        var velocity: Vector3D = Vector3D(),     // 速度
        var orientation: Vector3D = Vector3D(),  // 方向
        var timestamp: Long = 0                  // 时间戳
    )

    private var currentState = MotionState()

    // 3D向量数据类
    data class Vector3D(
        var x: Float = 0f,
        var y: Float = 0f,
        var z: Float = 0f
    ) {
        operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
        operator fun times(scalar: Float) = Vector3D(x * scalar, y * scalar, z * scalar)
    }

    // 卡尔曼滤波器参数
    private val kalmanFilter = KalmanFilter()

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> processLinearAcceleration(event)
                Sensor.TYPE_GYROSCOPE -> processGyroscope(event)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        sensorManager.registerListener(sensorEventListener, linearAcceleration, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(sensorEventListener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(sensorEventListener)
    }

    private fun processLinearAcceleration(event: SensorEvent) {
        if (currentState.timestamp == 0L) {
            currentState.timestamp = event.timestamp
            return
        }

        // 计算时间增量（转换为秒）
        val dt = (event.timestamp - currentState.timestamp) * NS2S
        currentState.timestamp = event.timestamp

        // 获取线性加速度数据
        val linearAcc = Vector3D(event.values[0], event.values[1], event.values[2])

        // 应用卡尔曼滤波
        val filteredAcc = kalmanFilter.update(linearAcc)

        // 更新速度（第一次积分）
        currentState.velocity = currentState.velocity + filteredAcc * dt

        // 更新位置（第二次积分）
        currentState.position = currentState.position + currentState.velocity * dt

        // 应用阻尼以减少漂移
        applyDamping()
    }

    private fun processGyroscope(event: SensorEvent) {
        if (currentState.timestamp == 0L) {
            currentState.timestamp = event.timestamp
            return
        }

        // 计算时间增量
        val dt = (event.timestamp - currentState.timestamp) * NS2S

        // 更新方向
        currentState.orientation = Vector3D(
            currentState.orientation.x + event.values[0] * dt,
            currentState.orientation.y + event.values[1] * dt,
            currentState.orientation.z + event.values[2] * dt
        )
    }

    private fun applyDamping() {
        // 速度阻尼
        val dampingFactor = 0.95f
        currentState.velocity = currentState.velocity * dampingFactor

        // 位置阻尼（可选）
        if (abs(currentState.velocity.x) < VELOCITY_THRESHOLD &&
            abs(currentState.velocity.y) < VELOCITY_THRESHOLD &&
            abs(currentState.velocity.z) < VELOCITY_THRESHOLD) {
            currentState.velocity = Vector3D()
        }
    }

    // 卡尔曼滤波器实现
    private class KalmanFilter {
        private var estimate = Vector3D()
        private var uncertainty = 1.0f
        private val measurementUncertainty = 0.1f
        private val processNoise = 0.0001f

        fun update(measurement: Vector3D): Vector3D {
            // 预测步骤
            uncertainty += processNoise

            // 更新步骤
            val kalmanGain = uncertainty / (uncertainty + measurementUncertainty)
            estimate = Vector3D(
                estimate.x + kalmanGain * (measurement.x - estimate.x),
                estimate.y + kalmanGain * (measurement.y - estimate.y),
                estimate.z + kalmanGain * (measurement.z - estimate.z)
            )
            uncertainty *= (1 - kalmanGain)

            return estimate
        }
    }

    companion object {
        private const val NS2S = 1.0f / 1000000000.0f // 纳秒到秒的转换
        private const val VELOCITY_THRESHOLD = 0.01f // 速度阈值
    }

    // 获取当前运动状态
    fun getCurrentMotion(): MotionState = currentState

    // 重置运动跟踪
    fun reset() {
        currentState = MotionState()
    }
}