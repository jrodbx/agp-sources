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

package com.android.build.gradle.internal.dependency

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails

private const val ANDROID_JVM_PLATFORM_TYPE = "androidJvm"
private const val JVM_PLATFORM_TYPE = "jvm"
private const val COMMON_TYPE = "common"

object KotlinPlatformAttribute {
    @JvmStatic
    fun configureKotlinPlatformAttribute(configs: List<Configuration>, project: Project) {
        val kotlinPlatformTypeAttribute =
            Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java)

        configs.forEach {
            it.attributes.attribute(
                kotlinPlatformTypeAttribute,
                ANDROID_JVM_PLATFORM_TYPE
            )
        }

        project.dependencies.attributesSchema.attribute(kotlinPlatformTypeAttribute).also {
            it.compatibilityRules.add(KotlinPlatformCompatibilityRule::class.java)
            it.disambiguationRules.add(KotlinPlatformDisambiguationRule::class.java)
        }
    }
}

class KotlinPlatformCompatibilityRule : AttributeCompatibilityRule<String> {
    override fun execute(details: CompatibilityCheckDetails<String>) = with(details) {
        if (producerValue == JVM_PLATFORM_TYPE && consumerValue == ANDROID_JVM_PLATFORM_TYPE)
            compatible()

        if (consumerValue == COMMON_TYPE)
            compatible()
    }
}

class KotlinPlatformDisambiguationRule : AttributeDisambiguationRule<String> {
    override fun execute(details: MultipleCandidatesDetails<String?>) = with(details) {
        if (consumerValue in candidateValues) {
            closestMatch(checkNotNull(consumerValue))
            return@with
        }

        if (consumerValue == null && ANDROID_JVM_PLATFORM_TYPE in candidateValues && JVM_PLATFORM_TYPE in candidateValues) {
            closestMatch(JVM_PLATFORM_TYPE)
            return@with
        }

        if (COMMON_TYPE in candidateValues && JVM_PLATFORM_TYPE !in candidateValues && ANDROID_JVM_PLATFORM_TYPE !in candidateValues) {
            closestMatch(COMMON_TYPE)
            return@with
        }
    }
}
