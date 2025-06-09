/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.api

import groovy.lang.Closure

/**
 * An AndroidSourceSet represents a logical group of Java, aidl and RenderScript sources
 * as well as Android and non-Android (Java-style) resources.
 */
@Deprecated(
    "Deprecated and moved to a new package",
    ReplaceWith("com.android.build.api.dsl.AndroidSourceSet"))
interface AndroidSourceSet: com.android.build.api.dsl.AndroidSourceSet {

    override fun getName(): String

    override val resources: AndroidSourceDirectorySet
    fun resources(configureClosure: Closure<*>): AndroidSourceSet

    override val java: AndroidSourceDirectorySet
    fun java(configureClosure: Closure<*>): AndroidSourceSet

    /**
     * Returns the name of the compile configuration for this source set.
     */
    @get:Deprecated(
        "This property is deprecated. Use getImplementationConfigurationName() instead",
        ReplaceWith("implementationConfigurationName()"))
    val compileConfigurationName: String

    /**
     * Returns the name of the runtime configuration for this source set.
     */
    @get:Deprecated(
        "This property is deprecated. Use getRuntimeOnlyConfigurationName() instead",
        ReplaceWith("runtimeOnlyConfigurationName"))
    val packageConfigurationName: String

    /**
     * Returns the name of the compiled-only configuration for this source set.
     */
    @get:Deprecated(
        "This property is deprecated. Use getCompileOnlyConfigurationName() instead",
        ReplaceWith("compileOnlyConfigurationName"))
    val providedConfigurationName: String

    override val manifest: AndroidSourceFile

    /**
     * Configures the location of the Android Manifest for this set.
     *
     *
     * The given closure is used to configure the [AndroidSourceFile] which contains the
     * manifest.
     *
     * @param configureClosure The closure to use to configure the Android Manifest.
     * @return this
     */
    fun manifest(configureClosure: Closure<*>): AndroidSourceSet

    override val res: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android Resources for this set.
     *
     *
     * The given closure is used to configure the [AndroidSourceDirectorySet]
     * which contains the resources.
     *
     * @param configureClosure The closure to use to configure the Resources.
     * @return this
     */
    fun res(configureClosure: Closure<*>): AndroidSourceSet

    override val assets: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android Assets for this set.
     *
     *
     * The given closure is used to configure the [AndroidSourceDirectorySet]
     * which contains the assets.
     *
     * @param configureClosure The closure to use to configure the Assets.
     * @return this
     */
    fun assets(configureClosure: Closure<*>): AndroidSourceSet

    override val aidl: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android AIDL source for this set.
     *
     *
     * The given closure is used to configure the [AndroidSourceDirectorySet]
     * which contains the AIDL source.
     *
     * @param configureClosure The closure to use to configure the AIDL source.
     * @return this
     */
    fun aidl(configureClosure: Closure<*>): AndroidSourceSet

    override val renderscript: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android RenderScript source for this set.
     *
     *
     * The given closure is used to configure the [AndroidSourceDirectorySet]
     * which contains the Renderscript source.
     *
     * @param configureClosure The closure to use to configure the Renderscript source.
     * @return this
     */
    fun renderscript(configureClosure: Closure<*>): AndroidSourceSet

    /**
     * The Android JNI source directory for this source set.
     * @deprecated This is unused and will be removed in AGP 9.0
     */
    @Deprecated("This property is unused and will be removed in AGP 9.0")
    override val jni: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android JNI source for this set.
     *
     *
     * @param configureClosure The closure to use to configure the JNI source.
     * @return this
     *
     * @deprecated This is unused and will be removed in AGP 9.0
     */
    @Deprecated("This property is unused and will be removed in AGP 9.0")
    fun jni(configureClosure: Closure<*>): AndroidSourceSet

    override val jniLibs: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android JNI libs for this set.
     *
     *
     * The given closure is used to configure the [AndroidSourceDirectorySet]
     * which contains the JNI libs.
     *
     * @param configureClosure The closure to use to configure the JNI libs.
     * @return this
     */
    fun jniLibs(configureClosure: Closure<*>): AndroidSourceSet

    override val shaders: AndroidSourceDirectorySet

    /**
     * Configures the location of the Android shaders for this set.
     *
     *
     * The given closure is used to configure the [AndroidSourceDirectorySet]
     * which contains the shaders.
     *
     * @param configureClosure The closure to use to configure the shaders.
     * @return this
     */
    fun shaders(configureClosure: Closure<*>): AndroidSourceSet

    override val mlModels: AndroidSourceDirectorySet

    /**
     * Configures the location of the machine learning models for this set.
     *
     * <p>The given closure is used to configure the {@link AndroidSourceDirectorySet} which
     * contains the ml models.
     *
     * @param configureClosure The closure to use to configure the ml models.
     * @return this
     */
    fun mlModels(configureClosure: Closure<*>): AndroidSourceSet

    override fun setRoot(path: String): AndroidSourceSet
}
