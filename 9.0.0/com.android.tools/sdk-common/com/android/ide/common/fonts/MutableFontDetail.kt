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
    var name: String,
    var type: FontType,
    var weight: Int,
    var width: Float,
    var italics: Float,
    var exact: Boolean,
    var fontUrl: String,
    var styleName: String,
    var hasExplicitStyle: Boolean,
) {
    constructor(name: String, weight: Int, width: Float, italics: Float, exact: Boolean)
            : this(name, FontType.SINGLE, weight, width, italics, exact, "", "", false)

    constructor(name: String, type: FontType, italics: Float, exact: Boolean)
            : this(name, type, DEFAULT_WEIGHT, DEFAULT_WIDTH, italics, exact, "", "", false)

    constructor()
            : this("", FontType.SINGLE, NORMAL, DEFAULT_EXACT)

    fun findBestMatch(fonts: Collection<FontDetail>): FontDetail? {
        var best: FontDetail? = null
        var bestMatch = Float.MAX_VALUE
        for (detail in fonts) {
            val match = match(detail)
            if (match < bestMatch) {
                bestMatch = match
                best = detail
                if (match == 0f) {
                    break
                }
            }
        }
        return best
    }

    fun match(other: FontDetail): Float {
        return Math.abs(weight - other.weight) +
                Math.abs(width - other.width) +
                Math.abs(italics - other.italics) * 50f +
                if (type != other.type) 500f else 0f
    }
}
