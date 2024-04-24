/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.gradle.api.Incubating

/**
 * Provides access to all source directories for a [Variant].
 */
interface Sources {

    /**
     * Access to the Java source folders.
     */
    val java: SourceDirectories.Flat?

    /**
     * Access to the Kotlin source folders.
     */
    @get:Incubating
    val kotlin: SourceDirectories.Flat?

    /**
     * Access to the Android resources sources folders if the component supports android resources
     * or if [com.android.build.api.dsl.LibraryBuildFeatures.androidResources] is true for library
     * variants, otherwise null.
     */
    val res: SourceDirectories.Layered?

    /**
     * Access to the Java-style resources sources folders.
     */
    val resources: SourceDirectories.Flat?

    /**
     * Access to the Android assets sources folders.
     */
    val assets: SourceDirectories.Layered?

    /**
     * Access to the JNI libraries folders
     */
    val jniLibs: SourceDirectories.Layered?

    /**
     * Access to the shaders sources folders if [com.android.build.api.dsl.BuildFeatures.shaders]
     * is true otherwise null
     */
    val shaders: SourceDirectories.Layered?

    /**
     * Access to the machine learning models folders if [com.android.build.api.dsl.ApplicationBuildFeatures.mlModelBinding]
     * is true otherwise null
     */
    val mlModels: SourceDirectories.Layered?

    /**
     * Access to the aidl sources folders if [com.android.build.api.dsl.BuildFeatures.aidl]
     * is true otherwise null
     */
    val aidl: SourceDirectories.Flat?

    /**
     * Access to the renderscript sources folders if
     * [com.android.build.api.dsl.BuildFeatures.renderScript] is true otherwise null.
     * RenderScript APIs are deprecated starting in Android 12
     * <a href="https://developer.android.com/guide/topics/renderscript/migrate">See more</a>.
     */
    val renderscript: SourceDirectories.Flat?

    /**
     * Access to the Baseline profiles source folders.
     */
    val baselineProfiles: SourceDirectories.Flat?

    /**
     * Access to all android manifest files.
     * Main manifest file is always the first one.
     */
    val manifests: ManifestFiles

    /**
     * Access (and potentially creates) a new [Flat] for a custom source type that can
     * be referenced by its [name].
     *
     * The first caller will create the new instance, other callers with the same [name] will get
     * the same instance returned. Any callers can obtain the final list of the folders registered
     * under this custom source type by calling [Flat.all].
     *
     * These sources directories are attached to the variant and will be visible to Android Studio.
     */
    @Incubating
    fun getByName(name: String): SourceDirectories.Flat
}
