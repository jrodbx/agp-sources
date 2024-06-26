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

package com.android.build.gradle.internal.component

import com.android.build.api.component.impl.ApkCreationConfigImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResValue
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.dexing.DexingType
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.io.Serializable

/**
 * CreationConfig for variants that produces an artifact that is directly install-able to devices
 * like APKs or AABs or used by other projects as a versioned reusable logic like AARs.
 */
interface ConsumableCreationConfig: ComponentCreationConfig {
    val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>>

    @Deprecated("DO NOT USE, use buildConfigFields map property")
    val dslBuildConfigFields: Map<String, BuildConfigField<out Serializable>>

    val resValues: MapProperty<ResValue.Key, ResValue>

    val packaging: Packaging

    /**
     * Returns the minimum SDK version for which is used for dexing this variant.
     * See [ApkCreationConfigImpl.minSdkVersionForDexing] for details.
     */
    val minSdkVersionForDexing: AndroidVersion

    val isMultiDexEnabled: Boolean

    val isCoreLibraryDesugaringEnabled: Boolean

    val proguardFiles: ListProperty<RegularFile>

    /**
     * Returns the component ids of those library dependencies whose keep rules are ignored when
     * building the project.
     */
    val ignoredLibraryKeepRules: Provider<Set<String>>

    /**
     * Returns whether to ignore all keep rules from external library dependencies.
     */
    val ignoreAllLibraryKeepRules: Boolean

    val renderscriptTargetApi: Int

    val dexingType: DexingType

    val minifiedEnabled: Boolean

    /** Returns whether we need to create a stream from the merged java resources */
    fun getNeedsMergedJavaResStream(): Boolean

    fun getJava8LangSupportType(): VariantScope.Java8LangSupport

    /**
     * Returns if we need to shrink desugar lib when desugaring Core Library.
     */
    val needsShrinkDesugarLibrary: Boolean

    val renderscript: Renderscript?
}
