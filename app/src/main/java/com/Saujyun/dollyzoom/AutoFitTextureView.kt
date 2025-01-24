package com.Saujyun.dollyzoom

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

/**
 * desc:
 * Created by Auntieli on 2025/1/24
 * Copyright (c) 2025 TENCENT. All rights reserved.
 */

// AutoFitTextureView.kt - 自适应纵横比的TextureView
class AutoFitTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextureView(context, attrs, defStyle) {

    private var ratioWidth = 0
    private var ratioHeight = 0

    fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Size cannot be negative." }
        ratioWidth = width
        ratioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        if (ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, height)
        } else {
            // 计算实际显示尺寸，保持比例
            val ratio = ratioWidth.toFloat() / ratioHeight
            val newWidth: Int
            val newHeight: Int

            if (width < height * ratio) {
                newWidth = width
                newHeight = (width / ratio).toInt()
            } else {
                newWidth = (height * ratio).toInt()
                newHeight = height
            }

            setMeasuredDimension(newWidth, newHeight)
        }
    }
}