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
package com.android.ide.common.gradle

/**
 * Represents a Gradle Dependency declaration on an (external) module with a given
 * [RichVersion] Dependency constraint.
 */
data class Dependency(
    val group: String? = null,
    val name: String,
    val version: RichVersion? = null,
    val classifier: String? = null,
    val extension: String? = null
) {
    private val atExt get() = if (extension == null) "" else "@${extension}"

    /**
     * Returns a String identifying this declared dependency, if it exists.  If one does
     * not exist, return `null`.  It is guaranteed that if [toIdentifier] returns a String,
     * then [parse] acting on that String will return an equivalent [Dependency].
     */
    fun toIdentifier(): String? = when {
        group == null -> "${name}${atExt}"
            .takeIf { version == null }
            ?.takeIf { classifier == null }
            ?.takeIf { !name.contains(':') && (extension != null || !name.contains('@')) }
            ?.takeIf { extension?.let { !it.contains('@') } ?: true }
        version == null -> "${group}:${name}${atExt}"
            .takeIf { classifier == null }
            ?.takeIf { !group.contains(':') && (extension != null || !group.contains('@')) }
            ?.takeIf { !name.contains(':') && (extension != null || !name.contains('@')) }
            ?.takeIf { extension?.let { !it.contains('@') } ?: true }
        classifier == null -> version.toIdentifier()?.let { v ->
            "${group}:${name}:${v}${atExt}"
                .takeIf { !group.contains(':') && (extension != null || !group.contains('@')) }
                ?.takeIf { !name.contains(':') && (extension != null || !name.contains('@')) }
                ?.takeIf { !v.contains(':') && (extension != null || !v.contains('@')) }
                ?.takeIf { extension?.let { !it.contains('@') } ?: true }
        }
        else -> version.toIdentifier()?.let { v ->
            "${group}:${name}:${v}:${classifier}${atExt}"
                .takeIf { !group.contains(':') && (extension != null || !group.contains('@')) }
                ?.takeIf { !name.contains(':') && (extension != null || !name.contains('@')) }
                ?.takeIf { !v.contains(':') && (extension != null || !v.contains('@')) }
                // colons are allowed in the classifier.
                ?.takeIf { extension != null || !classifier.contains('@') }
                ?.takeIf { extension?.let { !it.contains('@') } ?: true }
        }
    }

    override fun toString() = when (val id = toIdentifier()) {
        is String -> id
        else -> StringBuilder("Dependency(").let { sb ->
            group?.let { sb.append("group=$it, ") }
            sb.append("name=$name")
            version?.let { sb.append(", version=$it") }
            classifier?.let { sb.append(", classifier=$it") }
            extension?.let { sb.append(", extension=$it") }
            sb.append(")").toString()
        }
    }

    /**
     * Is true if the [Dependency] has a [version], and that [RichVersion] explicitly includes
     * preview versions in any way: as the preferred version, or as either endpoint of the
     * version declaration.  Explicit mentions of previews in the version's exclude list are not
     * considered as inclusions.
     */
    val explicitlyIncludesPreview: Boolean
        get() = version?.run {
            if (prefer?.isPreview == true) return true
            (require ?: strictly)?.run {
                (hasLowerBound() && lowerEndpoint().isPreview) ||
                        (hasUpperBound() && upperEndpoint().isPreview)
            }
        } ?: false

    /**
     * Is true if the [Dependency] has a [version], and that [RichVersion] has an upper bound
     * which is distinct from its lower bound (which may be empty).  Upper bounds implicitly
     * formed by an entry in the version's exclude list with no upper bound are not considered
     * explicit for the purposes of this field.
     */
    val hasExplicitDistinctUpperBound: Boolean
        get() = version?.run {
            (require ?: strictly)?.run {
                hasUpperBound() && (!hasLowerBound() || lowerEndpoint() != upperEndpoint())
            }
        } ?: false

    /**
     * If this [Dependency] has a [version] which explicitly encodes the range of a single version,
     * return it.  See [RichVersion.explicitSingletonVersion].
     */
    val explicitSingletonVersion: Version?
        get() = version?.explicitSingletonVersion

    companion object {
        /**
         * Parse a String as a [Dependency].  All strings are valid dependencies; the last
         * instance of `@` in the string, if any, separates the extension from the other parts;
         * then the first `:` characters in the string (up to 3) separate, in order, the [group]
         * from the [name]; the [name] from the [version], and the [version] from the [classifier]
         * (and a string with no `:` before any `@` denotes a [Dependency] with no [group],
         * [version] or [classifier].
         */
        fun parse(string: String): Dependency {
            val lastAt = string.lastIndexOf('@').takeIf { it != -1 }
            val extension = lastAt?.let { string.substring(1+lastAt) }
            val nonExtensionString = lastAt?.let { string.substring(0, it) } ?: string
            // - 0  atsigns: no extension
            // - >0 atsigns: extension is after last atsign
            //
            // then of the non-extension string:
            //
            // - 0  colons: name
            // - 1   colon: group:name (NB check ArtifactDependencySpec which does something else)
            // - 2  colons: group:name:version
            // - >2 colons: group:name:version:classifier
            nonExtensionString.run {
                return when (count { it == ':' }) {
                    0 -> Dependency(name = nonExtensionString, extension = extension)
                    1 -> indexOf(':').let { first ->
                        Dependency(
                            group = substring(0, first),
                            name = substring(1+first),
                            extension = extension,
                        )
                    }
                    2 -> indexOf(':').let { first ->
                         indexOf(':', startIndex = 1+first).let { second ->
                            Dependency(
                                group = substring(0, first),
                                name = substring(1+first, second),
                                version = RichVersion.parse(substring(1 + second)),
                                extension = extension,
                            )
                        }
                    }
                    else -> indexOf(':').let { first ->
                        indexOf(':', startIndex = 1+first).let { second ->
                            indexOf(':', startIndex = 1+second).let { third ->
                                Dependency(
                                    group = substring(0, first),
                                    name = substring(1+first, second),
                                    version = RichVersion.parse(substring(1+second, third)),
                                    classifier = substring(1+third),
                                    extension = extension,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
