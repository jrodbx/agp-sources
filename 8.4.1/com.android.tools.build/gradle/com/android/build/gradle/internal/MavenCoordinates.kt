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

package com.android.build.gradle.internal

/**
 * Enums representing Maven coordinates that have usages
 */
enum class MavenCoordinates (val group: String, val artifact: String,  val defaultVersion: String) {
    ANDROIDX_PRIVACY_SANDBOX_SDK_API_GENERATOR(
        "androidx.privacysandbox.tools",
        "tools-apigenerator",
        "1.0.0-alpha03"
    ),
    ANDROIDX_PRIVACYSANDBOX_TOOLS_TOOLS_API_PACKAGER(
        "androidx.privacysandbox.tools",
        "tools-apipackager",
        "1.0.0-alpha03"
    ),
    ORG_JETBRAINS_KOTLINX_KOTLINX_COROUTINES_ANDROID(
        "org.jetbrains.kotlinx",
        "kotlinx-coroutines-android",
        "1.6.4"
    ),
    KOTLIN_COMPILER(
        "org.jetbrains.kotlin",
        "kotlin-compiler-embeddable",
        "1.7.10");

    fun withVersion(newVersion: String): String = "$group:$artifact:$newVersion"
    override fun toString(): String = "$group:$artifact:$defaultVersion"

}
