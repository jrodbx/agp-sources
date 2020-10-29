/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.manifest

import com.android.SdkConstants
import com.android.build.gradle.internal.manifest.ManifestData.AndroidTarget
import com.android.build.gradle.internal.services.ProjectServices
import com.android.builder.errors.IssueReporter
import com.android.utils.XmlUtils
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.util.function.BooleanSupplier
import javax.xml.parsers.SAXParserFactory

/**
 * a lazy manifest parser that can create a `Provider<ManifestData>`
 */
class LazyManifestParser(
    private val manifestFile: Provider<RegularFile>,
    private val manifestFileRequired: Boolean,
    private val projectServices: ProjectServices,
    private val manifestParsingAllowed: BooleanSupplier
): ManifestDataProvider {

     override val manifestData: Provider<ManifestData> by lazy {
        // using map will allow us to keep task dependency should the manifest be generated or
        // transformed via a task
        val provider = manifestFile.map {
            parseManifest(
                it.asFile,
                manifestFileRequired,
                manifestParsingAllowed,
                projectServices.issueReporter
            )
        }

        // wrap the provider in a property to allow memoization
        projectServices.objectFactory.property(ManifestData::class.java).also {
            it.set(provider)
            it.finalizeValueOnRead()
            // TODO disable early get
        }
    }

    override val manifestLocation: String
        get() = manifestFile.get().asFile.absolutePath
}

private fun parseManifest(
    file: File,
    manifestFileRequired: Boolean,
    manifestParsingAllowed: BooleanSupplier,
    issueReporter: IssueReporter
): ManifestData {
    if (!manifestParsingAllowed.asBoolean) {
        // This is not an exception since we still want sync to succeed if this occurs.
        // Instead print part of the relevant stack trace so that the developer will know
        // how this occurred.
        val stackTrace = Thread.currentThread().stackTrace.map { it.toString() }.filter {
            !it.startsWith("com.android.build.gradle.internal.manifest.") && !it.startsWith("org.gradle.")
        }.subList(1, 10)
        val stackTraceString = stackTrace.joinToString(separator = "\n")
        issueReporter.reportWarning(
            IssueReporter.Type.MANIFEST_PARSED_DURING_CONFIGURATION,
            "The manifest is being parsed during configuration. Please either remove android.disableConfigurationManifestParsing from build.gradle or remove any build configuration rules that read the android manifest file.\n$stackTraceString"
        )
    }

    val data = ManifestData()

    if (!file.exists()) {
        if (manifestFileRequired) {
            issueReporter.reportError(
                IssueReporter.Type.MISSING_ANDROID_MANIFEST,
                "Manifest file does not exist: ${file.absolutePath}"
            )
            // init data with dummy values to prevent code down the line to have to deal with NPE
            data.packageName = "fake.package.name.for.sync"
        }

        return data
    }

    val handler: DefaultHandler =
        object : DefaultHandler() {
            @Throws(SAXException::class)
            override fun startElement(
                uri: String?,
                localName: String,
                qName: String,
                attributes: Attributes
            ) {
                if (uri.isNullOrEmpty()) {
                    when {
                        SdkConstants.TAG_MANIFEST == localName -> {
                            data.split = attributes.getValue("", SdkConstants.ATTR_SPLIT)
                            data.packageName = attributes.getValue("", SdkConstants.ATTR_PACKAGE)
                            data.versionCode =
                                attributes.getValue(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_VERSION_CODE
                                )?.toInt()

                            data.versionName =
                                attributes.getValue(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_VERSION_NAME
                                )

                        }
                        SdkConstants.TAG_INSTRUMENTATION == localName -> {
                            data.testLabel =
                                attributes.getValue(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_LABEL
                                )

                            data.functionalTest =
                                attributes.getValue(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_FUNCTIONAL_TEST
                                )?.toBoolean()

                            data.instrumentationRunner = attributes.getValue(
                                SdkConstants.ANDROID_URI,
                                SdkConstants.ATTR_NAME
                            )

                            data.handleProfiling =
                                attributes.getValue(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_HANDLE_PROFILING
                                )?.toBoolean()
                        }
                        SdkConstants.TAG_USES_SDK == localName -> {

                            data.minSdkVersion =
                                attributes.getValue(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_MIN_SDK_VERSION
                                )?.toAndroidTarget()

                            data.targetSdkVersion =
                                attributes.getValue(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_TARGET_SDK_VERSION
                                )?.toAndroidTarget()

                        }
                        SdkConstants.TAG_APPLICATION == localName -> {
                            data.extractNativeLibs =
                                attributes.getValue(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_EXTRACT_NATIVE_LIBS
                                )?.toBoolean()

                            data.useEmbeddedDex =
                                attributes.getValue(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_USE_EMBEDDED_DEX
                                )?.toBoolean()
                        }
                    }
                }
            }
        }

    try {
        val saxParser = XmlUtils.createSaxParser(PARSER_FACTORY)
        saxParser.parse(file, handler)
    } catch (e: Exception) {
        throw RuntimeException(e)
    }

    return data
}

private val PARSER_FACTORY = SAXParserFactory.newInstance().also {
    XmlUtils.configureSaxFactory(it, true, false)
}

private fun String.toAndroidTarget(): AndroidTarget {
    return try {
        val apiLevel = Integer.valueOf(this)

        AndroidTarget(apiLevel = apiLevel, codeName = null)
    } catch (ignored: NumberFormatException) {
        AndroidTarget(apiLevel = null, codeName = this)
    }
}

