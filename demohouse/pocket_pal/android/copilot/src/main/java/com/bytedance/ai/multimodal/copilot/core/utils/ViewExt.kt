package com.bytedance.ai.multimodal.copilot.core.utils

import android.app.Activity
import android.graphics.Point
import android.graphics.drawable.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import androidx.core.graphics.minus
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

const val clickGapTagId = 123400001
const val lastClickTagId = 123400002
const val TAG_VIEW  = "ViewExt"

private var View.clickGap: Long
    get() = (getTag(clickGapTagId) as? Long) ?: 500L
    set(value) {
        setTag(clickGapTagId, value)
    }

private var View.lastClickTime: Long
    get() = (getTag(lastClickTagId) as? Long) ?: 0L
    set(value) {
        setTag(lastClickTagId, value)
    }

fun <T : View> T?.clickWithDebounce(onClick: (it: T) -> Unit) {
    if (this == null) return
    this.clickGap = 500
    setOnClickListener {
        if (!this.isQuickClick()) {
            onClick(this)
        }
    }
}

fun <T : View> T?.clickWithDebounce(listener: OnClickListener) {
    if (this == null) return
    this.clickGap = 500
    setOnClickListener {
        if (!this.isQuickClick()) {
            listener.onClick(this)
        }
    }
}



private fun View.isQuickClick(): Boolean {
    val cur = System.currentTimeMillis()
    val isQuick = cur - this.lastClickTime < this.clickGap
    if (!isQuick) {
        this.lastClickTime = cur
    }
    return isQuick
}

inline fun View.updatePaddingRelative(
    @Px start: Int = paddingStart,
    @Px top: Int = paddingTop,
    @Px end: Int = paddingEnd,
    @Px bottom: Int = paddingBottom,
    source: String? = "",
) {
    Log.d(
        "updatePaddingRelative",
        "view:${this.javaClass.simpleName},source:$source,start:$start,top:$top,end:$end,bottom:$bottom"
    )
    setPaddingRelative(start, top, end, bottom)
}

inline fun View.setPadding(@Px size: Int) {
    setPadding(size, size, size, size)
}

inline fun View.updatePadding(
    @Px left: Int = paddingLeft,
    @Px top: Int = paddingTop,
    @Px right: Int = paddingRight,
    @Px bottom: Int = paddingBottom
) {
    setPadding(left, top, right, bottom)
}

fun View.hideIme() {
    if (!isImeShowed()) return
    (context as? Activity)?.window?.let {
        WindowInsetsControllerCompat(it, this).hide(WindowInsetsCompat.Type.ime())
    }
    val focus = findFocus() ?: return
    focus.clearFocus()
}

fun View.isImeShowed(): Boolean {
    return ViewCompat.getRootWindowInsets(this)?.isVisible(
        WindowInsetsCompat.Type.ime()
    ) ?: false
}

fun View.clipCorner(px: Int) {
    UIUtils.setClipViewCornerRadius(this, px)
}

/**
 * 为了解决鸿蒙3.0 darkmode获取Drawable.xml中色值异常，采用代码编写Drawable，色值外部传入；
 * 代替一下类似xml布局
 *  <?xml version="1.0" encoding="utf-8"?>
 *  <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
 *  <item android:drawable="@color/neutral_transparent_2" />
 *  <item
 *  android:drawable="@color/base_1"
 *  android:top="@dimen/dp_0_5" />
 *  </layer-list>
 *
 */
fun View.setFlowBackgroundLayerDrawable(
    left: Int = 0,
    right: Int = 0,
    top: Int = 0,
    bottom: Int = 0,
    @ColorRes layer1Color: Int,
    @ColorRes layer2Color: Int
) {
    val layers = arrayOf<Drawable>(
        ColorDrawable(ContextCompat.getColor(context, layer1Color)),
        ColorDrawable(ContextCompat.getColor(context, layer2Color))
    )

    val layerDrawable = LayerDrawable(layers).apply {
        setLayerInset(1, left, top, right, bottom)
    }
    background = layerDrawable
}
/**
 * 为了解决鸿蒙3.0 darkmode获取Drawable.xml中色值异常，采用代码编写Drawable，色值外部传入；
 * 代替一下类似xml布局
 *   <?xml version="1.0" encoding="utf-8"?>
 *      <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
 *   <item android:drawable="@color/neutral_transparent_2" />
 *   <item
 *   android:drawable="@color/base_1"
 *   android:top="@dimen/dp_0_5" />
 *   </layer-list>
 */
fun View.setFlowBackgroundDrawable(
    topLeftCorner: Float = 0f,
    topRightCorner: Float = 0f,
    bottomRightCorner: Float = 0f,
    bottomLeftCorner: Float = 0f,
    @ColorRes normalColor: Int,
    @ColorRes pressedColor: Int
) {
    background = createStateListDrawable(
        floatArrayOf(
            topLeftCorner,
            topLeftCorner,
            topRightCorner,
            topRightCorner,
            bottomRightCorner,
            bottomRightCorner,
            bottomLeftCorner,
            bottomLeftCorner
        ),
        ContextCompat.getColor(context, normalColor),
        ContextCompat.getColor(context, pressedColor)
    )
}

/**
 * 为了解决鸿蒙3.0 darkmode 获取Drawable.xml中色值异常，采用代码编写Drawable，色值外部传入；
 * 代替一下xml布局
 * <?xml version="1.0" encoding="utf-8"?>
 *   <shape xmlns:android="http://schemas.android.com/apk/res/android"
 *    android:shape="rectangle">
 *    <solid android:color="@color/base_1" />
 *    </shape>
 *
 */
fun View.setFlowBackgroundDrawable(
    corner: Float = 0f,
    @ColorRes normalColor: Int,
    @ColorRes pressedColor: Int
) {
    background = createStateListDrawable(
        floatArrayOf(
            corner,
            corner,
            corner,
            corner,
            corner,
            corner,
            corner,
            corner
        ),
        ContextCompat.getColor(context, normalColor),
        ContextCompat.getColor(context, pressedColor)
    )
}

fun View.setFlowBackgroundDrawable(corner: Float = 0f, @ColorRes color: Int) {
    background = createDrawable(corner, ContextCompat.getColor(context, color))
}

fun View.setFlowBackgroundDrawable2(corner: Float = 0f, normalColor: Int) {
    background = createDrawable(corner, normalColor)
}

fun View.setFlowOvalBackgroundDrawable(width: Int, height:Int, @ColorRes color: Int) {
    background = createOvalDrawable(width, height, ContextCompat.getColor(context, color))
}

fun View.setFlowBackgroundDrawable(width: Int, height:Int, corner: Float = 0f, @ColorRes color: Int) {
    background = createDrawable(width, height, corner, ContextCompat.getColor(context, color))
}

fun View.setFlowBackgroundGradientDrawable(@ColorInt startColor: Int, @ColorInt endColor: Int) {
    background = createGradientDrawable(startColor, endColor)
}

private fun createStateListDrawable(
    cornerRadii: FloatArray,
    normalColor: Int,
    pressedColor: Int
): StateListDrawable {
    val normalState = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        this.cornerRadii = cornerRadii
        setColor(normalColor)
    }

    val pressedState = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        this.cornerRadii = cornerRadii
        setColor(pressedColor)
    }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressedState)
        addState(intArrayOf(), normalState)
    }
}

fun createDrawable(radius: Float, color: Int): Drawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }
}

fun createDrawable(width: Int, height:Int, radius: Float, color: Int): Drawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
        setSize(width, height)
    }
}

fun createOvalDrawable(width: Int, height:Int, color: Int): Drawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setSize(width, height)
    }
}

fun createGradientDrawable(@ColorInt startColor: Int, @ColorInt endColor: Int): Drawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        colors = intArrayOf(startColor, endColor)
    }
}

fun View.createLayerListDrawable(
    left: Int = 0,
    right: Int = 0,
    top: Int = 0,
    bottom: Int = 0,
    layer1Color: Int,
    layer2Color: Int
): Drawable {
    val layers = arrayOf<Drawable>(
        ColorDrawable(ContextCompat.getColor(context, layer1Color)),
        ColorDrawable(ContextCompat.getColor(context, layer2Color))
    )

    val layerDrawable = LayerDrawable(layers).apply {
        setLayerInset(1, left, top, right, bottom)
    }
    return layerDrawable
}

fun View.getLocationInParent(parent: View): Point {
    var top = 0
    var left = 0
    var curr: View? = this
    while (curr != parent) {
        if (curr == null) break
        left += curr.left
        top += curr.top
        curr = curr.parent as? View
    }
    return Point(left, top)
}

fun View.getLocationInContentRoot(): Point {
    var top = 0
    var left = 0
    var curr: View? = this
    var addToRoot = false
    while (curr != null) {
        if (curr.id == android.R.id.content) {
            addToRoot = true
            break
        }
        left += curr.left
        top += curr.top
        curr = curr.parent as? View
    }
    if (!addToRoot) {
        Log.e(TAG_VIEW,"$this is not add to view tree!")
    }
    return Point(left, top)
}

fun View.getRelativeLocationTo(other: View): Point {
    val location4me = this.getLocationInContentRoot()
    val location4Other = other.getLocationInContentRoot()
    return location4me - location4Other
}

fun View.getLocationOffsetFor(other: View, alignCenter: Boolean = false): Point {
    val location4me = this.getLocationInContentRoot()
    val location4Other = other.getLocationInContentRoot()

    if (alignCenter) {
        location4me.x += width / 2
        location4me.y += height / 2
        location4Other.x += other.width / 2
        location4Other.y += other.height / 2
    }

    return location4Other - location4me
}

enum class Alignment {
    Leading, Trailing, Center
}

fun View.getLocationOffsetFor(other: View, alignment: Alignment): Point {
    val location4me = this.getLocationInContentRoot()
    val location4Other = other.getLocationInContentRoot()

    when (alignment) {
        Alignment.Leading -> {
        }
        Alignment.Trailing -> {
            location4Other.x += (other.width - width)
            location4Other.y += (other.height - height)
        }
        Alignment.Center -> {
            location4me.x += width / 2
            location4me.y += height / 2
            location4Other.x += other.width / 2
            location4Other.y += other.height / 2
        }
    }
    return location4Other - location4me
}

fun View.getLayoutInflater(): LayoutInflater = LayoutInflater.from(context)

fun ViewGroup.inflate(layoutId: Int, attachToRoot: Boolean = true): View =
    getLayoutInflater().inflate(layoutId, this, attachToRoot)

fun View.getFragment(): Fragment? {
    val act = context as? FragmentActivity ?: return null
    return getFragment(act.supportFragmentManager, this)
}

private fun getFragment(fm: FragmentManager, view: View): Fragment? {
    val count = fm.fragments.size
    for (i in count - 1 downTo 0) {
        val fragment = fm.fragments[i]
        val fragmentV = fragment.view ?: continue
        val res = getFragment(fragment.childFragmentManager, view)
        if (res != null) {
            return res
        } else if (isParentView(fragmentV, view)) {
            return fragment
        }
    }
    return null
}

private fun isParentView(parent: View, child: View): Boolean {
    var childP = child.parent
    while (childP != null && childP is View && childP != parent) {
        childP = (childP as View).parent
    }
    return childP == parent
}






