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

package com.android.build.gradle.internal.core.dsl.impl

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.DynamicFeatureBuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.VariantDimension
import com.android.build.api.transform.Transform
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.MergedJavaCompileOptions
import com.android.build.gradle.internal.core.MergedOptions
import com.android.build.gradle.internal.core.PostProcessingBlockOptions
import com.android.build.gradle.internal.core.PostProcessingOptions
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.ComponentType
import com.android.builder.model.BaseConfig
import com.android.builder.model.ClassField
import com.android.builder.model.VectorDrawablesOptions
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.DirectoryProperty
import java.io.File

internal abstract class ComponentDslInfoImpl internal constructor(
    override val componentIdentity: ComponentIdentity,
    final override val componentType: ComponentType,
    protected val defaultConfig: DefaultConfig,
    /**
     * Public because this is needed by the old Variant API. Nothing else should touch this.
     */
    val buildTypeObj: BuildType,
    override val productFlavorList: List<ProductFlavor>,
    protected val services: VariantServices,
    private val buildDirectory: DirectoryProperty,
    @Deprecated("use extension") private val oldExtension: BaseExtension?,
    protected val extension: CommonExtension<*, *, *, *>
): ComponentDslInfo {

    override val buildType: String?
        get() = componentIdentity.buildType
    override val productFlavors: List<Pair<String, String>>
        get() = componentIdentity.productFlavors

    /**
     * This should be mostly private and not used outside this class, but is still public for legacy
     * variant API and model v1 support.
     *
     * At some point we should remove this and rely on each property to combine dsl values in the
     * manner that it is meaningful for the property. Take a look at
     * [VariantDslInfoImpl.initApplicationId] for guidance on how will that look like.
     *
     * DO NOT USE. You should mostly use the interfaces which does not give access to this.
     */
    val mergedFlavor: MergedFlavor by lazy {
        MergedFlavor.mergeFlavors(
            defaultConfig,
            productFlavorList.map { it as com.android.build.gradle.internal.dsl.ProductFlavor },
            applicationId,
            services
        )
    }

    final override val javaCompileOptionsSetInDSL = MergedJavaCompileOptions()

    init {
        computeMergedOptions(
            javaCompileOptionsSetInDSL,
            { javaCompileOptions as JavaCompileOptions },
            { javaCompileOptions as JavaCompileOptions }
        )
    }

    // merged flavor delegates

    override val missingDimensionStrategies: ImmutableMap<String, AbstractProductFlavor.DimensionRequest>
        get() = ImmutableMap.copyOf(mergedFlavor.missingDimensionStrategies)

    override val resourceConfigurations: ImmutableSet<String>
        get() = ImmutableSet.copyOf(mergedFlavor.resourceConfigurations)

    override val vectorDrawables: VectorDrawablesOptions
        get() = mergedFlavor.vectorDrawables

    // extension delegates

    override val compileOptions: CompileOptions
        get() = extension.compileOptions
    override val androidResources: AndroidResources
        get() = extension.androidResources
    override val transforms: List<Transform>
        get() = oldExtension?.transforms ?: emptyList()

    // build type delegates

    override val isPseudoLocalesEnabled: Boolean
        get() = buildTypeObj.isPseudoLocalesEnabled

    override val isAndroidTestCoverageEnabled: Boolean
        get() = buildTypeObj.enableAndroidTestCoverage || buildTypeObj.isTestCoverageEnabled

    override val isCrunchPngs: Boolean?
        get() {
            return when (buildTypeObj) {
                is ApplicationBuildType -> buildTypeObj.isCrunchPngs
                is DynamicFeatureBuildType -> buildTypeObj.isCrunchPngs
                else -> false
            }
        }

    override val postProcessingOptions: PostProcessingOptions by lazy {
        if ((buildTypeObj as com.android.build.gradle.internal.dsl.BuildType)
                .postProcessingConfiguration ==
            com.android.build.gradle.internal.dsl.BuildType.PostProcessingConfiguration.POSTPROCESSING_BLOCK
        ) {
            PostProcessingBlockOptions(
                buildTypeObj.postprocessing, componentType.isTestComponent
            )
        } else object : PostProcessingOptions {
            override fun getProguardFiles(type: ProguardFileType): Collection<File> =
                buildTypeObj.getProguardFiles(type)

            override fun getDefaultProguardFiles(): List<File> =
                listOf(
                    ProguardFiles.getDefaultProguardFile(
                        ProguardFiles.ProguardFile.DONT_OPTIMIZE.fileName,
                        buildDirectory
                    )
                )

            override fun getPostprocessingFeatures(): PostprocessingFeatures? = null

            override fun codeShrinkerEnabled() = buildTypeObj.isMinifyEnabled

            override fun resourcesShrinkingEnabled(): Boolean = buildTypeObj.isShrinkResources

            override fun hasPostProcessingConfiguration() = false
        }
    }

    override fun gatherProguardFiles(type: ProguardFileType): Collection<File> {
        val result: MutableList<File> = ArrayList(defaultConfig.getProguardFiles(type))
        for (flavor in productFlavorList) {
            result.addAll((flavor as com.android.build.gradle.internal.dsl.ProductFlavor).getProguardFiles(type))
        }
        result.addAll(postProcessingOptions.getProguardFiles(type))
        return result
    }

    override val isCrunchPngsDefault: Boolean
        // does not exist in the new DSL
        get() = (buildTypeObj as com.android.build.gradle.internal.dsl.BuildType).isCrunchPngsDefault

    // helper methods

    override fun getResValues(): Map<ResValue.Key, ResValue> {
        val resValueFields = mutableMapOf<ResValue.Key, ResValue>()

        fun addToListIfNotAlreadyPresent(classField: ClassField, comment: String) {
            val key = ResValueKeyImpl(classField.type, classField.name)
            if (!resValueFields.containsKey(key)) {
                resValueFields[key] = ResValue(
                    value = classField.value,
                    comment = comment
                )
            }
        }

        (buildTypeObj as com.android.build.gradle.internal.dsl.BuildType).resValues.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Value from build type: ${buildTypeObj.name}")
        }

        productFlavorList.forEach { flavor ->
            (flavor as com.android.build.gradle.internal.dsl.ProductFlavor).resValues.values.forEach { classField ->
                addToListIfNotAlreadyPresent(
                    classField,
                    "Value from product flavor: ${flavor.name}"
                )
            }
        }

        defaultConfig.resValues.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Value from default config.")
        }

        return resValueFields
    }


    /**
     * Merge a specific option in GradleVariantConfiguration.
     *
     *
     * It is assumed that merged option type with a method to reset and append is created for the
     * option being merged.
     *
     *
     * The order of priority is BuildType, ProductFlavors, and default config. ProductFlavor
     * added earlier has higher priority than ProductFlavor added later.
     *
     * @param mergedOption The merged option store in the GradleVariantConfiguration.
     * @param getFlavorOption A Function to return the option from a ProductFlavor.
     * @param getBuildTypeOption A Function to return the option from a BuildType.
     * takes priority and overwrite option in the first input argument.
     * @param <CoreOptionsT> The core type of the option being merge.
     * @param <MergedOptionsT> The merge option type.
    </MergedOptionsT></CoreOptionsT> */
    protected fun <CoreOptionsT, MergedOptionsT : MergedOptions<CoreOptionsT>> computeMergedOptions(
        mergedOption: MergedOptionsT,
        getFlavorOption: VariantDimension.() -> CoreOptionsT?,
        getBuildTypeOption: BuildType.() -> CoreOptionsT?
    ) {
        mergedOption.reset()

        val defaultOption = defaultConfig.getFlavorOption()
        if (defaultOption != null) {
            mergedOption.append(defaultOption)
        }
        // reverse loop for proper order
        for (i in productFlavorList.indices.reversed()) {
            val flavorOption = productFlavorList[i].getFlavorOption()
            if (flavorOption != null) {
                mergedOption.append(flavorOption)
            }
        }
        val buildTypeOption = buildTypeObj.getBuildTypeOption()
        if (buildTypeOption != null) {
            mergedOption.append(buildTypeOption)
        }
    }

    private fun BaseConfig.getProguardFiles(type: ProguardFileType): Collection<File> = when (type) {
        ProguardFileType.EXPLICIT -> this.proguardFiles
        ProguardFileType.TEST -> this.testProguardFiles
        ProguardFileType.CONSUMER -> this.consumerProguardFiles
    }

    /**
     * Combines all the appId suffixes into a single one.
     *
     * The suffixes are separated by '.' whether their first char is a '.' or not.
     */
    protected fun computeApplicationIdSuffix(): String {
        // for the suffix we combine the suffix from all the flavors. However, we're going to
        // want the higher priority one to be last.
        val suffixes = mutableListOf<String>()
        defaultConfig.applicationIdSuffix?.let {
            suffixes.add(it)
        }

        suffixes.addAll(
            productFlavorList
                .asSequence()
                .filterIsInstance(ApplicationProductFlavor::class.java)
                .mapNotNull { it.applicationIdSuffix })

        // then we add the build type after.
        (buildTypeObj as? ApplicationBuildType)?.applicationIdSuffix?.let {
            suffixes.add(it)
        }
        val nonEmptySuffixes = suffixes.filter { it.isNotEmpty() }
        return if (nonEmptySuffixes.isNotEmpty()) {
            ".${nonEmptySuffixes.joinToString(separator = ".", transform = { it.removePrefix(".") })}"
        } else {
            ""
        }
    }
}
