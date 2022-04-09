/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.base.CaseFormat
import java.util.TreeSet
import com.google.common.annotations.VisibleForTesting

private fun GradleCoordinate.isAndroidX(): Boolean = groupId.startsWith("androidx.")

/**
 * Pick name with next steps. Each step generates name and check whether it
 * exists in reserved set where comparison is done in case-insensitive way.
 * If same name already exists, it tries the next step. Steps defined as follows:
 * - if artifact is androidx and any reserved alias has androidx prefix - add
 *   "androidx-" prefix;
 * - in other case just use artifactID
 *   "org.jetbrains.kotlin:kotlin-reflect" => "kotlin-reflect";
 * - if name already exists - add group suffix if it's not similar to name
 *   "com.google.libraries:guava" => "libraries-guava";
 * - if already exists - add group prefix
 *   "org.jetbrains.kotlin:kotlin-reflect" => "jetbrains-kotlin-reflect";
 * - if already exists - add group
 *   "org.jetbrains.kotlin:kotlin-reflect" => "jetbrains-kotlin-kotlin-reflect";
 * - if already exists - add number at the end until no such name in reserved
 *   "jetbrains-kotlin-reflect" => "jetbrains-kotlin-reflect2".
 */
fun pickLibraryVariableName(
    gc: GradleCoordinate,
    includeVersionInKey: Boolean,
    caseSensitiveReserved: Set<String>
): String {
    val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)
    reserved.addAll(caseSensitiveReserved)
    val versionSuffix =
        if (includeVersionInKey) "-" + gc.revision.replace('.', '_').toSafeKey() else ""

    if (gc.isAndroidX() && (reserved.isEmpty() || reserved.any { it.startsWith("androidx-") })) {
        val key = "androidx-${gc.artifactId.toSafeKey()}$versionSuffix"
        if (!reserved.contains(key)) {
            return key
        }
    }

    // Try a few combinations: just the artifact name, just the group-suffix and the artifact name,
    // just the group-prefix and the artifact name, etc.
    val artifactId = gc.artifactId.toSafeKey()
    val artifactKey = artifactId + versionSuffix
    if (!reserved.contains(artifactKey)) {
        return artifactKey
    }

    // Normally the groupId suffix plus artifact is used if it's not similar to artifact, e.g.
    // "com.google.libraries:guava" => "libraries-guava"
    val groupSuffix = gc.groupId.substringAfterLast('.').toSafeKey()
    val withGroupSuffix = "$groupSuffix-$artifactId$versionSuffix"
    if (!(artifactId.startsWith(groupSuffix))) {
        if (!reserved.contains(withGroupSuffix)) {
            return withGroupSuffix
        }
    }

    val groupPrefix = getGroupPrefix(gc)
    val withGroupPrefix = "$groupPrefix-$artifactId$versionSuffix"
    if (!reserved.contains(withGroupPrefix)) {
        return withGroupPrefix
    }

    val groupId = gc.groupId.toSafeKey()
    val full = "$groupId-$artifactId$versionSuffix"
    if (!reserved.contains(full)) {
        return full
    }

    // Final fallback; this is unlikely but JUST to be sure we get a unique version
    var id = 2
    while (true) {
        val name = "${full}${if (versionSuffix.isNotEmpty()) "-" else ""}${id++}"
        // Will eventually succeed
        if (!reserved.contains(name)) {
            return name
        }
    }
}

@VisibleForTesting
internal fun String.toSafeKey(): String {
    // Should filter to set of valid characters in an unquoted key; see `unquoted-key-char` in
    // https://github.com/toml-lang/toml/blob/main/toml.abnf .
    // In practice this seems close enough to Java's isLetterOrDigit definition.
    if (all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        return this
    }
    val sb = StringBuilder()
    for (c in this) {
        sb.append(if (c.isLetterOrDigit() || c == '-') c else if (c == '.') '-' else '_')
    }
    return sb.toString()
}

private fun getGroupPrefix(gc: GradleCoordinate): String {
    // For com.google etc., use "google" instead of "com"
    val groupPrefix = gc.groupId.substringBefore('.').toSafeKey()
    if (groupPrefix == "com" || groupPrefix == "org" || groupPrefix == "io") {
        return gc.groupId.substringAfter('.').substringBefore('.').toSafeKey()
    }
    return groupPrefix.toSafeKey()
}

/**
 * Variable name generator takes artifact id as a base. It analyses reserved
 * aliases and chose same notation. This can be a lower camel, lower hyphen,
 * lower camel with Version suffix. Alias will have hyphen, and to lower camel
 * notation otherwise. Picking variable happens in steps. Each step generates
 * some name around updated artifact id and check whether it's reserved with
 * case-insensitive set. If yes, jumping to the next step.
 * Steps defined as follows:
 * - use artifactId + "Version" suffix if this is the style;
 * - use artifactId as variable name;
 * - use artifactId + "Version" suffix if it's not a hyphen notation;
 * - use group prefix + "-" + artifactId;
 * - use group prefix + "-" + artifactId + "Version";
 * - use group name + "-" + artifactId;
 * - use group name + "-" + artifactId + /number/.
 */
 fun pickVersionVariableName(gc: GradleCoordinate, caseSensitiveReserved: Set<String>): String {
    // If using the artifactVersion convention, follow that
    val artifact = gc.artifactId.toSafeKey()
    val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)
    reserved.addAll(caseSensitiveReserved)

    if (reserved.isEmpty()) {
        return artifact
    }

    // Use a case-insensitive set when checking for clashes, such that
    // we don't for example pick a new variable named "appcompat" if "appCompat"
    // is already in the map.
    var haveCamelCase = false
    var haveHyphen = false
    for (name in reserved) {
        for (i in name.indices) {
            val c = name[i]
            if (c == '-') {
                haveHyphen = true
            } else if (i > 0 && c.isUpperCase() && name[i - 1].isLowerCase() && name[0].isLowerCase()) {
                haveCamelCase = true
            }
        }
    }

    val artifactCamel =
        if (artifact.contains('-')) CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, artifact)
        else artifact
    val artifactName = if (haveCamelCase) artifactCamel else artifact

    if (reserved.isNotEmpty() && reserved.first().endsWith("Version")) {
        val withVersion = "${artifactCamel}Version"
        if (!reserved.contains(withVersion)) {
            return withVersion
        }
    }

    // Default convention listed in https://docs.gradle.org/current/userguide/platforms.html seems to
    // be to just use the artifact name
    if (!reserved.contains(artifactName)) {
        return artifactName
    }

    if (!haveHyphen) {
        val withVersion = "${artifactCamel}Version"
        if (!reserved.contains(withVersion)) {
            return withVersion
        }
    }

    val groupPrefix = getGroupPrefix(gc)
    val withGroupIdPrefix = "$groupPrefix-$artifactName"
    if (!reserved.contains(withGroupIdPrefix)) {
        return withGroupIdPrefix
    }

    if (!haveHyphen) {
        val withGroupIdPrefixVersion = "$groupPrefix-${artifactCamel}Version"
        if (!reserved.contains(withGroupIdPrefixVersion)) {
            return withGroupIdPrefixVersion
        }
    }

    // With full group
    val groupId = gc.groupId.toSafeKey()
    val withGroupId = "$groupId-$artifactName"
    if (!reserved.contains(withGroupId)) {
        return withGroupId
    }

    // Final fallback; this is unlikely but JUST to be sure we get a unique version
    var id = 2
    while (true) {
        val name = "${withGroupId}${id++}"
        // Will eventually succeed
        if (!reserved.contains(name)) {
            return name
        }
    }
}
