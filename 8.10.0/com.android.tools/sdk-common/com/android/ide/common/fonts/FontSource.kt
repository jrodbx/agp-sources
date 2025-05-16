/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.fonts

enum class FontSource {
    SYSTEM, // The font is a system font i.e. one of the 8 predefined fonts in the Android platform.
    PROJECT, // The font is a reference to a font created in a font resource file in the project.
    DOWNLOADABLE, // The font is a reference to a font in a font directory from a specific font provider (e.g. Google Fonts).
    LOOKUP, // Fake font family used to lookup the real font reference.
    HEADER        // Fake font family used in UI to refer to a header in a list of fonts.
}
