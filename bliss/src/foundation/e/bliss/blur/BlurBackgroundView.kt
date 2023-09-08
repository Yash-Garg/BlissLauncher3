/*
 * Copyright © MURENA SAS 2023.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package foundation.e.bliss.blur

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.android.launcher3.Insettable
import foundation.e.bliss.utils.runOnMainThread

class BlurBackgroundView(context: Context, attrs: AttributeSet?) :
    View(context, attrs), Insettable, BlurWallpaperProvider.Listener {

    private val blurWallpaperProvider by lazy { BlurWallpaperProvider.getInstanceNoCreate() }

    private var fullBlurDrawable: BlurDrawable? = null
    private var blurAlpha = 255

    private val blurDrawableCallback by lazy {
        object : Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {}

            override fun invalidateDrawable(who: Drawable) {
                runOnMainThread { invalidate() }
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
        }
    }

    init {
        createFullBlurDrawable()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        BlurWallpaperProvider.getInstanceNoCreate().addListener(this)
        fullBlurDrawable?.startListening()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        BlurWallpaperProvider.getInstanceNoCreate().removeListener(this)
        fullBlurDrawable?.stopListening()
    }

    override fun onDraw(canvas: Canvas) {
        fullBlurDrawable?.apply {
            alpha = blurAlpha
            this.draw(canvas)
        }
        canvas.drawARGB((blurAlpha * 0.01).toInt(), 0, 0, 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (changed) {
            fullBlurDrawable?.setBounds(left, top, right, bottom)
        }
    }

    private fun createFullBlurDrawable(
        config: BlurWallpaperProvider.BlurConfig = BlurWallpaperProvider.blurConfigBackground
    ) {
        fullBlurDrawable?.let { if (isAttachedToWindow) it.stopListening() }
        fullBlurDrawable =
            blurWallpaperProvider.createBlurDrawable(config).apply {
                callback = blurDrawableCallback
                setBounds(left, top, right, bottom)
                if (isAttachedToWindow) startListening()
            }
    }

    override fun onEnabledChanged() {
        createFullBlurDrawable()
    }

    override fun setInsets(insets: Rect) {}
}
