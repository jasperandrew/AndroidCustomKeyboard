/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jasperandrew.customkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import java.util.*

class CandidateView(context: Context) : View(context) {
    private var mService: SoftKeyboard? = null
    private var mSuggestions: List<String>? = null
    private var mSelectedIndex = 0
    private var mTouchX = OUT_OF_BOUNDS
    private val mSelectionHighlight: Drawable? = ResourcesCompat.getDrawable(context.resources, android.R.drawable.list_selector_background, null)
    private var mTypedWordValid = false
    private var mBgPadding: Rect? = null
    private val mWordWidth = IntArray(MAX_SUGGESTIONS)
    private val mWordX = IntArray(MAX_SUGGESTIONS)
    private val mColorNormal: Int
    private val mColorRecommended: Int
    private val mColorOther: Int
    private val mVerticalPadding: Int
    private val mPaint: Paint
    private var mScrolled = false
    private var mTargetScrollX = 0
    private var mTotalWidth = 0
    private val mGestureDetector: GestureDetector
    private val zeroRect = Rect()

    /**
     * A connection back to the service to communicate with the text field
     * @param listener
     */
    fun setService(listener: SoftKeyboard?) {
        mService = listener
    }

    public override fun computeHorizontalScrollRange(): Int {
        return mTotalWidth
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = resolveSize(50, widthMeasureSpec)

        // Get the desired height of the icon menu view (last row of items does
        // not have a divider below)
        val padding = zeroRect
        mSelectionHighlight?.getPadding(padding)
        val desiredHeight = (mPaint.textSize.toInt() + mVerticalPadding
                + padding.top + padding.bottom)

        // Maximum possible width and desired height
        setMeasuredDimension(measuredWidth,
                resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas?) {
        if (canvas != null) super.onDraw(canvas)
        drawSuggestion(canvas)
    }

    /**
     * If the canvas is null, then only touch calculations are performed to pick the target
     * candidate.
     */
    private fun drawSuggestion(canvas: Canvas?) {
        mTotalWidth = 0
        if (mSuggestions == null) return
        if (mBgPadding == null) {
            mBgPadding = Rect(0, 0, 0, 0)
            if (background != null) {
                background.getPadding(mBgPadding!!)
            }
        }
        var x = 0
        val count = mSuggestions!!.size
        val height = height
        val bgPadding = mBgPadding!!
        val paint = mPaint
        val touchX = mTouchX
        val scrollX = scrollX
        val scrolled = mScrolled
        val typedWordValid = mTypedWordValid
        val y = ((height - mPaint.textSize) / 2 - mPaint.ascent()).toInt()
        for (i in 0 until count) {
            val suggestion = mSuggestions!![i]
            val textWidth = paint.measureText(suggestion)
            val wordWidth = textWidth.toInt() + X_GAP * 2
            mWordX[i] = x
            mWordWidth[i] = wordWidth
            paint.color = mColorNormal
            if (touchX + scrollX >= x && touchX + scrollX < x + wordWidth && !scrolled) {
                if (canvas != null) {
                    canvas.translate(x.toFloat(), 0f)
                    mSelectionHighlight?.setBounds(0, bgPadding.top, wordWidth, height)
                    mSelectionHighlight?.draw(canvas)
                    canvas.translate(-x.toFloat(), 0f)
                }
                mSelectedIndex = i
            }
            if (canvas != null) {
                if (i == 1 && !typedWordValid || i == 0 && typedWordValid) {
                    paint.isFakeBoldText = true
                    paint.color = mColorRecommended
                } else if (i != 0) {
                    paint.color = mColorOther
                }
                canvas.drawText(suggestion, (x + X_GAP).toFloat(), y.toFloat(), paint)
                paint.color = mColorOther
                canvas.drawLine(x + wordWidth + 0.5f, bgPadding.top.toFloat(),
                        x + wordWidth + 0.5f, (height + 1).toFloat(), paint)
                paint.isFakeBoldText = false
            }
            x += wordWidth
        }
        mTotalWidth = x
        if (mTargetScrollX != getScrollX()) {
            scrollToTarget()
        }
    }

    private fun scrollToTarget() {
        var sx = scrollX
        if (mTargetScrollX > sx) {
            sx += SCROLL_PIXELS
            if (sx >= mTargetScrollX) {
                sx = mTargetScrollX
                requestLayout()
            }
        } else {
            sx -= SCROLL_PIXELS
            if (sx <= mTargetScrollX) {
                sx = mTargetScrollX
                requestLayout()
            }
        }
        scrollTo(sx, scrollY)
        invalidate()
    }

    fun setSuggestions(suggestions: List<String>?, completions: Boolean, typedWordValid: Boolean) {
        clear()
        if (suggestions != null) {
            mSuggestions = ArrayList(suggestions)
        }
        mTypedWordValid = typedWordValid
        scrollTo(0, 0)
        mTargetScrollX = 0
        // Compute the total width
        drawSuggestion(null)
        invalidate()
        requestLayout()
    }

    fun clear() {
        mSuggestions = EMPTY_LIST
        mTouchX = OUT_OF_BOUNDS
        mSelectedIndex = -1
        invalidate()
    }

    // Suppressed because blind folks probably don't have much need for a soft keyboard
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        if (mGestureDetector.onTouchEvent(me)) {
            return true
        }
        val action = me.action
        val x = me.x.toInt()
        val y = me.y.toInt()
        mTouchX = x
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mScrolled = false
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (y <= 0) {
                    // Fling up!?
                    if (mSelectedIndex >= 0) {
                        mService!!.pickSuggestionManually(mSelectedIndex)
                        mSelectedIndex = -1
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (!mScrolled) {
                    if (mSelectedIndex >= 0) {
                        mService!!.pickSuggestionManually(mSelectedIndex)
                    }
                }
                mSelectedIndex = -1
                removeHighlight()
                requestLayout()
            }
        }
        return true
    }

    /**
     * For flick through from keyboard, call this method with the x coordinate of the flick
     * gesture.
     * @param x
     */
    fun takeSuggestionAt(x: Float) {
        mTouchX = x.toInt()
        // To detect candidate
        drawSuggestion(null)
        if (mSelectedIndex >= 0) {
            mService!!.pickSuggestionManually(mSelectedIndex)
        }
        invalidate()
    }

    private fun removeHighlight() {
        mTouchX = OUT_OF_BOUNDS
        invalidate()
    }

    companion object {
        private const val OUT_OF_BOUNDS = -1
        private const val MAX_SUGGESTIONS = 32
        private const val SCROLL_PIXELS = 20
        private const val X_GAP = 60
        private val EMPTY_LIST: List<String> = ArrayList()
    }

    /**
     * Construct a CandidateView for showing suggested words for completion.
     */
    init {
        mSelectionHighlight?.state = intArrayOf(
                android.R.attr.state_enabled,
                android.R.attr.state_focused,
                android.R.attr.state_window_focused,
                android.R.attr.state_pressed
        )
        val r = context.resources
        setBackgroundColor(r.getColor(R.color.candidate_background, null))
        mColorNormal = r.getColor(R.color.candidate_normal, null)
        mColorRecommended = r.getColor(R.color.candidate_recommended, null)
        mColorOther = r.getColor(R.color.candidate_other, null)
        mVerticalPadding = r.getDimensionPixelSize(R.dimen.candidate_vertical_padding)
        mPaint = Paint()
        mPaint.color = mColorNormal
        mPaint.isAntiAlias = true
        mPaint.textSize = r.getDimensionPixelSize(R.dimen.candidate_font_height).toFloat()
        mPaint.strokeWidth = 0f
        mGestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent, e2: MotionEvent,
                                  distanceX: Float, distanceY: Float): Boolean {
                mScrolled = true
                var sx = scrollX
                sx += distanceX.toInt()
                if (sx < 0) {
                    sx = 0
                }
                if (sx + width > mTotalWidth) {
                    sx -= distanceX.toInt()
                }
                mTargetScrollX = sx
                scrollTo(sx, scrollY)
                invalidate()
                return true
            }
        })
        isHorizontalFadingEdgeEnabled = true
        setWillNotDraw(false)
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
    }
}