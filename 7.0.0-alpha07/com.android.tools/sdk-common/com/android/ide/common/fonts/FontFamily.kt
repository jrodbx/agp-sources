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

import com.google.common.collect.ImmutableList
import java.util.Collections
import java.util.Comparator
import java.util.Objects

const val FILE_PROTOCOL_START = "file://"
const val HTTPS_PROTOCOL_START = "https://"

class FontFamily : Comparable<FontFamily> {
    val provider: FontProvider
    val fontSource: FontSource
    val name: String
    val menu: String
    val menuName: String
    val fonts: List<FontDetail>

    constructor(provider: FontProvider,
                fontSource: FontSource,
                name: String,
                menu: String,
                menuName: String,
                fonts: List<MutableFontDetail>) {
        this.provider = provider
        this.fontSource = fontSource
        this.name = name
        this.menu = menu
        this.menuName = if (menuName.isNotEmpty()) menuName else name
        this.fonts = build(fonts)
    }

    /**
     * Special use for creating synonyms in font-family files with references to other fonts.
     * The fontFamily property of each [FontDetail] will not be the [FontFamily] created here.
     */
    constructor(provider: FontProvider,
                fontSource: FontSource,
                name: String,
                menu: String,
                menuName: String,
                fonts: ImmutableList<FontDetail>) {
        this.provider = provider
        this.fontSource = fontSource
        this.name = name
        this.menu = menu
        this.menuName = if (menuName.isNotEmpty()) menuName else name
        this.fonts = fonts
    }

    /**
     * Special use for creating a [FontFamily] as a lookup key.
     */
    constructor(provider: FontProvider, name: String) :
            this(provider, FontSource.LOOKUP, name, "", "", Collections.emptyList())

    override fun compareTo(other: FontFamily): Int {
        return Comparator
                .comparing(FontFamily::name)
                .thenComparing(FontFamily::provider)
                .compare(this, other)
    }

    override fun hashCode(): Int {
        return Objects.hash(provider, name)
    }

    override fun equals(other: Any?): Boolean {
        return other is FontFamily && provider == other.provider && name == other.name
    }

    private fun build(fonts: List<MutableFontDetail>): ImmutableList<FontDetail> {
        val details = ImmutableList.builder<FontDetail>()
        for (font in fonts) {
            if (!font.fontUrl.isEmpty()) {
                details.add(FontDetail(this, font))
            }
        }
        return details.build()
    }
}
