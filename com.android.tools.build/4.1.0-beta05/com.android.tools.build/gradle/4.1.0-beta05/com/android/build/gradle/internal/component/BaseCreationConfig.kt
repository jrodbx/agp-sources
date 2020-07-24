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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.impl.TestComponentPropertiesImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.model.ApiVersion
import com.android.sdklib.AndroidVersion
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * Base of the interfaces used internally to access *PropertiesImpl object.
 *
 * This allows a graph hierarchy rather than a strict tree, in order to have multiple
 * supertype and make some tasks receive a generic type that does not fit the actual
 * implementation hierarchy (see for instance ApkCreationConfig)
 */
interface BaseCreationConfig : ComponentIdentity {
    // ---------------------------------------------------------------------------------------------
    // BASIC INFO
    // ---------------------------------------------------------------------------------------------
    val dirName: String
    val baseName: String
    val variantType: VariantType
    val description: String

    // ---------------------------------------------------------------------------------------------
    // NEEDED BY ALL COMPONENTS
    // ---------------------------------------------------------------------------------------------

    // needed by resource compilation/link
    val applicationId: Provider<String>
    val packageName: Provider<String>
    val resourceConfigurations: ImmutableSet<String>
    val isPrecompileDependenciesResourcesEnabled: Boolean

    // ---------------------------------------------------------------------------------------------
    // TODO figure out whether these properties are needed by all
    // ---------------------------------------------------------------------------------------------

    val minSdkVersion: AndroidVersion
    val maxSdkVersion: Int?
    val targetSdkVersion: ApiVersion
    val outputs: VariantOutputList
    val dexingType: DexingType
    val needsMainDexList: Boolean
    val manifestArtifactType: InternalArtifactType<Directory>

    // ---------------------------------------------------------------------------------------------
    // INTERNAL DELEGATES
    // ---------------------------------------------------------------------------------------------
    val buildFeatures: BuildFeatureValues
    val variantScope: VariantScope
    val variantDslInfo: VariantDslInfo
    val variantSources: VariantSources
    val variantDependencies: VariantDependencies
    val artifacts: ArtifactsImpl
    val taskContainer: MutableTaskContainer
    val transformManager: TransformManager
    val paths: VariantPathHelper
    val services: TaskCreationServices
    @Deprecated("Do not use if you can avoid it. Check if services has what you need")
    val globalScope: GlobalScope

    // ---------------------------------------------------------------------------------------------
    // INTERNAL HELPERS
    // ---------------------------------------------------------------------------------------------

    fun computeTaskName(prefix: String, suffix: String = ""): String

    /**
     * Returns the tested variant. This is null for [VariantPropertiesImpl] instances
     *

     * This declares is again, even though the public interfaces only have it via
     * [TestComponentProperties]. This is to facilitate places where one cannot use
     * [TestComponentPropertiesImpl].
     *
     * see [onTestedConfig] for a utility function helping deal with nullability
     */
    val testedConfig: VariantCreationConfig?

    /**
     * Runs an action on the tested variant and return the results of the action.
     *
     * if there is no tested variant this does nothing and returns null.
     */
    fun <T> onTestedConfig(action: (VariantCreationConfig) -> T): T?
}