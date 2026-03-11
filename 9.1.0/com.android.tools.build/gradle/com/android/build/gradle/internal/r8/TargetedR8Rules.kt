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

package com.android.build.gradle.internal.r8

import com.android.build.gradle.internal.dependency.ShrinkerVersion
import com.android.build.gradle.internal.r8.LegacyProguardRules.Companion.PROGUARD_TXT_FOR_AAR
import com.android.ide.common.r8.ConsumerRuleGlobalGuardian
import java.io.File
import java.util.zip.ZipInputStream

/**
 * R8 rules that are tailored for specific R8 versions.
 *
 * Targeted R8 rules were first introduced in AGP 3.6 (https://issuetracker.google.com/135672715).
 *
 * The rules consist of:
 * - [VersionedR8Rules] (version-specific R8 rules)
 * - [LegacyProguardRules] (non-version-specific Proguard rules, which existed before the introduction of targeted R8 rules)
 *
 * [LegacyProguardRules] are included to maintain backward compatibility. Specifically:
 * - On the producing side, a library (AAR or JAR) can choose to specify both [VersionedR8Rules] and [LegacyProguardRules], or only
 *   [LegacyProguardRules]. (If the library was created before the introduction of targeted R8 rules, then [LegacyProguardRules] was the
 *   only option.) The library should not specify only [VersionedR8Rules] as older R8 versions will not be able to consume it.
 * - On the consuming side, R8 needs to be able to consume both types of libraries:
 *     + If a library has [LegacyProguardRules] only, R8 will consume it.
 *     + If a library has both [VersionedR8Rules] and [LegacyProguardRules], R8 will consume [VersionedR8Rules] and ignore
 *       [LegacyProguardRules]. Note that older R8 versions that are not able to consume [VersionedR8Rules] will continue to consume
 *       [LegacyProguardRules].
 *
 * Any filtering for that should occur is done during extraction from the relevant JAR/AAR.
 */
data class TargetedR8Rules(val r8Rules: List<VersionedR8Rules>, val legacyProguardRules: List<LegacyProguardRules>)

/** Version-specific R8 rules. */
data class VersionedR8Rules(

  /**
   * The minimum R8 version which supports reading the [r8Rules].
   *
   * If it is null, there is no constraint.
   */
  val minVersion: ShrinkerVersion?,

  /**
   * The lowest R8 version above [minVersion] which no longer supports reading the [r8Rules].
   *
   * If it is null, there is no constraint.
   */
  val maxVersionExclusive: ShrinkerVersion?,

  /** The name of the file containing [r8Rules]. */
  val fileName: String,

  /** The R8 rules. */
  val r8Rules: String,
) {

  /**
   * The relative path of the file containing [r8Rules]. It is relative to the root directory inside a JAR or inside classes.jar of an AAR.
   */
  val relativeFilePath: String
    get() =
      "META-INF/com.android.tools/r8" +
        (minVersion?.let { "-from-${it.asString()}" } ?: "") +
        (maxVersionExclusive?.let { "-upto-${it.asString()}" } ?: "") +
        "/$fileName"

  companion object {

    /** [Regex] describing the [relativeFilePath] of R8 rules. */
    val relativeFilePathRegex: Regex by lazy {
      "META-INF/com.android.tools/r8(-from-(?<minVersion>((?!-upto-)[^/])+))?(-upto-(?<maxVersionExclusive>[^/]+))?/(?<fileName>[^/]+)"
        .toRegex()
    }
  }
}

/** Legacy Proguard rules, which existed before the introduction of [TargetedR8Rules]. */
data class LegacyProguardRules(

  /**
   * The name of the file containing [legacyProguardRules].
   *
   * Note: For an AAR, it is "proguard.txt" (see [PROGUARD_TXT_FOR_AAR]).
   */
  val fileName: String,

  /** The legacy Proguard rules. */
  val legacyProguardRules: String,
) {

  /**
   * The relative path of the file containing [legacyProguardRules]. It is relative to the root directory inside a JAR or inside an AAR (not
   * inside classes.jar of an AAR).
   * - For a JAR, the path is "META-INF/proguard/[fileName]".
   * - For an AAR, the path is "proguard.txt" (see [PROGUARD_TXT_FOR_AAR]).
   */
  fun getRelativeFilePath(forAar: Boolean): String {
    return if (forAar) {
      check(fileName == PROGUARD_TXT_FOR_AAR)
      PROGUARD_TXT_FOR_AAR
    } else {
      "META-INF/proguard/$fileName"
    }
  }

  companion object {

    /**
     * [Regex] describing the relative path of the file containing legacy Proguard rules for a JAR.
     *
     * Note: This does not apply to classes.jar of an AAR (see [PROGUARD_TXT_FOR_AAR]).
     */
    val legacyProguardRulesRelativeFilePathRegexForJar: Regex by lazy { "META-INF/proguard/(?<fileName>[^/]+)".toRegex() }

    /**
     * The name of the file containing legacy Proguard rules for an AAR. Note that it is located directly under the AAR, not inside
     * classes.jar of the AAR.
     */
    const val PROGUARD_TXT_FOR_AAR: String = "proguard.txt"
  }
}

/** Utility to read/write [TargetedR8Rules]. */
object TargetedR8RulesReadWriter {

  /**
   * Given the [TargetedR8Rules], returns a map from the relative file paths of the [TargetedR8Rules] to their contents. This map can then
   * be used to write to a JAR or classes.jar of an AAR.
   * - For a JAR, return all contents including [LegacyProguardRules].
   * - For classes.jar of an AAR ([isClassesJarInAar] = true), return all contents except [LegacyProguardRules]. This is because for an AAR,
   *   the location of the legacy Proguard rules is outside classes.jar (see [LegacyProguardRules.PROGUARD_TXT_FOR_AAR]).
   */
  fun TargetedR8Rules.createJarContents(isClassesJarInAar: Boolean = false): Map<String, ByteArray> {
    return r8Rules.associate { it.relativeFilePath to it.r8Rules.toByteArray() } +
      if (isClassesJarInAar) emptyMap()
      else legacyProguardRules.associate { it.getRelativeFilePath(forAar = false) to it.legacyProguardRules.toByteArray() }
  }

  /**
   * Reads [TargetedR8Rules] from the given JAR or classes.jar of an AAR.
   * - For a JAR, read all contents including [LegacyProguardRules].
   * - For classes.jar of an AAR ([isClassesJarInAar] = true), read all contents except [LegacyProguardRules]. This is because for an AAR,
   *   the location of the legacy Proguard rules is outside classes.jar (see [LegacyProguardRules.PROGUARD_TXT_FOR_AAR]).
   *
   * Filtering out banned global options may be performed at read time by specifying [shouldRemoveBannedGlobals].
   */
  fun readFromJar(jarFile: File, isClassesJarInAar: Boolean, shouldRemoveBannedGlobals: Boolean): TargetedR8Rules {
    val r8Rules = mutableListOf<VersionedR8Rules>()
    val legacyProguardRules = mutableListOf<LegacyProguardRules>()

    ZipInputStream(jarFile.inputStream().buffered()).use { zipInputStream ->
      while (true) {
        val zipEntry = zipInputStream.nextEntry ?: break

        VersionedR8Rules.relativeFilePathRegex.matchEntire(zipEntry.name)?.let { matchResult ->
          r8Rules.add(
            VersionedR8Rules(
              minVersion = matchResult.groups["minVersion"]?.value?.let { ShrinkerVersion.tryParse(it) },
              maxVersionExclusive = matchResult.groups["maxVersionExclusive"]?.value?.let { ShrinkerVersion.tryParse(it) },
              fileName = matchResult.groups["fileName"]!!.value,
              r8Rules =
                ConsumerRuleGlobalGuardian.readConsumerKeepRulesRemovingBannedGlobals(
                  zipInputStream,
                  shouldRemoveBannedGlobals = shouldRemoveBannedGlobals,
                ),
            )
          )
        }
          ?: if (isClassesJarInAar) null
          else {
            LegacyProguardRules.legacyProguardRulesRelativeFilePathRegexForJar.matchEntire(zipEntry.name)?.let { matchResult ->
              legacyProguardRules.add(
                LegacyProguardRules(
                  fileName = matchResult.groups["fileName"]!!.value,
                  legacyProguardRules =
                    ConsumerRuleGlobalGuardian.readConsumerKeepRulesRemovingBannedGlobals(
                      zipInputStream,
                      shouldRemoveBannedGlobals = shouldRemoveBannedGlobals,
                    ),
                )
              )
            }
          }
      }
    }

    return TargetedR8Rules(r8Rules, legacyProguardRules)
  }
}
