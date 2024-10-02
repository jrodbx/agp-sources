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

package com.android.build.api.variant

import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.LayeredSourceDirectoriesImpl
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Internal representation of the public variant sources API.
 */
interface InternalSources: Sources {
    override val java: FlatSourceDirectoriesImpl?
    override val kotlin: FlatSourceDirectoriesImpl?
    override val res: LayeredSourceDirectoriesImpl?
    override val resources: FlatSourceDirectoriesImpl?
    override val assets: LayeredSourceDirectoriesImpl?
    override val jniLibs: LayeredSourceDirectoriesImpl?
    override val shaders: LayeredSourceDirectoriesImpl?
    override val mlModels: LayeredSourceDirectoriesImpl?
    override val aidl: FlatSourceDirectoriesImpl?
    override val renderscript: FlatSourceDirectoriesImpl?
    override val baselineProfiles: FlatSourceDirectoriesImpl?

    /**
     * runs [action] passing the [Sources.java] internal representation if not null.
     * If null, action is not run.
     */
    fun java(action: (FlatSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.kotlin] internal representation if not null.
     * If null, action is not run.
     */
    fun kotlin(action: (FlatSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.resources] internal representation if not null.
     * If null, action is not run.
     */
    fun resources(action: (FlatSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.aidl] internal representation if not null.
     * If null, action is not run.
     */
    fun aidl(action: (FlatSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.renderscript] internal representation if not null.
     * If null, action is not run.
     */
    fun renderscript(action: (FlatSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.baselineProfiles] internal representation if not null.
     * If null, action is not run.
     */
    fun baselineProfiles(action: (FlatSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.res] internal representation if not null.
     * If null, action is not run.
     */
    fun res(action: (LayeredSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.assets] internal representation if not null.
     * If null, action is not run.
     */
    fun assets(action: (LayeredSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.jniLibs] internal representation if not null.
     * If null, action is not run.
     */
    fun jniLibs(action: (LayeredSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.shaders] internal representation if not null.
     * If null, action is not run.
     */
    fun shaders(action: (LayeredSourceDirectoriesImpl) -> Unit)

    /**
     * runs [action] passing the [Sources.mlModels] internal representation if not null.
     * If null, action is not run.
     */
    fun mlModels(action: (LayeredSourceDirectoriesImpl) -> Unit)

    val artProfile: Provider<File>?
    val manifestFile: Provider<File>
    // it's ordered from less to most prioritized
    val manifestOverlayFiles: Provider<List<File>>

    val sourceProviderNames: List<String>

    /**
     * DO NOT USE, this is public for model support only.
     */
    val multiFlavorSourceProvider: DefaultAndroidSourceSet?

    /**
     * DO NOT USE, this is public for model support only.
     */
    val variantSourceProvider: DefaultAndroidSourceSet?
}
