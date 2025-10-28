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

package com.android.build.gradle.internal.dependency

import org.gradle.api.Action
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.plugins.JavaBasePlugin

class ConfigurationVariantMapping(private val scope: String, private val optional: Boolean) :
    Action<ConfigurationVariantDetails> {

    override fun execute(details: ConfigurationVariantDetails) {
        val variant = details.configurationVariant
        if (checkValidArtifact(variant)) {
            details.mapToMavenScope(this.scope)
            if (this.optional) {
                details.mapToOptional()
            }
        } else {
            details.skip()
        }
    }

    private fun checkValidArtifact(element: ConfigurationVariant): Boolean {
        for (artifact in element.artifacts) {
            if (JavaBasePlugin.UNPUBLISHABLE_VARIANT_ARTIFACTS.contains(artifact.type)) {
                return false
            }
        }
        return true
    }
}
