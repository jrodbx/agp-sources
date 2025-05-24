/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The revision of an Android SDK package.
 *
 * <p>Distinguishes between x and x.0, x.0.0, x.y.0: it keeps track of the precision of the revision
 * string.
 */
public class Revision implements Comparable<Revision>, Serializable {
    public static final int MISSING_MAJOR_REV  = 0;
    public static final int IMPLICIT_MINOR_REV = 0;
    public static final int IMPLICIT_MICRO_REV = 0;
    public static final int NOT_A_PREVIEW      = 0;

    public static final Revision NOT_SPECIFIED = new Revision(MISSING_MAJOR_REV);

    public enum Precision {

        /** Only major revision specified: 1 term */
        MAJOR(1),

        /** Only major and minor revisions specified: 2 terms (x.y) */
        MINOR(2),

        /** Major, minor and micro revisions specified: 3 terms (x.y.z) */
        MICRO(3),

        /** Major, minor, micro and preview revisions specified: 4 terms (x.y.z-rcN) */
        PREVIEW(4);

        private final int mTermCount;
        Precision(int termCount) {
            mTermCount = termCount;
        }

        int getTermCount() {
            return mTermCount;
        }
    }
    private static final Pattern FULL_REVISION_PATTERN =
            //                   1=major       2=minor       3=micro     4=separator  5=previewType      6=preview
            Pattern.compile("\\s*([0-9]+)(?:\\.([0-9]+)(?:\\.([0-9]+))?)?([\\s-]*)?(?:(rc|alpha|beta|\\.)([0-9]+))?\\s*");

    protected static final String DEFAULT_SEPARATOR = " ";

    private final int mMajor;
    private final int mMinor;
    private final int mMicro;
    private final int mPreview;
    private final Precision mPrecision;
    private final String mPreviewSeparator;

    /**
     * Parses a string of format "major.minor.micro rcPreview" and returns a new {@link Revision}
     * for it.
     *
     * All the fields except major are optional. <p>
     *
     * @param revisionString   A non-null revisionString to parse.
     * @param minimumPrecision Create a {@code Revision} with at least the given precision,
     *                         regardless of how precise the {@code revisionString} is.
     * @return A new non-null {@link Revision}.
     * @throws NumberFormatException if the parsing failed.
     */
    @NonNull
    public static Revision parseRevision(@NonNull String revisionString,
            @NonNull Precision minimumPrecision)
            throws NumberFormatException {
        Throwable cause = null;
        try {
            Matcher m = FULL_REVISION_PATTERN.matcher(revisionString);
            if (m.matches()) {
                int major = Integer.parseInt(m.group(1));

                int minor = IMPLICIT_MINOR_REV;
                int micro = IMPLICIT_MICRO_REV;
                int preview = NOT_A_PREVIEW;
                Precision precision = Precision.MAJOR;
                String previewSeparator = " ";

                String s = m.group(2);
                if (s != null) {
                    minor = Integer.parseInt(s);
                    precision = Precision.MINOR;
                }

                s = m.group(3);
                if (s != null) {
                    micro = Integer.parseInt(s);
                    precision = Precision.MICRO;
                }

                s = m.group(6);
                if (s != null) {
                    preview = Integer.parseInt(s);
                    previewSeparator = m.group(4);
                    precision = Precision.PREVIEW;
                }

                if (minimumPrecision.compareTo(precision) >= 0) {
                    precision = minimumPrecision;
                }

                return new Revision(major, minor, micro, preview, precision,
                        previewSeparator);
            }
        } catch (Throwable t) {
            cause = t;
        }

        NumberFormatException n = new NumberFormatException(
                "Invalid revision: " + revisionString);
        if (cause != null) {
            n.initCause(cause);
        }
        throw n;
    }

    /**
     * Parses a string of format "major.minor.micro rcPreview" and returns a new {@code Revision}
     * for it.
     *
     * All the fields except major are optional. <p>
     *
     * @param revisionString A non-null revisionString to parse.
     * @return A new non-null {@link Revision}, with precision depending on the precision of {@code
     *         revisionString}.
     * @throws NumberFormatException if the parsing failed.
     */
    @NonNull
    public static Revision parseRevision(@NonNull String revisionString)
            throws NumberFormatException {
        return parseRevision(revisionString, Precision.MAJOR);
    }

  /**
   * A safe version of {@link #parseRevision} that does not throw, but instead returns an
   * unspecified revision if it fails to parse.
   */
    @NonNull
    public static Revision safeParseRevision(@NonNull String revisionString) {
        try {
            return parseRevision(revisionString);
        } catch (NumberFormatException ignored) {
            return NOT_SPECIFIED;
        }
    }

    /**
     * Creates a new {@code Revision} with the specified major revision and no other revision
     * components.
     */
    public Revision(int major) {
        this(major, IMPLICIT_MINOR_REV, IMPLICIT_MICRO_REV, NOT_A_PREVIEW, Precision.MAJOR,
                DEFAULT_SEPARATOR);
    }

    /**
     * Creates a new {@code Revision} with the specified major and minor revision components and no
     * others.
     */
    public Revision(int major, int minor) {
        this(major, minor, IMPLICIT_MICRO_REV, NOT_A_PREVIEW, Precision.MINOR, DEFAULT_SEPARATOR);
    }

    /**
     * Creates a copy of the specified {@code Revision}.
     */
    public Revision(@NonNull Revision revision) {
        this(revision.getMajor(), revision.getMinor(), revision.getMicro(), revision.getPreview(),
                revision.mPrecision, revision.getSeparator());
    }

    /**
     * Creates a new {@code Revision} with the specified major, minor, and micro revision components
     * and no preview component.
     */
    public Revision(int major, int minor, int micro) {
        this(major, minor, micro, NOT_A_PREVIEW, Precision.MICRO, DEFAULT_SEPARATOR);
    }

    /**
     * Creates a new {@code Revision} with the specified components.
     */
    public Revision(int major, int minor, int micro, int preview) {
        this(major, minor, micro, preview, Precision.PREVIEW, DEFAULT_SEPARATOR);
    }

    Revision(int major, int minor, int micro, int preview, @NonNull Precision precision,
            @NonNull String separator) {
        mMajor = major;
        mMinor = minor;
        mMicro = micro;
        mPreview = preview;
        mPreviewSeparator = separator;
        mPrecision = precision;
    }

    /**
     * Creates a new {@code Revision} with the specified components. The precision will be exactly
     * sufficient to include all non-null components.
     */
    public Revision(int major, @Nullable Integer minor, @Nullable Integer micro,
            @Nullable Integer preview) {
        this(major, minor == null ? IMPLICIT_MINOR_REV : minor,
                micro == null ? IMPLICIT_MICRO_REV : micro,
                preview == null ? NOT_A_PREVIEW : preview,
                preview != null ? Precision.PREVIEW : micro != null ? Precision.MICRO
                        : minor != null ?  Precision.MINOR :  Precision.MAJOR, DEFAULT_SEPARATOR);
    }

    /**
     * Returns the version in a fixed format major.minor.micro with an optional "rc preview#". For
     * example it would return "18.0.0", "18.1.0" or "18.1.2 rc5", with the separator between the
     * main version number and the preview component being specified by {@code previewSeparator}.
     */
    public String toString(@NonNull String previewSeparator) {
        StringBuilder sb = new StringBuilder();
        sb.append(getMajor());

        if (mPrecision.compareTo(Precision.MINOR) >= 0) {
            sb.append('.').append(getMinor());
            if (mPrecision.compareTo(Precision.MICRO) >= 0) {
                sb.append('.').append(getMicro());
                if (mPrecision.compareTo(Precision.PREVIEW) >= 0 && isPreview()) {
                    sb.append(previewSeparator).append("rc").append(getPreview());
                }
            }
        }

        return sb.toString();
    }

    /**
     * Returns the version in a fixed format major.minor.micro with an optional "rc preview#". For
     * example it would return "18.0.0", "18.1.0" or "18.1.2 rc5". The character before "rc" is
     * specified at construction time, and defaults to space.
     */
    @Override
    public String toString() {
        return toString(getSeparator());
    }

    /**
     * Returns an {@code int[]} containing the Major, Minor, and Micro (and optionally Preview)
     * components (if specified) of this revision
     *
     * @param includePreview If false, the preview component of this revision will be ignored.
     * @return An array exactly long enough to include the components specified in this revision.
     * For example, if only Major and Minor revisions are specified the array will be of length 2.
     * If a preview component is specified and {@code includePreview} is true, the result will
     * always be of length 4.
     */
    @NonNull
    public int[] toIntArray(boolean includePreview) {
        int[] result;
        if (mPrecision.compareTo(Precision.PREVIEW) >= 0) {
            if (includePreview) {
                result = new int[mPrecision.getTermCount()];
                result[3] = getPreview();
            } else {
                result = new int[mPrecision.getTermCount() - 1];
            }
        } else {
            result = new int[mPrecision.getTermCount()];
        }
        result[0] = getMajor();
        if (mPrecision.compareTo(Precision.MINOR) >= 0) {
            result[1] = getMinor();
            if (mPrecision.compareTo(Precision.MICRO) >= 0) {
                result[2] = getMicro();
            }
        }

        return result;
    }

    /**
     * Returns {@code true} if this revision is equal, <b>including in precision</b> to {@code rhs}.
     * That is, {@code (new Revision(20)).equals(new Revision(20, 0, 0)} will return {@code false}.
     */
    @Override
    public boolean equals(@Nullable Object rhs) {
        if (this == rhs) {
            return true;
        }
        if (rhs == null) {
            return false;
        }
        if (!(rhs instanceof Revision)) {
            return false;
        }
        Revision other = (Revision) rhs;
        if (mMajor != other.mMajor) {
            return false;
        }
        if (mMinor != other.mMinor) {
            return false;
        }
        if (mMicro != other.mMicro) {
            return false;
        }
        if (mPreview != other.mPreview) {
            return false;
        }
        return mPrecision == other.mPrecision;
    }

    public int getMajor() {
        return mMajor;
    }

    public int getMinor() {
        return mMinor;
    }

    public int getMicro() {
        return mMicro;
    }

    @NonNull
    protected String getSeparator() {
        return mPreviewSeparator;
    }

    public boolean isPreview() {
        return mPreview > NOT_A_PREVIEW;
    }

    public int getPreview() {
        return mPreview;
    }

    /**
     * Returns the version in a dynamic format "major.minor.micro rc#". This is similar to {@link
     * #toString()} except it omits minor, micro or preview versions when they are zero. For example
     * it would return "18 rc1" instead of "18.0.0 rc1", or "18.1 rc2" instead of "18.1.0 rc2".
     */
    @NonNull
    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mMajor);
        if (mMinor > 0 || mMicro > 0) {
            sb.append('.').append(mMinor);
        }
        if (mMicro > 0) {
            sb.append('.').append(mMicro);
        }
        if (mPreview != NOT_A_PREVIEW) {
            sb.append(mPreviewSeparator).append("rc").append(mPreview);
        }

        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mMajor;
        result = prime * result + mMinor;
        result = prime * result + mMicro;
        result = prime * result + mPreview;
        result = prime * result + mPrecision.getTermCount();
        return result;
    }

    /**
     * Trivial comparison of a version, e.g 17.1.2 < 18.0.0.
     *
     * Note that preview/release candidate are released before their final version, so "18.0.0 rc1"
     * comes below "18.0.0". The best way to think of it as if the lack of preview number was
     * "+inf": "18.1.2 rc5" ⇒ "18.1.2.5" so its less than "18.1.2.+INF" but more than "18.1.1.0"
     * and more than "18.1.2.4"
     *
     * @param rhs The right-hand side {@link Revision} to compare with.
     * @return &lt;0 if lhs &lt; rhs; 0 if lhs==rhs; &gt;0 if lhs &gt; rhs.
     */
    @Override
    public int compareTo(@NonNull Revision rhs) {
        return compareTo(rhs, PreviewComparison.COMPARE_NUMBER);
    }

    /**
     * Trivial comparison of a version, e.g 17.1.2 < 18.0.0.
     *
     * Note that preview/release candidate are released before their final version, so "18.0.0 rc1"
     * comes below "18.0.0". The best way to think of it as if the lack of preview number was
     * "+inf": "18.1.2 rc5" ⇒ "18.1.2.5" so its less than "18.1.2.+INF" but more than "18.1.1.0"
     * and more than "18.1.2.4"
     *
     * @param rhs            The right-hand side {@link Revision} to compare with.
     * @param comparePreview How to compare the preview value.
     * @return &lt;0 if lhs &lt; rhs; 0 if lhs==rhs; &gt;0 if lhs &gt; rhs.
     */
    public int compareTo(@NonNull Revision rhs, @NonNull PreviewComparison comparePreview) {
        int delta = mMajor - rhs.mMajor;
        if (delta != 0) {
            return delta;
        }

        delta = mMinor - rhs.mMinor;
        if (delta != 0) {
            return delta;
        }

        delta = mMicro - rhs.mMicro;
        if (delta != 0) {
            return delta;
        }

        int p1, p2;
        switch (comparePreview) {
            case IGNORE:
                // Nothing to compare.
                break;

            case COMPARE_NUMBER:
                p1 = mPreview == NOT_A_PREVIEW ? Integer.MAX_VALUE : mPreview;
                p2 = rhs.mPreview == NOT_A_PREVIEW ? Integer.MAX_VALUE : rhs.mPreview;
                delta = p1 - p2;
                break;

            case COMPARE_TYPE:
                p1 = mPreview == NOT_A_PREVIEW ? 1 : 0;
                p2 = rhs.mPreview == NOT_A_PREVIEW ? 1 : 0;
                delta = p1 - p2;
                break;

            case ASCENDING:
                delta = mPreview - rhs.mPreview;
                break;
        }
        return delta;
    }

    /**
     * Indicates how to compare the preview field in {@link Revision#compareTo(Revision,
     * PreviewComparison)}
     */
    public enum PreviewComparison {
        /**
         * Both revisions must have exactly the same preview number.
         */
        COMPARE_NUMBER,
        /**
         * Both revisions must have the same preview type (both must be previews or both must not be
         * previews, but the actual number is irrelevant.) This is the most typical choice used to
         * find updates of the same type.
         */
        COMPARE_TYPE,
        /**
         * The preview field is ignored and not used in the comparison.
         */
        IGNORE,
        /**
         * Treat the preview field as just another normal field
         * (that is, 1.0.0.0 < 1.0.0.1 < 1.0.0.2 < 1.0.1.0)
         */
        ASCENDING
    }


}
