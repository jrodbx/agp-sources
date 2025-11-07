/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.CompileSdkVersion
import com.android.build.api.dsl.MinSdkSpec
import com.android.build.api.dsl.MinSdkVersion
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.utils.parseTargetHash
import com.android.build.gradle.internal.utils.updateIfChanged
import com.android.build.gradle.internal.utils.validatePreviewTargetValue
import com.android.builder.core.DefaultApiVersion
import com.android.builder.errors.IssueReporter
import com.android.builder.model.ApiVersion
import org.gradle.api.Action

/**
 * Represents the properties for configuring the compile SDK version in an Android project.
 *
 * This interface provides various ways to specify the Android API level that the project will be
 * compiled against, including release versions, preview versions, and versions with extensions
 * or addons.
 *
 * This interface, along with its implementation, serves as a delegate to share common property
 * implementations between the standard Android Gradle Plugin extensions and the Kotlin
 * Multiplatform (KMP) Android target extension.
 */
interface CompileSdkProperties {
    var compileSdk: Int?
    var compileSdkPreview: String?
    var compileSdkExtension: Int?
    var compileSdkMinor: Int?
    var compileSdkVersion: String?
    fun compileSdk(action: CompileSdkSpec.() -> Unit)
    fun compileSdk(action: Action<CompileSdkSpec>)
    fun compileSdkVersion(apiLevel: Int)
    fun compileSdkVersion(version: String)
    fun compileSdkAddon(vendor: String, name: String, version: Int)
}

internal class CompileSdkDelegate(
    private val getCompileSdk: () -> CompileSdkVersion?,
    private val setCompileSdk: (CompileSdkVersion?) -> Unit,
    private val issueReporter: IssueReporter,
    private val dslServices: DslServices
): CompileSdkProperties {
    override var compileSdk: Int?
        get() {
            val currentSdk = getCompileSdk()
            val isAddonOrPreview = currentSdk?.vendorName != null || currentSdk?.codeName != null
            return if (!isAddonOrPreview) currentSdk?.apiLevel else null
        }
        set(value) {
            compileSdk { version = value?.let { release(it) }}
        }

    override var compileSdkPreview: String?
        get() = getCompileSdk()?.codeName
        set(value) {
            if (value == null) {
                if (getCompileSdk()?.codeName != null) {
                    setCompileSdk(null)
                }
                return
            }

            setCompileSdk(null)

            val previewValue = validatePreviewTargetValue(value)
            if (previewValue != null) {
                compileSdk { version = preview(previewValue) }
            } else {
                if (value.toIntOrNull() != null) {
                    issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        RuntimeException("Invalid integer value for compileSdkPreview ($value). Use compileSdk instead")
                    )
                } else {
                    val expected = if (value.startsWith("android-")) value.substring(8) else "S"
                    issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        RuntimeException("Invalid value for compileSdkPreview (\"$value\"). Value must be a platform preview name (e.g. \"$expected\")")
                    )
                }
            }
        }

    override var compileSdkExtension: Int?
        get() = getCompileSdk()?.sdkExtension
        set(value) {
            compileSdk {
                getCompileSdk()?.apiLevel?.let { apiLevel ->
                    version = release(apiLevel) {
                        sdkExtension = value
                        minorApiLevel = getCompileSdk()?.minorApiLevel
                    }
                }
            }
        }

    override var compileSdkMinor: Int?
        get() = getCompileSdk()?.minorApiLevel
        set(value) {
            getCompileSdk()?.apiLevel?.let { apiLevel ->
                compileSdk {
                    version = release(apiLevel) {
                        minorApiLevel = value
                        sdkExtension = version?.sdkExtension
                    }
                }
                if (apiLevel < 36 && getCompileSdk()?.minorApiLevel != null) {
                    dslServices.issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        RuntimeException("Minor versions are only supported for API 36 and above.")
                    )
                    compileSdk {
                        version = release(apiLevel) {
                            minorApiLevel = null
                            sdkExtension = version?.sdkExtension
                        }
                    }
                }
            }
        }

    override var compileSdkVersion: String?
        get() {
            val version = getCompileSdk()
            return CompileSdkVersionImpl(
                apiLevel = version?.apiLevel,
                minorApiLevel = version?.minorApiLevel,
                sdkExtension = version?.sdkExtension,
                codeName = version?.codeName,
                addonName = version?.addonName,
                vendorName = version?.vendorName,
            ).toHash()
        }
        set(value) {
            parseAndSetCompileSdkVersion(value)
        }

    override fun compileSdk(action: CompileSdkSpec.() -> Unit) {
        createCompileSdkSpec().also {
            action.invoke(it)
            updateIfChanged(getCompileSdk(), it.version) {
                setCompileSdk(it)
            }
        }
    }

    override fun compileSdk(action: Action<CompileSdkSpec>) {
        createCompileSdkSpec().also {
            action.execute(it)
            updateIfChanged(getCompileSdk(), it.version) {
                setCompileSdk(it)
            }
        }
    }

    override fun compileSdkVersion(apiLevel: Int) {
        compileSdk { version = release(apiLevel) }
    }

    override fun compileSdkVersion(version: String) {
        parseAndSetCompileSdkVersion(version)
    }

    override fun compileSdkAddon(vendor: String, name: String, version: Int) {
        compileSdk { this.version = addon(vendor, name, version) }
    }

    private fun createCompileSdkSpec(): CompileSdkSpecImpl {
        return dslServices.newDecoratedInstance(CompileSdkSpecImpl::class.java, dslServices).also {
            it.version = getCompileSdk()
        }
    }

    private fun parseAndSetCompileSdkVersion(value: String?) {
        // then set the other values
        setCompileSdk(null)

        if (value == null) {
            return
        }

        val compileData = parseTargetHash(value)

        if (compileData.isAddon()) {
            compileSdk {
                version = addon(
                    vendor = compileData.vendorName!!,
                    name = compileData.addonName!!,
                    version = compileData.apiLevel!!
                )
            }
        } else {
            if (compileData.codeName != null) {
                compileSdk {
                    version = preview(compileData.codeName)
                }
            } else {
                compileData.apiLevel?.let { apiLevel ->
                    compileSdk {
                        version = release(apiLevel) {
                            minorApiLevel = compileData.minorApiLevel
                            sdkExtension = compileData.sdkExtension
                        }
                    }
                }
            }
        }
    }
}

/**
 * Represents the properties for configuring the min SDK version for an Android module.
 *
 * This interface defines the minimum API level that the module's code is compatible with.
 * It provides methods to set this value using either a stable API level or a preview codename.
 *
 * This interface, along with its implementation, serves as a delegate to share common property
 * implementations between the standard Android Gradle Plugin extensions and the Kotlin
 * Multiplatform (KMP) Android target extension.
 */
interface MinSdkProperties {
    var minSdk: Int?
    var minSdkVersion: ApiVersion?
    var minSdkPreview: String?
    fun minSdk(action: MinSdkSpec.() -> Unit)
    fun minSdk(action: Action<MinSdkSpec>)
    fun setMinSdkVersion(minSdkVersion: Int)
    fun setMinSdkVersion(minSdkVersion: String?)
}

class MinSdkDelegate(
    private val getMinSdk: () -> MinSdkVersion?,
    private val setMinSdk: (MinSdkVersion?) -> Unit,
    private val dslServices: DslServices
): MinSdkProperties {
    override var minSdk: Int?
        get() = getMinSdk()?.apiLevel
        set(value) {
            minSdk { version = value?.let { release(value)} }
        }

    override var minSdkVersion: ApiVersion?
        get() = getMinSdk()?.let { DefaultApiVersion(it.apiLevel, it.codeName) }
        set(value) {
            if (value == null) {
                setMinSdk(null)
            } else {
                val codeName = value.getCodename()
                if (codeName != null) {
                    minSdk { version = preview(codeName) }
                } else {
                    minSdk { version = release(value.apiLevel) }
                }
            }
        }

    override var minSdkPreview: String?
        get() = getMinSdk()?.codeName
        set(value) {
            setMinSdkVersion(value)
        }

    override fun minSdk(action: MinSdkSpec.() -> Unit) {
        createMinSdkSpec().also {
            action.invoke(it)
            updateIfChanged(getMinSdk(), it.version) {
                setMinSdk(it)
            }
        }
    }

    override fun minSdk(action: Action<MinSdkSpec>) {
        createMinSdkSpec().also {
            action.execute(it)
            updateIfChanged(getMinSdk(), it.version) {
                setMinSdk(it)
            }
        }
    }

    override fun setMinSdkVersion(minSdkVersion: Int) {
        minSdk { version = release(minSdkVersion) }
    }

    override fun setMinSdkVersion(minSdkVersion: String?) {
        minSdk {
            version = minSdkVersion?.let { minSdkVersion ->
                val apiLevel = minSdkVersion.apiVersionToInt()
                if (apiLevel != null) {
                    release(apiLevel)
                } else {
                    preview(minSdkVersion)
                }
            }
        }
    }

    private fun createMinSdkSpec(): MinSdkSpecImpl {
        return dslServices.newDecoratedInstance(MinSdkSpecImpl::class.java, dslServices).also {
           it.version = getMinSdk()
        }
    }
}

/**
 * Try to convert apiVersion from String to Int if the String is probably consisted with digits
 *
 * Return exception when converting fails. Returns null when this apiVersion should be codeName.
 */
fun String.apiVersionToInt(): Int? {
    return if (this[0].isDigit()) {
        try {
            this.toInt()
        } catch (e: NumberFormatException) {
            throw RuntimeException("'$this' is not a valid API level. ", e)
        }
    } else null
}
