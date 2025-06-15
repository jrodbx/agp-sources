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
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Shrinking rules that target specific code shrinkers (R8 or Proguard), as well as specific
 * shrinker versions (see [VersionedShrinkRules]).
 *
 * These rules were introduced in AGP 3.6 (b/135672715).
 *
 * The rules consist of:
 *   - [VersionedR8Rules]
 *   - [VersionedProguardRules]
 *   - [LegacyProguardRules] (non-version-specific Proguard rules, which existed before the
 *     introduction of targeted shrinking rules)
 *
 * [LegacyProguardRules] are included to support backward compatibility. Specifically:
 *   - On the producing side, a library (AAR or JAR) can choose to specify both
 *   [VersionedShrinkRules] and [LegacyProguardRules], or only [LegacyProguardRules]. (If the
 *   library was created before the introduction of targeted shrink rules in AGP 3.6, then
 *   [LegacyProguardRules] was the only option.) The library should not specify only
 *   [VersionedShrinkRules] as older code shrinkers will not be able to consume it.
 *   - On the consuming side, a code shrinker (R8 or Proguard) needs to be able to consume both
 *   types of libraries:
 *     + If a library has [LegacyProguardRules] only, the code shrinker will consume it.
 *     + If a library has both [TargetedShrinkRules] and [LegacyProguardRules], the code shrinker
 *       will consume [TargetedShrinkRules] and ignore [LegacyProguardRules].
 */
data class TargetedShrinkRules(
    val r8Rules: List<VersionedR8Rules>,
    val proguardRules: List<VersionedProguardRules>,
    val legacyProguardRules: List<LegacyProguardRules>
)

/** Version-specific R8 or Proguard rules. */
sealed interface VersionedShrinkRules {

    /** The minimum shrinker version which supports reading these rules. */
    val minVersion: ShrinkerVersion?

    /** The lowest shrinker version above [minVersion] which no longer supports reading these rules. */
    val maxVersionExclusive: ShrinkerVersion?

    /** The name of the file containing these rules. */
    val fileName: String

    /** The R8/Proguard rules. */
    val shrinkRules: String

    /** The location of the R8/Proguard rules inside a JAR (or inside classes.jar of an AAR). */
    val relativeFilePath: String

}

/** Version-specific R8 rules. */
data class VersionedR8Rules(
    override val minVersion: ShrinkerVersion?,
    override val maxVersionExclusive: ShrinkerVersion?,
    override val fileName: String,
    override val shrinkRules: String
) : VersionedShrinkRules {

    override val relativeFilePath: String
        get() = "META-INF/com.android.tools/r8" +
                (minVersion?.let { "-from-${it.asString()}" } ?: "") +
                (maxVersionExclusive?.let { "-upto-${it.asString()}" } ?: "") +
                "/$fileName"

    companion object {

        /** [Regex] describing the location of R8 rules inside a JAR (or inside classes.jar of an AAR). */
        val filePathRegex: Regex
            get() = "META-INF/com.android.tools/r8(-from-(?<minVersion>((?!-upto-)[^/])+))?(-upto-(?<maxVersionExclusive>[^/]+))?/(?<fileName>[^/]+)".toRegex()
    }
}

/** Version-specific Proguard rules. */
data class VersionedProguardRules(
    override val minVersion: ShrinkerVersion?,
    override val maxVersionExclusive: ShrinkerVersion?,
    override val fileName: String,
    override val shrinkRules: String
) : VersionedShrinkRules {

    override val relativeFilePath: String
        get() = "META-INF/com.android.tools/proguard" +
                (minVersion?.let { "-from-${it.asString()}" } ?: "") +
                (maxVersionExclusive?.let { "-upto-${it.asString()}" } ?: "") +
                "/$fileName"

    companion object {

        /** [Regex] describing the location of Proguard rules inside a JAR (or inside classes.jar of an AAR). */
        val filePathRegex: Regex
            get() = "META-INF/com.android.tools/proguard(-from-(?<minVersion>((?!-upto-)[^/])+))?(-upto-(?<maxVersionExclusive>[^/]+))?/(?<fileName>[^/]+)".toRegex()
    }
}

/** Legacy Proguard rules, which existed before the introduction of [TargetedShrinkRules]. */
data class LegacyProguardRules(

    /** The name of the file containing these rules. */
    val fileName: String,

    /** The legacy Proguard rules. */
    val legacyProguardRules: String
) {

    /** The location of the [LegacyProguardRules] for a JAR (not classes.jar of an AAR). */
    val relativeFilePathForJar: String
        get() = "META-INF/proguard/$fileName"

    /** The location of the [LegacyProguardRules] for an AAR. */
    val relativeFilePathForAar: String
        get() = run {
            check(fileName == LEGACY_PROGUARD_RULES_FILE_PATH_FOR_AAR) {
                "Legacy Proguard rules file name for an AAR must be `$LEGACY_PROGUARD_RULES_FILE_PATH_FOR_AAR`, but it is currently: $fileName"
            }
            fileName
        }

    companion object {

        /** [Regex] describing the location of [LegacyProguardRules] for a JAR (not classes.jar of an AAR). */
        val legacyProguardRulesFilePathRegexForJar: Regex
            get() = "META-INF/proguard/(?<fileName>[^/]+)".toRegex()

        /** The location of [LegacyProguardRules] for an AAR. */
        const val LEGACY_PROGUARD_RULES_FILE_PATH_FOR_AAR: String = "proguard.txt"
    }

}

/** Utility to read/write [TargetedShrinkRules]. */
object TargetedShrinkRulesReadWriter {

    /**
     * Writes [TargetedShrinkRules] to a map from the locations of the [TargetedShrinkRules]
     * to their contents, which can then be used to write to a JAR.
     */
    fun TargetedShrinkRules.createJarContents(): Map<String, ByteArray> {
        return (r8Rules + proguardRules).associate {
            it.relativeFilePath to it.shrinkRules.toByteArray()
        } + legacyProguardRules.associate {
            it.relativeFilePathForJar to it.legacyProguardRules.toByteArray()
        }
    }

    /** Reads [TargetedShrinkRules] from the given [jarFile]. */
    fun readFromJar(jarFile: File): TargetedShrinkRules {
        val r8Rules = mutableListOf<VersionedR8Rules>()
        val proguardRules = mutableListOf<VersionedProguardRules>()
        val legacyProguardRules = mutableListOf<LegacyProguardRules>()

        ZipInputStream(jarFile.inputStream().buffered()).use { zipInputStream ->
            while (true) {
                val zipEntry = zipInputStream.nextEntry ?: break

                VersionedR8Rules.filePathRegex.matchEntire(zipEntry.name)?.let { matchResult ->
                    r8Rules.add(
                        VersionedR8Rules(
                            fileName = matchResult.groups["fileName"]!!.value,
                            minVersion = matchResult.groups["minVersion"]?.value?.let { ShrinkerVersion.tryParse(it) },
                            maxVersionExclusive = matchResult.groups["maxVersionExclusive"]?.value?.let { ShrinkerVersion.tryParse(it) },
                            shrinkRules = zipInputStream.readBytes().decodeToString()
                        )
                    )
                } ?: VersionedProguardRules.filePathRegex.matchEntire(zipEntry.name)?.let { matchResult ->
                        proguardRules.add(
                            VersionedProguardRules(
                                fileName = matchResult.groups["fileName"]!!.value,
                                minVersion = matchResult.groups["minVersion"]?.value?.let { ShrinkerVersion.tryParse(it) },
                                maxVersionExclusive = matchResult.groups["maxVersionExclusive"]?.value?.let { ShrinkerVersion.tryParse(it) },
                                shrinkRules = zipInputStream.readBytes().decodeToString()
                            )
                        )
                } ?: LegacyProguardRules.legacyProguardRulesFilePathRegexForJar.matchEntire(zipEntry.name)?.let { matchResult ->
                    legacyProguardRules.add(
                        LegacyProguardRules(
                            fileName = matchResult.groups["fileName"]!!.value,
                            legacyProguardRules = zipInputStream.readBytes().decodeToString()
                        )
                    )
                }
            }
        }

        return TargetedShrinkRules(r8Rules, proguardRules, legacyProguardRules)
    }
}
