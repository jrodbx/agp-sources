/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.sdklib;

import static com.android.SdkConstants.FN_AAPT;
import static com.android.SdkConstants.FN_AAPT2;
import static com.android.SdkConstants.FN_AIDL;
import static com.android.SdkConstants.FN_BCC_COMPAT;
import static com.android.SdkConstants.FN_CORE_LAMBDA_STUBS;
import static com.android.SdkConstants.FN_DEXDUMP;
import static com.android.SdkConstants.FN_JACK;
import static com.android.SdkConstants.FN_JACK_COVERAGE_PLUGIN;
import static com.android.SdkConstants.FN_JACK_JACOCO_REPORTER;
import static com.android.SdkConstants.FN_JILL;
import static com.android.SdkConstants.FN_LD_ARM;
import static com.android.SdkConstants.FN_LD_ARM64;
import static com.android.SdkConstants.FN_LD_MIPS;
import static com.android.SdkConstants.FN_LD_X86;
import static com.android.SdkConstants.FN_LD_X86_64;
import static com.android.SdkConstants.FN_LLD;
import static com.android.SdkConstants.FN_RENDERSCRIPT;
import static com.android.SdkConstants.FN_SPLIT_SELECT;
import static com.android.SdkConstants.FN_ZIPALIGN;
import static com.android.SdkConstants.OS_FRAMEWORK_RS;
import static com.android.SdkConstants.OS_FRAMEWORK_RS_CLANG;
import static com.android.sdklib.BuildToolInfo.PathId.AAPT;
import static com.android.sdklib.BuildToolInfo.PathId.AAPT2;
import static com.android.sdklib.BuildToolInfo.PathId.AIDL;
import static com.android.sdklib.BuildToolInfo.PathId.ANDROID_RS;
import static com.android.sdklib.BuildToolInfo.PathId.ANDROID_RS_CLANG;
import static com.android.sdklib.BuildToolInfo.PathId.BCC_COMPAT;
import static com.android.sdklib.BuildToolInfo.PathId.CORE_LAMBDA_STUBS;
import static com.android.sdklib.BuildToolInfo.PathId.DAEMON_AAPT2;
import static com.android.sdklib.BuildToolInfo.PathId.DEXDUMP;
import static com.android.sdklib.BuildToolInfo.PathId.JACK;
import static com.android.sdklib.BuildToolInfo.PathId.JACK_COVERAGE_PLUGIN;
import static com.android.sdklib.BuildToolInfo.PathId.JACK_JACOCO_REPORTER;
import static com.android.sdklib.BuildToolInfo.PathId.JILL;
import static com.android.sdklib.BuildToolInfo.PathId.LD_ARM;
import static com.android.sdklib.BuildToolInfo.PathId.LD_ARM64;
import static com.android.sdklib.BuildToolInfo.PathId.LD_MIPS;
import static com.android.sdklib.BuildToolInfo.PathId.LD_X86;
import static com.android.sdklib.BuildToolInfo.PathId.LD_X86_64;
import static com.android.sdklib.BuildToolInfo.PathId.LLD;
import static com.android.sdklib.BuildToolInfo.PathId.LLVM_RS_CC;
import static com.android.sdklib.BuildToolInfo.PathId.SPLIT_SELECT;
import static com.android.sdklib.BuildToolInfo.PathId.ZIP_ALIGN;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Information on a specific build-tool folder.
 * <p>
 * For unit tests, see:
 * - sdklib/src/test/.../LocalSdkTest
 * - sdklib/src/test/.../SdkManagerTest
 * - sdklib/src/test/.../BuildToolInfoTest
 */
public class BuildToolInfo {

    public enum PathId {
        /** OS Path to the target's version of the aapt tool. */
        AAPT("1.0.0"),
        /** OS Path to the target's version of the aidl tool. */
        AIDL("1.0.0"),
        /** OS Path to the llvm-rs-cc binary for Renderscript. */
        LLVM_RS_CC("1.0.0"),
        /** OS Path to the Renderscript include folder. */
        ANDROID_RS("1.0.0"),
        /** OS Path to the Renderscript(clang) include folder. */
        ANDROID_RS_CLANG("1.0.0"),

        DEXDUMP("1.0.0"),

        // --- NEW IN 18.1.0 ---

        /** OS Path to the bcc_compat tool. */
        BCC_COMPAT("18.1.0"),
        /** OS Path to the ARM linker. */
        LD_ARM("18.1.0", "30.0.0 rc4"),
        /** OS Path to the X86 linker. */
        LD_X86("18.1.0", "30.0.0 rc4"),
        /** OS Path to the MIPS linker. */
        LD_MIPS("18.1.0", "30.0.0 rc4"),

        // --- NEW IN 19.1.0 ---
        ZIP_ALIGN("19.1.0"),

        // --- NEW IN 21.x.y ---
        JACK("21.1.0", "28.0.0 rc1"),
        JILL("21.1.0", "28.0.0 rc1"),

        SPLIT_SELECT("22.0.0"),

        // --- NEW IN 23.0.3 ---
        /** OS Path to the ARM64 linker. */
        LD_ARM64("23.0.3", "30.0.0 rc4"),

        // --- NEW IN 24.0.0 ---
        JACK_JACOCO_REPORTER("24.0.0", "28.0.0 rc1"),
        JACK_COVERAGE_PLUGIN("24.0.0", "28.0.0 rc1"),

        /** OS Path to the ARM64 linker. */
        LD_X86_64("24.0.0", "30.0.0 rc4"),

        /** OS Path to aapt2. */
        AAPT2("24.0.0 rc2"),

        /** OS Path to the LLD linker. */
        LLD("29.0.3"),

        /** OS Path to aapt2 that supports daemon mode. */
        // TODO(imorlowska): figure out which build tools will include the daemon mode.
        DAEMON_AAPT2("26.0.2"),
        CORE_LAMBDA_STUBS("27.0.3"),
        ;

        /**
         * min revision this element was introduced. Controls {@link BuildToolInfo#isValid(ILogger)}
         */
        private final Revision minRevision;

        /**
         * max revision this element was removed. Controls {@link BuildToolInfo#isValid(ILogger)}
         */
        private final Revision removalRevision;

        /**
         * Creates the enum with a min revision in which this
         * tools appeared in the build tools.
         *
         * @param minRevision the min revision.
         */
        PathId(@NonNull String minRevision) {
            this(minRevision, null);
        }

        /**
         * Creates the enum with a min revisions in which this tools appeared in the build tools and
         * the revision where it was removed.
         *
         * @param minRevision the min revision.
         * @param removalRevision the first revision where the component was removed or null if the
         *     component is still present.
         */
        PathId(@NonNull String minRevision, @Nullable String removalRevision) {
            this.minRevision = Revision.parseRevision(minRevision);
            this.removalRevision =
                    removalRevision != null ? Revision.parseRevision(removalRevision) : null;
        }

        /**
         * Returns whether the enum of present in a given rev of the build tools.
         *
         * @param revision the build tools revision.
         * @return true if the tool is present.
         */
        public boolean isPresentIn(@NonNull Revision revision) {
            return revision.compareTo(minRevision) >= 0
                    && (removalRevision == null || revision.compareTo(removalRevision) < 0);
        }
    }

    /**
     * Creates a {@link BuildToolInfo} from a directory which follows the standard layout
     * convention.
     */
    @NonNull
    public static BuildToolInfo fromStandardDirectoryLayout(
            @NonNull Revision revision, @NonNull Path path) {
        return new BuildToolInfo(revision, path);
    }

    /** Creates a {@link BuildToolInfo} from a {@link LocalPackage}. */
    @NonNull
    public static BuildToolInfo fromLocalPackage(@NonNull LocalPackage localPackage) {
        checkNotNull(localPackage, "localPackage");
        checkArgument(
                localPackage.getPath().contains(SdkConstants.FD_BUILD_TOOLS),
                "%s package required.",
                SdkConstants.FD_BUILD_TOOLS);

        return fromStandardDirectoryLayout(localPackage.getVersion(), localPackage.getLocation());
    }

    /**
     * Creates a full {@link BuildToolInfo} from the specified paths.
     *
     * <p>The {@link Nullable} paths can only be null if corresponding tools were not present in the
     * specified version of build tools.
     */
    @NonNull
    public static BuildToolInfo modifiedLayout(
            @NonNull Revision revision,
            @NonNull Path mainPath,
            @NonNull Path aapt,
            @NonNull Path aidl,
            @NonNull Path llmvRsCc,
            @NonNull Path androidRs,
            @NonNull Path androidRsClang,
            @Nullable Path bccCompat,
            @Nullable Path ldArm,
            @Nullable Path ldArm64,
            @Nullable Path ldX86,
            @Nullable Path ldX86_64,
            @Nullable Path ldMips,
            @Nullable Path lld,
            @NonNull Path zipAlign,
            @Nullable Path aapt2) {
        BuildToolInfo result = new BuildToolInfo(revision, mainPath);

        result.add(AAPT, aapt);
        result.add(AIDL, aidl);
        result.add(LLVM_RS_CC, llmvRsCc);
        result.add(ANDROID_RS, androidRs);
        result.add(ANDROID_RS_CLANG, androidRsClang);
        result.add(ZIP_ALIGN, zipAlign);

        if (bccCompat != null) {
            result.add(BCC_COMPAT, bccCompat);
        } else if (BCC_COMPAT.isPresentIn(revision)) {
            throw new IllegalArgumentException("BCC_COMPAT required in " + revision.toString());
        }
        if (ldArm != null) {
            result.add(LD_ARM, ldArm);
        } else if (LD_ARM.isPresentIn(revision)) {
            throw new IllegalArgumentException("LD_ARM required in " + revision.toString());
        }
        if (ldArm64 != null) {
            result.add(LD_ARM64, ldArm64);
        } else if (LD_ARM64.isPresentIn(revision)) {
            throw new IllegalArgumentException("LD_ARM64 required in " + revision.toString());
        }

        if (ldX86 != null) {
            result.add(LD_X86, ldX86);
        } else if (LD_X86.isPresentIn(revision)) {
            throw new IllegalArgumentException("LD_X86 required in " + revision.toString());
        }

        if (ldX86_64 != null) {
            result.add(LD_X86_64, ldX86_64);
        } else if (LD_X86_64.isPresentIn(revision)) {
            throw new IllegalArgumentException("LD_X86_64 required in " + revision.toString());
        }

        if (ldMips != null) {
            result.add(LD_MIPS, ldMips);
        } else if (LD_MIPS.isPresentIn(revision)) {
            throw new IllegalArgumentException("LD_MIPS required in " + revision.toString());
        }

        if (lld != null) {
            result.add(LLD, lld);
        } else if (LLD.isPresentIn(revision)) {
            throw new IllegalArgumentException("LLD required in " + revision.toString());
        }

        if (aapt2 != null) {
            result.add(AAPT2, aapt2);
            result.add(DAEMON_AAPT2, aapt2);
        } else if (AAPT2.isPresentIn(revision)) {
            throw new IllegalArgumentException("AAPT2 required in " + revision.toString());
        }

        return result;
    }

    /**
     * Creates a new {@link BuildToolInfo} where only some tools are present.
     *
     * <p>This may be the case when paths are managed by an external build system.
     */
    @NonNull
    public static BuildToolInfo partial(
            @NonNull Revision revision, @NonNull Path location, @NonNull Map<PathId, Path> paths) {
        BuildToolInfo result = new BuildToolInfo(revision, location);

        paths.forEach(result::add);

        return result;
    }

    /** The build-tool revision. */
    @NonNull
    private final Revision mRevision;

    /** The path to the build-tool folder specific to this revision. */
    @NonNull private final Path mPath;

    private final Map<PathId, String> mPaths = Maps.newEnumMap(PathId.class);

    private BuildToolInfo(@NonNull Revision revision, @NonNull Path path) {
        mRevision = revision;
        mPath = path;

        add(AAPT, FN_AAPT);
        add(AAPT2, FN_AAPT2);
        add(DAEMON_AAPT2, FN_AAPT2);
        add(AIDL, FN_AIDL);
        add(LLVM_RS_CC, FN_RENDERSCRIPT);
        add(ANDROID_RS, OS_FRAMEWORK_RS);
        add(ANDROID_RS_CLANG, OS_FRAMEWORK_RS_CLANG);
        add(DEXDUMP, FN_DEXDUMP);
        add(BCC_COMPAT, FN_BCC_COMPAT);
        add(LD_ARM, FN_LD_ARM);
        add(LD_ARM64, FN_LD_ARM64);
        add(LD_X86, FN_LD_X86);
        add(LD_X86_64, FN_LD_X86_64);
        add(LD_MIPS, FN_LD_MIPS);
        add(LLD, FN_LLD);
        add(ZIP_ALIGN, FN_ZIPALIGN);
        add(JACK, FN_JACK);
        add(JILL, FN_JILL);
        add(JACK_JACOCO_REPORTER, FN_JACK_JACOCO_REPORTER);
        add(JACK_COVERAGE_PLUGIN, FN_JACK_COVERAGE_PLUGIN);
        add(SPLIT_SELECT, FN_SPLIT_SELECT);
        add(CORE_LAMBDA_STUBS, FN_CORE_LAMBDA_STUBS);
    }

    private void add(PathId id, String leaf) {
        add(id, mPath.resolve(leaf));
    }

    private void add(PathId id, Path path) {
        String str = path.toAbsolutePath().toString();
        if (CancellableFileIo.isDirectory(path)
                && str.charAt(str.length() - 1) != File.separatorChar) {
            str += File.separatorChar;
        }
        mPaths.put(id, str);
    }

    /**
     * Returns the revision.
     */
    @NonNull
    public Revision getRevision() {
        return mRevision;
    }

    /**
     * Returns the build-tool revision-specific folder.
     *
     * <p>For compatibility reasons, use {@link #getPath(PathId)} if you need the path to a specific
     * tool.
     */
    @NonNull
    public Path getLocation() {
        return mPath;
    }

    /**
     * Returns the path of a build-tool component.
     *
     * @param pathId the id representing the path to return.
     * @return The absolute path for that tool, with a / separator if it's a folder.
     *         Null if the path-id is unknown.
     */
    public String getPath(PathId pathId) {
        assert pathId.isPresentIn(mRevision);

        return mPaths.get(pathId);
    }

    /**
     * Checks whether the build-tool is valid by verifying that the expected binaries
     * are actually present. This checks that all known paths point to a valid file
     * or directory.
     *
     * @param log An optional logger. If non-null, errors will be printed there.
     * @return True if the build-tool folder contains all the expected tools.
     */
    public boolean isValid(@Nullable ILogger log) {
        for (Map.Entry<PathId, String> entry : mPaths.entrySet()) {
            File f = new File(entry.getValue());
            // check if file is missing. It's only ok if the revision of the build-tools
            // is lower than the min rev of the element.
            if (!f.exists() && entry.getKey().isPresentIn(mRevision)) {
                if (log != null) {
                    log.warning("Build-tool %1$s is missing %2$s at %3$s",  //$NON-NLS-1$
                            mRevision.toString(),
                            entry.getKey(), f.getAbsolutePath());
                }
                return false;
            }
        }
        return true;
    }

    @VisibleForTesting
    @Nullable
    static Revision getCurrentJvmVersion() throws NumberFormatException {
        String javav = System.getProperty("java.version");              //$NON-NLS-1$
        // java Version is typically in the form "1.2.3_45" and we just need to keep up to "1.2.3"
        // since our revision numbers are in 3-parts form (1.2.3).
        Pattern p = Pattern.compile("((\\d+)(\\.\\d+)?(\\.\\d+)?).*");  //$NON-NLS-1$
        Matcher m = p.matcher(javav);
        if (m.matches()) {
            return Revision.parseRevision(m.group(1));
        }
        return null;
    }

    /**
     * Returns a debug representation suitable for unit-tests.
     * Note that unit-tests need to clean up the paths to avoid inconsistent results.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("rev", mRevision)
                .add("mPath", mPath)
                .add("mPaths", getPathString())
                .toString();
    }

    private String getPathString() {
        StringBuilder sb = new StringBuilder("{");

        for (Map.Entry<PathId, String> entry : mPaths.entrySet()) {
            if (entry.getKey().isPresentIn(mRevision)) {
                if (sb.length() > 1) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append('=').append(entry.getValue());
            }
        }

        sb.append('}');

        return sb.toString();
    }
}
