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

package com.android.builder.dependency;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Objects;
import java.io.Serializable;

/**
 * Serializable implementation of MavenCoordinates for use in the model.
 */
@Immutable
public final class MavenCoordinatesImpl implements MavenCoordinates, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String groupId;
    @NonNull
    private final String artifactId;
    @NonNull
    private final String version;
    @NonNull
    private final String packaging;
    @Nullable
    private final String classifier;

    // pre-computed derived values for performance, not part of the object identity.
    private final int hashCode;
    @NonNull
    private final String toString;
    @NonNull
    private final String versionLessId;

    public MavenCoordinatesImpl(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version) {
        this(groupId, artifactId, version, null /*packaging*/, null /*classifier*/);
    }

    public MavenCoordinatesImpl(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version,
            @Nullable String packaging,
            @Nullable String classifier) {
        this.groupId = groupId.intern();
        this.artifactId = artifactId.intern();
        this.version = version.intern();
        this.packaging = packaging != null ? packaging.intern() : SdkConstants.EXT_JAR;
        this.classifier = classifier != null ? classifier.intern() : null;
        this.toString = computeToString();
        this.hashCode = computeHashCode();
        this.versionLessId = computeVersionLessId();
    }

    @NonNull
    @Override
    public String getGroupId() {
        return groupId;
    }

    @NonNull
    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @NonNull
    @Override
    public String getVersion() {
        return version;
    }

    @NonNull
    @Override
    public String getPackaging() {
        return packaging;
    }

    @Nullable
    @Override
    public String getClassifier() {
        return classifier;
    }

    public boolean compareWithoutVersion(@NonNull MavenCoordinates coordinates) {
        return this == coordinates ||
                Objects.equal(groupId, coordinates.getGroupId()) &&
                        Objects.equal(artifactId, coordinates.getArtifactId()) &&
                        Objects.equal(packaging, coordinates.getPackaging()) &&
                        Objects.equal(classifier, coordinates.getClassifier());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MavenCoordinatesImpl that = (MavenCoordinatesImpl) o;
        return Objects.equal(groupId, that.groupId) &&
                Objects.equal(artifactId, that.artifactId) &&
                Objects.equal(version, that.version) &&
                Objects.equal(packaging, that.packaging) &&
                Objects.equal(classifier, that.classifier);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return toString;
    }

    /**
     * Returns this coordinates Id without the version attribute.
     */
    @NonNull
    @Override
    public String getVersionlessId() {
        return versionLessId;
    }

    private int computeHashCode() {
        return HashCodeUtils.hashCode(groupId, artifactId, version, packaging, classifier);
    }

    private String computeToString() {
        StringBuilder sb = new StringBuilder(
                groupId.length()
                        + artifactId.length()
                        + version.length()
                        + 2 // the 2 ':'
                        + (classifier != null ? classifier.length() + 1 : 0) // +1 for the ':'
                        + packaging.length() + 1); // +1 for the '@'
        sb.append(groupId).append(':').append(artifactId).append(':').append(version);
        if (classifier != null) {
            sb.append(':').append(classifier);
        }
        sb.append('@').append(packaging);
        return sb.toString().intern();
    }

    private String computeVersionLessId() {
        StringBuilder sb = new StringBuilder(
                groupId.length()
                        + artifactId.length()
                        + 1 // +1 for the ':'
                        + (classifier != null ? classifier.length() + 1 : 0)); // +1 for the ':'
        sb.append(groupId).append(':').append(artifactId);
        if (classifier != null) {
            sb.append(':').append(classifier);
        }
        return sb.toString().intern();
    }
}
