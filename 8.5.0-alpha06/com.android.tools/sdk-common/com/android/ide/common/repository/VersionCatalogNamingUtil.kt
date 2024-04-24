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

import com.android.ide.common.gradle.Dependency
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.CaseFormat
import java.util.TreeSet

private fun Dependency.isAndroidX(): Boolean = group?.startsWith("androidx.") ?: false

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
    dependency: Dependency,
    includeVersionInKey: Boolean,
    caseSensitiveReserved: Set<String>
): String {
    val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)
    reserved.addAll(caseSensitiveReserved)

    val transform = getMaybeTransformToCamelCase(reserved)

    val versionIdentifier = dependency.version?.toIdentifier()
    val versionSuffix = when {
        versionIdentifier == null -> ""
        !includeVersionInKey -> ""
        else -> "-" + "v${versionIdentifier.replace("[^A-Za-z0-9]".toRegex(), "")}".toSafeKey()
    }

    if (dependency.isAndroidX() && (reserved.isEmpty() || reserved.any { it.startsWith("androidx-") })) {
        val key = transform("androidx-${dependency.name.toSafeKey()}$versionSuffix")
        if (!reserved.contains(key)) {
            return key
        }
    }

    // Try a few combinations: just the artifact name, just the group-suffix and the artifact name,
    // just the group-prefix and the artifact name, etc.
    val artifactId = dependency.name.toSafeKey()
    val artifactKey = transform(artifactId + versionSuffix)
    if (!reserved.contains(artifactKey)) {
        return artifactKey
    }

    // Normally the groupId suffix plus artifact is used if it's not similar to artifact, e.g.
    // "com.google.libraries:guava" => "libraries-guava"
    val groupSuffix = dependency.group?.substringAfterLast('.')?.toSafeKey() ?: "nogroup"
    val withGroupSuffix = transform("$groupSuffix-$artifactId$versionSuffix")
    if (!(artifactId.startsWith(groupSuffix))) {
        if (!reserved.contains(withGroupSuffix)) {
            return withGroupSuffix
        }
    }

    val groupPrefix = getGroupPrefix(dependency)
    val withGroupPrefix = transform("$groupPrefix-$artifactId$versionSuffix")
    if (!reserved.contains(withGroupPrefix)) {
        return withGroupPrefix
    }

    val groupId = dependency.group?.toSafeKey() ?: "nogroup"
    val full = "$groupId-$artifactId$versionSuffix"
    val fullSafe = transform(full)
    if (!reserved.contains(fullSafe)) {
        return fullSafe
    }

    // Final fallback; this is unlikely but JUST to be sure we get a unique version
    var id = 2
    while (true) {
        val name = transform("${full}${if (versionSuffix.isNotEmpty()) "-x" else ""}${id++}")
        // Will eventually succeed
        if (!reserved.contains(name)) {
            return name
        }
    }
}

private fun getMaybeTransformToCamelCase(reserved: Set<String>): (String) -> String {
    val (haveCamelCase, haveHyphen) = getAliasStyle(reserved)
    val camelCaseOutput = haveCamelCase && !haveHyphen

    return { alias ->
        val safeAlias = alias.toSafeHyphenKey()
        if (camelCaseOutput && safeAlias.contains("-")) {
            CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, alias.toSafeHyphenKey())
        } else alias
    }
}

private fun getMaybeTransformVersionToCamelCase(reserved: Set<String>): (String) -> String {
    // Use a case-insensitive set when checking for clashes, such that
    // we don't for example pick a new variable named "appcompat" if "appCompat"
    // is already in the map.
    val (haveCamelCase, haveHyphen) = getAliasStyle(reserved)
    val camelCaseOutput = haveCamelCase || !haveHyphen

    return { version ->
        if (camelCaseOutput) CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, version) else version
    }
}

/**
 * Pick name with next steps. Each step generates name and check whether it
 * exists in reserved set where comparison is done in case-insensitive way.
 * If same name already exists, it tries the next step. Steps defined as follows:
 * - pluginId will cut root domain and transform "." to hyphens
 *   "org.android.application" => android-application
 * - in other case it will transform "." to hyphens for whole pluginId
 *   "org.android.application" => org-android-application
 * - if already exists - add "X" + /number/ at the end until no such name in reserved
 *   "org.android.application" => org-android-applicationX2
 */
fun pickPluginVariableName(
    pluginId: String,
    caseSensitiveReserved: Set<String>
): String {
    val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)
    reserved.addAll(caseSensitiveReserved)

    val transform = getMaybeTransformToCamelCase(reserved)

    val plugin = cutDomainPrefix(pluginId)

    // without com/org domain prefix
    val shortName = transform(plugin.toSafeHyphenKey())
    if (!reserved.contains(shortName)) {
        return shortName
    }

    val fullName = transform(pluginId.toSafeHyphenKey())
    if (!reserved.contains(fullName)) {
        return fullName
    }

    return generateWithSuffix(fullName + "X", reserved, transform)
}

private fun maybeLowCamelTransform(name: String):String =
    if (name.contains("-")) CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, name)
    else name

@VisibleForTesting
internal fun String.toSafeKey(): String {
    fun Char.isSafe() = isLowerCase() || isDigit()
    fun Char.isSeparator() = this == '_' || this == '-'
    // Handle edge cases and actually safe keys
    when {
        isEmpty() -> return "empty"
        length == 1 && this[0].lowercaseChar().isLowerCase() -> return "${this[0].lowercaseChar()}x"
        length == 1 -> return "xx"
        this[0].isLowerCase() && all { it.isSafe() } -> return this
    }

    // Construct a safe key
    val sb = StringBuilder()
    this[0].lowercaseChar().let { c -> sb.append(if (c.isLowerCase()) c else 'x') }
    for (c in this.substring(1)) {
        when {
            c.isSafe() -> sb.append(c)
            c.lowercaseChar().isSafe() -> sb.append(c.lowercaseChar())
            c.isSeparator() -> if (!sb[sb.length-1].isSeparator()) sb.append(c)
            c == '.' -> if (!sb[sb.length-1].isSeparator()) sb.append('-')
            else -> if (!sb[sb.length-1].isSeparator()) sb.append('_')
        }
    }
    if (sb.length == 1 || sb[sb.length-1].isSeparator()) sb.append('z')
    return sb.toString()
}

// All symbols like ".", "_" and non letter or digit will be substituted with "-"
internal fun String.toSafeHyphenKey(): String =
    this.toSafeKey().replace("_","-")

private fun getGroupPrefix(dependency: Dependency): String {
    // For com.google etc., use "google" instead of "com"
    val group = dependency.group ?: return "nogroup"
    val groupPrefix = group.substringBefore('.').toSafeKey()
    if (groupPrefix.isCommonDomain()) {
        return group.substringAfter('.').substringBefore('.').toSafeKey()
    }
    return groupPrefix.toSafeKey()
}

private fun String.isCommonDomain() = this == "com" || this == "org" || this == "io"

private fun cutDomainPrefix(group: String): String {
    val groupPrefix = group.substringBefore('.').toSafeKey()
    if (groupPrefix.isCommonDomain() || groupPrefix == "androidx") {
        return group.substringAfter('.').toSafeKey()
    }
    return groupPrefix.toSafeKey()
}

/**
 * Variable name generator takes artifact id as a base. It analyses reserved
 * aliases and chose same notation. This can be a lower camel or hyphen case.
 * notation otherwise. Lower camel is preferable - if no reserved, algorithm
 * will pick lower camel.
 *
 * Picking variable name happens in steps. Each step generates
 * some name around artifact id with hyphen and transform to camel case if it is the style.
 * Then check whether it's reserved with case-insensitive set. If yes, jumping to the next step.
 * Steps defined as follows:
 * - use artifactId if no reserved;
 * - use artifactId + "Version" suffix if this is the style;
 * - use artifactId as variable name;
 * - use artifactId + "Version" suffix if it's not a hyphen notation;
 * - use group prefix + artifactId;
 * - use group prefix + artifactId + "Version";
 * - use group name + artifactId;
 * - use group name + artifactId + /number/.
 */
 fun pickVersionVariableName(dependency: Dependency, caseSensitiveReserved: Set<String>): String {
    // If using the artifactVersion convention, follow that
    val artifact = dependency.name.toSafeKey()
    val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)
    reserved.addAll(caseSensitiveReserved)

    val transform = getMaybeTransformVersionToCamelCase(reserved)

    if (reserved.isEmpty()) {
        return transform(artifact)
    }

    if (reserved.isNotEmpty() && reserved.first().lowercase().endsWith("version")) {
        val withVersion = transform("${artifact}-version")
        if (!reserved.contains(withVersion)) {
            return withVersion
        }
    }

    // Default convention listed in https://docs.gradle.org/current/userguide/platforms.html seems to
    // be to just use the artifact name
    val artifactName = transform(artifact)
    if (!reserved.contains(artifactName)) {
        return artifactName
    }

    val withVersion = transform("${artifact}-version")
    if (!reserved.contains(withVersion)) {
        return withVersion
    }

    val groupPrefix = getGroupPrefix(dependency)
    val withGroupIdPrefix = transform("$groupPrefix-$artifact")
    if (!reserved.contains(withGroupIdPrefix)) {
        return withGroupIdPrefix
    }


    val withGroupIdPrefixVersion = transform("$groupPrefix-${artifact}-version")
    if (!reserved.contains(withGroupIdPrefixVersion)) {
        return withGroupIdPrefixVersion
    }


    // With full group
    val groupId = dependency.group?.toSafeKey() ?: "nogroup"
    val withGroupId = "$groupId-$artifact"
    val transformedWithGroupId = transform(withGroupId)
    if (!reserved.contains(transformedWithGroupId)) {
        return transformedWithGroupId
    }

    // Final fallback; this is unlikely but JUST to be sure we get a unique version
    return generateWithSuffix(withGroupId, reserved, transform)
}

data class AliasStyle(val haveCamelCase: Boolean, val haveHyphen: Boolean)

private fun getAliasStyle(reservedAliases: Set<String>): AliasStyle {
    var haveCamelCase = false
    var haveHyphen = false
    for (name in reservedAliases) {
        for (i in name.indices) {
            val c = name[i]
            if (c == '-') {
                haveHyphen = true
            } else if (i > 0 && c.isUpperCase() && name[i - 1].isLowerCase() && name[0].isLowerCase()) {
                haveCamelCase = true
            }
        }
    }
    return AliasStyle(haveCamelCase, haveHyphen)
}

private fun generateWithSuffix(prefix:String, reserved:Set<String>, transform: ((String) -> String)?): String {
    var id = 2
    while (true) {
        val name = "${prefix}${id++}"
        var maybeUpdatedName = transform?.invoke(name) ?: name
        // Will eventually succeed
        if (!reserved.contains(maybeUpdatedName)) {
            return maybeUpdatedName
        }
    }
}

/**
 * Variable name generator takes plugin id as a base. It analyses reserved
 * aliases and chose same notation. This can be a lower camel or hyphen case.
 * Picking variable happens in steps. Each step generates
 * some name around updated plugin id and check whether it's reserved with
 * case-insensitive set. If yes, jumping to the next step.
 * Steps defined as follows:
 * - use pluginId if no reserved
 * - use pluginId suffix (without root domain com/org) + "Version" suffix if this is the style;
 * - use pluginId suffix as variable name;
 * - use pluginId suffix + "Version" suffix if it's not a hyphen notation;
 * - use full pluginId.
 * - use pluginId + /number/.
 * Those steps are similar to picking library version
 */
fun pickPluginVersionVariableName(
    pluginId: String,
    caseSensitiveReserved: Set<String>
): String {
    val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)
    reserved.addAll(caseSensitiveReserved)
    val plugin = cutDomainPrefix(pluginId)

    val safeKey = plugin.toSafeHyphenKey()

    if (reserved.isEmpty()) {
        return safeKey
    }

    val transform = getMaybeTransformVersionToCamelCase(reserved)

    if (reserved.isNotEmpty() && reserved.first().lowercase().endsWith("version")) {
        val withVersion = transform("${plugin}-version")
        if (!reserved.contains(withVersion)) {
            return withVersion
        }
    }

    val transformedName = transform(plugin)
    if (!reserved.contains(transformedName)) {
        return transformedName
    }

    val withVersion = transform("${plugin}-version")
    if (!reserved.contains(withVersion)) {
        return withVersion
    }

    // with full pluginId
    val fullName = transform(pluginId.toSafeKey())
    if (!reserved.contains(fullName)) {
        return fullName
    }

    return generateWithSuffix(fullName, reserved, transform)
}

fun keysMatch(s1: String?, s2: String): Boolean {
    s1 ?: return false
    if (s1.length != s2.length) {
        return false
    }
    for (i in s1.indices) {
        if (s1[i].normalize() != s2[i].normalize()) {
            return false
        }
    }
    return true
}

// Gradle converts dashed-keys or dashed_keys into dashed.keys
private fun Char.normalize(): Char {
    if (this == '-' || this == '_') {
        return '.'
    }
    return this
}
