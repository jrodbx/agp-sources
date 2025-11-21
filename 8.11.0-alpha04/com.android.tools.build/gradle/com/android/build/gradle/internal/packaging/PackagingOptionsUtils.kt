/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.packaging

// ATTENTION - keep this in sync with com.android.build.gradle.internal.dsl.PackagingOptions JavaDoc
val defaultExcludes: Set<String> = setOf(
    "/META-INF/LICENSE",
    "/META-INF/LICENSE.txt",
    "/META-INF/MANIFEST.MF",
    "/META-INF/NOTICE",
    "/META-INF/NOTICE.txt",
    "/META-INF/*.DSA",
    "/META-INF/*.EC",
    "/META-INF/*.SF",
    "/META-INF/*.RSA",
    "/META-INF/maven/**",
    "/META-INF/proguard/*",
    "/META-INF/com.android.tools/**",
    "/NOTICE",
    "/NOTICE.txt",
    "/LICENSE.txt",
    "/LICENSE",

    // Exclude version control folders.
    "**/.svn/**",
    "**/CVS/**",
    "**/SCCS/**",

    // Exclude hidden and backup files.
    "**/.*/**",
    "**/.*",
    "**/*~",

    // Exclude index files
    "**/thumbs.db",
    "**/picasa.ini",

    // Exclude javadoc files
    "**/about.html",
    "**/package.html",
    "**/overview.html",

    // Exclude protobuf metadata files
    "**/protobuf.meta",

    // Exclude stuff for unknown reasons
    "**/_*",
    "**/_*/**",

    // Exclude kotlin metadata files
    "**/*.kotlin_metadata"
)

// ATTENTION - keep this in sync with com.android.build.gradle.internal.dsl.PackagingOptions JavaDoc
val defaultMerges: Set<String> = setOf("/META-INF/services/**", "jacoco-agent.properties")
