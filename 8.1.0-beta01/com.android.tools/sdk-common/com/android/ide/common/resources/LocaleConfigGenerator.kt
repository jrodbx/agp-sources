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

package com.android.ide.common.resources

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.FileInputStream
import java.util.Properties

fun generateLocaleConfigManifestAttribute(localeConfigFileName: String): String {
    return "@xml/${localeConfigFileName}"
}

@VisibleForTesting
fun generateLocaleString(localeQualifier: LocaleQualifier): String {
    return localeQualifier.language!! +
            (localeQualifier.script?.let { script -> "-$script" } ?: "") +
            (localeQualifier.region?.let { region -> "-$region" } ?: "")
}

fun generateLocaleList(resources: Collection<File>): List<String> {
    // Fold all root resource directories into a one-dimensional
    // list of qualified resource directories (main/res -> main/res/values-en-rUS, etc.)
    val allResources = resources.fold(mutableListOf<File>()) { acc, it ->
        acc.addAll(it.listFiles()?.toList() ?: listOf())
        acc
    }.filter {
        it.isDirectory && it.listFiles()!!.isNotEmpty() // Ignore empty folders and files
    }
    // Then process all folder names into folder configurations, and then get the locale qualifier
    val localeQualifiers = mutableListOf<LocaleQualifier?>()
    allResources.forEach {
        FolderConfiguration.getConfig(it.name.split("-"))
            ?.let { folderConfig -> localeQualifiers.add(folderConfig.localeQualifier) }
    }
    // Finally, extract the locale name to be used in the configuration file
    val supportedLocales = mutableSetOf<String>()
    localeQualifiers.forEach {
        it?.let { localeQualifier ->
            supportedLocales.add(generateLocaleString(localeQualifier))
        }
    }
    return supportedLocales.toList()
}

fun mergeLocaleLists(allLocales: Collection<Collection<String>>): List<String> {
    val foldedLocales = allLocales.fold(mutableSetOf<String>()) { acc, it ->
        acc.addAll(it)
        acc
    }
    return foldedLocales.toList()
}

fun writeSupportedLocales(output: File, locales: Collection<String>, defaultLocale: String?) {
    if (defaultLocale.isNullOrEmpty()) {
        output.writeText(listOf(*(locales.toTypedArray())).joinToString("\n"))
    } else {
        output.writeText(
            listOf(defaultLocale, *(locales.toTypedArray())).joinToString("\n")
        )
    }
}

/* Converts the locale string to BCP-47, processes it and return a locale string with the same
 * formatting as the others. Returns null if the locale is invalid. This method does not check if
 * a locale exists.
 * By returning a re-processed string, any user-introduced formatting differences are removed
 * and the string will be comparable with others.
 * Strings are supposed to be in the following format: zh-Hans-SG
 */
fun validateLocale(locale: String): String? {
    val localeQualifier = LocaleQualifier.parseBcp47(
        LocaleQualifier.BCP_47_PREFIX + locale.replace("-", "+")
    )
    return localeQualifier?.run { generateLocaleString(localeQualifier) }
}

fun writeLocaleConfig(output: File, locales: Collection<String>) {
    val localeSet = locales.toMutableSet()
    val outLines = mutableListOf<String>()
    outLines.add("<locale-config xmlns:android=\"http://schemas.android.com/apk/res/android\">")
    localeSet.forEach { localeString ->
        outLines.add("    <locale android:name=\"$localeString\"/>")
    }
    outLines.add("</locale-config>")
    output.writeText(outLines.joinToString(System.lineSeparator()))
}

fun readResourcesPropertiesFile(inputFiles: Collection<File>): String? {
    var defaultLocale: String? = null
    inputFiles.forEach { input ->
        val properties = Properties()
        FileInputStream(input).use {
            properties.load(it)
        }
        if (properties.containsKey("unqualifiedResLocale")) {
            val unqualifiedResLocale: String? = properties.getProperty("unqualifiedResLocale")
            if (defaultLocale != null && defaultLocale != unqualifiedResLocale) {
                throw RuntimeException("Multiple resources.properties files found with " +
                        "different unqualifiedResLocale values. " +
                        "See https://developer.android.com/r/studio-ui/build/automatic-per-app-languages")
            } else {
                defaultLocale = unqualifiedResLocale
            }
        }
    }
    return defaultLocale
}
