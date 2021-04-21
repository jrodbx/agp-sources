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

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.dsl.TestCoverage
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.Variant
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import com.android.repository.Revision
import java.util.function.Supplier
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

/** Internal implementation of the 'new' DSL interface */
abstract class CommonExtensionImpl<
        BuildFeaturesT : BuildFeatures,
        BuildTypeT : com.android.build.api.dsl.BuildType,
        DefaultConfigT : DefaultConfig,
        ProductFlavorT : com.android.build.api.dsl.ProductFlavor,
        VariantBuilderT : VariantBuilder,
        VariantT : Variant>(
            protected val dslServices: DslServices,
            dslContainers: DslContainerProvider<DefaultConfigT, BuildTypeT, ProductFlavorT, SigningConfig>
        ) : InternalCommonExtension<
        BuildFeaturesT,
        BuildTypeT,
        DefaultConfigT,
        ProductFlavorT,
        VariantBuilderT,
        VariantT> {

    private val sourceSetManager = dslContainers.sourceSetManager

    private var buildToolsRevision: Revision = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION

    // This is exposed only to support AndroidConfig.libraryRequests
    // TODO: Make private when AndroidConfig is removed
    val libraryRequests: MutableList<LibraryRequest> = mutableListOf()

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

    override val androidResources: AndroidResources = dslServices.newInstance(
        AaptOptions::class.java,
        dslServices.projectOptions[BooleanOption.ENABLE_RESOURCE_NAMESPACING_DEFAULT]
    )

    override fun androidResources(action: AndroidResources.() -> Unit) {
        action.invoke(androidResources)
    }

    override val aaptOptions: AaptOptions get() = androidResources as AaptOptions

    override fun aaptOptions(action: com.android.build.api.dsl.AaptOptions.() -> Unit) {
        action.invoke(aaptOptions)
    }

    override val adbOptions: AdbOptions = dslServices.newInstance(AdbOptions::class.java)

    override fun adbOptions(action: com.android.build.api.dsl.AdbOptions.() -> Unit) {
        action.invoke(adbOptions)
    }

    fun buildFeatures(action: Action<BuildFeaturesT>) {
        action.execute(buildFeatures)
    }

    override fun buildFeatures(action: BuildFeaturesT.() -> Unit) {
        action(buildFeatures)
    }

    override val compileOptions: CompileOptions =
        dslServices.newInstance(CompileOptions::class.java)

    override fun compileOptions(action: com.android.build.api.dsl.CompileOptions.() -> Unit) {
        action.invoke(compileOptions)
    }

    protected abstract var compileSdkVersion: String?

    override var compileSdk: Int?
        get() {
            if (compileSdkVersion == null) {
                return null
            }
            if (compileSdkVersion!!.startsWith("android-")) {
                return try {
                    Integer.valueOf(compileSdkVersion!!.substring(8))
                } catch (e: Exception) {
                    null
                }
            }
            return null
        }
        set(value) {
            compileSdkVersion = if (value == null) null
            else "android-$value"
        }
    override var compileSdkPreview: String?
        get() = compileSdkVersion
        set(value) {
            compileSdkVersion = value
        }

    override fun compileSdkAddon(vendor: String, name: String, version: Int) {
        compileSdkVersion = "$vendor:$name:$version"
    }

    override val composeOptions: ComposeOptionsImpl =
        dslServices.newInstance(ComposeOptionsImpl::class.java, dslServices)

    override fun composeOptions(action: ComposeOptions.() -> Unit) {
        action.invoke(composeOptions)
    }

    override fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildTypeT>>) {
        action.execute(buildTypes)
    }

    override fun NamedDomainObjectContainer<BuildTypeT>.debug(action: BuildTypeT.() -> Unit) {
        getByName("debug", action)
    }

    override fun NamedDomainObjectContainer<BuildTypeT>.release(action: BuildTypeT.() -> Unit)  {
        getByName("release", action)
    }

    override val dataBinding: DataBindingOptions =
        dslServices.newInstance(
            DataBindingOptions::class.java,
            Supplier { buildFeatures },
            dslServices
        )

    override fun dataBinding(action: com.android.build.api.dsl.DataBinding.() -> Unit) {
        action.invoke(dataBinding)
    }

    override fun defaultConfig(action: Action<DefaultConfigT>) {
        action.execute(defaultConfig)
    }

    override val externalNativeBuild: ExternalNativeBuild =
        dslServices.newInstance(ExternalNativeBuild::class.java, dslServices)

    override fun externalNativeBuild(action: com.android.build.api.dsl.ExternalNativeBuild.() -> Unit) {
        action.invoke(externalNativeBuild)
    }

    override val testCoverage: TestCoverage  = dslServices.newInstance(JacocoOptions::class.java)

    override fun testCoverage(action: TestCoverage.() -> Unit) {
        action.invoke(testCoverage)
    }

    override val jacoco: JacocoOptions
        get() = testCoverage as JacocoOptions

    override fun jacoco(action: com.android.build.api.dsl.JacocoOptions.() -> Unit) {
        action.invoke(jacoco)
    }

    override val lint: Lint = dslServices.newInstance(LintOptions::class.java, dslServices)

    override fun lint(action: Lint.() -> Unit) {
        action.invoke(lint)
    }

    override val lintOptions: LintOptions
        get() = lint as LintOptions

    override fun lintOptions(action: com.android.build.api.dsl.LintOptions.() -> Unit) {
        action.invoke(lintOptions)
    }

    override val packagingOptions: PackagingOptions =
        dslServices.newInstance(PackagingOptions::class.java, dslServices)

    override fun packagingOptions(action: com.android.build.api.dsl.PackagingOptions.() -> Unit) {
        action.invoke(packagingOptions)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavorT>>) {
        action.execute(productFlavors)
    }

    override fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>) {
        action.execute(signingConfigs)
    }

    override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>
        get() = sourceSetManager.sourceSetsContainer

    override fun sourceSets(action: NamedDomainObjectContainer<AndroidSourceSet>.() -> Unit) {
        sourceSetManager.executeAction(action)
    }

    override val splits: Splits =
        dslServices.newInstance(Splits::class.java, dslServices)

    override fun splits(action: com.android.build.api.dsl.Splits.() -> Unit) {
        action.invoke(splits)
    }

    override val testOptions: TestOptions =
        dslServices.newInstance(TestOptions::class.java, dslServices)

    override fun testOptions(action: com.android.build.api.dsl.TestOptions.() -> Unit) {
        action.invoke(testOptions)
    }

    override var resourcePrefix: String? = null

    override var ndkVersion: String? = null

    override var ndkPath: String? = null

    override var buildToolsVersion: String
        get() = buildToolsRevision.toString()
        set(version) {
            //The underlying Revision class has the maven artifact semantic,
            // so 20 is not the same as 20.0. For the build tools revision this
            // is not the desired behavior, so normalize e.g. to 20.0.0.
            buildToolsRevision = Revision.parseRevision(version, Revision.Precision.MICRO)
        }

    override fun useLibrary(name: String) {
        useLibrary(name, true)
    }

    override fun useLibrary(name: String, required: Boolean) {
        libraryRequests.add(LibraryRequest(name, required))
    }

    override var namespace: String? = null
}
