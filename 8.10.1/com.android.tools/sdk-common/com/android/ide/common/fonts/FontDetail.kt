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

import java.util.Locale
import java.util.Objects

const val DEFAULT_WEIGHT = 400
const val DEFAULT_WIDTH = 100f
const val DEFAULT_EXACT = true
const val ITALICS = 1f
const val NORMAL = 0f

enum class FontType {
    SINGLE,
    VARIABLE
}

/**
 * A [FontDetail] is a reference to a specific font with weight, width, and italics attributes.
 * Each instance refers to a possibly remote (*.ttf) font file.
 */
class FontDetail {
    val family: FontFamily
    val type: FontType
    val weight: Int
    val width: Float
    val italics: Float
    val exact: Boolean
    val fontUrl: String
    val styleName: String
    val hasExplicitStyle: Boolean

    val fontStyle: String
        get() = if (italics != NORMAL) "italic" else "normal"

    constructor(fontFamily: FontFamily, font: MutableFontDetail) {
        family = fontFamily
        type = font.type
        weight = font.weight
        width = font.width
        italics = font.italics
        exact = font.exact
        fontUrl = font.fontUrl
        hasExplicitStyle = font.hasExplicitStyle
        styleName = generateStyleName(font)
    }

    /**
     * Special use for creating synonyms in font-family files with references to other fonts.
     */
    constructor(detail: FontDetail, withStyle: MutableFontDetail) {
        family = detail.family
        type = withStyle.type
        weight = withStyle.weight
        width = withStyle.width
        italics = withStyle.italics
        exact = withStyle.exact
        fontUrl = detail.fontUrl
        hasExplicitStyle = detail.hasExplicitStyle
        styleName = generateStyleName(withStyle)
    }


    fun toMutableFontDetail(): MutableFontDetail {
        return MutableFontDetail(family.name, type, weight, width, italics, exact, fontUrl, styleName, hasExplicitStyle)
    }

    fun generateQueryV12(): String {
        val query = StringBuilder().append(family.name)
        if (type == FontType.VARIABLE) {
            query.append(":vf")
            if (italics != 0f) {
                query.append(":italic")
            }
            return query.toString()
        }
        if (weight != DEFAULT_WEIGHT) {
            query.append(":wght").append(weight)
        }
        if (italics != NORMAL) {
            query.append(":ital").append(italics.floatAsString())
        }
        if (width != DEFAULT_WIDTH) {
            query.append(":wdth").append(width.floatAsString())
        }
        if (!exact) {
            query.append(":nearest")
        }
        return query.toString()
    }

    fun generateQueryV11(): String {
        if (weight == DEFAULT_WEIGHT && width == DEFAULT_WIDTH && italics == NORMAL && exact == DEFAULT_EXACT) {
            return family.name
        }
        val query = StringBuilder().append("name=").append(family.name)
        if (weight != DEFAULT_WEIGHT) {
            query.append("&weight=").append(weight)
        }
        if (italics != NORMAL) {
            query.append("&italic=").append(italics.floatAsString())
        }
        if (width != DEFAULT_WIDTH) {
            query.append("&width=").append(width.floatAsString())
        }
        if (!exact) {
            query.append("&besteffort=true")
        }
        return query.toString()
    }

    override fun hashCode(): Int {
        return Objects.hash(weight, width, italics)
    }

    override fun equals(other: Any?): Boolean {
        return other is FontDetail
                && weight == other.weight
                && width == other.width
                && italics == other.italics
    }

    private fun generateStyleName(font: MutableFontDetail): String {
        if (font.styleName.isNotEmpty()) {
            return font.styleName
        }
        return getWeightStyleName(font.weight) + getItalicStyleNameSuffix(font.italics)
    }

    private fun getWeightStyleName(weight: Int): String {
        when (weight) {
            100 -> return "Thin"
            200 -> return "Extra-Light"
            300 -> return "Light"
            400 -> return "Regular"
            500 -> return "Medium"
            600 -> return "Semi-Bold"
            700 -> return "Bold"
            800 -> return "Extra-Bold"
            900 -> return "Black"
            else -> return if (weight > 400) {
                "Custom-Bold"
            } else {
                "Custom-Light"
            }
        }
    }

    private fun getItalicStyleNameSuffix(italics: Float): String {
        return if (italics != NORMAL) " Italic" else ""
    }

    /** Formats a float without trailing zeros and uses period for the decimal separator */
    private fun Float.floatAsString(): String {
        return String.format(Locale.ROOT, if (this % 1f != 0f) "%s" else "%.0f", this)
    }
}
