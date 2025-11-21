/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.gradle.api.JavaCompileOptions
import com.android.builder.model.BuildType
import org.gradle.api.provider.Property

/**
 * A build type with addition properties for building with Gradle plugin.
 */
@Deprecated("Use a more specific type instead")
interface CoreBuildType : BuildType {
    val ndkConfig: CoreNdkOptions?
    val externalNativeBuildOptions: CoreExternalNativeBuildOptions?
    val javaCompileOptions: JavaCompileOptions
    val shaders: CoreShaderOptions

    @get:Deprecated("Use {@link AndroidResourcesCreationConfig#useResourceShrinker()} instead. ")
    val isShrinkResources: Boolean

    @get:Deprecated("Use {@link VariantScope#getCodeShrinker()} instead. ")
    val isUseProguard: Boolean?

    val isCrunchPngs: Boolean?

    @get:Deprecated("Can go away once {@link AaptOptions#cruncherEnabled} is removed. ")
    val isCrunchPngsDefault: Boolean

    fun getIsDefault(): Property<Boolean>
}
