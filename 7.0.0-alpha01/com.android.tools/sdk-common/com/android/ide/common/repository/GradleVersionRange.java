/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.HashCodes;
import com.google.common.base.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GradleVersionRange is used to describe dependency version requirements.
 *
 * <p>e.g. a library has a dependency on androidx.core:core:1.5.7 it means that any library in the
 * range [1.5.7, 2.0.0) would work as a dependency because androidx libraries guarantees semantic
 * versioning.
 */
public class GradleVersionRange {
    private static final Pattern RANGE_PATTERN = Pattern.compile("\\[([^,)]+),([^,)]+)\\)");
    private final GradleVersion myMin;
    private final GradleVersion myMax;

    /**
     * Parses the given version range.
     *
     * @param value the version range to parse example: [2.3.4,3.0.0)
     * @return the created {@code Version} object.
     * @throws IllegalArgumentException if the given value does not conform with any of the
     *     supported version formats.
     */
    @NonNull
    public static GradleVersionRange parse(@NonNull String value) {
        return parse(value, KnownVersionStability.INCOMPATIBLE);
    }

    /**
     * Parses the given version range.
     *
     * @param value the version range to parse.
     * @param stability the stability of the artifact.
     * @return the created {@code Version} object.
     * @throws IllegalArgumentException if the given value does not conform with any of the
     *     supported version formats.
     * @see <a href="https://semver.org">Semantic Versioning</a>
     */
    @NonNull
    public static GradleVersionRange parse(
            @NonNull String value, @NonNull KnownVersionStability stability) {
        if (!value.startsWith("[")) {
            GradleVersion minimum = GradleVersion.parse(value);
            return new GradleVersionRange(minimum, stability.expiration(minimum));
        }
        Matcher matcher = RANGE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw parsingFailure(value);
        }
        return new GradleVersionRange(
                GradleVersion.parse(matcher.group(1)), GradleVersion.parse(matcher.group(2)));
    }

    /**
     * Parses the given version. This method does the same as {@link #parse(String)}, but it does
     * not throw exceptions if the given value does not conform with any of the supported version
     * formats.
     *
     * @param value the version to parse.
     * @param stability the stability of the artifact.
     * @return the created {@code GradleVersionRange} object, or {@code null} if the given value
     *     does not conform with any of the supported version formats.
     */
    @Nullable
    public static GradleVersionRange tryParse(
            @NonNull String value, @NonNull KnownVersionStability stability) {
        try {
            return parse(value, stability);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Parses the given version. This method does the same as {@link #parse(String)}, but it does
     * not throw exceptions if the given value does not conform with any of the supported version
     * formats.
     *
     * @param value the version to parse.
     * @return the created {@code GradleVersionRange} object, or {@code null} if the given value
     *     does not conform with any of the supported version formats.
     */
    @Nullable
    public static GradleVersionRange tryParse(@NonNull String value) {
        return tryParse(value, KnownVersionStability.INCOMPATIBLE);
    }

    @NonNull
    private static IllegalArgumentException parsingFailure(@NonNull String value) {
        return new IllegalArgumentException(
                String.format("'%1$s' is not a valid version range", value));
    }

    private GradleVersionRange(@NonNull GradleVersion min, @Nullable GradleVersion max) {
        myMin = min;
        myMax = max;
    }

    /** The lower bound (inclusive) */
    @NonNull
    public GradleVersion getMin() {
        return myMin;
    }

    /** The upper bound (exclusive) */
    @Nullable
    public GradleVersion getMax() {
        return myMax;
    }

    @Nullable
    public GradleVersionRange intersection(@NonNull GradleVersionRange other) {
        if (myMax == null && other.myMax == null) {
            return other.myMin.equals(myMin) ? this : null;
        }
        if (myMax == null) {
            return myMin.compareTo(other.myMin) >= 0 && myMin.compareTo(other.myMax) < 0
                    ? this
                    : null;
        }
        if (other.myMax == null) {
            return other.myMin.compareTo(myMin) >= 0 && other.myMin.compareTo(myMax) < 0
                    ? other
                    : null;
        }
        GradleVersion min = myMin.compareTo(other.myMin) >= 0 ? myMin : other.myMin;
        GradleVersion max = myMax.compareTo(other.myMax) <= 0 ? myMax : other.myMax;
        return min.compareTo(max) < 0 ? new GradleVersionRange(min, max) : null;
    }

    @Nullable
    public GradleVersionRange intersection(@NonNull GradleVersion version) {
        return intersection(new GradleVersionRange(version, null));
    }

    @Override
    public int hashCode() {
        return HashCodes.mix(myMin.hashCode(), myMax != null ? myMax.hashCode() : 0);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GradleVersionRange)) {
            return false;
        }
        GradleVersionRange range = (GradleVersionRange) other;
        return Objects.equal(myMin, range.myMin) && Objects.equal(myMax, range.myMax);
    }

    @Override
    public String toString() {
        if (myMax == null) {
            return myMin.toString();
        }
        return String.format("[%1$s,%2$s)", myMin, myMax);
    }
}
