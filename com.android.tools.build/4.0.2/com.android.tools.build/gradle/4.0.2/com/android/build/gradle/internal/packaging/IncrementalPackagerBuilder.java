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

package com.android.build.gradle.internal.packaging;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.incremental.CapturingChangesApkCreator;
import com.android.build.gradle.internal.incremental.FolderBasedApkCreator;
import com.android.build.gradle.internal.signing.SigningConfigData;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.resources.FileStatus;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory;
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * Factory class to create instances of {@link IncrementalPackager}. Since there are many options
 * for {@link IncrementalPackager} and not all are always required, this makes building the packager
 * easier.
 *
 * <p>While some parameters have sensible defaults, some parameters must be defined. See the
 * {@link #build()} method for information on which parameters are mandatory.
 */
public class IncrementalPackagerBuilder {
    private static int NO_V1_SDK = 24;

    /** Enums for all the supported output format. */
    public enum ApkFormat {
        /** Usual APK format. */
        FILE {
            @Override
            ApkCreatorFactory factory(boolean debuggableBuild) {
                return ApkCreatorFactories.fromProjectProperties(debuggableBuild);
            }
        },

        FILE_WITH_LIST_OF_CHANGES {
            @SuppressWarnings({"OResourceOpenedButNotSafelyClosed", "resource"})
            @Override
            ApkCreatorFactory factory(boolean debuggableBuild) {
                ApkCreatorFactory apk = ApkCreatorFactories.fromProjectProperties(debuggableBuild);
                return creationData ->
                        new CapturingChangesApkCreator(creationData, apk.make(creationData));
            }
        },

        /** Directory with a structure mimicking the APK format. */
        DIRECTORY {
            @SuppressWarnings({"OResourceOpenedButNotSafelyClosed", "resource"})
            @Override
            ApkCreatorFactory factory(boolean debuggableBuild) {
                return creationData ->
                        new CapturingChangesApkCreator(
                                creationData, new FolderBasedApkCreator(creationData));
            }
        };

        abstract ApkCreatorFactory factory(boolean debuggableBuild);
    }

    /**
     * Type of build that invokes the instance of IncrementalPackagerBuilder
     *
     * <p>This information is provided as a hint for possible performance optimizations
     */
    public enum BuildType {
        UNKNOWN,
        CLEAN,
        INCREMENTAL
    }

    /** Builder for the data to create APK file. */
    @NonNull
    private ApkCreatorFactory.CreationData.Builder creationDataBuilder =
            ApkCreatorFactory.CreationData.builder();


    /** Desired format of the output. */
    @NonNull private ApkFormat apkFormat;

    /**
     * How should native libraries be packaged. If not defined, it can be inferred if {@link
     * #manifestFile} is defined.
     */
    @Nullable private NativeLibrariesPackagingMode nativeLibrariesPackagingMode;

    /**
     * The no-compress predicate: returns {@code true} for paths that should not be compressed. If
     * not defined, but {@link #aaptOptionsNoCompress} and {@link #manifestFile} are both defined,
     * it can be inferred.
     */
    @Nullable private Predicate<String> noCompressPredicate;

    /**
     * Directory for intermediate contents.
     */
    @Nullable
    private File intermediateDir;

    /**
     * Is the build debuggable?
     */
    private boolean debuggableBuild;

    /**
     * Is the build JNI-debuggable?
     */
    private boolean jniDebuggableBuild;

    /**
     * ABI filters. Empty if none.
     */
    @NonNull
    private Set<String> abiFilters;

    /** Manifest. */
    @Nullable private File manifestFile;

    /** Whether the manifest file is required to exist. */
    private boolean isManifestFileRequired;

    /** aapt options no compress config. */
    @Nullable private Collection<String> aaptOptionsNoCompress;

    @NonNull private BuildType buildType;

    @NonNull private ApkCreatorType apkCreatorType = ApkCreatorType.APK_Z_FILE_CREATOR;

    @NonNull private Map<RelativeFile, FileStatus> changedDexFiles = new HashMap<>();
    @NonNull private Map<RelativeFile, FileStatus> changedJavaResources = new HashMap<>();
    @NonNull private Map<RelativeFile, FileStatus> changedAssets = new HashMap<>();
    @NonNull private Map<RelativeFile, FileStatus> changedAndroidResources = new HashMap<>();
    @NonNull private Map<RelativeFile, FileStatus> changedNativeLibs = new HashMap<>();

    /** Creates a new builder. */
    public IncrementalPackagerBuilder(@NonNull ApkFormat apkFormat, @NonNull BuildType buildType) {
        abiFilters = new HashSet<>();
        this.apkFormat = apkFormat;
        this.buildType = buildType;
        creationDataBuilder.setIncremental(buildType == BuildType.INCREMENTAL);
    }

    /** Creates a new builder. */
    public IncrementalPackagerBuilder(@NonNull ApkFormat apkFormat) {
        this(apkFormat, BuildType.UNKNOWN);
    }

    /**
     * Sets the signing configuration information for the incremental packager.
     *
     * @param signingConfig the signing config; if {@code null} then the APK will not be signed
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withSigning(@Nullable SigningConfigData signingConfig) {
        return withSigning(signingConfig, 1);
    }

    /**
     * Sets the signing configuration information for the incremental packager.
     *
     * @param signingConfig the signing config; if {@code null} then the APK will not be signed
     * @param minSdk the minimum SDK
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withSigning(
            @Nullable SigningConfigData signingConfig, int minSdk) {
        return withSigning(signingConfig, minSdk, null, null);
    }

    /**
     * This method has a decision logic on whether to sign with v1 signature or not.
     *
     * @param v1Enabled if v1 signature is enabled by default or by the user
     * @param v1Configured if v1 signature is configured by the user
     * @param minSdk the minimum SDK
     * @param targetApi optional injected target Api
     * @return if we actually sign with v1 signature
     */
    @VisibleForTesting
    static boolean enableV1Signing(
            boolean v1Enabled, boolean v1Configured, int minSdk, @Nullable Integer targetApi) {
        if (!v1Enabled) {
            return false;
        }

        // we only do optimization(disable signing) if there is no user input
        if (!v1Configured && (targetApi != null && targetApi >= NO_V1_SDK || minSdk >= NO_V1_SDK)) {
            return false;
        }
        return true;
    }

    /**
     * This method has a decision logic on whether to sign with v2 signature or not.
     *
     * @param v2Enabled if v2 signature is enabled by default or by the user
     * @param v2Configured if v2 signature is configured by the user
     * @param targetApi optional injected target Api
     * @return if we actually sign with v2 signature
     */
    @VisibleForTesting
    static boolean enableV2Signing(
            boolean v2Enabled, boolean v2Configured, @Nullable Integer targetApi) {
        if (!v2Enabled) {
            return false;
        }

        // we only do optimization(disable signing) if there is no user input
        if (!v2Configured && (targetApi != null && targetApi < NO_V1_SDK)) {
            return false;
        }
        return true;
    }

    /**
     * Sets the signing configuration information for the incremental packager.
     *
     * @param signingConfig the signing config; if {@code null} then the APK will not be signed
     * @param minSdk the minimum SDK
     * @param targetApi optional injected target Api
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withSigning( //TODO change it to use dsl signingConfig class?
            @Nullable SigningConfigData signingConfig,
            int minSdk,
            @Nullable Integer targetApi,
            @Nullable byte[] sdkDependencyData) {
        if (signingConfig == null) {
            return this;
        }
        try {
            String error =
                    "SigningConfig \""
                            + signingConfig.getName()
                            + "\" is missing required property \"%s\".";
            CertificateInfo certificateInfo =
                    KeystoreHelper.getCertificateInfo(
                            signingConfig.getStoreType(),
                            Preconditions.checkNotNull(
                                    signingConfig.getStoreFile(), error, "storeFile"),
                            Preconditions.checkNotNull(
                                    signingConfig.getStorePassword(), error, "storePassword"),
                            Preconditions.checkNotNull(
                                    signingConfig.getKeyPassword(), error, "keyPassword"),
                            Preconditions.checkNotNull(
                                    signingConfig.getKeyAlias(), error, "keyAlias"));

            boolean enableV1Signing =
                    enableV1Signing(
                            signingConfig.getV1SigningEnabled(),
                            signingConfig.getV1SigningConfigured(),
                            minSdk,
                            targetApi);
            boolean enableV2Signing =
                    enableV2Signing(
                            signingConfig.getV2SigningEnabled(),
                            signingConfig.getV2SigningConfigured(),
                            targetApi);

            creationDataBuilder.setSigningOptions(
                    SigningOptions.builder()
                            .setKey(certificateInfo.getKey())
                            .setCertificates(certificateInfo.getCertificate())
                            .setV1SigningEnabled(enableV1Signing)
                            .setV2SigningEnabled(enableV2Signing)
                            .setMinSdkVersion(minSdk)
                            .setValidation(computeValidation())
                            .setSdkDependencyData(sdkDependencyData)
                            .setExecutor(
                                    provider -> {
                                        // noinspection CommonForkJoinPool
                                        ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
                                        try {
                                            int jobCount = forkJoinPool.getParallelism();
                                            List<Future<?>> jobs = new ArrayList<>(jobCount);

                                            for (int i = 0; i < jobCount; i++) {
                                                jobs.add(
                                                        forkJoinPool.submit(
                                                                provider.createRunnable()));
                                            }

                                            for (Future<?> future : jobs) {
                                                future.get();
                                            }
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new RuntimeException(e);
                                        } catch (ExecutionException e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .build());
        } catch (KeytoolException|FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        return this;
    }

    private SigningOptions.Validation computeValidation() {
        switch (buildType) {
            case INCREMENTAL:
                return SigningOptions.Validation.ASSUME_VALID;
            case CLEAN:
                return SigningOptions.Validation.ASSUME_INVALID;
            case UNKNOWN:
                return SigningOptions.Validation.ALWAYS_VALIDATE;
            default:
                throw new RuntimeException(
                        "Unknown IncrementalPackagerBuilder build type " + buildType);
        }
    }

    /**
     * Sets the output file for the APK.
     *
     * @param f the output file
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withOutputFile(@NonNull File f) {
        creationDataBuilder.setApkPath(f);
        return this;
    }

    /**
     * Sets the packaging mode for native libraries.
     *
     * @param packagingMode the packging mode
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withNativeLibraryPackagingMode(
            @NonNull NativeLibrariesPackagingMode packagingMode) {
        nativeLibrariesPackagingMode = packagingMode;
        return this;
    }

    /**
     * Sets the manifest. While the manifest itself is not used for packaging, information on the
     * native libraries packaging mode can be inferred from the manifest.
     *
     * @param manifest the manifest
     * @param isManifestFileRequired whether the manifest file is required to exist
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withManifest(
            @NonNull File manifest, boolean isManifestFileRequired) {
        this.manifestFile = manifest;
        this.isManifestFileRequired = isManifestFileRequired;
        return this;
    }

    /**
     * Sets the no-compress predicate. This predicate returns {@code true} for files that should
     * not be compressed
     *
     * @param noCompressPredicate the predicate
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withNoCompressPredicate(
            @NonNull Predicate<String> noCompressPredicate) {
        this.noCompressPredicate = noCompressPredicate;
        return this;
    }

    /**
     * Sets the {@code aapt} options no compress predicate.
     *
     * <p>The no-compress predicate can be computed if this and the manifest (see {@link
     * #withManifest(File, boolean)}) are both defined.
     */
    @NonNull
    public IncrementalPackagerBuilder withAaptOptionsNoCompress(
            @Nullable Collection<String> aaptOptionsNoCompress) {
        this.aaptOptionsNoCompress = aaptOptionsNoCompress;
        return this;
    }

    /**
     * Sets the intermediate directory used to store information for incremental builds.
     *
     * @param intermediateDir the intermediate directory
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withIntermediateDir(@NonNull File intermediateDir) {
        this.intermediateDir = intermediateDir;
        return this;
    }

    /**
     * Sets the created-by parameter.
     *
     * @param createdBy the optional value for created-by
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withCreatedBy(@Nullable String createdBy) {
        creationDataBuilder.setCreatedBy(createdBy);
        return this;
    }

    /**
     * Sets whether the build is debuggable or not.
     *
     * @param debuggableBuild is the build debuggable?
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withDebuggableBuild(boolean debuggableBuild) {
        this.debuggableBuild = debuggableBuild;
        return this;
    }

    /**
     * Sets whether the build is JNI-debuggable or not.
     *
     * @param jniDebuggableBuild is the build JNI-debuggable?
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withJniDebuggableBuild(boolean jniDebuggableBuild) {
        this.jniDebuggableBuild = jniDebuggableBuild;
        return this;
    }

    /**
     * Sets the set of accepted ABIs.
     *
     * @param acceptedAbis the accepted ABIs; if empty then all ABIs are accepted
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withAcceptedAbis(@NonNull Set<String> acceptedAbis) {
        this.abiFilters = ImmutableSet.copyOf(acceptedAbis);
        return this;
    }

    /**
     * Sets the {@link ApkCreatorType}
     *
     * @param apkCreatorType the {@link ApkCreatorType}
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withApkCreatorType(@NonNull ApkCreatorType apkCreatorType) {
        this.apkCreatorType = apkCreatorType;
        return this;
    }

    /**
     * Sets the changed dex files
     *
     * @param changedDexFiles the changed dex files
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withChangedDexFiles(
            @NonNull Map<RelativeFile, FileStatus> changedDexFiles) {
        this.changedDexFiles = ImmutableMap.copyOf(changedDexFiles);
        return this;
    }

    /**
     * Sets the changed java resources
     *
     * @param changedJavaResources the changed java resources
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withChangedJavaResources(
            @NonNull Map<RelativeFile, FileStatus> changedJavaResources) {
        this.changedJavaResources = ImmutableMap.copyOf(changedJavaResources);
        return this;
    }

    /**
     * Sets the changed assets
     *
     * @param changedAssets the changed assets
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withChangedAssets(
            @NonNull Map<RelativeFile, FileStatus> changedAssets) {
        this.changedAssets = ImmutableMap.copyOf(changedAssets);
        return this;
    }

    /**
     * Sets the changed android resources
     *
     * @param changedAndroidResources the changed android resources
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withChangedAndroidResources(
            @NonNull Map<RelativeFile, FileStatus> changedAndroidResources) {
        this.changedAndroidResources = ImmutableMap.copyOf(changedAndroidResources);
        return this;
    }

    /**
     * Sets the changed native libs
     *
     * @param changedNativeLibs the changed native libs
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withChangedNativeLibs(
            @NonNull Map<RelativeFile, FileStatus> changedNativeLibs) {
        this.changedNativeLibs = ImmutableMap.copyOf(changedNativeLibs);
        return this;
    }

    /**
     * Creates the packager, verifying that all the minimum data has been provided. The required
     * information are:
     *
     * <ul>
     *    <li>{@link #withOutputFile(File)}
     *    <li>{@link #withIntermediateDir(File)}
     * </ul>
     *
     * @return the incremental packager
     */
    @NonNull
    public IncrementalPackager build() {
        Preconditions.checkState(intermediateDir != null, "intermediateDir == null");

        ManifestAttributeSupplier manifest =
                this.manifestFile != null
                        ? new DefaultManifestParser(
                                this.manifestFile, () -> true, isManifestFileRequired, null)
                        : null;

        if (noCompressPredicate == null) {
            if (manifest != null) {
                noCompressPredicate =
                        PackagingUtils.getNoCompressPredicate(aaptOptionsNoCompress, manifest);
            } else {
                noCompressPredicate = path -> false;
            }
        }

        if (nativeLibrariesPackagingMode == null) {
            if (manifest != null) {
                nativeLibrariesPackagingMode =
                        PackagingUtils.getNativeLibrariesLibrariesPackagingMode(manifest);
            } else {
                nativeLibrariesPackagingMode = NativeLibrariesPackagingMode.COMPRESSED;
            }
        }

        creationDataBuilder
                .setNativeLibrariesPackagingMode(nativeLibrariesPackagingMode)
                .setNoCompressPredicate(noCompressPredicate::test);

        try {
            return new IncrementalPackager(
                    creationDataBuilder.build(),
                    intermediateDir,
                    apkFormat.factory(debuggableBuild),
                    ApkFormat.FILE.equals(apkFormat),
                    abiFilters,
                    jniDebuggableBuild,
                    debuggableBuild,
                    apkCreatorType,
                    changedDexFiles,
                    changedJavaResources,
                    changedAssets,
                    changedAndroidResources,
                    changedNativeLibs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
