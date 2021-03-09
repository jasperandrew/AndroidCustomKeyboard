/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.android.inputmethodcommon.InputMethodSettingsFragment

/**
 * Displays the IME preferences inside the input method setting.
 */
class ImePreferences : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().replace(android.R.id.content, Settings()).commit()
    }

    class Settings : InputMethodSettingsFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setInputMethodSettingsCategoryTitle(R.string.language_selection_title)
            setSubtypeEnablerTitle(R.string.select_language)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.ime_preferences)
        }
    }
}