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
    ANDROIDX_PRIVACYSANDBOX_TOOLS_TOOLS_APIGENERATOR(
        "androidx.privacysandbox.tools",
        "tools-apigenerator",
        "1.0.0-alpha08"
    ),
    ANDROIDX_PRIVACYSANDBOX_TOOLS_TOOLS_APIPACKAGER(
        "androidx.privacysandbox.tools",
        "tools-apipackager",
        "1.0.0-alpha08"
    ),
    ANDROIDX_PRIVACYSANDBOX_UI_UI_CORE(
        "androidx.privacysandbox.ui",
        "ui-core",
        "1.0.0-alpha07"
    ),
    ANDROIDX_PRIVACYSANDBOX_UI_UI_CLIENT(
        "androidx.privacysandbox.ui",
        "ui-client",
        "1.0.0-alpha07"
    ),
    ORG_JETBRAINS_KOTLIN_KOTLIN_COMPILER_EMBEDDABLE(
        "org.jetbrains.kotlin",
        "kotlin-compiler-embeddable",
        "1.8.10"
    ),
    ORG_JETBRAINS_KOTLIN_KOTLIN_STDLIB(
        "org.jetbrains.kotlin",
        "kotlin-stdlib",
        "1.7.20-RC"
    ),
    ORG_JETBRAINS_KOTLINX_KOTLINX_COROUTINES_ANDROID(
    "org.jetbrains.kotlinx",
    "kotlinx-coroutines-android",
    "1.7.1"
    )
    ;

    fun withVersion(newVersion: String): String = "$group:$artifact:$newVersion"
    override fun toString(): String = "$group:$artifact:$defaultVersion"

}
