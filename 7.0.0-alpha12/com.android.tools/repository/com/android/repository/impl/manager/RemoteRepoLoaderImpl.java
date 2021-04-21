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
import com.android.repository.api.Channel;
import com.android.repository.api.DelegatingProgressIndicator;
import com.android.repository.api.Downloader;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.Repository;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.JAXBException;

/**
 * Utility class that loads {@link Repository}s from {@link RepositorySource}s.
 */
public class RemoteRepoLoaderImpl implements RemoteRepoLoader {

    /**
     * Timeout to wait for the packages to be fetched. Each time the timeout is reached, a warning
     * will be logged but waiting for the thread pool termination will continue. It is expected that
     * network operations will eventually time out on their own and/or throw exception in the worst
     * case, leading to the thread pool termination anyway.
     */
    private static final int FETCH_PACKAGES_WAITING_ITERATION_SECONDS = 10;

    private static final String FETCH_PACKAGES_WAITING_MESSAGE =
            "Still waiting for package manifests to be fetched remotely.";

    /** {@link FallbackRemoteRepoLoader} to use if we get an XML file we can't parse. */
    private final FallbackRemoteRepoLoader mFallback;

    /**
     * The {@link RepositorySourceProvider}s to load from.
     */
    private final Collection<RepositorySourceProvider> mSourceProviders;

    /**
     * Constructor
     *
     * @param sources          The {@link RepositorySourceProvider}s to get the {@link
     *                         RepositorySource}s to load from.
     * @param fallback         The {@link FallbackRemoteRepoLoader} to use if we can't parse an XML
     *                         file.
     */
    public RemoteRepoLoaderImpl(@NonNull Collection<RepositorySourceProvider> sources,
            @Nullable FallbackRemoteRepoLoader fallback) {
        mSourceProviders = sources;
        mFallback = fallback;
    }

    @Override
    @NonNull
    public Map<String, RemotePackage> fetchPackages(
            @NonNull ProgressIndicator progress,
            @NonNull Downloader downloader,
            @Nullable SettingsController settings) {
        Map<RepositorySource, Collection<? extends RemotePackage>> parsedPackages = new HashMap<>();
        Map<RepositorySource, Collection<? extends RemotePackage>> legacyParsedPackages =
                new HashMap<>();
        List<RepositorySource> sources = Lists.newArrayList();

        double progressMax = 0;
        for (RepositorySourceProvider provider : mSourceProviders) {
            progressMax += 0.1 / mSourceProviders.size();
            sources.addAll(
                    provider.getSources(
                            downloader, progress.createSubProgress(progressMax), false));
        }

        // In the context below we are not concerned that much about the precise progress reporting, because
        // the manifests downloading is about a dozen tiny files, so it's expected to be relatively fast
        // and OTOH a lock-based thread-safe ProgressIndicator implementation would even likely be a bottleneck
        // (presumably, probability of that is linearly proportional to how often the progress gets reported
        // by the downloads). So we are going to report precise progress only when we manage to process one
        // download result, but we won't report the partial progress of every single download. For the latter,
        // progress reporting will only be limited to logging in case there are any issues (and logging is deemed
        // thread-safe).
        boolean wasIndeterminate = progress.isIndeterminate();
        progress.setIndeterminate(true);
        LoggingOnlyProgressIndicator loggingOnlyProgress =
                new LoggingOnlyProgressIndicator(progress);
        // This is a typical producer-consumer context - first spawn all the download threads, and then process
        // the download results one by one in this thread, waiting where necessary. This ensures that starvation
        // on both sides is minimized (as opposed to e.g. first waiting for _all_ downloads to complete, and then
        // processing them sequentially).
        ArrayBlockingQueue<Map.Entry<RepositorySource, InputStream>> downloadedRepoManifests =
                new ArrayBlockingQueue<>(sources.size());
        ExecutorService sourceThreadPool = Executors.newCachedThreadPool();
        int threadsSubmitted = 0, resultsReceived = 0;
        try {
            for (RepositorySource source : sources) {
                if (!source.isEnabled()) {
                    continue;
                }
                ++threadsSubmitted;
                sourceThreadPool.submit(
                        () -> {
                            String errorMessage = null;
                            Throwable error = null;
                            try {
                                InputStream repoStream =
                                        downloader.downloadAndStream(
                                                new URL(source.getUrl()), loggingOnlyProgress);
                                downloadedRepoManifests.put(
                                        new AbstractMap.SimpleImmutableEntry<>(source, repoStream));
                            } catch (MalformedURLException e) {
                                errorMessage = "Malformed URL";
                                error = e;
                            } catch (IOException e) {
                                errorMessage = "IO exception while downloading manifest";
                                error = e;
                            } catch (InterruptedException e) {
                                errorMessage =
                                        "Thread interrupted while enqueuing downloaded manifest";
                                error = e;
                            }
                            if (errorMessage != null) {
                                source.setFetchError(errorMessage);
                                progress.logWarning(errorMessage, error);
                            }
                        });
            }

            // Indicate we're not going to add any more tasks for processing.
            sourceThreadPool.shutdown();
            // Collect & process the results, blocking where necessary & checking whether there is anything to wait for
            // before blocking again.
            progress.setIndeterminate(false);
            double progressIncrement = 0.9 / (sources.size() * 2);
            while ((!sourceThreadPool.isTerminated() && (resultsReceived < threadsSubmitted))
                    || !downloadedRepoManifests.isEmpty()) {
                Map.Entry<RepositorySource, InputStream> repoResult = null;
                try {
                    for (int waitedSeconds = 0;
                            waitedSeconds < FETCH_PACKAGES_WAITING_ITERATION_SECONDS;
                            ++waitedSeconds) {
                        // Check sourceThreadPool every second to detect early termination.
                        repoResult = downloadedRepoManifests.poll(1, TimeUnit.SECONDS);
                        if (repoResult != null || sourceThreadPool.isTerminated()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    // ignored
                }

                if (repoResult == null) {
                    // Timeout has elapsed.
                    progress.logWarning(FETCH_PACKAGES_WAITING_MESSAGE);
                } else {
                    // We have got a download result - process it while other downloaders are working.
                    ++resultsReceived;
                    progressMax += progressIncrement;
                    RepositorySource source = repoResult.getKey();
                    InputStream repoStream = repoResult.getValue();
                    parseSource(
                            source,
                            repoStream,
                            downloader,
                            settings,
                            parsedPackages,
                            legacyParsedPackages,
                            progress,
                            progressMax);
                    progressMax += progressIncrement;
                    progress.setFraction(progressMax);
                }
            }

        } finally {
            shutdownAndJoin(sourceThreadPool, progress);
            progress.setIndeterminate(wasIndeterminate);
        }

        Map<String, RemotePackage> result = new HashMap<>();

        for (RepositorySource source : sources) {
            Collection<? extends RemotePackage> regularPackages = parsedPackages.get(source);
            if (regularPackages != null) {
                mergePackages(regularPackages, source, settings, result);
            }
        }
        for (RepositorySource source : sources) {
            // Legacy after since they are lower priority
            Collection<? extends RemotePackage> legacyPackages = legacyParsedPackages.get(source);
            if (legacyPackages != null) {
                mergePackages(legacyPackages, source, settings, result);
            }
        }
        return result;
    }

    private void parseSource(
            @NonNull RepositorySource source,
            @NonNull InputStream repoStream,
            @NonNull Downloader downloader,
            @Nullable SettingsController settings,
            @NonNull Map<RepositorySource, Collection<? extends RemotePackage>> result,
            @NonNull Map<RepositorySource, Collection<? extends RemotePackage>> legacyResult,
            @NonNull ProgressIndicator progress,
            double progressMax) {
        final List<String> errors = Lists.newArrayList();

        // Don't show the errors, in case the fallback loader can read it. But keep
        // track of them to show later in case not.
        ProgressIndicator unmarshalProgress =
                new ProgressIndicatorAdapter() {
                    @Override
                    public void logWarning(@NonNull String s, Throwable e) {
                        errors.add(s);
                        if (e != null) {
                            errors.add(e.toString());
                        }
                    }

                    @Override
                    public void logError(@NonNull String s, Throwable e) {
                        errors.add(s);
                        if (e != null) {
                            errors.add(e.toString());
                        }
                    }
                };

        Repository repo = null;
        try {
            repo =
                    (Repository)
                            SchemaModuleUtil.unmarshal(
                                    repoStream,
                                    source.getPermittedModules(),
                                    true,
                                    unmarshalProgress);
        } catch (JAXBException e) {
            errors.add(e.toString());
        }

        Collection<? extends RemotePackage> parsedPackages = null;
        boolean legacy = false;
        if (repo != null) {
            parsedPackages = repo.getRemotePackage();
            progress.setFraction(progressMax);
        } else if (mFallback != null) {
            // TODO: don't require downloading again
            parsedPackages =
                    mFallback.parseLegacyXml(
                            source, downloader, settings, progress.createSubProgress(progressMax));
            legacy = true;
        }

        if (parsedPackages != null && !parsedPackages.isEmpty()) {
            (legacy ? legacyResult : result).put(source, parsedPackages);
        } else {
            progress.logWarning("Errors during XML parse:");
            for (String error : errors) {
                progress.logWarning(error);
            }
            //noinspection VariableNotUsedInsideIf
            if (mFallback != null) {
                progress.logWarning("Additionally, the fallback loader failed to parse the XML.");
            }
            source.setFetchError(errors.isEmpty() ? "unknown error" : errors.get(0));
        }
    }

    private void mergePackages(
            @NonNull Collection<? extends RemotePackage> packagesFromSource,
            @NonNull RepositorySource source,
            @Nullable SettingsController settings,
            @NonNull Map<String, RemotePackage> result) {
        for (RemotePackage pkg : packagesFromSource) {
            RemotePackage existing = result.get(pkg.getPath());
            if (existing != null) {
                int compare = existing.getVersion().compareTo(pkg.getVersion());
                if (compare > 0) {
                    // If there are multiple versions of the same package available,
                    // pick the latest.
                    continue;
                } else if (compare == 0) {
                    // If there's a file:// version (for debugging) and an http:// version, use the
                    // file:// version.
                    try {
                        URL newUrl = new URL(source.getUrl());
                        String newProtocol = newUrl.getProtocol();
                        if (!newProtocol.equals("file")) {
                            // If the existing package is local, use it.
                            continue;
                        }
                    } catch (MalformedURLException ignore) {
                        // If it's not a valid url, don't prioritize it.
                        continue;
                    }
                }
            }
            Channel settingsChannel =
                    settings == null || settings.getChannel() == null
                            ? pkg.createFactory().createChannelType(Channel.DEFAULT_ID)
                            : settings.getChannel();

            if (pkg.getArchive() != null && pkg.getChannel().compareTo(settingsChannel) <= 0) {
                pkg.setSource(source);
                result.put(pkg.getPath(), pkg);
            }
            source.setFetchError(null);
        }
    }

    private static void shutdownAndJoin(
            @NonNull ExecutorService threadPool, @NonNull ProgressIndicator progress) {
        if (threadPool.isTerminated()) {
            return;
        }

        threadPool.shutdown();

        try {
            while (!threadPool.awaitTermination(
                    FETCH_PACKAGES_WAITING_ITERATION_SECONDS, TimeUnit.SECONDS)) {
                progress.logWarning(FETCH_PACKAGES_WAITING_MESSAGE);
            }
        } catch (InterruptedException ignored) {
            // ignored
        }
    }

    /**
     * A thread-safe implementation of {@link DelegatingProgressIndicator} which does not report the
     * fraction, but preserves the ability to report the errors/warnings, as most underlying logging
     * implementations are thread-safe.
     */
    private static class LoggingOnlyProgressIndicator extends DelegatingProgressIndicator {
        LoggingOnlyProgressIndicator(@NonNull ProgressIndicator progress) {
            super(progress);
        }

        @Override
        public void setFraction(double fraction) {}

        @Override
        public double getFraction() {
            return 0;
        }

        @Override
        public void setText(@Nullable String text) {}

        @Override
        public void setSecondaryText(@Nullable String text) {}

        @Override
        public ProgressIndicator createSubProgress(double max) {
            return this;
        }
    }
}
