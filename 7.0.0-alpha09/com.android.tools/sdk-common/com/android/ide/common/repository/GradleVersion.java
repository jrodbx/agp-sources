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
package com.android.ide.common.repository;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supports versions in the given formats: <ul> <li>major (e.g. 1)</li> <li>major.minor (e.g.
 * 1.0)</li> <li>major.minor.micro (e.g. 1.1.1)</li> </ul> A version can also be a "preview" (e.g.
 * 1-alpha1, 1.0.0-rc2) or an unreleased version (or "snapshot") (e.g. 1-SNAPSHOT,
 * 1.0.0-alpha1-SNAPSHOT).
 */
public class GradleVersion implements Comparable<GradleVersion>, Serializable {
    private static final String PLUS = "+";

    private static final Pattern PREVIEW_PATTERN = Pattern.compile("([a-zA-z]+)[\\-]?([\\d]+)?(-\\d+)?");

    private final String mRawValue;

    @NonNull
    private final VersionSegment mMajorSegment;

    @Nullable
    private final VersionSegment mMinorSegment;

    @Nullable
    private final VersionSegment mMicroSegment;

    private final int mPreview;

    @Nullable
    private final String mPreviewType;

    private final boolean mSnapshot;

    @NonNull
    private final List<VersionSegment> mAdditionalSegments;

    private final String mQualifiers;

    /**
     * Parses the given version. This method does the same as {@link #parse(String)}, but it does
     * not throw exceptions if the given value does not conform with any of the supported version
     * formats.
     *
     * @param value the version to parse.
     * @return the created {@code Version} object, or {@code null} if the given value does not
     * conform with any of the supported version formats.
     */
    @Nullable
    public static GradleVersion tryParse(@NonNull String value) {
        try {
            return parse(value);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Parses the given version.
     *
     * @param value the version to parse.
     * @return the created {@code Version} object.
     * @throws IllegalArgumentException if the given value does not conform with any of the
     *                                  supported version formats.
     */
    @NonNull
    public static GradleVersion parse(@NonNull String value) {
        String version = value;
        String qualifiers = null;
        char dash = '-';
        int dashIndex = value.indexOf(dash);
        if (dashIndex != -1) {
            if (dashIndex < value.length() - 1) {
                qualifiers = value.substring(dashIndex + 1);
            }
            version = value.substring(0, dashIndex);
        }

        try {
            List<VersionSegment> parsedVersionSegments = splitSegments(version);
            int segmentCount = parsedVersionSegments.size();

            VersionSegment majorSegment;
            VersionSegment minorSegment = null;
            VersionSegment microSegment = null;

            List<VersionSegment> additionalSegments = Lists.newArrayList();
            if (segmentCount > 0) {
                majorSegment = parsedVersionSegments.get(0);
                if (segmentCount > 1) {
                    minorSegment = parsedVersionSegments.get(1);
                }
                if (segmentCount >= 3) {
                    microSegment = parsedVersionSegments.get(2);
                }
                if (segmentCount > 3) {
                    additionalSegments.addAll(parsedVersionSegments.subList(3, segmentCount));
                }

                int preview = 0;
                String previewType = null;
                boolean snapshot = false;

                if (qualifiers != null) {
                    if (isSnapshotQualifier(qualifiers)) {
                        snapshot = true;
                        qualifiers = null;
                    }
                    else {
                        // find and remove "SNAPSHOT" at the end of the qualifiers.
                        int lastDashIndex = qualifiers.lastIndexOf(dash);
                        if (lastDashIndex != -1) {
                            String mayBeSnapshot = qualifiers.substring(lastDashIndex + 1);
                            if (isSnapshotQualifier(mayBeSnapshot)) {
                                snapshot = true;
                                qualifiers = qualifiers.substring(0, lastDashIndex);
                            }
                        }
                    }
                    if (!isNullOrEmpty(qualifiers)) {
                        Matcher matcher = PREVIEW_PATTERN.matcher(qualifiers);
                        if (matcher.matches()) {
                            previewType = matcher.group(1);
                            if (matcher.groupCount() >= 2) {
                                String group = matcher.group(2);
                                if (!isNullOrEmpty(group)) {
                                    preview = Integer.parseInt(group);
                                }
                            }
                        }
                    }
                }
                return new GradleVersion(value, majorSegment, minorSegment, microSegment,
                        additionalSegments, preview, previewType, snapshot, qualifiers);
            }
        } catch (NumberFormatException e) {
            throw parsingFailure(value, e);
        }
        throw parsingFailure(value);
    }

    @NonNull
    private static List<VersionSegment> splitSegments(@NonNull String version) {
        Iterable<String> segments = Splitter.on('.').split(version);
        List<VersionSegment> parsedSegments = Lists.newArrayListWithCapacity(3);

        for (String segment : segments) {
            parsedSegments.addAll(parseSegment(segment));
        }

        return parsedSegments;
    }

    @NonNull
    private static List<VersionSegment> parseSegment(@NonNull String text) {
        int length = text.length();
        if (length > 1 && text.endsWith(PLUS)) {
            // Segment has a number and a '+' (e.g. second segment in '2.1+')
            List<VersionSegment> segments = Lists.newArrayListWithCapacity(2);

            // We need to split '1+' into 2 segments: '1' and '+'
            segments.add(new VersionSegment(text.substring(0, length - 1)));
            segments.add(new VersionSegment(PLUS));
            return segments;
        }
        return Collections.singletonList(new VersionSegment(text));
    }

    private static boolean isSnapshotQualifier(@NonNull String value) {
        return "SNAPSHOT".equalsIgnoreCase(value) || "dev".equalsIgnoreCase(value);
    }

    @NonNull
    private static IllegalArgumentException parsingFailure(@NonNull String value) {
        return parsingFailure(value, null);
    }

    @NonNull
    private static IllegalArgumentException parsingFailure(@NonNull String value,
            @Nullable Throwable cause) {
        return new IllegalArgumentException(String.format("'%1$s' is not a valid version", value),
                cause);
    }

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    GradleVersion() {
        mRawValue = "";
        mMajorSegment = new VersionSegment();
        mMinorSegment = new VersionSegment();
        mMicroSegment = new VersionSegment();
        mPreview = 0;
        mPreviewType = null;
        mSnapshot = false;
        mAdditionalSegments = Collections.emptyList();
        mQualifiers = null;
    }

    public GradleVersion(int major, int minor) {
        this((major + "." + minor), new VersionSegment(major),
                new VersionSegment(minor), null,
                Collections.emptyList(), 0, null, false, null);
    }

    public GradleVersion(int major, int minor, int micro) {
        this((major + "." + minor + "." + micro), new VersionSegment(major),
                new VersionSegment(minor), new VersionSegment(micro),
                Collections.emptyList(), 0, null, false, null);
    }

    private GradleVersion(@NonNull String rawValue,
            @NonNull VersionSegment majorSegment,
            @Nullable VersionSegment minorSegment,
            @Nullable VersionSegment microSegment,
            @NonNull List<VersionSegment> additionalSegments,
            int preview,
            @Nullable String previewType,
            boolean snapshot,
            String qualifiers) {
        mRawValue = rawValue;
        mMajorSegment = majorSegment;
        mMinorSegment = minorSegment;
        mMicroSegment = microSegment;
        mAdditionalSegments = ImmutableList.copyOf(additionalSegments);
        mPreview = preview;
        mPreviewType = previewType;
        mSnapshot = snapshot;
        mQualifiers = qualifiers;
    }

    public int getMajor() {
        return valueOf(mMajorSegment);
    }

    @NonNull
    public VersionSegment getMajorSegment() {
        return mMajorSegment;
    }

    public int getMinor() {
        return valueOf(mMinorSegment);
    }

    @Nullable
    public VersionSegment getMinorSegment() {
        return mMinorSegment;
    }

    public int getMicro() {
        return valueOf(mMicroSegment);
    }

    private static int valueOf(@Nullable VersionSegment segment) {
        return segment != null ? segment.getValue() : 0;
    }

    @Nullable
    public VersionSegment getMicroSegment() {
        return mMicroSegment;
    }

    public int getPreview() {
        return mPreview;
    }

    public boolean isPreview() {
        return mPreviewType != null || mSnapshot;
    }

    @Nullable
    public String getPreviewType() {
        return mPreviewType;
    }

    public boolean isSnapshot() {
        return mSnapshot;
    }

    public int compareTo(@NonNull String version) {
        return compareTo(parse(version));
    }

    @Override
    public int compareTo(@NonNull GradleVersion version) {
        return compareTo(version, false);
    }

    public int compareIgnoringQualifiers(@NonNull String version) {
        return compareIgnoringQualifiers(parse(version));
    }

    public int compareIgnoringQualifiers(@NonNull GradleVersion version) {
        return compareTo(version, true);
    }

    private int compareTo(@NonNull GradleVersion version, boolean ignoreQualifiers) {
        int delta = getMajor() - version.getMajor();
        if (delta != 0) {
            return delta;
        }
        delta = getMinor() - version.getMinor();
        if (delta != 0) {
            return delta;
        }
        delta = getMicro() - version.getMicro();
        if (delta != 0) {
            return delta;
        }
        if (!ignoreQualifiers) {
            if (mQualifiers == null) {
                //noinspection VariableNotUsedInsideIf
                if (version.mQualifiers != null) {
                    return 1;
                }
                delta = mSnapshot == version.mSnapshot ? 0 : (mSnapshot ? -1 : 1);
            } else if (version.mQualifiers == null) {
                return -1;
            } else if (mQualifiers.startsWith("dev") && version.mQualifiers.startsWith("dev")) {
                delta = mQualifiers.compareTo(version.mQualifiers);
            } else if (mQualifiers.startsWith("dev") || version.mQualifiers.startsWith("dev")) {
                delta = mQualifiers.startsWith("dev") ? -1 : 1;
            } else {
                delta = mQualifiers.compareTo(version.mQualifiers);
            }
        }
        return delta;
    }

    /**
     * Is this {@linkplain GradleVersion} at least as high as the given
     * major, minor, micro version?
     */
    public boolean isAtLeast(int major, int minor, int micro) {
        return isAtLeast(major, minor, micro, null, 0, false);
    }

    /**
     * Is this {@linkplain GradleVersion} at least as high as the given
     * major, minor, micro version? Any preview suffixes are ignored.
     */
    public boolean isAtLeastIncludingPreviews(int major, int minor, int micro) {
        return isAtLeast(major, minor, micro, "", 0, false);
    }

    /**
     * Is this {@linkplain GradleVersion} at least as high as the given
     * major, minor, micro version?
     */
    public boolean isAtLeast(int major, int minor, int micro,
            @Nullable String previewType, int previewVersion, boolean isSnapshot) {
        int delta = getMajor() - major;
        if (delta != 0) {
            return delta >= 0;
        }
        delta = getMinor() - minor;
        if (delta != 0) {
            return delta >= 0;
        }
        delta = getMicro() - micro;
        if (delta != 0) {
            return delta >= 0;
        }

        if (mPreviewType == null) {
            //noinspection VariableNotUsedInsideIf
            if (previewType != null) {
                return true;
            }
        } else if (previewType == null) {
            return false;
        } else {
            delta = mPreviewType.compareToIgnoreCase(previewType);
        }
        if (delta != 0) {
            return delta >= 0;
        }

        delta = mPreview - previewVersion;
        if (delta != 0) {
            return delta >= 0;
        }
        return mSnapshot == isSnapshot || !mSnapshot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GradleVersion that = (GradleVersion) o;
        return compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mMajorSegment, mMinorSegment, mMicroSegment, mPreview, mPreviewType,
                mSnapshot);
    }

    @Override
    public String toString() {
        return mRawValue;
    }

    /**
     * @return version segments present after the "micro" segments. For example, parsing "1.2.3.4.5"
     * will result in "4" and "5" to be considered "additional" version segments.
     */
    @NonNull
    public List<VersionSegment> getAdditionalSegments() {
        return mAdditionalSegments;
    }

    /** Returns the max of the two versions */
    @Nullable
    public static GradleVersion max(@Nullable GradleVersion version1, @Nullable GradleVersion version2) {
        if (version2 == null) {
            return version1;
        } else if (version1 == null) {
            return version2;
        } else if (version1.compareTo(version2) < 0) {
            return version2;
        } else {
            return version1;
        }
    }

    public static class VersionSegment implements Serializable {
        @NonNull
        private final String mText;

        private final int mValue;

        // Used for serialization by the IDE.
        @SuppressWarnings("unused")
        VersionSegment() {
            this(0);
        }

        VersionSegment(int value) {
            mText = String.valueOf(value);
            mValue = value;
        }

        VersionSegment(@NonNull String text) {
            mText = text;
            if (PLUS.equals(text)) {
                mValue = Integer.MAX_VALUE;
            } else {
                // +1 is a valid number which will be parsed correctly but it is not a correct
                // version segment.
                if (text.startsWith(PLUS)) {
                    throw new NumberFormatException("Version segment cannot start with +");
                }
                int value;
                try {
                    value = Integer.parseInt(text);
                }
                catch (NumberFormatException e) {
                    value = 0;
                }
                mValue = value;
            }
        }

        @NonNull
        public String getText() {
            return mText;
        }

        public int getValue() {
            return mValue;
        }

        public boolean acceptsGreaterValue() {
            return PLUS.equals(mText);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VersionSegment that = (VersionSegment) o;
            return Objects.equal(mText, that.mText);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mText);
        }

        @Override
        public String toString() {
            return mText;
        }
    }

    /**
     * Parses the given string into a {@link GradleVersion} instance if it represents a valid
     * Android Gradle plugin version, or returns {@code null} otherwise.
     *
     * <p>This method is similar to {@link #tryParse(String)} but has stricter constraints on what
     * will be accepted as a valid Android Gradle plugin version. For example, this method does not
     * accept "3.1.0.0" as a valid plugin version, but the other method does.
     *
     * @param value the string to parse
     * @return the created {@link GradleVersion} instance, or {@code null} if the given string does
     *     not represent a valid Android Gradle plugin version.
     * @see #tryParse(String)
     */
    @Nullable
    public static GradleVersion tryParseAndroidGradlePluginVersion(@NonNull String value) {
        if (value.matches("\\d+\\.\\d+\\.\\d+(-(((alpha|beta|rc)\\d+)|(dev\\d*)))?")) {
            // Any string that matches the above pattern is a valid Android Gradle plugin version
            // and should be parsable by tryParse()
            return Verify.verifyNotNull(tryParse(value));
        } else {
            return null;
        }
    }

    @NonNull
    public static GradleVersion parseAndroidGradlePluginVersion(@NonNull String value) {
        GradleVersion version = tryParseAndroidGradlePluginVersion(value);
        if (version == null) {
            throw new IllegalArgumentException(
                    value + " is not a valid Android Gradle plugin version");
        } else {
            return version;
        }
    }
}
