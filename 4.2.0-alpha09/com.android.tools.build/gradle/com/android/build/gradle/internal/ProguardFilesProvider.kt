/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal

import java.io.File

/**
 * An interface to unify access to different proguard file types
 */
interface ProguardFilesProvider {
    /**
     * @param type - a type of proguard files to be returned
     * @returns unresolved collection of proguard files
     */
    fun getProguardFiles(type: ProguardFileType): Collection<File>
}

/**
 * Types of proguard files
 */
enum class ProguardFileType {
    /**
     * Files specified by proguardFile(s) in DSL
     */
    EXPLICIT,
    /**
     * Files specified by testProguardFile(s) in DSL
     */
    TEST,
    /**
     * Files specified by consumerProguardFile(s) in DSL
     */
    CONSUMER
}
