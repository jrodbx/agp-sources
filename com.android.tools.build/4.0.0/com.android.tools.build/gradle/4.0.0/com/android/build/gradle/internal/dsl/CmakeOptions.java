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
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.model.CoreCmakeOptions;
import java.io.File;
import javax.inject.Inject;

/** See {@link com.android.build.api.dsl.CmakeOptions} */
public class CmakeOptions implements CoreCmakeOptions, com.android.build.api.dsl.CmakeOptions {
    @NonNull private final DslScope dslScope;

    @Nullable
    private File path;

    @Nullable private File buildStagingDirectory;

    // CMake version to use. If it's null, it'll default to the CMake shipped with the SDK
    @Nullable private String version;

    @Inject
    public CmakeOptions(@NonNull DslScope dslScope) {
        this.dslScope = dslScope;
    }

    /**
     * Specifies the relative path to your <code>CMakeLists.txt</code> build script.
     *
     * <p>For example, if your CMake build script is in the same folder as your module-level <code>
     * build.gradle</code> file, you simply pass the following:
     *
     * <pre>
     * android {
     *     ...
     *     externalNativeBuild {
     *         cmake {
     *             ...
     *             // Tells Gradle to find the root CMake build script in the same
     *             // directory as the module's build.gradle file. Gradle requires this
     *             // build script to add your CMake project as a build dependency and
     *             // pull your native sources into your Android project.
     *             path "CMakeLists.txt"
     *         }
     *     }
     * }
     * </pre>
     *
     * @since 2.2.0
     */
    @Nullable
    @Override
    public File getPath() {
        return this.path;
    }

    public void setPath(@Nullable Object path) {
        this.path = dslScope.file(path);
    }

    @Override
    public void setPath(@Nullable File path) {
        this.path = path;
    }

    /**
     * Specifies the path to your external native build output directory.
     *
     * <p>This directory also includes other build system files that should persist when performing
     * clean builds, such as <a href="https://ninja-build.org/">Ninja build files</a>. If you do not
     * specify a value for this property, the Android plugin uses the <code>
     * &lt;project_dir&gt;/&lt;module&gt;/.externalNativeBuild/</code> directory by default.
     *
     * <p>If you specify a path that does not exist, the Android plugin creates it for you. Relative
     * paths are relative to the <code>build.gradle</code> file, as shown below:
     *
     * <pre>
     * android {
     *     ...
     *     externalNativeBuild {
     *         cmake {
     *             ...
     *             // Tells Gradle to put outputs from external native
     *             // builds in the path specified below.
     *             buildStagingDirectory "./outputs/cmake"
     *         }
     *     }
     * }
     * </pre>
     *
     * <p>If you specify a path that's a subdirectory of your project's temporary <code>build/
     * </code> directory, you get a build error. That's because files in this directory do not
     * persist through clean builds. So, you should either keep using the default <code>
     * &lt;project_dir&gt;/&lt;module&gt;/.externalNativeBuild/</code> directory or specify a path
     * outside the temporary build directory.
     *
     * @since 3.0.0
     */
    @Nullable
    @Override
    public File getBuildStagingDirectory() {
        return buildStagingDirectory;
    }

    @Override
    public void setBuildStagingDirectory(@Nullable File buildStagingDirectory) {
        this.buildStagingDirectory = dslScope.file(buildStagingDirectory);
    }

    /**
     * The version of CMake that the Android plugin should use when building your CMake project.
     *
     * <p>When you specify a version of CMake, as shown below, the plugin searches for the
     * appropriate CMake binary within your PATH environmental variable. So, make sure you add the
     * path to the target CMake binary to your PATH environmental variable.
     *
     * <pre>
     * android {
     *     ...
     *     externalNativeBuild {
     *         cmake {
     *             ...
     *             // Specifies the version of CMake the Android plugin should use. You need to
     *             // include the path to the CMake binary of this version to your PATH
     *             // environmental variable.
     *             version "3.7.1"
     *         }
     *     }
     * }
     * </pre>
     *
     * <p>If you do not configure this property, the plugin uses the version of CMake available from
     * the <a href="https://developer.android.com/studio/intro/update.html#sdk-manager">SDK
     * manager</a>. (Android Studio prompts you to download this version of CMake if you haven't
     * already done so).
     *
     * <p>Alternatively, you can specify a version of CMake in your project's <code>local.properties
     * </code> file, as shown below:
     *
     * <pre>
     * // The path may be either absolute or relative to the the local.properties file
     * // you are editing.
     * cmake.dir="&lt;path-to-cmake&gt;"
     * </pre>
     *
     * @since 3.0.0
     */
    @Nullable
    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(@Nullable String version) {
        this.version = version;
    }

    public void setBuildStagingDirectory(@Nullable Object buildStagingDirectory) {
        this.buildStagingDirectory = dslScope.file(buildStagingDirectory);
    }
}
