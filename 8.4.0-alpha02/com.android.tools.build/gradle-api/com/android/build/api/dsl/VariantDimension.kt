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

package com.android.build.api.dsl

import org.gradle.api.Incubating
import java.io.File

/**
 * Shared properties between DSL objects that contribute to a variant.
 *
 * That is, [BuildType] and [ProductFlavor] and [DefaultConfig].
 */
interface VariantDimension {
    /**
     * Text file with additional ProGuard rules to be used to determine which classes are compiled
     * into the main dex file.
     *
     * If set, rules from this file are used in combination with the default rules used by the
     * build system.
     */
    var multiDexKeepProguard: File?

    /**
     * Text file that specifies additional classes that will be compiled into the main dex file.
     *
     * Classes specified in the file are appended to the main dex classes computed using
     * `aapt`.
     *
     * If set, the file should contain one class per line, in the following format:
     * `com/example/MyClass.class`
     */
    @Deprecated("This property is deprecated. Migrate to multiDexKeepProguard.")
    var multiDexKeepFile: File?

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters.  */
    val ndk: Ndk

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters.  */
    fun ndk(action: Ndk.() -> Unit)

    /**
     * Specifies the ProGuard configuration files that the plugin should use.
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt`,
     * except with optimizations enabled. You can use [getDefaultProguardFile(String)]
     * to return the full path of the files.
     *
     * @return a non-null collection of files.
     */
    val proguardFiles: MutableList<File>

    /**
     * Adds a new ProGuard configuration file.
     *
     * `proguardFile getDefaultProguardFile('proguard-android.txt')`
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt`,
     * except with optimizations enabled. You can use [getDefaultProguardFile(String)]
     * to return the full path of the files.
     *
     * This method has a return value for legacy reasons.
     */
    fun proguardFile(proguardFile: Any): Any

    /**
     * Adds new ProGuard configuration files.
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt`,
     * except with optimizations enabled. You can use [getDefaultProguardFile(String)]
     * to return the full path of the files.
     *
     * This method has a return value for legacy reasons.
     */
    fun proguardFiles(vararg files: Any): Any

    /**
     * Replaces the ProGuard configuration files.
     *
     * This method has a return value for legacy reasons.
     */
    fun setProguardFiles(proguardFileIterable: Iterable<*>): Any

        /**
     * The collection of proguard rule files to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    val testProguardFiles: MutableList<File>

    /**
     * Adds a proguard rule file to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     *
     * This method has a return value for legacy reasons.
     */
    fun testProguardFile(proguardFile: Any): Any

    /**
     * Adds proguard rule files to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     *
     * This method has a return value for legacy reasons.
     */
    fun testProguardFiles(vararg proguardFiles: Any): Any

    /**
     * The manifest placeholders.
     *
     * See
     * [Inject Build Variables into the Manifest](https://developer.android.com/studio/build/manifest-build-variables.html).
     */
    val manifestPlaceholders: MutableMap<String, Any>

    /**
     * Adds manifest placeholders.
     *
     * See
     * [Inject Build Variables into the Manifest](https://developer.android.com/studio/build/manifest-build-variables.html).
     */
    fun addManifestPlaceholders(manifestPlaceholders: Map<String, Any>)

    @Deprecated("Use manifestPlaceholders property instead")
    fun setManifestPlaceholders(manifestPlaceholders: Map<String, Any>): Void?

    /** Options for configuring Java compilation. */
    val javaCompileOptions: JavaCompileOptions

    /** Options for configuring Java compilation. */
    fun javaCompileOptions(action: JavaCompileOptions.() -> Unit)

    /** Options for configuring the shader compiler.  */
    val shaders: Shaders

    /** Configure the shader compiler options. */
    fun shaders(action: Shaders.() -> Unit)

    /**
     * Encapsulates per-variant CMake and ndk-build configurations for your external native build.
     *
     * To learn more, see
     * [Add C and C++ Code to Your Project](http://developer.android.com/studio/projects/add-native-code.html#).
     */
    @get:Incubating
    val externalNativeBuild: ExternalNativeBuildFlags

    /**
     * Encapsulates per-variant CMake and ndk-build configurations for your external native build.
     *
     * To learn more, see
     * [Add C and C++ Code to Your Project](http://developer.android.com/studio/projects/add-native-code.html#).
     */
    @Incubating
    fun externalNativeBuild(action: ExternalNativeBuildFlags.() -> Unit)

    /**
     * Adds a new field to the generated BuildConfig class.
     *
     *
     * The field is generated as: `<type> <name> = <value>;`
     *
     *
     * This means each of these must have valid Java content. If the type is a String, then the
     * value should include quotes.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    fun buildConfigField(type: String, name: String, value: String)

    /**
     * Adds a new generated resource.
     *
     *
     * This is equivalent to specifying a resource in res/values.
     *
     *
     * See [Resource Types](http://developer.android.com/guide/topics/resources/available-resources.html).
     *
     * @param type the type of the resource
     * @param name the name of the resource
     * @param value the value of the resource
     */
    fun resValue(type: String, name: String, value: String)

    @get:Incubating
    val optimization: Optimization

    @Incubating
    fun optimization(action: Optimization.() -> Unit)
}
