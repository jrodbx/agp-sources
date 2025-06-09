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

package com.android.build.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Only there to ensure other plugins does not fail to run.
 *
 * @deprecated This version of AGP does not support features anymore
 */
@Deprecated
public class FeaturePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        throw new RuntimeException(
                "The Feature plugin is not supported by this version of Android Gradle Plugin");
    }
}
