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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.utils.FileUtils
import com.google.common.collect.Iterables
import com.google.common.io.Files
import java.io.File
import java.io.Serializable
import javax.inject.Inject

class PublishLintJarWorkerRunnable @Inject constructor(val params: PublishLintJarRequest) : Runnable {
    override fun run() {
        // there could be more than one files if the dependency is on a sub-projects that
        // publishes its compile dependencies. Rather than query getSingleFile and fail with
        // a weird message, do a manual check
        if (params.files.size > 1) {
            throw RuntimeException(
                "Found more than one jar in the '"
                        + VariantDependencies.CONFIG_NAME_LINTPUBLISH
                        + "' configuration. Only one file is supported. If using a separate Gradle project, make sure compilation dependencies are using compileOnly")
        }

        if (params.files.isEmpty()) {
            if (params.outputLintJar.isFile) {
                FileUtils.delete(params.outputLintJar)
            }
        } else {
            FileUtils.mkdirs(params.outputLintJar.parentFile)
            Files.copy(Iterables.getOnlyElement(params.files), params.outputLintJar)
        }
    }
}

data class PublishLintJarRequest(
    val files: Set<File>,
    val outputLintJar: File
): Serializable