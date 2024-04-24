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

import java.util.Objects

const val DEFAULT_WEIGHT = 400
const val DEFAULT_WIDTH = 100

/**
 * A [FontDetail] is a reference to a specific font with weight, width, and italics attributes.
 * Each instance refers to a possibly remote (*.ttf) font file.
 */
class FontDetail {
    val family: FontFamily
    val weight: Int
    val width: Int
    val italics: Boolean
    val fontUrl: String
    val styleName: String
    val hasExplicitStyle: Boolean

    val fontStyle: String
        get() = if (italics) "italic" else "normal"

    constructor(fontFamily: FontFamily, font: MutableFontDetail) {
        family = fontFamily
        weight = font.weight
        width = font.width
        italics = font.italics
        fontUrl = font.fontUrl
        hasExplicitStyle = font.hasExplicitStyle
        styleName = generateStyleName(font)
    }

    /**
     * Special use for creating synonyms in font-family files with references to other fonts.
     */
    constructor(detail: FontDetail, withStyle: MutableFontDetail) {
        family = detail.family
        weight = withStyle.weight
        width = withStyle.width
        italics = withStyle.italics
        fontUrl = detail.fontUrl
        hasExplicitStyle = detail.hasExplicitStyle
        styleName = generateStyleName(withStyle)
    }


    fun toMutableFontDetail(): MutableFontDetail {
        return MutableFontDetail(weight, width, italics, fontUrl, styleName, false, hasExplicitStyle)
    }

    fun generateQuery(exact: Boolean): String {
        if (weight == DEFAULT_WEIGHT && width == DEFAULT_WIDTH && !italics && !exact) {
            return family.name
        }
        val query = StringBuilder().append("name=").append(family.name)
        if (weight != DEFAULT_WEIGHT) {
            query.append("&weight=").append(weight)
        }
        if (italics) {
            query.append("&italic=1")
        }
        if (width != DEFAULT_WIDTH) {
            query.append("&width=").append(width)
        }
        if (exact) {
            query.append("&besteffort=false")
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

    private fun getItalicStyleNameSuffix(italics: Boolean): String {
        return if (italics) " Italic" else ""
    }
}
