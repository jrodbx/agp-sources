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

import com.android.annotations.concurrency.GuardedBy
import com.android.utils.XmlUtils
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.URL
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

const val FONT_PROVIDERS = "providers"
const val FONT_PROVIDERS_FILENAME = "provider_directory.xml"
const val FONTS_FOLDER = "fonts"
const val FONT_DIRECTORY_FOLDER = "directory"
const val FONT_DIRECTORY_FILENAME = "font_directory.xml"

open class FontLoader {
    protected val lock = Object()
    @GuardedBy("lock") protected var sdkHome: File? = null
    @GuardedBy("lock") protected var providers = HashMap<String, FontProvider>()
    @GuardedBy("lock") protected var fonts = HashMap<FontFamily, FontFamily>()
    @GuardedBy("lock") protected var sortedFontFamilies = ArrayList<FontFamily>()

    companion object {
        @JvmField protected var instance: FontLoader? = null

        @JvmStatic fun getInstance(sdkHome: File?): FontLoader {
            if (instance == null) {
                instance = FontLoader()
            }
            instance!!.clear(sdkHome)
            return instance!!
        }
    }

    val fontPath: File?
        get() {
            synchronized(lock) {
                if (sdkHome == null) {
                    return null
                }
                return File(sdkHome, FONTS_FOLDER)
            }
        }

    val fontFamilies: List<FontFamily>
        get() {
            synchronized(lock) {
                lazyLoad()
                return ArrayList<FontFamily>(sortedFontFamilies)
            }
        }

    /**
     * After downloading a new directory, the instances maintained by this class can be
     * updated by calling this method.
     */
    fun loadDirectory(provider: FontProvider, url: URL) {
        val loadedFonts = loadDirectory(provider, InputSource(url.toString()))
        mergeFonts(provider, loadedFonts)
    }

    fun findProvider(authority: String): FontProvider? {
        synchronized(lock) {
            lazyLoad()
            return providers[authority]
        }
    }

    fun findOnlyKnownProvider(): FontProvider? {
        synchronized(lock) {
            lazyLoad()
            if (providers.size != 1) {
                return null
            }
            return providers.values.first()
        }
    }

    fun fontsLoaded(): Boolean {
        synchronized(lock) {
            lazyLoad()
            return fonts.isNotEmpty()
        }
    }

    fun findFont(provider: FontProvider, fontName: String): FontFamily? {
        synchronized(lock) {
            lazyLoad()
            return fonts[FontFamily(provider, fontName)]
        }
    }

    protected fun clear(newSdkHome: File?) {
        synchronized(lock) {
            if (sdkHome != newSdkHome) {
                sdkHome = newSdkHome
                providers.clear()
                fonts.clear()
            }
        }
    }

    private fun lazyLoad() {
        synchronized(lock) {
            if (providers.isNotEmpty()) {
                return
            }
            loadProviders()
            loadFonts()
        }
    }

    private fun loadProviders() {
        val localSdkHome = sdkHome
        if (localSdkHome != null && localSdkHome.exists()) {
            val fontFolder = File(localSdkHome, FONTS_FOLDER)
            val providerFolder = File(fontFolder, FONT_PROVIDERS)
            val providerFile = File(providerFolder, FONT_PROVIDERS_FILENAME)
            if (providerFile.exists()) {
                val providerList = loadProviders(InputSource(FileReader(providerFile)))
                for (provider in providerList) {
                    providers[provider.authority] = provider
                }
            }
        }
        if (providers.isEmpty()) {
            providers[GOOGLE_FONT_AUTHORITY] = FontProvider.GOOGLE_PROVIDER
        }
    }

    open protected fun loadFonts() {
        val localSdkHome = sdkHome ?: return
        val fontFolder = File(localSdkHome, FONTS_FOLDER)
        for (provider in providers.values) {
            val providerFolder = File(fontFolder, provider.authority)
            val directoryFolder = File(providerFolder, FONT_DIRECTORY_FOLDER)
            val directoryFile = File(directoryFolder, FONT_DIRECTORY_FILENAME)
            if (directoryFile.exists()) {
                val families = loadDirectory(provider, InputSource(FileReader(directoryFile)))
                for (family in families) {
                    fonts[family] = family
                }
                sortedFontFamilies.addAll(families)
            }
        }
        sortedFontFamilies.sort()
    }

    private fun loadProviders(source: InputSource): List<FontProvider> {
        val handler = ProviderHandler()
        parseXml(source, handler)
        return handler.fontProviders
    }

    private fun loadDirectory(provider: FontProvider, source: InputSource): List<FontFamily> {
        val handler = DirectoryHandler(provider)
        parseXml(source, handler)
        return handler.fontFamilies
    }

    @Throws(SAXException::class, ParserConfigurationException::class, IOException::class)
    private fun parseXml(source: InputSource, handler: DefaultHandler) {
        try {
            val factory = SAXParserFactory.newInstance()
            XmlUtils.configureSaxFactory(factory, false, false)
            val parser = XmlUtils.createSaxParser(factory)
            parser.parse(source, handler)
        } catch (ex: SAXException) {
        } catch (ex: ParserConfigurationException) {
        } catch (ex: IOException) {
        }
    }

    /**
     * Perform a merge sort of the existing fonts and the list form a newly completed font directory
     * download. This method will remove fonts that are from the specified provider, and add newly
     * found fonts. If there are multiple providers this method will be called after each completed
     * download.
     * The result is a list of fonts ordered by font name. Fonts from different providers will be
     * mixed together.
     * @param provider the provider of the newly downloaded fonts.
     * @param fontFamilies the newly list of font families from a provider sorted by name.
     */
    private fun mergeFonts(provider: FontProvider, fontFamilies: List<FontFamily>) {
        synchronized(lock) {
            val existingFonts = ArrayList(sortedFontFamilies)
            sortedFontFamilies.clear()
            Collections.sort(fontFamilies)
            val existing = existingFonts.iterator()
            val loaded = fontFamilies.iterator()
            var existingFont = next(existing)
            var loadedFont = next(loaded)
            while (existingFont != null && loadedFont != null) {
                when (existingFont.compareTo(loadedFont)) {
                    1 -> {
                        sortedFontFamilies.add(loadedFont)
                        fonts[loadedFont] = loadedFont
                        loadedFont = next(loaded)
                    }
                    -1 -> {
                        if (provider == existingFont.provider) {
                            fonts.remove(existingFont)
                        }
                        else {
                            sortedFontFamilies.add(existingFont)
                        }
                        existingFont = next(existing)
                    }
                    else -> {
                        sortedFontFamilies.add(loadedFont)
                        fonts[loadedFont] = loadedFont
                        existingFont = next(existing)
                        loadedFont = next(loaded)
                    }
                }
            }
            while (existingFont != null) {
                if (provider == existingFont.provider) {
                    fonts.remove(existingFont)
                }
                else {
                    sortedFontFamilies.add(existingFont)
                }
                existingFont = next(existing)
            }
            while (loadedFont != null) {
                sortedFontFamilies.add(loadedFont)
                fonts[loadedFont] = loadedFont
                loadedFont = next(loaded)
            }
        }
    }

    private fun next(iterator: Iterator<FontFamily>): FontFamily? {
        return if (iterator.hasNext()) iterator.next() else null
    }
}
