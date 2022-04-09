/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.dsl

import org.gradle.api.Incubating

interface BundleTexture {
    @get:Incubating
    @set:Incubating
    var enableSplit: Boolean?

    /**
     * Specifies the default texture format to be used when it's not possible
     * to deliver a format targeted to the device. This is for example the case
     * for standalone APKs, generated for pre-Lollipop devices.
     *
     * If left empty or unspecified, the fallback folders (folders not containing
     * a #tcf suffix) will be delivered to pre-Lollipop devices.
     *
     * If the default format is set and not empty (for example, it's set to "etc2"),
     * and a pre-Lollipop device does not support this format, then the app will be
     * considered as not compatible and won't be installable.
     *
     * since 4.1.0
     */
    @get:Incubating
    @set:Incubating
    var defaultFormat: String?
}
