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

package com.android.build.gradle.internal.plugins

import com.android.SdkConstants
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.ide.common.repository.GradleVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.io.File.separator

/**
 * Plugin that only checks the version of Gradle vs our min required version.
 *
 * By extracting this in a separate plugin this helps making sure that this plugin
 * can be instantiated and run its check.
 */
class VersionCheckPlugin: Plugin<Project> {

    companion object {
        @JvmField
        val GRADLE_MIN_VERSION = GradleVersion.parse(SdkConstants.GRADLE_MINIMUM_VERSION)
    }

    override fun apply(project: Project) {
        val projectOptions = ProjectOptions(project)
        val logger = project.logger

        val currentVersion = project.gradle.gradleVersion
        if (GRADLE_MIN_VERSION > currentVersion) {
            val file = File("gradle${separator}wrapper${separator}gradle-wrapper.properties")

            val errorMessage = String.format(
                "Minimum supported Gradle version is $GRADLE_MIN_VERSION. Current version is $currentVersion. "
                        + "If using the gradle wrapper, try editing the distributionUrl in ${file.absolutePath} "
                        + "to gradle-$GRADLE_MIN_VERSION-all.zip")

            if (projectOptions.get(BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY)) {
                logger.warn(errorMessage)
                logger.warn("As ${BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.propertyName} is set, continuing anyway.")
            } else {
                throw RuntimeException(errorMessage)
            }
        }
    }
}