/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.google.common.annotations.VisibleForTesting;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;

/**
 * DSL object for per-variant CMake and ndk-build configurations, such as toolchain arguments and
 * compiler flags.
 *
 * <pre>
 * android {
 *     // Similar to other properties in the defaultConfig block, you can override
 *     // these properties for each product flavor you configure.
 *     defaultConfig {
 *         // This block is different from the one you use to link Gradle
 *         // to your CMake or ndk-build script.
 *         externalNativeBuild {
 *             // For ndk-build, instead use the ndkBuild block.
 *             cmake {
 *                 // Passes optional arguments to CMake.
 *                 arguments "-DANDROID_ARM_NEON=TRUE", "-DANDROID_TOOLCHAIN=clang"
 *
 *                 // Sets a flag to enable format macro constants for the C compiler.
 *                 cFlags "-D__STDC_FORMAT_MACROS"
 *
 *                 // Sets optional flags for the C++ compiler.
 *                 cppFlags "-fexceptions", "-frtti"
 *
 *                 // Specifies the library and executable targets from your CMake project
 *                 // that Gradle should build.
 *                 targets "libexample-one", "my-executible-demo"
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>To enable external native builds and set the path to your CMake or ndk-build script, use
 * {@link com.android.build.gradle.internal.dsl.ExternalNativeBuild android.externalNativeBuild}.
 */
public class ExternalNativeBuildOptions implements CoreExternalNativeBuildOptions {
    @NonNull
    private ExternalNativeNdkBuildOptions ndkBuildOptions;
    @NonNull
    private ExternalNativeCmakeOptions cmakeOptions;

    @VisibleForTesting
    public ExternalNativeBuildOptions() {
        ndkBuildOptions = new ExternalNativeNdkBuildOptions();
        cmakeOptions = new ExternalNativeCmakeOptions();
    }

    @Inject
    public ExternalNativeBuildOptions(@NonNull ObjectFactory objectFactory) {
        ndkBuildOptions = objectFactory.newInstance(ExternalNativeNdkBuildOptions.class);
        cmakeOptions = objectFactory.newInstance(ExternalNativeCmakeOptions.class);
    }

    public void _initWith(ExternalNativeBuildOptions that) {
        ndkBuildOptions._initWith(that.getExternalNativeNdkBuildOptions());
        cmakeOptions._initWith(that.getExternalNativeCmakeOptions());
    }

    @Nullable
    @Override
    public ExternalNativeNdkBuildOptions getExternalNativeNdkBuildOptions() {
        return getNdkBuild();
    }

    /**
     * Encapsulates per-variant ndk-build configurations, such as compiler flags and toolchain
     * arguments.
     *
     * <p>To enable external native builds and set the path to your <code>Android.mk</code> script,
     * use {@link com.android.build.gradle.internal.dsl.NdkBuildOptions:path
     * android.externalNativeBuild.ndkBuild.path}.
     */
    @NonNull
    public ExternalNativeNdkBuildOptions getNdkBuild() {
        return ndkBuildOptions;
    }

    public void ndkBuild(Action<ExternalNativeNdkBuildOptions> action) {
        action.execute(ndkBuildOptions);
    }

    @Nullable
    @Override
    public ExternalNativeCmakeOptions getExternalNativeCmakeOptions() {
        return getCmake();
    }

    /**
     * Encapsulates per-variant CMake configurations, such as compiler flags and toolchain
     * arguments.
     *
     * <p>To enable external native builds and set the path to your <code>CMakeLists.txt</code>
     * script, use {@link com.android.build.gradle.internal.dsl.CmakeOptions:path
     * android.externalNativeBuild.cmake.path}.
     */
    public ExternalNativeCmakeOptions getCmake() {
        return cmakeOptions;
    }

    public void cmake(Action<ExternalNativeCmakeOptions> action) {
        action.execute(cmakeOptions);
    }
}
