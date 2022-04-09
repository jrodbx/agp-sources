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
package com.android.build.api.component.impl

import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.core.dsl.ConsumableComponentDslInfo
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.builder.dexing.DexingType
import com.android.builder.errors.IssueReporter

/**
 * This class and subclasses are implementing methods defined in the CreationConfig
 * interfaces but should not be necessarily implemented by the VariantImpl
 * and subclasses. The reasons are usually because it makes more sense to implement
 * the method in a class hierarchy that follows the interface definition so to avoid
 * repeating implementation in various disparate VariantImpl sub-classes.
 *
 * Instead [com.android.build.api.variant.impl.VariantImpl] will delegate
 * to these objects for methods which are cross cutting across the Variant
 * implementation hierarchy.
 */

/**
 * Constructor for [ConsumableCreationConfigImpl].
 *
 * @param config configuration object that will be delegating calls to this
 * object, and will also provide access to other Variant configuration data
 * @param dslInfo variant configuration coming from the DSL.
 */
open class ConsumableCreationConfigImpl<T: ConsumableCreationConfig>(
    protected val config: T,
    protected open val dslInfo: ConsumableComponentDslInfo
) {

    val dexingType: DexingType
        get() =
            if (config is DynamicFeatureCreationConfig) {
                // dynamic features can always be build in native multidex mode
                DexingType.NATIVE_MULTIDEX
            } else if (config.isMultiDexEnabled) {
                if (config.minSdkVersion.getFeatureLevel() >= 21 ||
                    dslInfo.targetDeployApiFromIDE?.let { it >= 21 } == true
                ) {
                    // if minSdkVersion is 21+ or we are deploying to 21+ device, use native multidex
                    DexingType.NATIVE_MULTIDEX
                } else DexingType.LEGACY_MULTIDEX
            } else {
                DexingType.MONO_DEX
            }

    fun getNeedsMergedJavaResStream(): Boolean {
        // We need to create a stream from the merged java resources if we're in a library module,
        // or if we're in an app/feature module which uses the transform pipeline.
        return (dslInfo.componentType.isAar
                || dslInfo.transforms.isNotEmpty()
                || config.minifiedEnabled)
    }

    fun getJava8LangSupportType(): Java8LangSupport {
        // in order of precedence
        return if (!dslInfo.compileOptions.targetCompatibility.isJava8Compatible) {
            Java8LangSupport.UNUSED
        } else if (config.services.projectInfo.hasPlugin("me.tatarka.retrolambda")) {
            Java8LangSupport.RETROLAMBDA
        } else if (config.minifiedEnabled) {
            Java8LangSupport.R8
        } else {
            // D8 cannot be used if R8 is used
            Java8LangSupport.D8
        }
    }

    val isCoreLibraryDesugaringEnabled: Boolean
        get() = isCoreLibraryDesugaringEnabled(config)

    open val needsShrinkDesugarLibrary: Boolean
        get() = isCoreLibraryDesugaringEnabled(config)

    /**
     * Returns if core library desugaring is enabled.
     *
     * Java language desugaring and multidex are required for enabling core library desugaring.
     * @param creationConfig
     */
    fun isCoreLibraryDesugaringEnabled(creationConfig: ConsumableCreationConfig): Boolean {
        val libDesugarEnabled = dslInfo.compileOptions.isCoreLibraryDesugaringEnabled
        val multidexEnabled = creationConfig.isMultiDexEnabled
        val langSupportType = getJava8LangSupportType()
        val langDesugarEnabled = langSupportType == Java8LangSupport.D8 ||
                langSupportType == Java8LangSupport.R8
        if (libDesugarEnabled && !langDesugarEnabled) {
            config
                .services
                .issueReporter
                .reportError(
                        IssueReporter.Type.GENERIC, "In order to use core library desugaring, "
                        + "please enable java 8 language desugaring with D8 or R8.")
        }
        if (libDesugarEnabled && !multidexEnabled) {
            config
                .services
                .issueReporter
                .reportError(
                        IssueReporter.Type.GENERIC, "In order to use core library desugaring, "
                        + "please enable multidex.")
        }
        return libDesugarEnabled
    }

     open val minSdkVersionForDexing: AndroidVersion
        get() = config.minSdkVersion
}
