/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants.NDK_DEFAULT_VERSION
import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.CompileSdkVersion
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.SdkComponents
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.utils.validateNamespaceValue
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.errors.IssueReporter
import com.android.repository.Revision
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import java.io.File

/** Internal implementation of the 'new' DSL interface */
abstract class CommonExtensionImpl<
        BuildTypeT : com.android.build.api.dsl.BuildType,
        DefaultConfigT : DefaultConfig,
        ProductFlavorT : com.android.build.api.dsl.ProductFlavor>(
            protected val dslServices: DslServices,
            dslContainers: DslContainerProvider<DefaultConfigT, BuildTypeT, ProductFlavorT, SigningConfig>
        ) : InternalCommonExtension {

    protected val sourceSetManager = dslContainers.sourceSetManager

    private var buildToolsRevision: Revision = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION

    // This is exposed only to support AndroidConfig.libraryRequests
    // TODO: Make private when AndroidConfig is removed
    abstract val libraryRequests: MutableList<LibraryRequest>

    override val sdkComponents: SdkComponents by lazy {
        dslServices.newInstance(
            SdkComponentsImpl::class.java,
            dslServices,
            dslServices.provider(String::class.java, compileSdkVersion),
            dslServices.provider(Revision::class.java, buildToolsRevision),
            dslServices.provider(String::class.java, ndkVersion),
            dslServices.provider(String::class.java, ndkPath)
        )
    }

    override val buildTypes: NamedDomainObjectContainer<BuildTypeT> =
        dslContainers.buildTypeContainer

    override val defaultConfig: DefaultConfigT = dslContainers.defaultConfig

    override val productFlavors: NamedDomainObjectContainer<ProductFlavorT> =
        dslContainers.productFlavorContainer

    override val signingConfigs: NamedDomainObjectContainer<SigningConfig> =
        dslContainers.signingConfigContainer

    protected abstract var _namespace: String?

    override var namespace: String?
        get() = _namespace
        set(value) {
            _namespace = value
            val errorMsg = validateNamespaceValue(value)
            errorMsg?.let {
                dslServices.issueReporter.reportError(IssueReporter.Type.GENERIC, it)
            }
        }

    protected abstract var _compileSdk: CompileSdkVersion?

    private val compileSdkDelegate = CompileSdkDelegate(
        getCompileSdk = { _compileSdk },
        setCompileSdk = { _compileSdk = it },
        issueReporter = dslServices.issueReporter,
        dslServices = dslServices
    )

    override var compileSdkVersion: String?
        get() = compileSdkDelegate.compileSdkVersion
        set(value) {
            compileSdkDelegate.compileSdkVersion = value
        }

    override var compileSdk: Int?
        get() = compileSdkDelegate.compileSdk
        set(value) {
            compileSdkDelegate.compileSdk = value
        }

    override var compileSdkExtension: Int?
        get() = compileSdkDelegate.compileSdkExtension
        set(value) {
            compileSdkDelegate.compileSdkExtension = value
        }

    override var compileSdkPreview: String?
        get() = compileSdkDelegate.compileSdkPreview
        set(value) {
            compileSdkDelegate.compileSdkPreview = value
        }

    override var compileSdkMinor: Int?
        get() = compileSdkDelegate.compileSdkMinor
        set(value) {
            compileSdkDelegate.compileSdkMinor = value
        }

    override fun compileSdkAddon(vendor: String, name: String, version: Int) {
       compileSdkDelegate.compileSdkAddon(vendor, name, version)
    }

    override fun compileSdk(action: CompileSdkSpec.() -> Unit) {
        compileSdkDelegate.compileSdk(action)
    }

    override fun compileSdk(action: Action<CompileSdkSpec>) {
        compileSdkDelegate.compileSdk(action)
    }

    override fun compileSdkVersion(apiLevel: Int) {
        compileSdkDelegate.compileSdkVersion(apiLevel)
    }

    override fun compileSdkVersion(version: String) {
        compileSdkDelegate.compileSdkVersion(version)
    }

    override val packagingOptions: PackagingOptions
        get() = packaging as PackagingOptions


    override var buildToolsVersion: String
        get() = buildToolsRevision.toString()
        set(version) {
            //The underlying Revision class has the maven artifact semantic,
            // so 20 is not the same as 20.0. For the build tools revision this
            // is not the desired behavior, so normalize e.g. to 20.0.0.
            buildToolsRevision = Revision.parseRevision(version, Revision.Precision.MICRO)
        }

    override var ndkVersion: String = NDK_DEFAULT_VERSION

    override fun buildToolsVersion(buildToolsVersion: String) {
        this.buildToolsVersion = buildToolsVersion
    }

    override fun useLibrary(name: String) {
        useLibrary(name, true)
    }

    override fun useLibrary(name: String, required: Boolean) {
        libraryRequests.add(LibraryRequest(name, required))
    }

    override fun getDefaultProguardFile(name: String): File {
        if (dslServices.projectOptions[BooleanOption.R8_PROGUARD_ANDROID_TXT_DISALLOWED] &&
            name == ProguardFiles.ProguardFile.DONT_OPTIMIZE.fileName
        ) {
            dslServices
                .issueReporter
                .reportError(
                    IssueReporter.Type.GENERIC, ProguardFiles.DONTOPTIMIZE_DISALLOWED_MESSAGE
                )
        }
        if (!ProguardFiles.KNOWN_FILE_NAMES.contains(name)) {
            dslServices
                .issueReporter
                .reportError(
                    IssueReporter.Type.GENERIC, ProguardFiles.UNKNOWN_FILENAME_MESSAGE
                )
        }
        return ProguardFiles.getDefaultProguardFile(name, dslServices.buildDirectory)
    }

    override var enableKotlin: Boolean = true

    override val experimentalProperties: MutableMap<String, Any> = mutableMapOf()

    fun flavorDimensions(vararg dimensions: String) {
        flavorDimensions.clear()
        flavorDimensions.addAll(dimensions)
    }

    //TODO(b/421964815): remove the support for groovy space assignment(e.g `compileSdk 24`).
    @Deprecated(
        "To be removed after Gradle drops space assignment support. Use `compileSdk {}` instead."
    )
    open fun compileSdk(version: Int) {
        compileSdk = version
    }

    /** For groovy only (so `compileSdkVersion=2` works) */
    @Deprecated("Switch to the new CompileSdkSpec DSL.")
    open fun setCompileSdkVersion(apiLevel: Int?) {
        compileSdk = apiLevel
    }
}
