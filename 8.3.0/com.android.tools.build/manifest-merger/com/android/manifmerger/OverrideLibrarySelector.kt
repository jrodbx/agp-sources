/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.manifmerger

class OverrideLibrarySelector(private val selectorPackageName: String) {

    /**
     * Returns true if the passed element is "selected" by this selector. If so, any action this
     * selector decorated will be applied to the element.
     */
    fun appliesTo(element: XmlElement): Boolean {
        val packageName = element.document.getPackage()
        return packageName.isPresent && selectorPackageName == packageName.get().value
    }
}
