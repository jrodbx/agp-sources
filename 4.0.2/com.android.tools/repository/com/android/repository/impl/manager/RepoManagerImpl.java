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

package com.android.repository.impl.manager;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.Downloader;
import com.android.repository.api.FallbackLocalRepoLoader;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressRunner;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.io.FileOp;
import com.android.repository.io.impl.FileOpImpl;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Main implementation of {@link RepoManager}. Loads local and remote {@link RepoPackage}s
 * synchronously and asynchronously into a {@link RepositoryPackages} instance from the given local
 * path and from the registered {@link RepositorySourceProvider}s, using the registered {@link
 * SchemaModule}s.
 */
public class RepoManagerImpl extends RepoManager {

    /**
     * The registered {@link SchemaModule}s.
     */
    private final List<SchemaModule<?>> mModules = new ArrayList<>();

    /**
     * The {@link FallbackLocalRepoLoader} to use when loading local packages.
     */
    @Nullable
    private FallbackLocalRepoLoader mFallbackLocalRepoLoader;

    /**
     * The path under which to look for installed packages.
     */
    @Nullable
    private File mLocalPath;

    /**
     * The {@link FallbackRemoteRepoLoader} to use if the normal {@link RemoteRepoLoaderImpl} can't
     * understand a downloaded repository xml file.
     */
    @Nullable
    private FallbackRemoteRepoLoader mFallbackRemoteRepoLoader;

    /**
     * The {@link RepositorySourceProvider}s from which to get {@link RepositorySource}s to load
     * from.
     */
    private Set<RepositorySourceProvider> mSourceProviders = Sets.newHashSet();

    /**
     * The loaded packages.
     */
    private RepositoryPackages mPackages = new RepositoryPackages();

    /**
     * When we last loaded the remote packages.
     */
    private long mLastRemoteRefreshMs;

    /**
     * When we last loaded the local packages.
     */
    private long mLastLocalRefreshMs;

    /**
     * The task used to load packages. If non-null, a load is currently in progress.
     */
    private LoadTask mTask;

    /**
     * The time at which our current {@link LoadTask} was created.
     */
    private long mTaskCreateTime;

    /**
     * Lock used when setting {@link #mTask}.
     */
    private final Object mTaskLock = new Object();

    /**
     * {@link FileOp} to be used for local file operations. Should be {@link FileOpImpl} for normal
     * operation.
     */
    private final FileOp mFop;

    /**
     * Listeners that will be called when the known local packages change.
     */
    private final List<RepoLoadedCallback> mLocalListeners = Lists.newArrayList();

    /**
     * Listeners that will be called when the known remote packages change.
     */
    private final List<RepoLoadedCallback> mRemoteListeners = Lists.newArrayList();

    /**
     * How long we should let a load task run before assuming that it's dead.
     */
    private static final long TASK_TIMEOUT = TimeUnit.MINUTES.toMillis(3);

    /**
     * Install/uninstall operations that are currently running.
     */
    private final Map<RepoPackage, PackageOperation> mInProgressInstalls = Maps.newHashMap();

    /**
     * A facility for creating {@link LocalRepoLoader}s. By default, {@link
     * LocalRepoLoaderFactoryImpl}.
     */
    private final LocalRepoLoaderFactory mLocalRepoLoaderFactory;

    /**
     * A facility for creating {@link RemoteRepoLoader}s. By default, {@link
     * RemoteRepoLoaderFactoryImpl}.
     */
    private final RemoteRepoLoaderFactory mRemoteRepoLoaderFactory;

    /**
     * Create a new {@code RepoManagerImpl}. Before anything can be loaded, at least a local path
     * and/or at least one {@link RepositorySourceProvider} must be set.
     *
     * @param fop {@link FileOp} to use for local file operations. Should only be null if you're
     *            never planning to load a local repo using this {@code RepoManagerImpl}.
     */
    public RepoManagerImpl(@Nullable FileOp fop) {
        this(fop, null, null);
    }

    /**
     * @param localFactory  If {@code null}, {@link LocalRepoLoaderFactoryImpl} will be used. Can be
     *                      non-null for testing.
     * @param remoteFactory If {@code null}, {@link RemoteRepoLoaderFactoryImpl} will be used. Can
     *                      be non-null for testing.
     * @see #RepoManagerImpl(FileOp)
     */
    @VisibleForTesting
    public RepoManagerImpl(@Nullable FileOp fop, @Nullable LocalRepoLoaderFactory localFactory,
            @Nullable RemoteRepoLoaderFactory remoteFactory) {
        mFop = fop;
        registerSchemaModule(getCommonModule());
        registerSchemaModule(getGenericModule());
        mLocalRepoLoaderFactory = localFactory == null ? new LocalRepoLoaderFactoryImpl()
                : localFactory;
        mRemoteRepoLoaderFactory = remoteFactory == null ? new RemoteRepoLoaderFactoryImpl()
                : remoteFactory;
    }

    @Nullable
    @Override
    public File getLocalPath() {
        return mLocalPath;
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, ProgressRunner, Downloader, SettingsController,
     * boolean)} is called.
     */
    @Override
    public void setFallbackLocalRepoLoader(@Nullable FallbackLocalRepoLoader fallback) {
        mFallbackLocalRepoLoader = fallback;
        markInvalid();
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, ProgressRunner, Downloader, SettingsController,
     * boolean)} is called.
     */
    @Override
    public void setFallbackRemoteRepoLoader(@Nullable FallbackRemoteRepoLoader remote) {
        mFallbackRemoteRepoLoader = remote;
        markInvalid();
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, ProgressRunner, Downloader, SettingsController,
     * boolean)} is called.
     */
    @Override
    public void setLocalPath(@Nullable File path) {
        mLocalPath = path;
        markInvalid();
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, ProgressRunner, Downloader, SettingsController,
     * boolean)} is called.
     */
    @Override
    public void registerSourceProvider(@NonNull RepositorySourceProvider provider) {
        mSourceProviders.add(provider);
        markInvalid();
    }

    @VisibleForTesting
    @Override
    @NonNull
    public Set<RepositorySourceProvider> getSourceProviders() {
        return mSourceProviders;
    }

    @Override
    @NonNull
    public Set<RepositorySource> getSources(@Nullable Downloader downloader,
            @NonNull ProgressIndicator progress, boolean forceRefresh) {
        Set<RepositorySource> result = Sets.newHashSet();
        for (RepositorySourceProvider provider : mSourceProviders) {
            result.addAll(provider.getSources(downloader, progress, forceRefresh));
        }
        return result;
    }

    @Override
    @NonNull
    public List<SchemaModule<?>> getSchemaModules() {
        return mModules;
    }

    /**
     * {@inheritDoc} This calls {@link  #markInvalid()}, so a complete load will occur the next time
     * {@link #load(long, List, List, List, ProgressRunner, Downloader, SettingsController,
     * boolean)} is called.
     */
    @Override
    public void registerSchemaModule(@NonNull SchemaModule module) {
        mModules.add(module);
        markInvalid();
    }

    @Override
    public void markInvalid() {
        mLastRemoteRefreshMs = 0;
        mLastLocalRefreshMs = 0;
    }

    @Override
    public void markLocalCacheInvalid() {
        mLastLocalRefreshMs = 0;
    }

    @Override
    @Nullable
    public LSResourceResolver getResourceResolver(@NonNull ProgressIndicator progress) {
        Set<SchemaModule<?>> allModules = ImmutableSet.<SchemaModule<?>>builder().addAll(
                getSchemaModules()).add(
                getCommonModule()).add(
                getGenericModule()).build();
        return SchemaModuleUtil.createResourceResolver(allModules, progress);
    }

    @Override
    @NonNull
    public RepositoryPackages getPackages() {
        return mPackages;
    }

    // TODO: fix up invalidation. It's annoying that you have to manually reload.
    // TODO: Maybe: when you load, instead of load as now, you get back a loader, which knows how
    // TODO: to reload with same settings,
    // TODO: and contains current valid or invalid packages as they are cached here.
    @Override
    public void load(long cacheExpirationMs,
            @Nullable List<RepoLoadedCallback> onLocalComplete,
            @Nullable List<RepoLoadedCallback> onSuccess,
            @Nullable List<Runnable> onError,
            @NonNull ProgressRunner runner,
            @Nullable Downloader downloader,
            @Nullable SettingsController settings,
            boolean sync) {
        if (onLocalComplete == null) {
            onLocalComplete = ImmutableList.of();
        }
        if (onSuccess == null) {
            onSuccess = ImmutableList.of();
        }
        if (onError == null) {
            onError = ImmutableList.of();
        }

        // So we can block until complete in the synchronous case.
        final Semaphore completed = new Semaphore(1);
        try {
            completed.acquire();
        } catch (InterruptedException e) {
            // shouldn't happen.
        }

        // If we created the currently running task, we need to clean it up at the end.
        boolean createdTask = false;

        synchronized (mTaskLock) {
            long taskTimeout = System.currentTimeMillis() - TASK_TIMEOUT;
            if (mTask != null && mTaskCreateTime > taskTimeout) {
                // If there's a task running already, just add our callbacks to it.
                mTask.addCallbacks(onLocalComplete, onSuccess, onError, runner);
                if (sync) {
                    // If we're running synchronously, release the semaphore after run complete.
                    // Use a dummy runner to ensure we don't try to run on a different thread and
                    // then block trying to release the semaphore.
                    mTask.addCallbacks(ImmutableList.of(),
                            ImmutableList.of(packages -> completed.release()),
                            ImmutableList.of(completed::release),
                            new DummyProgressRunner(new ConsoleProgressIndicator()));
                }
            } else {
                // Otherwise, create a new task.
                mTask = new LoadTask(cacheExpirationMs, onLocalComplete, onSuccess, onError,
                        downloader, settings);
                mTaskCreateTime = System.currentTimeMillis();
                createdTask = true;
            }
        }

        if (createdTask) {
            // If we created a task, run it.
            if (sync) {
                runner.runSyncWithProgress(mTask);
            } else {
                runner.runAsyncWithProgress(mTask);
            }
        } else if (sync) {
            // Otherwise wait for artifactsthe semaphore to be released by the callback if we're
            // running synchronously.
            runner.runSyncWithProgress(
                    (indicator, runner2) -> {
                        try {
                            completed.acquire();
                        } catch (InterruptedException e) {
                            /* shouldn't happen*/
                        }
                    });
        }

    }

    @Override
    public boolean reloadLocalIfNeeded(@NonNull ProgressIndicator progress) {
        // TODO: there should be a nice interface whereby we can do this check without creating a
        // new LocalRepoLoader instance.
        LocalRepoLoader local = mLocalRepoLoaderFactory.createLocalRepoLoader();
        if (local == null) {
            return false;
        }

        if (local.needsUpdate(mLastLocalRefreshMs, true)) {
            mLastLocalRefreshMs = 0;
        }
        return loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, null, null);
    }

    @Override
    public void registerLocalChangeListener(@NonNull RepoLoadedCallback listener) {
        mLocalListeners.add(listener);
    }

    @Override
    public void registerRemoteChangeListener(@NonNull RepoLoadedCallback listener) {
        mRemoteListeners.add(listener);
    }

    @Override
    public void installBeginning(@NonNull RepoPackage remotePackage,
            @NonNull PackageOperation installer) {
        mInProgressInstalls.put(remotePackage, installer);
    }

    @Override
    public void installEnded(@NonNull RepoPackage remotePackage) {
        mInProgressInstalls.remove(remotePackage);
    }

    @Nullable
    @Override
    public PackageOperation getInProgressInstallOperation(@NonNull RepoPackage remotePackage) {
        return mInProgressInstalls.get(remotePackage);
    }

    /**
     * A task to load the local and remote repos.
     */
    private class LoadTask implements ProgressRunner.ProgressRunnable {

        /**
         * If callbacks get added to an already-running task, they might have a different {@link
         * ProgressRunner} than the one used to run the task. Here we keep the callback along with
         * the runner so the callback can be invoked correctly.
         */
        private class Callback {

            private RepoLoadedCallback mCallback;

            private ProgressRunner mRunner;

            public Callback(@NonNull RepoLoadedCallback callback, @Nullable ProgressRunner runner) {
                mCallback = callback;
                mRunner = runner;
            }

            public ProgressRunner getRunner(ProgressRunner defaultRunner) {
                return mRunner == null ? defaultRunner : mRunner;
            }

            public RepoLoadedCallback getCallback() {
                return mCallback;
            }
        }

        private final List<Callback> mOnSuccesses = Lists.newArrayList();

        private final List<Runnable> mOnErrors = Lists.newArrayList();

        // Must be synchronized since new elements can be added while the task is still in progress
        // (that is, before mTask is set to null).
        private final Queue<Callback> mOnLocalCompletes = new ConcurrentLinkedQueue<>();

        private final Downloader mDownloader;

        private final SettingsController mSettings;

        private final long mCacheExpirationMs;

        public LoadTask(long cacheExpirationMs,
                @NonNull List<RepoLoadedCallback> onLocalComplete,
                @NonNull List<RepoLoadedCallback> onSuccess,
                @NonNull List<Runnable> onError,
                @Nullable Downloader downloader,
                @Nullable SettingsController settings) {
            addCallbacks(onLocalComplete, onSuccess, onError, null);
            mDownloader = downloader;
            mSettings = settings;
            mCacheExpirationMs = cacheExpirationMs;
        }

        /**
         * Add callbacks to this task (if e.g. {@link #load(long, List, List, List, ProgressRunner,
         * Downloader, SettingsController, boolean)} is called again while a task is already
         * running.
         */
        public void addCallbacks(@NonNull List<RepoLoadedCallback> onLocalComplete,
                @NonNull List<RepoLoadedCallback> onSuccess,
                @NonNull List<Runnable> onError,
                @Nullable ProgressRunner runner) {
            for (RepoLoadedCallback local : onLocalComplete) {
                mOnLocalCompletes.add(new Callback(local, runner));
            }
            for (RepoLoadedCallback success : onSuccess) {
                mOnSuccesses.add(new Callback(success, runner));
            }
            mOnErrors.addAll(onError);
        }

        /**
         * Do the actual load.
         *
         * @param indicator {@link ProgressIndicator} for logging and showing actual progress
         * @param runner    {@link ProgressRunner} for running asynchronous tasks and callbacks.
         */
        @Override
        public void run(@NonNull ProgressIndicator indicator, @NonNull ProgressRunner runner) {
            boolean success = false;
            boolean localSuccess = false;
            boolean wasIndeterminate = indicator.isIndeterminate();
            indicator.setIndeterminate(false);
            try {
                LocalRepoLoader local = mLocalRepoLoaderFactory.createLocalRepoLoader();
                if (local != null &&
                        (mLastLocalRefreshMs + mCacheExpirationMs <= System.currentTimeMillis() ||
                                local.needsUpdate(mLastLocalRefreshMs, false))) {
                    if (mFallbackLocalRepoLoader != null) {
                        mFallbackLocalRepoLoader.refresh();
                    }
                    indicator.setText("Loading local repository...");
                    Map<String, LocalPackage> newLocals = local.getPackages(indicator);
                    boolean fireListeners = !newLocals.equals(mPackages.getLocalPackages());
                    mPackages.setLocalPkgInfos(newLocals.values());
                    mLastLocalRefreshMs = System.currentTimeMillis();
                    if (fireListeners) {
                        for (RepoLoadedCallback listener : mLocalListeners) {
                            listener.doRun(mPackages);
                        }
                    }
                }
                indicator.setFraction(0.25);
                if (indicator.isCanceled()) {
                    return;
                }
                // Set to true even if we didn't reload locals: the no-op is complete.
                localSuccess = true;

                // Access using the synchronized queue interface so we don't have to worry about
                // more elements getting added while we're in the middle of processing.
                Callback onLocalComplete = mOnLocalCompletes.poll();
                while (onLocalComplete != null) {
                    onLocalComplete.getRunner(runner).runSyncWithoutProgress(
                            new CallbackRunnable(onLocalComplete.mCallback, mPackages));
                    onLocalComplete = mOnLocalCompletes.poll();
                }
                indicator.setText("Fetch remote repository...");
                indicator.setSecondaryText("");

                if (!mSourceProviders.isEmpty() && mDownloader != null &&
                        mLastRemoteRefreshMs + mCacheExpirationMs <= System.currentTimeMillis()) {
                    RemoteRepoLoader remoteLoader = mRemoteRepoLoaderFactory
                            .createRemoteRepoLoader(indicator);
                    Map<String, RemotePackage> remotes =
                            remoteLoader.fetchPackages(
                                    indicator.createSubProgress(.75), mDownloader, mSettings);
                    indicator.setText("Computing updates...");
                    indicator.setFraction(0.75);
                    boolean fireListeners = !remotes.equals(mPackages.getRemotePackages());
                    mPackages.setRemotePkgInfos(remotes.values());
                    mLastRemoteRefreshMs = System.currentTimeMillis();
                    if (fireListeners) {
                        for (RepoLoadedCallback callback : mRemoteListeners) {
                            callback.doRun(mPackages);
                        }
                    }
                }

                if (indicator.isCanceled()) {
                    return;
                }
                indicator.setSecondaryText("");
                indicator.setFraction(1.0);

                if (indicator.isCanceled()) {
                    return;
                }
                success = true;
            } finally {
                indicator.setIndeterminate(wasIndeterminate);
                synchronized (mTaskLock) {
                    // The processing of the task is now complete.
                    // To ensure that no more callbacks are added, and to allow another task to be
                    // kicked off when needed, set mTask to null.
                    mTask = null;
                }
                // Note: in theory it's possible that another task could now be started and modify
                // mPackages before the callbacks are run below, since we're out of the synchronized
                // block. Since RepositoryPackages itself is synchronized, though, that should be
                // ok.

                // in case some were added by another call in the interim.
                if (localSuccess) {
                    for (Callback onLocalComplete : mOnLocalCompletes) {
                        onLocalComplete.getRunner(runner).runSyncWithoutProgress(
                                new CallbackRunnable(onLocalComplete.getCallback(), mPackages));
                    }
                }
                if (success) {
                    for (Callback onSuccess : mOnSuccesses) {
                        onSuccess.getRunner(runner).runSyncWithoutProgress(
                                new CallbackRunnable(onSuccess.getCallback(), mPackages));
                    }
                } else {
                    for (final Runnable onError : mOnErrors) {
                        onError.run();
                    }
                }
            }
        }
    }

    interface LocalRepoLoaderFactory {

        @Nullable
        LocalRepoLoader createLocalRepoLoader();
    }

    @VisibleForTesting
    public interface RemoteRepoLoaderFactory {

        @NonNull
        RemoteRepoLoader createRemoteRepoLoader(@NonNull ProgressIndicator progress);
    }

    private class LocalRepoLoaderFactoryImpl implements LocalRepoLoaderFactory {

        /**
         * @return A new {@link LocalRepoLoaderImpl} with our settings, or {@code null} if we don't
         * have a local path set.
         */
        @Override
        @Nullable
        public LocalRepoLoader createLocalRepoLoader() {
            if (mLocalPath != null && mFop != null) {
                return new LocalRepoLoaderImpl(mLocalPath, RepoManagerImpl.this,
                        mFallbackLocalRepoLoader, mFop);
            }
            return null;
        }
    }

    private class RemoteRepoLoaderFactoryImpl implements RemoteRepoLoaderFactory {

        @Override
        @NonNull
        public RemoteRepoLoader createRemoteRepoLoader(@NonNull ProgressIndicator progress) {
            return new RemoteRepoLoaderImpl(mSourceProviders, mFallbackRemoteRepoLoader);
        }
    }

    /**
     * A {@link Runnable} that wraps a {@link RepoLoadedCallback} and calls it with the appropriate
     * args.
     */
    private static class CallbackRunnable implements Runnable {

        RepoLoadedCallback mCallback;

        RepositoryPackages mPackages;

        public CallbackRunnable(@NonNull RepoLoadedCallback callback,
                @NonNull RepositoryPackages packages) {
            mCallback = callback;
            mPackages = packages;
        }

        @Override
        public void run() {
            mCallback.doRun(mPackages);
        }
    }
}
