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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.inputmethod.InputMethodSubtype

@Suppress("deprecation")
class LatinKeyboardView : KeyboardView {
    private val paint = Paint()

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    override fun onLongPress(key: Keyboard.Key): Boolean {
        return if (key.codes[0] == Keyboard.KEYCODE_CANCEL) {
            onKeyboardActionListener.onKey(KEYCODE_OPTIONS, null)
            true
            /*} else if (key.codes[0] == 113) {

            return true; */
        } else {
            //Log.d("LatinKeyboardView", "KEY: " + key.codes[0]);
            super.onLongPress(key)
        }
    }

    fun setSubtypeOnSpaceKey(subtype: InputMethodSubtype?) {
        val keyboard = keyboard as LatinKeyboard
        //keyboard.setSpaceIcon(getResources().getDrawable(subtype.getIconResId()));
        invalidateAllKeys()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paint = paint
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 28f
        paint.color = Color.LTGRAY
        val keys = keyboard.keys
        for (key in keys) {
            when (key.label) {
                "q" -> canvas.drawText("1", (key.x + (key.width - 25)).toFloat(), (key.y + 40).toFloat(), paint)
                "w" -> canvas.drawText("2", (key.x + (key.width - 25)).toFloat(), (key.y + 40).toFloat(), paint)
                "e" -> canvas.drawText("3", (key.x + (key.width - 25)).toFloat(), (key.y + 40).toFloat(), paint)
                "r" -> canvas.drawText("4", (key.x + (key.width - 25)).toFloat(), (key.y + 40).toFloat(), paint)
                "t" -> canvas.drawText("5", (key.x + (key.width - 25)).toFloat(), (key.y + 40).toFloat(), paint)
            }
        }
    }

    companion object {
        const val KEYCODE_OPTIONS = -100

        // TODO: Move this into com.jasperandrew.customkeyboard.Keyboard
        const val KEYCODE_LANGUAGE_SWITCH = -101
    }
}