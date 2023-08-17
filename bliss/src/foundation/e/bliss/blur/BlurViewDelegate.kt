/*
 * Copyright © MURENA SAS 2023.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package foundation.e.bliss.blur

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.ScrollView
import com.android.launcher3.PagedView
import com.android.launcher3.R

class BlurViewDelegate(
    private val view: View,
    private val config: BlurWallpaperProvider.BlurConfig,
    attrs: AttributeSet? = null
) : View.OnAttachStateChangeListener, BlurWallpaperProvider.Listener {

    private val context = view.context
    private val blurWallpaperProvider by lazy { BlurWallpaperProvider.getInstanceNoCreate() }

    private var fullBlurDrawable: BlurDrawable? = null
    private var blurAlpha = 255

    private val blurDrawableCallback by lazy {
        object : Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {}

            override fun invalidateDrawable(who: Drawable) {
                view.post(view::invalidate)
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
        }
    }

    private var wallpaperScrollOffset = 0
    private var wallpaperOffset = 0

    private var offsetParents = listOf<OffsetParent>()
        set(value) {
            field.forEach { it.removeOnOffsetChangeListener(onOffsetChangeListener) }
            field = value
            field.forEach { it.addOnOffsetChangeListener(onOffsetChangeListener) }
        }
    private val scrollViews = mutableListOf<View>()
    private var isScrolling = false
    private var previousScrollX = 0
    private var previousScrollY = 0
    private var parentOffsetX = 0f
    private var parentOffsetY = 0f

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateBounds() }
    private val onScrollChangedListener =
        ViewTreeObserver.OnScrollChangedListener {
            isScrolling = true
            view.invalidate()
        }
    private val onOffsetChangeListener =
        object : OffsetParent.OnOffsetChangeListener {
            override fun onOffsetChange() {
                computeParentOffset()
            }
        }

    var blurCornerRadius = 0f

    val outlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, blurCornerRadius)
            }
        }

    var overlayColor: Int = 0
        set(value) {
            field = value
            overlayPaint.color = value
        }

    private val overlayPaint =
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = BlendMode.MULTIPLY
            }
        }

    init {
        createFullBlurDrawable()
        view.addOnAttachStateChangeListener(this)

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.BlurLayout)
            blurCornerRadius = a.getDimension(R.styleable.BlurLayout_blurCornerRadius, 0f)
            overlayColor = a.getColor(R.styleable.BlurLayout_blurOverlayColor, 0)
            a.recycle()
        }
    }

    override fun onViewAttachedToWindow(v: View) {
        BlurWallpaperProvider.getInstanceNoCreate().addListener(this)
        fullBlurDrawable?.startListening()
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        view.viewTreeObserver.addOnScrollChangedListener(onScrollChangedListener)
        onGlobalLayoutListener.onGlobalLayout()
    }

    override fun onViewDetachedFromWindow(v: View) {
        BlurWallpaperProvider.getInstanceNoCreate().removeListener(this)
        fullBlurDrawable?.stopListening()
        view.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        view.viewTreeObserver.removeOnScrollChangedListener(onScrollChangedListener)
        scrollViews.clear()
        offsetParents = listOf()
    }

    fun draw(canvas: Canvas) {
        if (isScrolling) {
            computeScrollOffset()
            updateOffsets()
        }
        fullBlurDrawable?.apply {
            alpha = blurAlpha
            this.draw(canvas)
        }
        if (overlayColor != 0) {
            canvas.drawRoundRect(
                view.left.toFloat(),
                view.top.toFloat(),
                view.right.toFloat(),
                view.bottom.toFloat(),
                view.x,
                view.y,
                overlayPaint
            )
        }
    }

    private fun createFullBlurDrawable() {
        fullBlurDrawable?.let { if (view.isAttachedToWindow) it.stopListening() }
        fullBlurDrawable =
            blurWallpaperProvider.createBlurDrawable(config).apply {
                callback = blurDrawableCallback
                setBounds(view.left, view.top, view.right, view.bottom)
                if (view.isAttachedToWindow) startListening()
            }
    }

    override fun onEnabledChanged() {
        createFullBlurDrawable()
    }

    private fun updateBounds() {
        scrollViews.clear()
        val offsetParents = mutableListOf<OffsetParent>()
        var left = 0
        var top = 0
        var current: View? = view
        while (current != null) {
            left += current.left
            top += current.top
            if (current is ScrollView || current is PagedView<*>) {
                scrollViews.add(current)
            } else if (current is OffsetParent) {
                offsetParents.add(current)
            }
            current = current.parent as? View
        }

        val right = left + view.width
        val bottom = top + view.height
        fullBlurDrawable?.setBlurBounds(
            left.toFloat(),
            top.toFloat(),
            right.toFloat(),
            bottom.toFloat()
        )
        view.invalidate()
        this.offsetParents = offsetParents
        computeScrollOffset()
        computeParentOffset()
    }

    private fun computeScrollOffset() {
        var scrollX = 0
        var scrollY = 0
        scrollViews.forEach {
            scrollX -= it.scrollX
            scrollY -= it.scrollY
        }

        offsetParents.forEach {
            if (it.needWallpaperScroll) {
                scrollX += wallpaperScrollOffset
            }
        }

        if (previousScrollX == scrollX && previousScrollY == scrollY) {
            isScrolling = false
            return
        }
        previousScrollX = scrollX
        previousScrollY = scrollY
    }

    private fun computeParentOffset() {
        var offsetX = 0f
        var offsetY = 0f
        offsetParents.forEach {
            offsetX += it.offsetX
            offsetY += it.offsetY
        }
        offsetY += wallpaperOffset
        this.parentOffsetX = offsetX
        this.parentOffsetY = offsetY
        updateOffsets()
        view.invalidate()
    }

    private fun updateOffsets() {
        fullBlurDrawable?.setOffsets(
            previousScrollX.toFloat() + parentOffsetX,
            previousScrollY.toFloat() + parentOffsetY
        )
    }

    override fun onScrollOffsetChanged(offset: Float) {
        offsetParents.forEach {
            if (it.needWallpaperScroll) {
                wallpaperScrollOffset = offset.toInt()
                isScrolling = true
                view.invalidate()
            }
        }
    }

    override fun onOffsetChanged(offset: Float) {
        wallpaperOffset = offset.toInt()
    }
}
