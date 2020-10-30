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

class MutableFontDetail(
    var weight: Int,
    var width: Int,
    var italics: Boolean,
    var fontUrl: String,
    var styleName: String,
    var exact: Boolean,
    var hasExplicitStyle: Boolean) {

    constructor(weight: Int, width: Int, italics: Boolean, hasExplicitStyle: Boolean)
            : this(weight, width, italics, "", "", false, hasExplicitStyle)

    constructor(weight: Int, width: Int, italics: Boolean)
            : this(weight, width, italics, false)

    constructor()
            : this(DEFAULT_WEIGHT, DEFAULT_WIDTH, false)

    fun findBestMatch(fonts: Collection<FontDetail>): FontDetail? {
        var best: FontDetail? = null
        var bestMatch = Integer.MAX_VALUE
        for (detail in fonts) {
            val match = match(detail)
            if (match < bestMatch) {
                bestMatch = match
                best = detail
                if (match == 0) {
                    break
                }
            }
        }
        return best
    }

    fun match(other: FontDetail): Int {
        return Math.abs(weight - other.weight) +
                Math.abs(width - other.width) +
                if (italics != other.italics) 50 else 0
    }
}
