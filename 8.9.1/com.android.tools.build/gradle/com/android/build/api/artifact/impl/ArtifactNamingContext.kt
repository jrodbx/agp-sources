/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.artifact.impl

import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Class contains file name and folders for artifact
 */
class ArtifactNamingContext internal constructor(
    private val finalFilename: Property<String>,
    private val absoluteOutputLocation: String?,
    private val buildOutputLocation: Property<Directory>?){

    fun getFilename(): String? = finalFilename.orNull

    fun getOutputLocation(): File? = when {
        absoluteOutputLocation != null -> File(absoluteOutputLocation)
        buildOutputLocation != null -> buildOutputLocation.orNull?.asFile
        else -> null
    }
}
