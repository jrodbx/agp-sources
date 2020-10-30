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

import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.util.ArrayList

private const val FAMILY = "family"
private const val FONT = "font"
private const val ATTR_FONT_NAME = "name"
private const val ATTR_STYLE_NAME = "styleName"
private const val ATTR_MENU = "menu"
private const val ATTR_MENU_NAME = "menuName"
private const val ATTR_WEIGHT = "weight"
private const val ATTR_WIDTH = "width"
private const val ATTR_ITALIC = "italic"
private const val ATTR_FONT_URL = "url"

internal class DirectoryHandler(private val provider: FontProvider) : DefaultHandler() {
    private val fontDetails = ArrayList<MutableFontDetail>()
    private var fontName = ""
    private var fontMenu = ""
    private var fontMenuName = ""

    val fontFamilies = ArrayList<FontFamily>()

    @Throws(SAXException::class)
    override fun startElement(uri: String, localName: String, name: String, attributes: Attributes) {
        when (name) {
            FAMILY -> {
                fontName = attributes.getValue(ATTR_FONT_NAME) ?: ""
                fontMenu = addProtocol(attributes.getValue(ATTR_MENU))
                fontMenuName = attributes.getValue(ATTR_MENU_NAME) ?: ""
            }
            FONT -> {
                val font = MutableFontDetail()
                font.weight = parseInt(attributes.getValue(ATTR_WEIGHT), DEFAULT_WEIGHT)
                font.width = parseInt(attributes.getValue(ATTR_WIDTH), DEFAULT_WIDTH)
                font.italics = parseItalics(attributes.getValue(ATTR_ITALIC))
                font.fontUrl = addProtocol(attributes.getValue(ATTR_FONT_URL))
                font.styleName = attributes.getValue(ATTR_STYLE_NAME) ?: ""
                if (font.weight > 0 && font.width > 0 && font.fontUrl.isNotEmpty()) {
                    fontDetails.add(font)
                }
            }
        }
    }

    override fun endElement(uri: String, localName: String, name: String) {
        if (name == FAMILY) {
            if (fontName.isNotEmpty() && fontMenu.isNotEmpty()) {
                fontFamilies.add(FontFamily(provider, FontSource.DOWNLOADABLE, fontName, fontMenu, fontMenuName, fontDetails))
            }
            fontDetails.clear()
        }
    }

    private fun parseInt(intAsString: String?, defaultValue: Int): Int {
        if (intAsString == null) {
            return defaultValue
        }
        return try {
            Math.round(java.lang.Float.parseFloat(intAsString))
        } catch (ex: NumberFormatException) {
            defaultValue
        }
    }

    fun parseItalics(italics: String?): Boolean {
        return italics != null && italics.startsWith("1")
    }

    private fun addProtocol(url: String?): String {
        if (url == null) {
            return ""
        }
        if (url.startsWith("//")) {
            return HTTPS_PROTOCOL_START + url.substring(2)
        }
        return url
    }
}
