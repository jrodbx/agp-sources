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
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.builder.dexing.DexingType
import com.android.builder.errors.IssueReporter
import com.android.builder.model.CodeShrinker

/**
 * This class and subclasses are implementing methods defined in the CreationConfig
 * interfaces but should not be necessarily implemented by the VariantImpl
 * and subclasses. The reasons are usually because it makes more sense to implement
 * the method in a class hierarchy that follows the interface definition so to avoid
 * repeating implementation in various disparate VariantImpl sub-classes.
 *
 * Instead [com.android.build.api.variant.impl.VariantImpl] will delegate
 * to these objects for methods which are cross cutting across the VariantProperties
 * implementation hierarchy.
 */

/**
 * Constructor for [ConsumableCreationConfigImpl].
 *
 * @param config configuration object that will be delegating calls to this
 * object, and will also provide access to other VariantProperties configuration data
 * @param globalScope pointer to the global scope to access project wide options.
 * @param variantDslInfo variant configuration coming from the DSL.
 */
open class ConsumableCreationConfigImpl(
        open val config: ConsumableCreationConfig,
        val globalScope: GlobalScope,
        val variantDslInfo: VariantDslInfo) {

    val dexingType: DexingType
        get() = variantDslInfo.dexingType ?:  if (config.isMultiDexEnabled) {
            if (config.minSdkVersion.getFeatureLevel() < 21) DexingType.LEGACY_MULTIDEX else DexingType.NATIVE_MULTIDEX
        } else {
            DexingType.MONO_DEX
        }

    open fun getCodeShrinker(): CodeShrinker? {
        val codeShrinker: CodeShrinker = variantDslInfo.getPostProcessingOptions().getCodeShrinker() ?: return null
        var enableR8 = globalScope.projectOptions[OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8]
        if (variantDslInfo.variantType.isAar && !globalScope.projectOptions[BooleanOption.ENABLE_R8_LIBRARIES]) {
                // R8 is disabled for libraries
                enableR8 = false
        }
        return if (enableR8 == null) {
                codeShrinker
            } else if (enableR8) {
                CodeShrinker.R8
            } else {
                CodeShrinker.PROGUARD
            }
    }

    open fun getNeedsMergedJavaResStream(): Boolean {
        // We need to create a stream from the merged java resources if we're in a library module,
        // or if we're in an app/feature module which uses the transform pipeline.
        return (variantDslInfo.variantType.isAar
                || !globalScope.extension.transforms.isEmpty()
                || getCodeShrinker() != null)
    }

    open fun getJava8LangSupportType(): VariantScope.Java8LangSupport {
        // in order of precedence
        if (!globalScope.extension
                        .compileOptions
                        .targetCompatibility
                        .isJava8Compatible()) {
            return VariantScope.Java8LangSupport.UNUSED
        }
        if (globalScope.project.plugins.hasPlugin("me.tatarka.retrolambda")) {
            return VariantScope.Java8LangSupport.RETROLAMBDA
        }
        val shrinker = getCodeShrinker()
        if (shrinker == CodeShrinker.R8) {
            return VariantScope.Java8LangSupport.R8
        } else {
            // D8 cannot be used if R8 is used
            return VariantScope.Java8LangSupport.D8
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
        val extension: BaseExtension = globalScope.getExtension()
        val libDesugarEnabled = (extension.compileOptions.coreLibraryDesugaringEnabled != null
                && extension.compileOptions.coreLibraryDesugaringEnabled!!)
        val multidexEnabled = creationConfig.isMultiDexEnabled
        val langSupportType: VariantScope.Java8LangSupport = getJava8LangSupportType()
        val langDesugarEnabled = langSupportType == VariantScope.Java8LangSupport.D8 || langSupportType == VariantScope.Java8LangSupport.R8
        if (libDesugarEnabled && !langDesugarEnabled) {
            globalScope
                .getDslServices()
                .issueReporter
                .reportError(
                        IssueReporter.Type.GENERIC, "In order to use core library desugaring, "
                        + "please enable java 8 language desugaring with D8 or R8.")
        }
        if (libDesugarEnabled && !multidexEnabled) {
            globalScope
                .getDslServices()
                .issueReporter
                .reportError(
                        IssueReporter.Type.GENERIC, "In order to use core library desugaring, "
                        + "please enable multidex.")
        }
        return libDesugarEnabled
    }

     open val minSdkVersionWithTargetDeviceApi: AndroidVersion
        get() = config.minSdkVersion
}
