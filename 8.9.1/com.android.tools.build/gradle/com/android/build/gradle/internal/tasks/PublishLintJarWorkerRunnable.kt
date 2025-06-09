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
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty

abstract class PublishLintJarWorkerRunnable : ProfileAwareWorkAction<PublishLintJarRequest>() {
    override fun run() {
        // there could be more than one files if the dependency is on a sub-projects that
        // publishes its compile dependencies. Rather than query getSingleFile and fail with
        // a weird message, do a manual check
        if (parameters.files.files.size > 1) {
            throw RuntimeException(
                    "Found more than one jar in the '"
                            + VariantDependencies.CONFIG_NAME_LINTPUBLISH
                            + "' configuration. Only one file is supported. If using a separate Gradle project, make sure compilation dependencies are using compileOnly"
            )
        }

        val outputLintJar = parameters.outputLintJar.asFile.get()
        FileUtils.deleteIfExists(outputLintJar)
        if (!parameters.files.isEmpty) {
            FileUtils.mkdirs(outputLintJar.parentFile)
            parameters.files.singleFile.copyTo(outputLintJar)
        }
    }
}

abstract class PublishLintJarRequest : ProfileAwareWorkAction.Parameters() {
    abstract val files: ConfigurableFileCollection
    abstract val outputLintJar: RegularFileProperty
}
