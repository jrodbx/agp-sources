/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.builder.dependency

import com.android.SdkConstants
import com.android.annotations.concurrency.Immutable
import com.android.builder.internal.StringCachingService
import com.android.builder.internal.cacheString
import com.android.builder.model.MavenCoordinates
import com.google.common.base.Objects
import java.io.Serializable

/**
 * Serializable implementation of MavenCoordinates for use in the model.
 */
@Immutable
class MavenCoordinatesImpl private constructor(
    override val groupId: String,
    override val artifactId: String,
    override val version: String,
    override val packaging: String,
    override val classifier: String?,
    override val versionlessId: String,
    private val toString: String
) : MavenCoordinates, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        @JvmOverloads
        fun create(
            stringCachingService: StringCachingService?,
            groupId: String,
            artifactId: String,
            version: String,
            packaging: String? = null,
            classifier: String? = null
        ): MavenCoordinatesImpl {
            val packagingStr = if (packaging == null) {
                SdkConstants.EXT_JAR
            } else {
                stringCachingService.cacheString(packaging)
            }

            return MavenCoordinatesImpl(
                stringCachingService.cacheString(groupId),
                stringCachingService.cacheString(artifactId),
                stringCachingService.cacheString(version),
                packagingStr,
                classifier?.let { stringCachingService.cacheString(it) },
                stringCachingService.cacheString(
                    computeVersionLessId(
                        groupId,
                        artifactId,
                        classifier
                    )
                ),
                stringCachingService.cacheString(
                    computeToString(
                        groupId, artifactId, version, packagingStr, classifier
                    )
                )
            )
        }

        private fun computeVersionLessId(
            groupId: String,
            artifactId: String,
            classifier: String?
        ): String {
            val sb = StringBuilder(
                groupId.length
                        + groupId.length
                        + artifactId.length
                        + 2
                        + (classifier?.let { it.length + 1} ?: 0)
            )

            sb.append(groupId).append(':').append(artifactId)
            classifier?.let {
                sb.append(':').append(it)
            }

            return sb.toString()
        }

        private fun computeToString(
            groupId: String,
            artifactId: String,
            version: String,
            packaging: String,
            classifier: String?
        ): String {
            val sb = StringBuilder(
                groupId.length
                        + artifactId.length
                        + version.length
                        + 2 // the 2 ':'
                        + (if (classifier != null) classifier.length + 1 else 0) // +1 for the ':'
                        + packaging.length
                        + 1 // +1 for the '@'
            )
            sb.append(groupId).append(':').append(artifactId).append(':').append(version)
            classifier?.let {
                sb.append(':').append(it)
            }
            sb.append('@').append(packaging)
            return sb.toString()
        }
    }

    // pre-computed derived values for performance, not part of the object identity.
    private val hashCode: Int = computeHashCode()

    fun compareWithoutVersion(coordinates: MavenCoordinates): Boolean {
        return this === coordinates ||
                Objects.equal(groupId, coordinates.groupId) &&
                Objects.equal(
                    artifactId,
                    coordinates.artifactId
                ) &&
                Objects.equal(
                    packaging,
                    coordinates.packaging
                ) &&
                Objects.equal(
                    classifier,
                    coordinates.classifier
                )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as MavenCoordinatesImpl
        return Objects.equal(groupId, that.groupId) &&
                Objects.equal(artifactId, that.artifactId) &&
                Objects.equal(version, that.version) &&
                Objects.equal(packaging, that.packaging) &&
                Objects.equal(classifier, that.classifier)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        return toString
    }

    private fun computeHashCode(): Int {
        return HashCodeUtils.hashCode(groupId, artifactId, version, packaging, classifier)
    }
}
