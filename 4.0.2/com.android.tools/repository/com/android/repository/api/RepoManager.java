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

package com.android.repository.api;

import static com.android.repository.impl.meta.TypeDetails.GenericType;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.GenericFactory;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Primary interface for interacting with repository packages.
 *
 * To set up an {@code RepoManager}:
 * <ul>
 *     <li>
 *         Register the {@link SchemaModule}s used to parse the package.xml files and
 *         remote repositories used by this repo using {@link
 *         #registerSchemaModule(SchemaModule)}
 *     </li>
 *     <li>
 *         Set the path where the repo is installed locally using {@link #setLocalPath(File)}.
 *     </li>
 *     <li>
 *         If your local repo might contain packages created by a previous system, set a
 *         {@link FallbackLocalRepoLoader} that can recognize and convert those packages using
 *         {@link #setFallbackLocalRepoLoader(FallbackLocalRepoLoader)}.
 *     </li>
 *     <li>
 *         Add {@link RepositorySourceProvider}s to provide URLs for remotely-available packages.
 *     </li>
 *     <li>
 *         If some sources might be in a format used by a previous system, set a {@link
 *         FallbackRemoteRepoLoader} that can read and convert them.
 *     </li>
 * </ul>
 * <p>
 * To load the local and remote packages, use {@link #load(long, List, List, List, ProgressRunner,
 * Downloader, SettingsController, boolean)}
 * <br>
 * TODO: it would be nice if this could be redesigned such that load didn't need to be called
 * explicitly, or there was a better way to know if packages were or need to be loaded.
 * <p>
 * To use the loaded packages, get an {@link RepositoryPackages} object from {@link #getPackages()}.
 */
public abstract class RepoManager {

    /**
     * After loading the repository, this is the amount of time that must pass before we consider it
     * to be stale and need to be reloaded.
     */
    public static final long DEFAULT_EXPIRATION_PERIOD_MS = TimeUnit.DAYS.toMillis(1);

    /**
     * Pattern for name of the xsd file used in {@link #sCommonModule}.
     */
    private static final String COMMON_XSD_PATTERN = "repo-common-%02d.xsd";

    /**
     * Pattern for fully-qualified name of the {@code ObjectFactory} used in {@link
     * #sCommonModule}.
     */
    private static final String COMMON_OBJECT_FACTORY_PATTERN
            = "com.android.repository.impl.generated.v%d.ObjectFactory";

    /**
     * Pattern for name of the xsd file used in {@link #sCommonModule}.
     */
    private static final String GENERIC_XSD_PATTERN = "generic-%02d.xsd";

    /**
     * Pattern for fully-qualified name of the {@code ObjectFactory} used in {@link
     * #sCommonModule}.
     */
    private static final String GENERIC_OBJECT_FACTORY_PATTERN
            = "com.android.repository.impl.generated.generic.v%d.ObjectFactory";


    /**
     * The base {@link SchemaModule} that is created by {@code RepoManagerImpl} itself.
     */
    private static SchemaModule<CommonFactory> sCommonModule;

    static {
        try {
            sCommonModule = new SchemaModule<>(COMMON_OBJECT_FACTORY_PATTERN, COMMON_XSD_PATTERN,
                    RepoManager.class);
        } catch (Exception e) {
            // This should never happen unless there's something wrong with the common repo schema.
            assert false : "Failed to create SchemaModule: " + e;
        }
    }

    /**
     * The {@link SchemaModule} that contains an implementation of {@link GenericType}.
     */
    private static SchemaModule<GenericFactory> sGenericModule;

    static {
        try {
            sGenericModule = new SchemaModule<>(GENERIC_OBJECT_FACTORY_PATTERN, GENERIC_XSD_PATTERN,
                    RepoManager.class);
        } catch (Exception e) {
            // This should never happen unless there's something wrong with the generic repo schema.
            assert false : "Failed to create SchemaModule: " + e;
        }
    }

    /**
     * @param fop The {@link FileOp} to use for local filesystem operations. Probably {@link
     *            FileOpUtils#create()} unless part of a unit test.
     * @return A new {@code RepoManager}.
     */
    @NonNull
    public static RepoManager create(@NonNull FileOp fop) {
        return new RepoManagerImpl(fop);
    }

    /**
     * Register an {@link SchemaModule} that can be used when parsing XML for this repo.
     */
    public abstract void registerSchemaModule(@NonNull SchemaModule module);

    /**
     * Gets the currently-registered {@link SchemaModule}s. This probably shouldn't be used except
     * by code within the RepoManager or unit tests.
     */
    @NonNull
    public abstract List<SchemaModule<?>> getSchemaModules();

    /**
     * Gets the core {@link SchemaModule} created by the RepoManager itself. Contains the base
     * definition of repository, package, revision, etc.
     */
    @NonNull
    public static SchemaModule<CommonFactory> getCommonModule() {
        return sCommonModule;
    }

    /**
     * Gets the {@link SchemaModule} created by the RepoManager that includes the trivial generic
     * {@code typeDetails} type.
     */
    @NonNull
    public static SchemaModule<GenericFactory> getGenericModule() {
        return sGenericModule;
    }

    /**
     * Sets the path to the local repository root.
     */
    public abstract void setLocalPath(@Nullable File path);

    /**
     * Gets the path to the local repository root. This probably shouldn't be needed except by the
     * repository manager and unit tests.
     */
    @Nullable
    public abstract File getLocalPath();

    /**
     * Sets the {@link FallbackLocalRepoLoader} to use when scanning the local repository for
     * packages.
     */
    public abstract void setFallbackLocalRepoLoader(@Nullable FallbackLocalRepoLoader local);

    /**
     * Adds a {@link RepositorySourceProvider} from which to get {@link RepositorySource}s from
     * which to download lists of available repository packages.
     */
    public abstract void registerSourceProvider(@NonNull RepositorySourceProvider provider);

    /**
     * Gets the currently registered {@link RepositorySourceProvider}s. Should only be needed for
     * testing.
     */
    @NonNull
    @VisibleForTesting
    public abstract Set<RepositorySourceProvider> getSourceProviders();

    /**
     * Gets the actual {@link RepositorySource}s from the registered {@link
     * RepositorySourceProvider}s.
     *
     * Probably should only be needed by a repository UI.
     *
     * @param downloader   The {@link Downloader} to use for downloading source lists, if needed.
     * @param progress     A {@link ProgressIndicator} for source providers to use to show their
     *                     progress and for logging.
     * @param forceRefresh Individual {@link RepositorySourceProvider}s may cache their results. If
     *                     {@code forceRefresh} is true, specifies that they should reload rather
     *                     than returning cached results.
     * @return The {@link RepositorySource}s obtained from the providers.
     */
    public abstract Set<RepositorySource> getSources(@Nullable Downloader downloader,
            @NonNull ProgressIndicator progress, boolean forceRefresh);


    /**
     * Sets the {@link FallbackRemoteRepoLoader} to try when we encounter a remote xml file that the
     * RepoManger can't read.
     */
    public abstract void setFallbackRemoteRepoLoader(@Nullable FallbackRemoteRepoLoader remote);

    /**
     * Load the local and remote repositories.
     *
     * In callbacks, be careful of invoking tasks synchronously on other threads (e.g. the swing ui
     * thread), since they might also be used by the {@link ProgressRunner) passed in.
     *
     * @param cacheExpirationMs How long must have passed since the last load for us to reload.
     *                          Specify {@code 0} to reload immediately.
     * @param onLocalComplete   When loading, the local repo load happens first, and should be
     *                          relatively fast. When complete, the {@code onLocalComplete} {@link
     *                          RepoLoadedCallback}s are run. Will be called with a {@link
     *                          RepositoryPackages} that contains only the local packages.
     * @param onSuccess         Callbacks that are run when the entire load (local and remote) has
     *                          completed successfully. Called with an {@link RepositoryPackages}
     *                          containing both the local and remote packages.
     * @param onError           Callbacks that are run when there's an error at some point during
     *                          the load.
     * @param runner            The {@link ProgressRunner} to use for any tasks started during the
     *                          load, including running the callbacks.
     * @param downloader        The {@link Downloader} to use for downloading remote files,
     *                          including any remote list of repo sources and the remote
     *                          repositories themselves.
     * @param settings          The settings to use during the load, including for example proxy
     *                          settings used when fetching remote files.
     * @param sync              If true, load synchronously. If false, load asynchronously (this
     *                          method should return quickly, and the {@code onSuccess} callbacks
     *                          can be used to process the completed results).
     *
     * TODO: throw exception if cancelled
     */
    public abstract void load(long cacheExpirationMs,
            @Nullable List<RepoLoadedCallback> onLocalComplete,
            @Nullable List<RepoLoadedCallback> onSuccess,
            @Nullable List<Runnable> onError,
            @NonNull ProgressRunner runner,
            @Nullable Downloader downloader,
            @Nullable SettingsController settings,
            boolean sync);

    /**
     * Load the local and remote repositories synchronously.
     *
     * @param cacheExpirationMs How long must have passed since the last load for us to reload.
     *                          Specify {@code 0} to reload immediately.
     * @param progress          The {@link ProgressIndicator} to use for showing progress and
     *                          logging.
     * @param downloader        The {@link Downloader} to use for downloading remote files,
     *                          including any remote list of repo sources and the remote
     *                          repositories themselves.
     * @param settings          The settings to use during the load, including for example proxy
     *                          settings used when fetching remote files.
     * @return {@code true} if the load was successful (including if cached results were returned),
     * false otherwise.
     */
    public final boolean loadSynchronously(long cacheExpirationMs,
            @NonNull final ProgressIndicator progress,
            @Nullable Downloader downloader, @Nullable SettingsController settings) {
        final AtomicBoolean result = new AtomicBoolean(true);
        load(cacheExpirationMs, null, null, ImmutableList.of(
                () -> result.set(false)), new DummyProgressRunner(progress),
                downloader, settings, true);

        return result.get();
    }

    /**
     * Causes cached results to be considered expired. The next time {@link #load(long, List, List,
     * List, ProgressRunner, Downloader, SettingsController, boolean)} is called, a complete load
     * will be done.
     */
    public abstract void markInvalid();

    /**
     * Causes the cached results of the local repositories to be considered expired. The next time
     * {@link #load(long, List, List, List, ProgressRunner, Downloader, SettingsController,
     * boolean)} is called, the load will be done only for the local repositories, the remotes being
     * loaded from the cache if possible.
     */
    public abstract void markLocalCacheInvalid();

    /**
     * Check to see if there have been any changes to the local repo since the last load. This
     * includes scanning the local repo for packages, but does not involve any reading or parsing of
     * package metadata files. If there have been any changes, or if the cache is older than the
     * default timeout, the local packages will be reloaded.
     *
     * @return {@code true} if the load was successful, {@code false} otherwise}.
     */
    public abstract boolean reloadLocalIfNeeded(@NonNull ProgressIndicator progress);

    /**
     * Gets the currently-loaded {@link RepositoryPackages}.
     */
    @NonNull
    public abstract RepositoryPackages getPackages();

    /**
     * Gets an {@link LSResourceResolver} that can find the XSDs for all versions of the
     * currently-registered {@link SchemaModule}s by namespace. Returns null if there is an error.
     */
    @Nullable
    public abstract LSResourceResolver getResourceResolver(@NonNull ProgressIndicator progress);

    /**
     * Registers a listener that will be called whenever the local packages are reloaded and have
     * changed. The {@link RepositoryPackages} instance passed to the callback will contain only the
     * local packages.
     */
    public abstract void registerLocalChangeListener(@NonNull RepoLoadedCallback listener);

    /**
     * Register a listener that will be called whenever the remote packages are reloaded and have
     * changed. The {@link RepositoryPackages} instance will contain the remote and local packages.
     */
    public abstract void registerRemoteChangeListener(@NonNull RepoLoadedCallback listener);

    /**
     * Record that the given package is in the process of being installed by the given installer.
     */
    public abstract void installBeginning(@NonNull RepoPackage repoPackage,
            @NonNull PackageOperation installer);

    /**
     * Record that the given package is no longer in the process of being installed (that is,
     * install completed either successfully or unsuccessfully).
     */
    public abstract void installEnded(@NonNull RepoPackage repoPackage);

    /**
     * Gets the previously-registered installer that is currently installing the given package, or
     * {@code null} if there is none.
     */
    @Nullable
    public abstract PackageOperation getInProgressInstallOperation(
            @NonNull RepoPackage remotePackage);

    /**
     * Callback for when repository load is completed/partially completed.
     */
    public interface RepoLoadedCallback {

        /**
         * @param packages The packages that have been loaded so far. When this callback is used in
         *                 the {@code onLocalComplete} argument to {@link #load(long, List, List,
         *                 List, ProgressRunner, Downloader, SettingsController, boolean)} {@code
         *                 packages} will only include local packages.
         */
        void doRun(@NonNull RepositoryPackages packages);
    }

    protected static class DummyProgressRunner implements ProgressRunner {

        private final ProgressIndicator mProgress;

        public DummyProgressRunner(@NonNull ProgressIndicator progress) {
            mProgress = progress;
        }

        @Override
        public void runAsyncWithProgress(@NonNull ProgressRunnable r) {
            r.run(mProgress, this);
        }

        @Override
        public void runSyncWithProgress(@NonNull ProgressRunnable r) {
            r.run(mProgress, this);
        }

        @Override
        public void runSyncWithoutProgress(@NonNull Runnable r) {
            r.run();
        }
    }
}
