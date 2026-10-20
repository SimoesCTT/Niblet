package com.niblet.app.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import com.niblet.app.data.SettingsStore
import kotlin.math.abs

class ReaderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    var onTapLeft: (() -> Unit)? = null
    var onTapRight: (() -> Unit)? = null
    var direction: SettingsStore.Direction = SettingsStore.Direction.LTR
    var fit: SettingsStore.Fit = SettingsStore.Fit.PAGE
    var panelMode: Boolean = false
    var onPanelChanged: ((Int, Int) -> Unit)? = null

    private val matrix = Matrix()
    private var lastX = 0f; private var lastY = 0f; private var dragging = false
    private var currentBmp: Bitmap? = null
    private var panels: List<PanelDetector.Panel> = emptyList()
    private var panelIndex = 0

    private val scale = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val f = d.scaleFactor.coerceIn(0.5f, 6f)
                matrix.postScale(f, f, d.focusX, d.focusY)
                imageMatrix = matrix
                return true
            }
        })

    private val tap = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (panelMode && panels.size > 1) {
                    if (panelIndex < panels.size - 1) {
                        panelIndex++
                        applyPanel()
                        onPanelChanged?.invoke(panelIndex + 1, panels.size)
                    } else {
                        onTapRight?.invoke()
                    }
                    return true
                }
                val third = width / 3f
                if (e.x < third) {
                    if (direction == SettingsStore.Direction.RTL) onTapRight?.invoke()
                    else onTapLeft?.invoke()
                } else if (e.x > 2 * third) {
                    if (direction == SettingsStore.Direction.RTL) onTapLeft?.invoke()
                    else onTapRight?.invoke()
                }
                return true
            }
        })

    init { scaleType = ScaleType.MATRIX }

    fun show(bmp: Bitmap) {
        currentBmp = bmp
        panels = if (panelMode) PanelDetector.detect(bmp) else emptyList()
        panelIndex = 0
        setImageBitmap(bmp)
        matrix.reset()
        post {
            if (panelMode && panels.size > 1) applyPanel() else applyFit()
            if (panelMode) onPanelChanged?.invoke(panelIndex + 1, panels.size)
        }
    }

    private fun applyFit() {
        val d = drawable ?: return
        if (d.intrinsicWidth == 0) return
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()
        val s = when (fit) {
            SettingsStore.Fit.WIDTH -> vw / iw
            SettingsStore.Fit.HEIGHT -> vh / ih
            SettingsStore.Fit.PAGE -> minOf(vw / iw, vh / ih)
        }
        matrix.setScale(s, s)
        matrix.postTranslate((vw - iw * s) / 2f, (vh - ih * s) / 2f)
        imageMatrix = matrix
    }

    private fun applyPanel() {
        val d = drawable ?: return
        val p = panels.getOrNull(panelIndex) ?: return
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (iw <= 0f || ih <= 0f) return
        val pw = p.rect.width().toFloat().coerceAtLeast(1f)
        val ph = p.rect.height().toFloat().coerceAtLeast(1f)
        val s = minOf(width.toFloat() / pw, height.toFloat() / ph)
        val cx = p.rect.exactCenterX()
        val cy = p.rect.exactCenterY()
        matrix.reset()
        matrix.setScale(s, s)
        matrix.postTranslate(width / 2f - cx * s, height / 2f - cy * s)
        imageMatrix = matrix
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scale.onTouchEvent(ev); tap.onTouchEvent(ev)
        if (!scale.isInProgress) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = ev.x; lastY = ev.y; dragging = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - lastX; val dy = ev.y - lastY
                    if (abs(dx) + abs(dy) > 8f) dragging = true
                    if (dragging) {
                        matrix.postTranslate(dx, dy); imageMatrix = matrix
                        lastX = ev.x; lastY = ev.y
                    }
                }
            }
        }
        return true
    }
}
