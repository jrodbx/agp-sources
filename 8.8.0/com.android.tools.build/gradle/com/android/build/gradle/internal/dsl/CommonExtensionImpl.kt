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
import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.dsl.TestCoverage
import com.android.build.api.dsl.ViewBinding
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.utils.parseTargetHash
import com.android.build.gradle.internal.utils.validateNamespaceValue
import com.android.build.gradle.internal.utils.validatePreviewTargetValue
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.errors.IssueReporter
import com.android.repository.Revision
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import java.io.File
import java.util.function.Supplier

/** Internal implementation of the 'new' DSL interface */
abstract class CommonExtensionImpl<
        BuildFeaturesT : BuildFeatures,
        BuildTypeT : com.android.build.api.dsl.BuildType,
        DefaultConfigT : DefaultConfig,
        ProductFlavorT : com.android.build.api.dsl.ProductFlavor,
        AndroidResourcesT : AndroidResources,
        InstallationT : Installation>(
            protected val dslServices: DslServices,
            dslContainers: DslContainerProvider<DefaultConfigT, BuildTypeT, ProductFlavorT, SigningConfig>
        ) : InternalCommonExtension<
        BuildFeaturesT,
        BuildTypeT,
        DefaultConfigT,
        ProductFlavorT,
        AndroidResourcesT,
        InstallationT> {

    private val sourceSetManager = dslContainers.sourceSetManager

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

    override val aaptOptions: AaptOptions get() = androidResources as AaptOptions

    override fun aaptOptions(action: com.android.build.api.dsl.AaptOptions.() -> Unit) {
        action.invoke(aaptOptions)
    }

    override fun aaptOptions(action: Action<AaptOptions>) {
        action.execute(aaptOptions)
    }

    override fun androidResources(action: AndroidResourcesT.() -> Unit) {
        action(androidResources)
    }

    override fun androidResources(action: Action<AndroidResourcesT>) {
        action.execute(androidResources)
    }

    override fun installation(action: InstallationT.() -> Unit) {
        action.invoke(installation)
    }

    override fun installation(action: Action<InstallationT>) {
        action.execute(installation)
    }

    override val adbOptions: AdbOptions get() = installation as AdbOptions

    override fun adbOptions(action: com.android.build.api.dsl.AdbOptions.() -> Unit) {
        action.invoke(adbOptions)
    }

    override fun adbOptions(action: Action<AdbOptions>) {
        action.execute(adbOptions)
    }

    override fun buildFeatures(action: Action<BuildFeaturesT>) {
        action.execute(buildFeatures)
    }

    override fun buildFeatures(action: BuildFeaturesT.() -> Unit) {
        action(buildFeatures)
    }

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

    protected abstract var _compileSdkVersion: String?

    override var compileSdkVersion: String?
        get() = _compileSdkVersion

        set(value) {
            // set this first to enforce lockdown with right name
            _compileSdkVersion = value

            // then set the other values
            _compileSdk = null
            _compileSdkPreview = null
            _compileSdkAddon = null

            if (value == null) {
                return
            }

            val compileData = parseTargetHash(value)

            if (compileData.isAddon()) {
                _compileSdkAddon = "${compileData.vendorName}:${compileData.addonName}:${compileData.apiLevel}"
            } else {
                _compileSdk = compileData.apiLevel
                _compileSdkExtension = compileData.sdkExtension
                _compileSdkPreview = compileData.codeName
            }
        }

    protected abstract var _compileSdk: Int?

    override var compileSdk: Int?
        get() = _compileSdk
        set(value) {
            _compileSdk = value

            _compileSdkVersion = _compileSdk?.let { api ->
                if (_compileSdkExtension != null) {
                    "android-$api-ext$_compileSdkExtension"
                } else {
                    "android-$api"
                }
            }

            _compileSdkPreview = null
            _compileSdkAddon = null
        }

    protected abstract var _compileSdkExtension: Int?

    override var compileSdkExtension: Int?
        get() = _compileSdkExtension
        set(value) {
            _compileSdkExtension = value

            _compileSdkVersion = _compileSdk?.let { api ->
                if (value != null) {
                    "android-$api-ext$value"
                } else {
                    "android-$api"
                }
            }

            _compileSdkPreview = null
            _compileSdkAddon = null
        }

    private var _compileSdkPreview: String? = null

    override var compileSdkPreview: String?
        get() = _compileSdkPreview
        set(value) {
            if (value == null) {
                if (_compileSdkPreview != null) {
                    // if current compile sdk value is preview, then null it out.
                    _compileSdkPreview = null
                    _compileSdkVersion = null
                }
                return
            }

            // then set the values
            _compileSdk = null
            _compileSdkPreview = null
            _compileSdkAddon = null
            _compileSdkVersion = null

            val previewValue = validatePreviewTargetValue(value)
            if (previewValue != null) {
                _compileSdkPreview = previewValue
                _compileSdkVersion = "android-$previewValue"
            } else {
                if (value.toIntOrNull() != null) {
                    dslServices.issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        RuntimeException("Invalid integer value for compileSdkPreview ($value). Use compileSdk instead")
                    )
                } else {
                    val expected = if (value.startsWith("android-")) value.substring(8) else "S"
                    dslServices.issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        RuntimeException("Invalid value for compileSdkPreview (\"$value\"). Value must be a platform preview name (e.g. \"$expected\")")
                    )
                }
            }
        }

    private var _compileSdkAddon: String? = null

    override fun compileSdkAddon(vendor: String, name: String, version: Int) {
        _compileSdkAddon = "$vendor:$name:$version"

        _compileSdkVersion = _compileSdkAddon
        _compileSdk = null
        _compileSdkExtension = null
        _compileSdkPreview = null
    }

    override fun compileSdkVersion(apiLevel: Int) {
        compileSdk = apiLevel
    }

    override fun compileSdkVersion(version: String) {
        compileSdkVersion = version
    }

    override val composeOptions: ComposeOptionsImpl =
        dslServices.newInstance(ComposeOptionsImpl::class.java, dslServices)

    override fun composeOptions(action: ComposeOptions.() -> Unit) {
        action.invoke(composeOptions)
    }

    override fun composeOptions(action: Action<ComposeOptions>) {
        action.execute(composeOptions)
    }

    override fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildType>>) {
        action.execute(buildTypes as NamedDomainObjectContainer<BuildType>)
    }

    override fun buildTypes(action: NamedDomainObjectContainer<BuildTypeT>.() -> Unit) {
        action.invoke(buildTypes)
    }

    override fun NamedDomainObjectContainer<BuildTypeT>.debug(action: BuildTypeT.() -> Unit) {
        getByName("debug", action)
    }

    override fun NamedDomainObjectContainer<BuildTypeT>.release(action: BuildTypeT.() -> Unit)  {
        getByName("release", action)
    }

    override val dataBinding: DataBindingOptions =
        dslServices.newDecoratedInstance(
            DataBindingOptions::class.java,
            Supplier { buildFeatures },
            dslServices
        )

    override fun dataBinding(action: com.android.build.api.dsl.DataBinding.() -> Unit) {
        action.invoke(dataBinding)
    }

    override fun dataBinding(action: Action<DataBindingOptions>) {
        action.execute(dataBinding)
    }

    override val viewBinding: ViewBindingOptionsImpl
        get() = dslServices.newDecoratedInstance(
            ViewBindingOptionsImpl::class.java,
            Supplier { buildFeatures },
            dslServices
        )

    override fun viewBinding(action: Action<ViewBindingOptionsImpl>) {
        action.execute(viewBinding)
    }

    override fun viewBinding(action: ViewBinding.() -> Unit) {
        action.invoke(viewBinding)
    }

    override fun defaultConfig(action: Action<com.android.build.gradle.internal.dsl.DefaultConfig>) {
        action.execute(defaultConfig as com.android.build.gradle.internal.dsl.DefaultConfig)
    }

    override fun defaultConfig(action: DefaultConfigT.() -> Unit) {
        action.invoke(defaultConfig)
    }

    override val testCoverage: TestCoverage  = dslServices.newInstance(JacocoOptions::class.java)

    override fun testCoverage(action: TestCoverage.() -> Unit) {
        action.invoke(testCoverage)
    }

    override fun testCoverage(action: Action<TestCoverage>) {
        action.execute(testCoverage)
    }

    override val jacoco: JacocoOptions
        get() = testCoverage as JacocoOptions

    override fun jacoco(action: com.android.build.api.dsl.JacocoOptions.() -> Unit) {
        action.invoke(jacoco)
    }

    override fun jacoco(action: Action<JacocoOptions>) {
        action.execute(jacoco)
    }

    final override val lintOptions: LintOptions by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dslServices.newInstance(LintOptions::class.java, dslServices, lint)
    }

    override fun lintOptions(action: com.android.build.api.dsl.LintOptions.() -> Unit) {
        action.invoke(lintOptions)
    }

    override fun lintOptions(action: Action<LintOptions>) {
        action.execute(lintOptions)
    }

    override val packagingOptions: com.android.build.gradle.internal.dsl.PackagingOptions
        get() = packaging as com.android.build.gradle.internal.dsl.PackagingOptions

    override fun packagingOptions(action: Packaging.() -> Unit) {
        action.invoke(packaging)
    }

    override fun packagingOptions(action: Action<com.android.build.gradle.internal.dsl.PackagingOptions>) {
        action.execute(packaging as com.android.build.gradle.internal.dsl.PackagingOptions)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>) {
        action.execute(productFlavors as NamedDomainObjectContainer<ProductFlavor>)
    }

    override fun productFlavors(action: NamedDomainObjectContainer<ProductFlavorT>.() -> Unit) {
        action.invoke(productFlavors)
    }

    override fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>) {
        action.execute(signingConfigs)
    }

    override fun signingConfigs(action: NamedDomainObjectContainer<out ApkSigningConfig>.() -> Unit) {
        action.invoke(signingConfigs)
    }

    override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>
        get() = sourceSetManager.sourceSetsContainer

    override fun sourceSets(action: NamedDomainObjectContainer<out com.android.build.api.dsl.AndroidSourceSet>.() -> Unit) {
        sourceSetManager.executeAction(action)
    }

    override fun sourceSets(action: Action<NamedDomainObjectContainer<AndroidSourceSet>>) {
        action.execute(sourceSets)
    }

    override val testOptions: TestOptions =
        dslServices.newInstance(TestOptions::class.java, dslServices)

    override fun testOptions(action: com.android.build.api.dsl.TestOptions.() -> Unit) {
        action.invoke(testOptions)
    }

    override fun testOptions(action: Action<TestOptions>) {
        action.execute(testOptions)
    }

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

    override fun flavorDimensions(vararg dimensions: String) {
        flavorDimensions.clear()
        flavorDimensions.addAll(dimensions)
    }

    override fun useLibrary(name: String) {
        useLibrary(name, true)
    }

    override fun useLibrary(name: String, required: Boolean) {
        libraryRequests.add(LibraryRequest(name, required))
    }

    override fun getDefaultProguardFile(name: String): File {
        if (!ProguardFiles.KNOWN_FILE_NAMES.contains(name)) {
            dslServices
                .issueReporter
                .reportError(
                    IssueReporter.Type.GENERIC, ProguardFiles.UNKNOWN_FILENAME_MESSAGE
                )
        }
        return ProguardFiles.getDefaultProguardFile(name, dslServices.buildDirectory)
    }

    override val experimentalProperties: MutableMap<String, Any> = mutableMapOf()
}
