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

private const val FAMILY = "family"
private const val FONT = "font"
private const val ATTR_FONT_NAME = "name"
private const val ATTR_STYLE_NAME = "styleName"
private const val ATTR_MENU = "menu"
private const val ATTR_MENU_NAME = "menuName"
private const val ATTR_IS_VF = "isVf"
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
        font.type = attributes.getValue(ATTR_IS_VF).parseType()
        font.weight = attributes.getValue(ATTR_WEIGHT).parseIntOrDefault(DEFAULT_WEIGHT)
        font.width = attributes.getValue(ATTR_WIDTH).parseFloatOrDefault(DEFAULT_WIDTH)
        font.italics = attributes.getValue(ATTR_ITALIC).parseFloatOrDefault(NORMAL)
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

  private fun String?.parseType(): FontType {
    if (this == null) {
      return FontType.SINGLE
    }
    return if (this.equals("true", ignoreCase = true)) FontType.VARIABLE else FontType.SINGLE
  }

  private fun String?.parseIntOrDefault(defaultValue: Int): Int {
    if (this == null) {
      return defaultValue
    }
    return try {
      Integer.parseInt(this)
    } catch (_: NumberFormatException) {
      defaultValue
    }
  }

  private fun String?.parseFloatOrDefault(defaultValue: Float): Float {
    if (this == null) {
      return defaultValue
    }
    return try {
      java.lang.Float.parseFloat(this)
    } catch (_: NumberFormatException) {
      defaultValue
    }
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
