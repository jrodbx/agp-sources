/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.builder.core;


import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;

public class ToolsRevisionUtils {

    /**
     * Minimal supported version of build tools.
     *
     * <p>ATTENTION: When changing this value, make sure to update the release notes
     * (https://developer.android.com/studio/releases/gradle-plugin).
     */
    public static final Revision MIN_BUILD_TOOLS_REV =
            Revision.parseRevision(SdkConstants.CURRENT_BUILD_TOOLS_VERSION);

    /**
     * Default version of build tools that will be used if the user does not specify.
     *
     * <p>ATTENTION: This is usually the same as the minimum build tools version, as documented in
     * {@code com.android.build.gradle.BaseExtension#getBuildToolsVersion()} and {@code
     * com.android.build.api.dsl.extension.BuildProperties#getBuildToolsVersion()}, and in the
     * release notes (https://developer.android.com/studio/releases/gradle-plugin). If this version
     * is higher than the minimum version, make sure to update those places to document the new
     * behavior.
     */
    public static final Revision DEFAULT_BUILD_TOOLS_REVISION = MIN_BUILD_TOOLS_REV;

    /**
     * Maximum recommended compileSdk version.
     *
     * <p>The build system will warn if the compile SDK version is greater than this value to
     * encourage build authors to upgrade the Android Gradle Plugin.
     */
    public static final AndroidVersion MAX_RECOMMENDED_COMPILE_SDK_VERSION =
            SdkConstants.MAX_SUPPORTED_ANDROID_PLATFORM_VERSION;
}
