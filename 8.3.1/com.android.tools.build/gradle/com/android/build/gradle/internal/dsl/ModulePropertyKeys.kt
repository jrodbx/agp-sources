/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.parseBoolean
import org.gradle.api.artifacts.Dependency

sealed interface ModulePropertyKey<OutputT> {

    enum class Dependencies(override val key: String) : ModulePropertyKey<List<Dependency>?> {
        /**
         * A [Dependency] providing apigenerator artifact.
         */
        ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR(
                StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR.propertyName),

        /**
         * [ArrayList<Dependency>] of required runtime dependencies of the artifact of the apigenerator.
         */
        ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR_GENERATED_RUNTIME_DEPENDENCIES(
                StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR_GENERATED_RUNTIME_DEPENDENCIES.propertyName),

        /**
         * A [Dependency] providing apipackager artifact.
         */
        ANDROID_PRIVACY_SANDBOX_SDK_API_PACKAGER(
                StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_PACKAGER.propertyName)
        ;

        override fun getValue(properties: Map<String, Any>): List<Dependency>? {
            return when(val value = properties[key]) {
                null -> null
                is Dependency -> listOf(value)
                is List<*> -> value as List<Dependency>
                else -> throw IllegalArgumentException("Unexpected type ${value::class.qualifiedName} for property $key")
            }
        }
    }

    enum class OptionalBoolean(override val key: String) : ModulePropertyKey<Boolean?> {
        VERIFY_AAR_CLASSES(BooleanOption.VERIFY_AAR_CLASSES.propertyName),

        /**
         * Whether to use K2 UAST when running lint. The corresponding global property is
         * [BooleanOption.LINT_USE_K2_UAST].
         */
        LINT_USE_K2_UAST(BooleanOption.LINT_USE_K2_UAST.propertyName),
        ;

        override fun getValue(properties: Map<String, Any>): Boolean? {
            return properties[key]?.let { parseBoolean(key, it) }
        }
    }

    enum class OptionalString(override val key: String) : ModulePropertyKey<String?> {
        ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_NAME(
                "android.privacy_sandbox.local_deployment_signing_name"
        ),
        ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_STORE_TYPE(
                "android.privacy_sandbox.local_deployment_signing_store_type"
        ),
        ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_STORE_FILE(
                "android.privacy_sandbox.local_deployment_signing_store_file"
        ),
        ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_STORE_PASSWORD(
                "android.privacy_sandbox.local_deployment_signing_store_password"
        ),
        ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_KEY_ALIAS(
                "android.privacy_sandbox.local_deployment_signing_key_alias"
        ),
        ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_KEY_PASSOWRD(
                "android.privacy_sandbox.local_deployment_signing_key_password"
        ),

        /**
         * The page size used for alignment when writing uncompressed native libraries to the APK.
         * Supported values are "4k", "16k", and "64k". The default is "16k".
         */
        NATIVE_LIBRARY_PAGE_SIZE("android.nativeLibraryAlignmentPageSize"),
        ;

        override fun getValue(properties: Map<String, Any>): String? {
            return properties[key] as String?
        }
    }

    enum class BooleanWithDefault(override val key: String, private val default: Boolean) : ModulePropertyKey<Boolean> {
        /**
         * If false - the test APK instruments the target project APK, and the classes are provided.
         * If true - the test APK targets itself (e.g. for macro benchmarks)
         */
        SELF_INSTRUMENTING("android.experimental.self-instrumenting", false),

        /**
         * If false -  R8 will not be provided with the merged art-profile
         * If true - R8 will rewrite the art-profile
         */
        ART_PROFILE_R8_REWRITING("android.experimental.art-profile-r8-rewriting", true),

        /**
         * If false - R8 will not attempt to optimize startup dex
         * If true - R8 will optimize first dex for optimal startup performance.
         */
        R8_DEX_STARTUP_OPTIMIZATION("android.experimental.r8.dex-startup-optimization", true)
        ;

        override fun getValue(properties: Map<String, Any>): Boolean {
            return properties[key]?.let { parseBoolean(key, it) } ?: default
        }
    }

    fun getValue(properties: Map<String, Any>): OutputT

    val key: String
}

