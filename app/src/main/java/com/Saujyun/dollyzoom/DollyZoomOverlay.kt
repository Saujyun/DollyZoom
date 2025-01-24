package com.Saujyun.dollyzoom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * desc:
 * Created by Auntieli on 2025/1/24
 * Copyright (c) 2025 TENCENT. All rights reserved.
 */
// DollyZoomOverlay.kt - 自定义视图用于显示辅助线和引导
class DollyZoomOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制中心标记
        val centerX = width / 2f
        val centerY = height / 2f
        val size = 100f

        canvas.drawRect(
            centerX - size,
            centerY - size,
            centerX + size,
            centerY + size,
            paint
        )

        // 绘制辅助线
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, paint)
        canvas.drawLine(centerX, 0f, centerX, height.toFloat(), paint)
    }
}