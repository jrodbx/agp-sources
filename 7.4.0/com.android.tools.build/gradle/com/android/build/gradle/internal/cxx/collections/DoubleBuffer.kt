/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.collections


/**
 * Contains two StringBuilders that can be exchanged by calling flip().
 */
class DoubleStringBuilder : DoubleBuffer<StringBuilder>(StringBuilder(), StringBuilder())

/**
 * Contains instances of two buffer classes that can be exchanged by calling flip().
 */
open class DoubleBuffer<TBuffer>(
    initialFront : TBuffer,
    initialBack : TBuffer) {

    // Storage
    private var currentFront : TBuffer = initialFront
    private var currentBack : TBuffer = initialBack

    /**
     * The front instance.
     */
    val front get() = currentFront

    /**
     * The back instance.
     */
    val back get() = currentBack

    /**
     * Exchange the front and back instances.
     */
    fun flip() {
        val temp = currentBack
        currentBack = currentFront
        currentFront = temp
    }
}
