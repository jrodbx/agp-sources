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

@file:JvmName("ProjectIsolationUtils")

package com.android.build.gradle.internal

import org.gradle.api.provider.ProviderFactory

// TODO switch to public API once https://github.com/gradle/gradle/issues/23840 is fixed
fun projectIsolationRequested(providers: ProviderFactory): Boolean {
    return providers.systemProperty(PROJECT_ISOLATION_PROPERTY).orNull.toBoolean()
            || providers.gradleProperty(PROJECT_ISOLATION_PROPERTY).orNull.toBoolean()
}

private const val PROJECT_ISOLATION_PROPERTY = "org.gradle.unsafe.isolated-projects"
