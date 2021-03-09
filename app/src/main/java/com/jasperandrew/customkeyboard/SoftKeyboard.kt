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

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.os.IBinder
import android.text.InputType
import android.text.method.MetaKeyKeyListener
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.*
import android.view.textservice.*
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener
import java.util.*

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
@Suppress("deprecation")
class SoftKeyboard : InputMethodService(), OnKeyboardActionListener, SpellCheckerSessionListener {
    private var mInputMethodManager: InputMethodManager? = null
    private var mInputView: LatinKeyboardView? = null
    private var mCandidateView: CandidateView? = null
    private var mCompletions: Array<CompletionInfo?>? = null
    private val mComposing = StringBuilder()
    private var mPredictionOn = false
    private var mCompletionOn = false
    private var mLastDisplayWidth = 0
    private var mCapsLock = false
    private var mLastShiftTime: Long = 0
    private var mMetaState: Long = 0
    private var mSymbolsKeyboard: LatinKeyboard? = null
    private var mSymbolsShiftedKeyboard: LatinKeyboard? = null
    private var mQwertyKeyboard: LatinKeyboard? = null
    private var mCurKeyboard: LatinKeyboard? = null
    private var wordSeparators: String? = null
    private var mScs: SpellCheckerSession? = null
    private var mSuggestions: List<String>? = null

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    override fun onCreate() {
        super.onCreate()
        mInputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        wordSeparators = resources.getString(R.string.word_separators)
        val tsm = getSystemService(
                TEXT_SERVICES_MANAGER_SERVICE) as TextServicesManager
        mScs = tsm.newSpellCheckerSession(null, null, this, true)
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    override fun onInitializeInterface() {
        if (mQwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            val displayWidth = maxWidth
            if (displayWidth == mLastDisplayWidth) return
            mLastDisplayWidth = displayWidth
        }
        mQwertyKeyboard = LatinKeyboard(this, R.xml.qwerty)
        mSymbolsKeyboard = LatinKeyboard(this, R.xml.symbols)
        mSymbolsShiftedKeyboard = LatinKeyboard(this, R.xml.symbols_shift)
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Suppress("InflateParams")
    override fun onCreateInputView(): View {
        mInputView = layoutInflater.inflate(
                R.layout.input, null) as LatinKeyboardView
        mInputView!!.setOnKeyboardActionListener(this)
        mInputView!!.isPreviewEnabled = false
        setLatinKeyboard(mQwertyKeyboard)
        return mInputView!!
    }

    private fun setLatinKeyboard(nextKeyboard: LatinKeyboard?) {
        val shouldSupportLanguageSwitchKey = mInputMethodManager!!.shouldOfferSwitchingToNextInputMethod(token)
        nextKeyboard!!.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey)
        mInputView!!.keyboard = nextKeyboard
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like [.onCreateInputView].
     */
    override fun onCreateCandidatesView(): View {
        mCandidateView = CandidateView(this)
        mCandidateView!!.setService(this)
        return mCandidateView as CandidateView
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0)
        updateCandidates()
        if (!restarting) {
            // Clear shift states.
            mMetaState = 0
        }
        mPredictionOn = false
        mCompletionOn = false
        mCompletions = null
        when (attribute.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME ->                 // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mCurKeyboard = mSymbolsKeyboard
            InputType.TYPE_CLASS_PHONE ->                 // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard
            InputType.TYPE_CLASS_TEXT -> {
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mQwertyKeyboard
                mPredictionOn = true

                // We now look for a few special variations of text that will
                // modify our behavior.
                val variation = attribute.inputType and InputType.TYPE_MASK_VARIATION
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    mPredictionOn = false
                }
                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    mPredictionOn = false
                }
                if (attribute.inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    mPredictionOn = false
                    mCompletionOn = isFullscreenMode
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute)
            }
            else -> {
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mQwertyKeyboard
                updateShiftKeyState(attribute)
            }
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard!!.setImeOptions(resources, attribute.imeOptions)
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    override fun onFinishInput() {
        super.onFinishInput()

        // Clear current composing text and candidates.
        mComposing.setLength(0)
        updateCandidates()

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false)
        mCurKeyboard = mQwertyKeyboard
        if (mInputView != null) {
            mInputView!!.closing()
        }
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        // Apply the selected keyboard to the input view.
        setLatinKeyboard(mCurKeyboard)
        mInputView!!.closing()
        val subtype = mInputMethodManager!!.currentInputMethodSubtype
        mInputView!!.setSubtypeOnSpaceKey(subtype)
    }

    public override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype) {
        mInputView!!.setSubtypeOnSpaceKey(subtype)
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int,
                                   newSelStart: Int, newSelEnd: Int,
                                   candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd)

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.isNotEmpty() && (newSelStart != candidatesEnd
                        || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0)
            updateCandidates()
            val ic = currentInputConnection
            ic?.finishComposingText()
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    override fun onDisplayCompletions(completions: Array<CompletionInfo?>?) {
        if (mCompletionOn) {
            mCompletions = completions
            if (completions == null) {
                setSuggestions(null, false, typedWordValid = false)
                return
            }
            val stringList: MutableList<String> = ArrayList()
            for (i in completions.indices) {
                val ci = completions[i]
                if (ci != null) stringList.add(ci.text.toString())
            }
            setSuggestions(stringList, true, typedWordValid = true)
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private fun translateKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event)
        var c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState))
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState)
        val ic = currentInputConnection
        if (c == 0 || ic == null) {
            return false
        }
        if (c and KeyCharacterMap.COMBINING_ACCENT != 0) {
            c = c and KeyCharacterMap.COMBINING_ACCENT_MASK
        }
        if (mComposing.isNotEmpty()) {
            val accent = mComposing[mComposing.length - 1]
            val composed = KeyEvent.getDeadChar(accent.toInt(), c)
            if (composed != 0) {
                c = composed
                mComposing.setLength(mComposing.length - 1)
            }
        }
        onKey(c, null)
        return true
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK ->                 // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.repeatCount == 0 && mInputView != null) {
                    if (mInputView!!.handleBack()) {
                        return true
                    }
                }
            KeyEvent.KEYCODE_DEL ->
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.isNotEmpty()) {
                    onKey(Keyboard.KEYCODE_DELETE, null)
                    return true
                }
            KeyEvent.KEYCODE_ENTER ->                 // Let the underlying text editor always handle these.
                return false
            else -> {
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                        keyCode, event)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private fun commitTyped(inputConnection: InputConnection) {
        if (mComposing.isNotEmpty()) {
            inputConnection.commitText(mComposing, mComposing.length)
            mComposing.setLength(0)
            updateCandidates()
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private fun updateShiftKeyState(attr: EditorInfo?) {
        if (attr != null && mInputView != null && mQwertyKeyboard == mInputView!!.keyboard) {
            var caps = 0
            val ei = currentInputEditorInfo
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = currentInputConnection.getCursorCapsMode(attr.inputType)
            }
            mInputView!!.isShifted = mCapsLock || caps != 0
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private fun isAlphabet(code: Int): Boolean {
        return Character.isLetter(code)
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private fun keyDownUp(keyEventCode: Int) {
        currentInputConnection.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode))
        currentInputConnection.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, keyEventCode))
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private fun sendKey(keyCode: Int) {
        when (keyCode) {
            '\n'.toInt() -> keyDownUp(KeyEvent.KEYCODE_ENTER)
            else -> if (keyCode >= '0'.toInt() && keyCode <= '9'.toInt()) {
                keyDownUp(keyCode - '0'.toInt() + KeyEvent.KEYCODE_0)
            } else {
                currentInputConnection.commitText((keyCode.toChar()).toString(), 1)
            }
        }
    }

    // Implementation of KeyboardViewListener
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        Log.d("Test", "KEYCODE: $primaryCode")
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.isNotEmpty()) {
                commitTyped(currentInputConnection)
            }
            sendKey(primaryCode)
            updateShiftKeyState(currentInputEditorInfo)
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace()
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift()
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose()
            return
        } else if (primaryCode == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
            handleLanguageSwitch()
            return
        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
                && mInputView != null) {
            val current = mInputView!!.keyboard
            if (current === mSymbolsKeyboard || current === mSymbolsShiftedKeyboard) {
                setLatinKeyboard(mQwertyKeyboard)
            } else {
                setLatinKeyboard(mSymbolsKeyboard)
                mSymbolsKeyboard!!.isShifted = false
            }
        } else {
            if (keyCodes != null) {
                handleCharacter(primaryCode, keyCodes)
            }
        }
    }

    override fun onText(text: CharSequence) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (mComposing.isNotEmpty()) {
            commitTyped(ic)
        }
        ic.commitText(text, 0)
        ic.endBatchEdit()
        updateShiftKeyState(currentInputEditorInfo)
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private fun updateCandidates() {
        if (!mCompletionOn) {
            if (mComposing.isNotEmpty()) {
                val list = ArrayList<String>()
                //list.add(mComposing.toString());
                Log.d("SoftKeyboard", "REQUESTING: $mComposing")
                mScs!!.getSentenceSuggestions(arrayOf(TextInfo(mComposing.toString())), 5)
                setSuggestions(list, true, typedWordValid = true)
            } else {
                setSuggestions(null, false, typedWordValid = false)
            }
        }
    }

    private fun setSuggestions(suggestions: List<String>?, completions: Boolean,
                               typedWordValid: Boolean) {
        if (suggestions != null && suggestions.isNotEmpty()) {
            setCandidatesViewShown(true)
        } else if (isExtractViewShown) {
            setCandidatesViewShown(true)
        }
        mSuggestions = suggestions
        if (mCandidateView != null) {
            mCandidateView!!.setSuggestions(suggestions, completions, typedWordValid)
        }
    }

    private fun handleBackspace() {
        val length = mComposing.length
        when {
            length > 1 -> {
                mComposing.delete(length - 1, length)
                currentInputConnection.setComposingText(mComposing, 1)
                updateCandidates()
            }
            length > 0 -> {
                mComposing.setLength(0)
                currentInputConnection.commitText("", 0)
                updateCandidates()
            }
            else -> {
                keyDownUp(KeyEvent.KEYCODE_DEL)
            }
        }
        updateShiftKeyState(currentInputEditorInfo)
    }

    private fun handleShift() {
        if (mInputView == null) {
            return
        }
        val currentKeyboard = mInputView!!.keyboard
        when {
            mQwertyKeyboard == currentKeyboard -> {
                // Alphabet keyboard
                checkToggleCapsLock()
                mInputView!!.isShifted = mCapsLock || !mInputView!!.isShifted
            }
            currentKeyboard === mSymbolsKeyboard -> {
                mSymbolsKeyboard!!.isShifted = true
                setLatinKeyboard(mSymbolsShiftedKeyboard)
                mSymbolsShiftedKeyboard!!.isShifted = true
            }
            currentKeyboard === mSymbolsShiftedKeyboard -> {
                mSymbolsShiftedKeyboard!!.isShifted = false
                setLatinKeyboard(mSymbolsKeyboard)
                mSymbolsKeyboard!!.isShifted = false
            }
        }
    }

    private fun handleCharacter(primaryCode: Int, keyCodes: IntArray) {
        var code = primaryCode
        if (isInputViewShown) {
            if (mInputView!!.isShifted) {
                code = Character.toUpperCase(code)
            }
        }
        if (mPredictionOn) {
            mComposing.append(code.toChar())
            currentInputConnection.setComposingText(mComposing, 1)
            updateShiftKeyState(currentInputEditorInfo)
            updateCandidates()
        } else {
            currentInputConnection.commitText(code.toChar().toString(), 1)
        }
    }

    private fun handleClose() {
        commitTyped(currentInputConnection)
        requestHideSelf(0)
        mInputView!!.closing()
    }

    private val token: IBinder?
        get() {
            val dialog = window ?: return null
            val window = dialog.window ?: return null
            return window.attributes.token
        }

    private fun handleLanguageSwitch() {
        mInputMethodManager!!.switchToNextInputMethod(token, false /* onlyCurrentIme */)
    }

    private fun checkToggleCapsLock() {
        val now = System.currentTimeMillis()
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock
            mLastShiftTime = 0
        } else {
            mLastShiftTime = now
        }
    }

    private fun isWordSeparator(code: Int): Boolean {
        val separators = wordSeparators
        return separators!!.contains(code.toChar().toString())
    }

    private fun pickDefaultCandidate() {
        pickSuggestionManually(0)
    }

    fun pickSuggestionManually(index: Int) {
        if (mCompletionOn && mCompletions != null && index >= 0 && index < mCompletions!!.size) {
            val ci = mCompletions!![index]
            currentInputConnection.commitCompletion(ci)
            if (mCandidateView != null) {
                mCandidateView!!.clear()
            }
            updateShiftKeyState(currentInputEditorInfo)
        } else if (mComposing.isNotEmpty()) {
            if (mPredictionOn && mSuggestions != null && index >= 0) {
                mComposing.replace(0, mComposing.length, mSuggestions!![index])
            }
            commitTyped(currentInputConnection)
        }
    }

    override fun swipeRight() {
        Log.d("SoftKeyboard", "Swipe right")
        if (mCompletionOn || mPredictionOn) {
            pickDefaultCandidate()
        }
    }

    override fun swipeLeft() {
        Log.d("SoftKeyboard", "Swipe left")
        handleBackspace()
    }

    override fun swipeDown() {
        handleClose()
    }

    override fun swipeUp() {}
    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}

    /**
     * http://www.tutorialspoint.com/android/android_spelling_checker.htm
     * @param results results
     */
    override fun onGetSuggestions(results: Array<SuggestionsInfo>) {
        val sb = StringBuilder()
        for (i in results.indices) {
            // Returned suggestions are contained in SuggestionsInfo
            val len = results[i].suggestionsCount
            sb.append('\n')
            for (j in 0 until len) {
                sb.append("," + results[i].getSuggestionAt(j))
            }
            sb.append(" ($len)")
        }
        Log.d("SoftKeyboard", "SUGGESTIONS: $sb")
    }

    private fun dumpSuggestionsInfoInternal(
            sb: MutableList<String>, si: SuggestionsInfo, length: Int, offset: Int) {
        // Returned suggestions are contained in SuggestionsInfo
        val len = si.suggestionsCount
        for (j in 0 until len) {
            sb.add(si.getSuggestionAt(j))
        }
    }

    override fun onGetSentenceSuggestions(results: Array<SentenceSuggestionsInfo>) {
        Log.d("SoftKeyboard", "onGetSentenceSuggestions")
        val sb: MutableList<String> = ArrayList()
        for (i in results.indices) {
            val ssi = results[i]
            for (j in 0 until ssi.suggestionsCount) {
                dumpSuggestionsInfoInternal(
                        sb, ssi.getSuggestionsInfoAt(j), ssi.getOffsetAt(j), ssi.getLengthAt(j))
            }
        }
        Log.d("SoftKeyboard", "SUGGESTIONS: $sb")
        setSuggestions(sb, true, typedWordValid = true)
    }

    companion object {
        const val DEBUG = false

        /**
         * This boolean indicates the optional example code for performing
         * processing of hard keys in addition to regular text generation
         * from on-screen interaction.  It would be used for input methods that
         * perform language translations (such as converting text entered on
         * a QWERTY keyboard to Chinese), but may not be used for input methods
         * that are primarily intended to be used for on-screen text entry.
         */
        const val PROCESS_HARD_KEYS = true
        private const val NOT_A_LENGTH = -1
    }
}