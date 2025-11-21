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
import com.android.build.gradle.internal.services.RunOnceBuildServiceImpl
import com.android.build.gradle.options.BooleanOption
import com.android.ide.common.repository.GradleVersion
import com.google.common.annotations.VisibleForTesting
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
        // The minimum version of Gradle that this plugin should accept is the version of Gradle
        // whose features the plugin uses, which may be substantially later than the earliest
        // version of Gradle that has all the features that Studio uses directly.
        @JvmField
        val GRADLE_MIN_VERSION = GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)
    }

    override fun apply(project: Project) {
        // Run only once per build
        RunOnceBuildServiceImpl.RegistrationAction(project).execute().get()
                .runOnce("apply", VersionCheckPlugin::class.java.name) {
                    doApply(project)
                }
    }

    @VisibleForTesting
    fun doApply(project: Project) {
        val logger = project.logger

        val currentVersion = project.gradle.gradleVersion
        if (GRADLE_MIN_VERSION > currentVersion) {
            val file = File(project.rootProject.projectDir, "gradle${separator}wrapper${separator}gradle-wrapper.properties")

            val errorMessage = String.format(
                "Minimum supported Gradle version is $GRADLE_MIN_VERSION. Current version is $currentVersion. "
                        + "If using the gradle wrapper, try editing the distributionUrl in ${file.absolutePath} "
                        + "to gradle-$GRADLE_MIN_VERSION-all.zip")

            if (getVersionCheckOverridePropertyValue(project)) {
                logger.warn(errorMessage)
                logger.warn("As ${BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.propertyName} is set, continuing anyway.")
            } else {
                throw RuntimeException(errorMessage)
            }
        }
    }

    // Version check override property is not compatible with new pipeline of accessing gradle
    // properties because providerFactory.gradleProperty API doesn't exist in lower Gradle versions
    private fun getVersionCheckOverridePropertyValue(project: Project) : Boolean {
        val value = project.extensions.extraProperties.properties[BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.propertyName]
        return if (value == null) {
            BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.defaultValue
        } else {
             BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.parse(value)
        }
    }
}
