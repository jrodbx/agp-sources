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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

/**
 * Plugin that only checks the version of Gradle vs our min required version.
 *
 * By extracting this in a separate plugin this helps making sure that this plugin can be instantiated and run its check.
 */
class VersionCheckPlugin : Plugin<Project> {

  companion object {
    // The minimum version of Gradle that this plugin should accept is the version of Gradle
    // whose features the plugin uses, which may be substantially later than the earliest
    // version of Gradle that has all the features that Studio uses directly.
    val GRADLE_MIN_VERSION = GradleVersion.version(SdkConstants.GRADLE_LATEST_VERSION)!!
  }

  override fun apply(project: Project) {
    // Run only once per build
    RunOnceBuildServiceImpl.RegistrationAction(project).execute().get().runOnce("apply", VersionCheckPlugin::class.java.name) {
      doApply(project)
    }
  }

  private fun doApply(project: Project) {
    // Skip this check if the Gradle wrapper task is running (see b/372269616)
    if (isGradleWrapperTaskRunning(project)) return

    val currentVersion = GradleVersion.current()
    if (GRADLE_MIN_VERSION > currentVersion) {
      val errorMessage =
        """
                Minimum supported Gradle version is ${GRADLE_MIN_VERSION.version}. Current version is ${currentVersion.version}.
                Try updating the 'distributionUrl' property in ${project.rootDir.resolve("gradle/wrapper/gradle-wrapper.properties").path} to 'gradle-${GRADLE_MIN_VERSION.version}-bin.zip'.
                """
          .trimIndent()
      if (getVersionCheckOverridePropertyValue(project)) {
        project.logger.warn(errorMessage)
        project.logger.warn("As ${BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.propertyName} is set, continuing anyway.")
      } else {
        error(errorMessage)
      }
    }
  }

  private fun isGradleWrapperTaskRunning(project: Project): Boolean {
    val taskRequests = project.gradle.startParameter.taskRequests
    return taskRequests.singleOrNull()?.args?.getOrNull(0) == "wrapper"
  }

  // Version check override property is not compatible with new pipeline of accessing gradle
  // properties because providerFactory.gradleProperty API doesn't exist in lower Gradle versions
  private fun getVersionCheckOverridePropertyValue(project: Project): Boolean {
    val value = project.extensions.extraProperties.properties[BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.propertyName]
    return if (value == null) {
      BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.defaultValue
    } else {
      BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.parse(value)
    }
  }
}
