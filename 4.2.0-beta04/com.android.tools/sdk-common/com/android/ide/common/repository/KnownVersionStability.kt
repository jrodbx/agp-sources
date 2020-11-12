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
package com.android.ide.common.repository

import com.android.SdkConstants.ANNOTATIONS_LIB_ARTIFACT_ID
import com.android.SdkConstants.MATERIAL2_PKG
import com.android.SdkConstants.SUPPORT_LIB_GROUP_ID

private const val FIREBASE_GROUP_ID = "com.google.firebase"
private const val GOOGLE_MOBILE_SERVICES_GROUP_ID = "com.google.android.gms"
private const val GMS_AND_FIREBASE_SEMANTIC_START = 15

// The kotlin stability rules are found here:
//     https://kotlinlang.org/docs/reference/evolution/components-stability.html
private const val KOTLIN_GROUP_ID = "org.jetbrains.kotlin"
private const val KOTLIN_STDLIB = "kotlin-stdlib"
private const val KOTLIN_REFLECT = "kotlin-reflect"
private const val KOTLIN_JDK9 = "kotlin-stdlib-jdk9"
private const val KOTLIN_JDK8 = "kotlin-stdlib-jdk8"
private const val KOTLIN_JDK7 = "kotlin-stdlib-jdk7"
private const val KOTLIN_JRE9 = "kotlin-stdlib-jre9"
private const val KOTLIN_JRE8 = "kotlin-stdlib-jre8"
private const val KOTLIN_JRE7 = "kotlin-stdlib-jre7"

/**
 * Stability of known libraries.
 *
 * When we want to add a dependency we would like to warn the user if the added
 * library could be causing version incompatibilities. Unfortunately different
 * libraries adhere to different standards, and there is no known way to determine
 * the stability from the library itself.
 *
 * Here we setup the known rules for the following libraries:
 * - Android X libraries uses [semantic rules](https://semver.org/)
 * - The new material library uses semantic rules
 * - Legacy Android libraries have no guaranteed backwards compatibility
 * - Firebase uses semantic rules starting with 15.0.0 (no backwards compatibility before that)
 * - Google Mobile Services uses semantic rules starting with 15.0.0
 * - Mockito uses semantic rules
 * - Kotlib stdlib are all stable
 * - Kotlin reflection library is incremental
 *
 * Unknown libraries are handled as having no backwards compatibility.
 * This may lead to warnings that the user would have to accept.
 */
enum class KnownVersionStability {
    INCOMPATIBLE, // No backwards compatibility.
    INCREMENTAL,  // Backwards breaking changes may happen on minor version changes.
    SEMANTIC,     // Backwards breaking changes may happen on major version changes.
    STABLE;       // Guaranteed backwards compatibility forever.

  /**
   * Expiration from a given [version].
   *
   * For a given minimum [version] return the maximum or expiration [version] of a
   * dependency with a given stability. This is used to generate the exclusive upper
   * bound in a [GradleVersionRange].
   */
  fun expiration(version: GradleVersion): GradleVersion =
        when (this) {
            INCOMPATIBLE -> GradleVersion(version.major, version.minor, version.micro + 1)
            INCREMENTAL -> GradleVersion(version.major, version.minor + 1, 0)
            SEMANTIC -> GradleVersion(version.major + 1, 0, 0)
            STABLE -> GradleVersion(Int.MAX_VALUE, 0, 0)
        }
}

fun stabilityOf(
    groupId: String,
    artifactId: String,
    revision: String = "1.0.0"
): KnownVersionStability =
    when {
        groupId == KOTLIN_GROUP_ID -> kotlinStabilityOf(artifactId)
        groupId == GOOGLE_MOBILE_SERVICES_GROUP_ID -> gmsAndFirebaseStability(revision)
        groupId == FIREBASE_GROUP_ID -> gmsAndFirebaseStability(revision)
        groupId == MATERIAL2_PKG -> KnownVersionStability.SEMANTIC
        groupId == SUPPORT_LIB_GROUP_ID -> supportLibStability(artifactId)
        MavenRepositories.isAndroidX(groupId) -> KnownVersionStability.SEMANTIC
        else -> KnownVersionStability.INCOMPATIBLE
    }

private fun kotlinStabilityOf(artifactId: String): KnownVersionStability =
    when (artifactId) {
        KOTLIN_STDLIB -> KnownVersionStability.STABLE
        KOTLIN_REFLECT -> KnownVersionStability.INCREMENTAL
        KOTLIN_JDK9,
        KOTLIN_JDK8,
        KOTLIN_JDK7,
        KOTLIN_JRE9,
        KOTLIN_JRE8,
        KOTLIN_JRE7 -> KnownVersionStability.STABLE
        else -> KnownVersionStability.INCOMPATIBLE
    }

private fun gmsAndFirebaseStability(revision: String): KnownVersionStability {
    val version = GradleVersion.tryParse(revision)
    return if (version != null && version.major >= GMS_AND_FIREBASE_SEMANTIC_START)
        KnownVersionStability.SEMANTIC
    else
        KnownVersionStability.INCOMPATIBLE
}

/**
 * Stability of legacy support libraries.
 *
 * All support libraries must have the exact same version number.
 * However there are JetPack libraries that point to an old version of the support
 * annotations. This library just contain annotations and we will make the assumption
 * here that we nothing was removed or changed in the last couple of versions.
 *
 * <p>JetPack Details b/129408604
 */
private fun supportLibStability(artifactId: String): KnownVersionStability {
    return if (artifactId == ANNOTATIONS_LIB_ARTIFACT_ID)
        KnownVersionStability.STABLE
    else
        KnownVersionStability.INCOMPATIBLE
}
