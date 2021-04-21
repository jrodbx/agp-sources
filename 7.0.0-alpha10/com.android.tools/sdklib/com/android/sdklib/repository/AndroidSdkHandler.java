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
package com.android.sdklib.repository;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Slow;
import com.android.prefs.AbstractAndroidLocations;
import com.android.prefs.AndroidLocationsProvider;
import com.android.repository.Revision;
import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemoteListSourceProvider;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Repository;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.sources.LocalSourceProvider;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.legacy.LegacyLocalRepoLoader;
import com.android.sdklib.repository.legacy.LegacyRemoteRepoLoader;
import com.android.sdklib.repository.meta.AddonFactory;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.meta.RepoFactory;
import com.android.sdklib.repository.meta.SdkCommonFactory;
import com.android.sdklib.repository.meta.SysImgFactory;
import com.android.sdklib.repository.sources.RemoteSiteType;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Android SDK interface to {@link RepoManager}. Ensures that the proper android sdk-specific
 * schemas and source providers are registered, and provides android sdk-specific package logic
 * (pending as adoption continues).
 */
public final class AndroidSdkHandler {

    /** Schema module containing the package type information to be used in addon repos. */
    private static final SchemaModule<AddonFactory> ADDON_MODULE =
            new SchemaModule<>(
                    "com.android.sdklib.repository.generated.addon.v%d.ObjectFactory",
                    "/xsd/sdk-addon-%02d.xsd", AndroidSdkHandler.class);

    /** Schema module containing the package type information to be used in the primary repo. */
    private static final SchemaModule<RepoFactory> REPOSITORY_MODULE =
            new SchemaModule<>(
                    "com.android.sdklib.repository.generated.repository.v%d.ObjectFactory",
                    "/xsd/sdk-repository-%02d.xsd", AndroidSdkHandler.class);

    /** Schema module containing the package type information to be used in system image repos. */
    private static final SchemaModule<SysImgFactory> SYS_IMG_MODULE =
            new SchemaModule<>(
                    "com.android.sdklib.repository.generated.sysimg.v%d.ObjectFactory",
                    "/xsd/sdk-sys-img-%02d.xsd", AndroidSdkHandler.class);

    /** Common schema module used by the other sdk-specific modules. */
    private static final SchemaModule<SdkCommonFactory> COMMON_MODULE =
            new SchemaModule<>(
                    "com.android.sdklib.repository.generated.common.v%d.ObjectFactory",
                    "/xsd/sdk-common-%02d.xsd", AndroidSdkHandler.class);

    /**
     * The URL of the official Google sdk-repository site. The URL ends with a /, allowing easy
     * concatenation.
     */
    private static final String URL_GOOGLE_SDK_SITE = "https://dl.google.com/android/repository/";

    /**
     * A system property than can be used to add an extra fully-privileged update site.
     */
    private static final String CUSTOM_SOURCE_PROPERTY = "android.sdk.custom.url";

    /**
     * The name of the environment variable used to override the url of the primary repository, for
     * testing.
     */
    public static final String SDK_TEST_BASE_URL_ENV_VAR = "SDK_TEST_BASE_URL";

    /**
     * The name of the system property used to override the url of the primary repository, for
     * testing.
     */
    public static final String SDK_TEST_BASE_URL_PROPERTY = "sdk.test.base.url";

    /**
     * The name of the file containing user-specified remote repositories.
     */
    @VisibleForTesting
    static final String LOCAL_ADDONS_FILENAME = "repositories.cfg";

    /**
     * Pattern for the name of a (remote) file containing a list of urls to check for repositories.
     *
     * @see RemoteListSourceProvider
     */
    private static final String DEFAULT_SITE_LIST_FILENAME_PATTERN = "addons_list-%d.xml";

    /**
     * Lock for synchronizing changes to the our {@link RepoManager}.
     */
    private static final Object MANAGER_LOCK = new Object();

    /**
     * Pattern for the URL pointing to the repository xml (as defined by sdk-repository-01.xsd in
     * repository).
     */
    private static final String REPO_URL_PATTERN = "%srepository2-%d.xml";

    /**
     * The {@link RepoManager} initialized with our {@link SchemaModule}s, {@link
     * RepositorySource}s, and local SDK path.
     */
    private RepoManager mRepoManager;

    /**
     * Finds all {@link SystemImage}s in packages known to {@link #mRepoManager};
     */
    private SystemImageManager mSystemImageManager;

    /**
     * Creates {@link IAndroidTarget}s based on the platforms and addons known to
     * {@link #mRepoManager}.
     */
    private AndroidTargetManager mAndroidTargetManager;

    /**
     * Reference to our latest build tool package.
     */
    private BuildToolInfo mLatestBuildTool = null;

    /**
     * {@link FileOp} to use for local file operations. For normal operation should be
     * {@link FileOpUtils#create()}.
     */
    private final FileOp mFop;

    /** Singleton instance of this class. */
    private static final Map<Path, AndroidSdkHandler> sInstances = Maps.newConcurrentMap();

    private static final Path NULL_PATH = Paths.get("");

    /** Location of the local SDK. */
    @Nullable private final Path mLocation;

    /** @see AbstractAndroidLocations#getPrefsLocation() */
    @Nullable private final Path mAndroidFolder;

    /**
     * Provider for user-specified {@link RepositorySource}s.
     */
    private LocalSourceProvider mUserSourceProvider;

    /**
     * Lazily-initialized class containing our static repository configuration, shared between
     * AndroidSdkHandler instances.
     */
    private static RepoConfig sRepoConfig;

    /**
     * Get a {@code AndroidSdkHandler} instance.
     *
     * @param locationProvider a location provider to get the path to the .android folder.
     * @param localPath The path to the local SDK. If {@code null} this handler will only be used
     *     for remote operations.
     */
    @NonNull
    public static AndroidSdkHandler getInstance(
            @NonNull AndroidLocationsProvider locationProvider, @Nullable Path localPath) {
        Path key = localPath == null ? NULL_PATH : localPath;
        synchronized (sInstances) {
            return sInstances.computeIfAbsent(
                    key,
                    k -> {
                        Path androidFolder;
                        try {
                            androidFolder = locationProvider.getPrefsLocation();
                        } catch (Throwable e) {
                            androidFolder = null;
                        }
                        return new AndroidSdkHandler(
                                localPath, androidFolder, FileOpUtils.create(localPath));
                    });
        }
    }

    /**
     * Force removal of a cached {@code AndroidSdkHandler} instance. This will force a reparsing of
     * the SDK next time a component is looked up.
     *
     * @param localPath The path to the local SDK.
     */
    public static void resetInstance(@NonNull Path localPath) {
        synchronized (sInstances) {
            sInstances.remove(localPath);
        }
    }

    /**
     * Don't use this, use {@link #getInstance(AndroidLocationsProvider, Path)}, unless you're in a
     * unit test and need to specify a custom {@link FileOp} and/or {@code androidFolder}.
     */
    @VisibleForTesting
    public AndroidSdkHandler(
            @Nullable Path localPath, @Nullable Path androidFolder, @NonNull FileOp fop) {
        mLocation = localPath;
        mAndroidFolder = androidFolder;
        mFop = checkNotNull(fop);
    }

    /**
     * Don't use this either, unless you're in a unit test and need to specify a custom {@link
     * RepoManager}.
     *
     * @see #AndroidSdkHandler(Path, Path, FileOp)
     */
    @VisibleForTesting
    public AndroidSdkHandler(
            @Nullable Path localPath,
            @Nullable Path androidFolder,
            @NonNull FileOp fop,
            @NonNull RepoManager repoManager) {
        this(localPath, androidFolder, fop);
        mRepoManager = repoManager;
    }

    /**
     * Fetches {@link RepoManager} set up to interact with android SDK repositories. It should not
     * cached by callers of this method, since any changes to the fundamental properties of the
     * manager (fallback loaders, local path) will cause a new instance to be created.
     */
    @NonNull
    public RepoManager getSdkManager(@NonNull ProgressIndicator progress) {
        RepoManager result = mRepoManager;
        synchronized (MANAGER_LOCK) {
            if (result == null) {
                mSystemImageManager = null;
                mAndroidTargetManager = null;
                mLatestBuildTool = null;

                result = getRepoConfig(progress)
                        .createRepoManager(progress, mLocation, getUserSourceProvider(progress),
                                mFop);
                // Invalidate system images, targets, the latest build tool, and the legacy local
                // package manager when local packages change
                result.addLocalChangeListener(packages -> {
                    mSystemImageManager = null;
                    mAndroidTargetManager = null;
                    mLatestBuildTool = null;
                });
                mRepoManager = result;
            }
        }
        return mRepoManager;
    }

    /**
     * Gets (and creates if necessary) a {@link SystemImageManager} based on our local sdk packages.
     */
    @NonNull
    public SystemImageManager getSystemImageManager(@NonNull ProgressIndicator progress) {
        if (mSystemImageManager == null) {
            getSdkManager(progress);
            mSystemImageManager =
                    new SystemImageManager(mRepoManager, getSysImgModule().createLatestFactory());
        }
        return mSystemImageManager;
    }

    /**
     * Gets (and creates if necessary) an {@link AndroidTargetManager} based on our local sdk
     * packages.
     */
    @NonNull
    public AndroidTargetManager getAndroidTargetManager(@NonNull ProgressIndicator progress) {
        if (mAndroidTargetManager == null) {
            getSdkManager(progress);
            mAndroidTargetManager = new AndroidTargetManager(this);
        }
        return mAndroidTargetManager;
    }

    /** Gets the path of the local SDK, if set. */
    @Nullable
    public Path getLocation() {
        return mLocation;
    }

    @Nullable
    public Path getAndroidFolder() {
        return mAndroidFolder;
    }

    /**
     * Convenience to get a package from the local repo.
     */
    @Nullable
    public LocalPackage getLocalPackage(@NonNull String path, @NonNull ProgressIndicator progress) {
        return getSdkManager(progress).getPackages().getLocalPackages().get(path);
    }

    /**
     * @param packages a {@link Collection} of packages which share a common {@code prefix}, from
     *     which we wish to extract the "Latest" package, as sorted with {@code mapper} and {@code
     *     comparator} on the suffixes.
     * @param filter the revision predicate that has to be satisfied by the returned package
     * @param allowPreview whether we allow returning a preview package.
     * @param mapper maps from path suffix to a {@link Comparable}, so that we can sort the packages
     *     by suffix.
     * @param comparator how to sort suffixes after mapping them.
     * @param <P> {@link LocalPackage} or {@link RemotePackage}
     * @param <T> {@link Comparable} that we map the suffix to.
     * @return the "Latest" package from the {@link Collection}, as sorted with {@code mapper} and
     *     {@code comparator} on the last path component.
     */
    @Nullable
    public static <P extends RepoPackage, T> P getLatestPackageFromPrefixCollection(
            @NonNull Collection<P> packages,
            @Nullable Predicate<Revision> filter,
            boolean allowPreview,
            @NonNull Function<String, T> mapper,
            @NonNull Comparator<T> comparator) {
        Function<P, T> keyGen = p -> mapper.apply(p.getPath().substring(
                p.getPath().lastIndexOf(RepoPackage.PATH_SEPARATOR) + 1));
        return packages.stream()
                .filter(p -> (filter == null || filter.test(p.getVersion()))
                              && (allowPreview || !p.getVersion().isPreview()))
                .max((p1, p2) -> comparator.compare(keyGen.apply(p1), keyGen.apply(p2)))
                .orElse(null);
    }

    /**
     * Suppose that {@code prefix} is {@code p}, and we have these local packages: {@code p;1.1},
     * {@code p;1.2}, {@code p;2.1} What this should return is the package {@code p;2.1}. We operate
     * on the path suffix since we have no guarantee that the package revision is the same as used
     * in the path. We also have no guarantee that the format of the path even matches, so we ignore
     * the packages that don't fit the format.
     *
     * @see #getLatestLocalPackageForPrefix(String, Predicate, boolean, Function, ProgressIndicator)
     *     where {@link Function} is just converting path suffix to {@link Revision}. Suffixes that
     *     are not valid {@link Revision}s are assumed to be {@link Revision#NOT_SPECIFIED}, and
     *     would be the lowest.
     * @see Revision#safeParseRevision(String) for how the conversion is done.
     */
    @Nullable
    public LocalPackage getLatestLocalPackageForPrefix(
            @NonNull String prefix, @Nullable Predicate<Revision> filter, boolean allowPreview,
            @NonNull ProgressIndicator progress) {
        return getLatestLocalPackageForPrefix(prefix, filter, allowPreview,
                Revision::safeParseRevision, progress);
    }

    /**
     * @see #getLatestLocalPackageForPrefix(String, Predicate, boolean, Function, Comparator,
     * ProgressIndicator) , where {@link Comparator} is just the default order. Highest is latest.
     */
    @Nullable
    public LocalPackage getLatestLocalPackageForPrefix(
            @NonNull String prefix, @Nullable Predicate<Revision> filter, boolean allowPreview,
            @NonNull Function<String, ? extends Comparable> mapper,
            @NonNull ProgressIndicator progress) {
        return getLatestLocalPackageForPrefix(prefix, filter, allowPreview, mapper,
                Comparator.naturalOrder(), progress);
    }

    /**
     * This grabs the {@link Collection} of {@link LocalPackage}s from {@link RepoManager} with the
     * same prefix using {@link RepositoryPackages#getLocalPackagesForPrefix(String)}
     * and forwards it to {@link #getLatestPackageFromPrefixCollection}
     */
    @Nullable
    public <T> LocalPackage getLatestLocalPackageForPrefix(@NonNull String prefix,
            @Nullable Predicate<Revision> filter, boolean allowPreview,
            @NonNull Function<String, T> mapper, @NonNull Comparator<T> comparator,
            @NonNull ProgressIndicator progress) {
        return getLatestPackageFromPrefixCollection(
                getSdkManager(progress).getPackages().getLocalPackagesForPrefix(prefix),
                filter, allowPreview, mapper, comparator);
    }

    /**
     * @see #getLatestLocalPackageForPrefix(String, Predicate, boolean, ProgressIndicator), but
     *     for {@link RemotePackage}s instead.
     */
    @Nullable
    public RemotePackage getLatestRemotePackageForPrefix(@NonNull String prefix,
            @Nullable Predicate<Revision> filter, boolean allowPreview,
            @NonNull ProgressIndicator progress) {
        return getLatestRemotePackageForPrefix(prefix, filter, allowPreview,
                Revision::safeParseRevision, progress);
    }

    /**
     * @see #getLatestLocalPackageForPrefix(String, Predicate, boolean, Function,
     *     ProgressIndicator), but for {@link RemotePackage}s instead.
     */
    @Nullable
    public RemotePackage getLatestRemotePackageForPrefix(
            @NonNull String prefix,
            @Nullable Predicate<Revision> filter, boolean allowPreview,
            @NonNull Function<String, ? extends Comparable> mapper,
            @NonNull ProgressIndicator progress) {
        return getLatestRemotePackageForPrefix(prefix, filter, allowPreview,
                mapper, Comparator.naturalOrder(), progress);
    }

    /**
     * @see #getLatestLocalPackageForPrefix(String, Predicate, boolean, Function, Comparator,
     *     ProgressIndicator), but for {@link RemotePackage}s instead.
     */
    @Nullable
    public <T> RemotePackage getLatestRemotePackageForPrefix(@NonNull String prefix,
            @Nullable Predicate<Revision> filter, boolean allowPreview,
            @NonNull Function<String, T> mapper, @NonNull Comparator<T> comparator,
            @NonNull ProgressIndicator progress) {
        return getLatestPackageFromPrefixCollection(
                getSdkManager(progress).getPackages().getRemotePackagesForPrefix(prefix),
                filter, allowPreview, mapper, comparator);
    }

    /**
     * Resets the {@link RepoManager}s of all cached {@link AndroidSdkHandler}s.
     */
    private static void invalidateAll() {
        for (AndroidSdkHandler handler : sInstances.values()) {
            handler.mRepoManager = null;
        }
    }

    /**
     * @return The {@link SchemaModule} containing the common sdk-specific metadata. See
     * sdk-common-XX.xsd.
     */
    @NonNull
    public static SchemaModule<SdkCommonFactory> getCommonModule() {
        return COMMON_MODULE;
    }

    /**
     * @return The {@link SchemaModule} containing the metadata for addon-type {@link Repository}s.
     * See sdk-addon-XX.xsd.
     */
    @NonNull
    public static SchemaModule<AddonFactory> getAddonModule() {
        return ADDON_MODULE;
    }

    /**
     * @return The {@link SchemaModule} containing the metadata for the primary android SDK {@link
     * Repository} (containin platforms etc.). See sdk-repository-XX.xsd.
     */
    @NonNull
    public static SchemaModule<RepoFactory> getRepositoryModule() {
        return REPOSITORY_MODULE;
    }

    /**
     * @return The {@link SchemaModule} containing the metadata for system image-type {@link
     * Repository}s. See sdk-sys-img-XX.xsd.
     */
    @NonNull
    public static SchemaModule<SysImgFactory> getSysImgModule() {
        return SYS_IMG_MODULE;
    }

    /**
     * @return A list of all the known {@link SchemaModule}s
     */
    @NonNull
    public static List<SchemaModule<?>> getAllModules() {
        return ImmutableList.of(AndroidSdkHandler.getRepositoryModule(),
                AndroidSdkHandler.getAddonModule(),
                AndroidSdkHandler.getSysImgModule(),
                RepoManager.getCommonModule(),
                RepoManager.getGenericModule());
    }

    @NonNull
    @VisibleForTesting
    RemoteListSourceProvider getRemoteListSourceProvider(@NonNull ProgressIndicator progress) {
        return getRepoConfig(progress).getRemoteListSourceProvider();
    }

    /**
     * Gets the customizable {@link RepositorySourceProvider}. Can be null if there's a problem
     * with the user's environment.
     */
    @Nullable
    public LocalSourceProvider getUserSourceProvider(@NonNull ProgressIndicator progress) {
        if (mUserSourceProvider == null && mAndroidFolder != null) {
            mUserSourceProvider = RepoConfig.createUserSourceProvider(mAndroidFolder);
            synchronized (MANAGER_LOCK) {
                if (mRepoManager != null) {
                    // If the repo already exists cause it to be reloaded, so the userSourceProvider
                    // can be added to the config.
                    mRepoManager = null;
                    getSdkManager(progress);
                }
            }
        }
        return mUserSourceProvider;
    }

    /**
     * Add another {@link RepositorySourceProvider}. All existing {@link AndroidSdkHandler}s and
     * {@link RepoManager}s are invalidated, and all future instances will include the new
     * provider.
     */
    public static void addCustomSourceProvider(@NonNull RepositorySourceProvider provider,
            @NonNull ProgressIndicator progress) {
        getRepoConfig(progress).addCustomSourceProvider(provider);
        invalidateAll();
    }

    @NonNull
    private static RepoConfig getRepoConfig(@NonNull ProgressIndicator progress) {
        if (sRepoConfig == null) {
            sRepoConfig = new RepoConfig(progress);
        }
        return sRepoConfig;
    }

    /**
     * Class containing the repository configuration we can (lazily) create statically, as well
     * as a method to create a new {@link RepoManager} based on that configuration.
     *
     * Instances of this class may be shared between {@link AndroidSdkHandler} instances.
     */
    private static class RepoConfig {

        /**
         * Provider for a list of {@link RepositorySource}s fetched from the google.
         */
        private RemoteListSourceProvider mAddonsListSourceProvider;

        /** Provider for the main new-style {@link RepositorySource} */
        private final ConstantSourceProvider mRepositorySourceProvider;

        /**
         * Provider for the previous version of the main new-style {@link RepositorySource}, useful
         * during transition to the new version.
         */
        private ConstantSourceProvider mPrevRepositorySourceProvider;

        /** Extra source providers that were added externally. */
        private final Set<RepositorySourceProvider> mCustomSourceProviders = new HashSet<>();

        /**
         * Sets up our {@link SchemaModule}s and {@link RepositorySourceProvider}s if they haven't
         * been yet.
         *
         * @param progress Used for error logging.
         */
        public RepoConfig(@NonNull ProgressIndicator progress) {
            // Schema module for the list of update sites we download
            SchemaModule<?> addonListModule =
                    new SchemaModule<>(
                            "com.android.sdklib.repository.sources.generated.v%d.ObjectFactory",
                            "/xsd/sources/sdk-sites-list-%d.xsd", RemoteSiteType.class);

            try {
                // Specify what modules are allowed to be used by what sites.
                Map<Class<? extends RepositorySource>, Collection<SchemaModule<?>>> siteTypes =
                        ImmutableMap
                                .<Class<? extends RepositorySource>, Collection<SchemaModule<?>>>
                                        builder()
                                .put(
                                        RemoteSiteType.AddonSiteType.class,
                                        ImmutableSet.of(ADDON_MODULE))
                                .put(
                                        RemoteSiteType.SysImgSiteType.class,
                                        ImmutableSet.of(SYS_IMG_MODULE))
                                .build();
                mAddonsListSourceProvider = RemoteListSourceProvider
                        .create(getAddonListUrl(progress), addonListModule, siteTypes);
            } catch (URISyntaxException e) {
                progress.logError("Failed to set up addons source provider", e);
            }

            String url =
                    String.format(
                            Locale.US,
                            REPO_URL_PATTERN,
                            getBaseUrl(progress),
                            REPOSITORY_MODULE.getNamespaceVersionMap().size());
            mRepositorySourceProvider = new ConstantSourceProvider(url, "Android Repository",
                    ImmutableSet.of(REPOSITORY_MODULE, RepoManager.getGenericModule()));

            int prevRev = REPOSITORY_MODULE.getNamespaceVersionMap().size() - 1;
            if (prevRev > 0) {
                url = String.format(Locale.US, REPO_URL_PATTERN, getBaseUrl(progress), prevRev);
                mPrevRepositorySourceProvider =
                        new ConstantSourceProvider(
                                url,
                                "Android Repository v" + prevRev,
                                ImmutableSet.of(REPOSITORY_MODULE, RepoManager.getGenericModule()));
            } else {
                mPrevRepositorySourceProvider = null;
            }
        }

        /** Creates a customizable {@link RepositorySourceProvider}. */
        @NonNull
        public static LocalSourceProvider createUserSourceProvider(@NonNull Path androidFolder) {
            return new LocalSourceProvider(
                    androidFolder.resolve(LOCAL_ADDONS_FILENAME),
                    ImmutableList.of(SYS_IMG_MODULE, ADDON_MODULE));
        }

        @NonNull
        private static String getAddonListUrl(@NonNull ProgressIndicator progress) {
            return getBaseUrl(progress) + DEFAULT_SITE_LIST_FILENAME_PATTERN;
        }

        /**
         * Gets the default url (without the actual filename or specific final part of the path
         * (e.g. sys-img). This will be either the value of {@link #SDK_TEST_BASE_URL_ENV_VAR},
         * {@link #URL_GOOGLE_SDK_SITE} or {@link #SDK_TEST_BASE_URL_PROPERTY} JVM property.
         */
        @NonNull
        private static String getBaseUrl(@NonNull ProgressIndicator progress) {
            String baseUrl =
                    Optional.ofNullable(System.getenv(SDK_TEST_BASE_URL_ENV_VAR))
                            .orElse(System.getProperty(SDK_TEST_BASE_URL_PROPERTY));
            if (baseUrl != null) {
                if (!baseUrl.isEmpty() && baseUrl.endsWith("/")) {
                    return baseUrl;
                } else {
                    progress.logWarning("Ignoring invalid SDK_TEST_BASE_URL: " + baseUrl);
                }
            }
            return URL_GOOGLE_SDK_SITE;
        }

        @NonNull
        public RemoteListSourceProvider getRemoteListSourceProvider() {
            return mAddonsListSourceProvider;
        }

        /**
         * Add a {@link RepositorySourceProvider} to this config. It will be added to any {@link
         * RepoManager} created by {@link #createRepoManager(ProgressIndicator, Path,
         * LocalSourceProvider, FileOp)}
         */
        public void addCustomSourceProvider(@NonNull RepositorySourceProvider provider) {
            mCustomSourceProviders.add(provider);
        }

        @Slow
        @NonNull
        public RepoManager createRepoManager(
                @NonNull ProgressIndicator progress,
                @Nullable Path localLocation,
                @Nullable LocalSourceProvider userProvider,
                @NonNull FileOp fop) {
            RepoManager result = RepoManager.create();

            // Create the schema modules etc. if they haven't been already.
            result.registerSchemaModule(ADDON_MODULE);
            result.registerSchemaModule(REPOSITORY_MODULE);
            result.registerSchemaModule(SYS_IMG_MODULE);
            result.registerSchemaModule(COMMON_MODULE);

            result.registerSourceProvider(mRepositorySourceProvider);
            if (mPrevRepositorySourceProvider != null) {
                result.registerSourceProvider(mPrevRepositorySourceProvider);
            }
            mCustomSourceProviders.forEach(result::registerSourceProvider);
            String customSourceUrl = System.getProperty(CUSTOM_SOURCE_PROPERTY);
            if (customSourceUrl != null && !customSourceUrl.isEmpty()) {
                result.registerSourceProvider(
                        new ConstantSourceProvider(customSourceUrl, "Custom Provider",
                                result.getSchemaModules()));
            }
            result.registerSourceProvider(mAddonsListSourceProvider);
            if (userProvider != null) {
                result.registerSourceProvider(userProvider);
                // The customizable source provider needs a handle on the repo manager, so it can
                // mark the cached packages invalid if the sources change.
                userProvider.setRepoManager(result);
            }
            result.setFallbackRemoteRepoLoader(new LegacyRemoteRepoLoader());

            result.setLocalPath(localLocation);

            if (localLocation != null) {
                // If we have a local sdk path set, set up the old-style loader so we can parse
                // any legacy packages.
                result.setFallbackLocalRepoLoader(
                        new LegacyLocalRepoLoader(fop.toFile(localLocation), fop));

                // If a location is set we'll always want at least the local packages loaded, so
                // load them now.
                result.loadSynchronously(0, progress, null, null);
            }

            return result;
        }
    }

    /**
     * Returns a {@link BuildToolInfo} corresponding to the newest installed build tool {@link
     * RepoPackage}, or {@code null} if none are installed (or if the {@code allowPreview} parameter
     * is false and there was non-preview version available)
     *
     * @param progress a progress indicator
     * @param allowPreview ignore preview build tools version unless this parameter is true
     */
    @Nullable
    public BuildToolInfo getLatestBuildTool(@NonNull ProgressIndicator progress,
            boolean allowPreview) {
        return getLatestBuildTool(progress, null, allowPreview);
    }

    /**
     * Returns a {@link BuildToolInfo} corresponding to the newest installed build tool {@link
     * RepoPackage}, or {@code null} if none are installed (or if the {@code allowPreview} parameter
     * is false and there was non-preview version available)
     *
     * @param progress a progress indicator
     * @param filter the revision predicate to satisfy
     * @param allowPreview ignore preview build tools version unless this parameter is true
     */
    @Nullable
    public BuildToolInfo getLatestBuildTool(
            @NonNull ProgressIndicator progress,
            @Nullable Predicate<Revision> filter,
            boolean allowPreview) {
        if (!allowPreview && mLatestBuildTool != null) {
            return mLatestBuildTool;
        }

        LocalPackage latestBuildToolPackage = getLatestLocalPackageForPrefix(
                SdkConstants.FD_BUILD_TOOLS, filter, allowPreview, progress);

        if (latestBuildToolPackage == null) {
            return null;
        }

        BuildToolInfo latestBuildTool = BuildToolInfo.fromLocalPackage(latestBuildToolPackage);

        // Don't cache if preview.
        if (!latestBuildToolPackage.getVersion().isPreview()) {
            mLatestBuildTool = latestBuildTool;
        }

        return latestBuildTool;
    }

    /**
     * Creates a the {@link BuildToolInfo} for the specificed build tools revision, if available.
     *
     * @param revision The build tools revision requested
     * @param progress {@link ProgressIndicator} for logging.
     * @return The {@link BuildToolInfo} corresponding to the specified build tools package, or
     *     {@code} null if that revision is not installed.
     */
    @Nullable
    public BuildToolInfo getBuildToolInfo(
            @NonNull Revision revision, @NonNull ProgressIndicator progress) {
        RepositoryPackages packages = getSdkManager(progress).getPackages();
        LocalPackage p = packages.getLocalPackages()
                .get(DetailsTypes.getBuildToolsPath(revision));

        if (p == null) {
            return null;
        }
        return BuildToolInfo.fromLocalPackage(p);
    }

    /**
     * Gets our {@link FileOp}. Useful so both the sdk handler and file op don't both have to be
     * injected everywhere.
     *
     * @deprecated
     */
    @NonNull
    public FileOp getFileOp() {
        return mFop;
    }

    /** Converts a {@code File} into a {@code Path} on the {@code FileSystem} used by this SDK. */
    @NonNull
    public Path toCompatiblePath(@NonNull File file) {
        Path localPath = mRepoManager.getLocalPath();
        if (localPath != null) {
            return localPath.getFileSystem().getPath(file.getPath());
        }
        return file.toPath();
    }

    /** Converts a {@code String} into a {@code Path} on the {@code FileSystem} used by this SDK. */
    @NonNull
    public Path toCompatiblePath(@NonNull String file) {
        Path localPath = mRepoManager.getLocalPath();
        if (localPath != null) {
            return localPath.getFileSystem().getPath(file);
        }
        return Paths.get(file);
    }
}
