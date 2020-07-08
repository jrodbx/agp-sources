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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.BuildType;
import org.gradle.api.provider.Property;

/**
 * A build type with addition properties for building with Gradle plugin. @Deprecated do not use.
 * Use a more specific type instead
 */
@Deprecated
public interface CoreBuildType extends BuildType {

    @Nullable
    CoreNdkOptions getNdkConfig();

    @Nullable
    CoreExternalNativeBuildOptions getExternalNativeBuildOptions();

    @NonNull
    JavaCompileOptions getJavaCompileOptions();

    @NonNull
    CoreShaderOptions getShaders();

    /** @deprecated Use {@link VariantScope#useResourceShrinker()} instead. */
    @Deprecated
    boolean isShrinkResources();

    /** @deprecated Use {@link VariantScope#getCodeShrinker()} instead. */
    @Deprecated
    Boolean isUseProguard();
    /**
     * Whether to crunch PNGs.
     *
     * <p>Setting this property to <code>true</code> reduces of PNG resources that are not already
     * optimally compressed. However, this process increases build times.
     *
     * <p>PNG crunching is enabled by default in the release build type and disabled by default in
     * the debug build type.
     */
    @Nullable
    Boolean isCrunchPngs();

    /** @deprecated Can go away once {@link AaptOptions#cruncherEnabled} is removed. */
    @Deprecated
    boolean isCrunchPngsDefault();

    /** Whether this product flavor should be selected in Studio by default */
    Property<Boolean> getIsDefault();
}
