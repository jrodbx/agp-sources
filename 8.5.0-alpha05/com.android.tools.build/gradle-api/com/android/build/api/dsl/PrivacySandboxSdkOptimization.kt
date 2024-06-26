/*
 * Copyright (C) 2024 The Android Open Source Project
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

/**
 * DSL object for configurations aimed for optimizing build process(e.g. speed, correctness)
 */
@Incubating
interface PrivacySandboxSdkOptimization {

    /**
     * The collection of keep rule files that the plugin should use when optimizing the AAR
     */
    @get:Incubating
    val keepRules: PrivacySandboxKeepRules
}

@Incubating
interface PrivacySandboxKeepRules: ConfigurableFiles {

    /**
     * Ignore keep rules from listed external dependencies. External dependencies can be specified
     * via GAV coordinates(e.g. "groupId:artifactId:version") or in the format of
     * "groupId:artifactId" in which case dependencies are ignored as long as they match
     * groupId & artifactId.
     */
    @get:Incubating
    val ignoreFrom: MutableSet<String>

    /**
     * Ignore keep rules from all the external dependencies.
     */
    @get:Incubating
    @set:Incubating
    var ignoreFromAllExternalDependencies: Boolean
}
