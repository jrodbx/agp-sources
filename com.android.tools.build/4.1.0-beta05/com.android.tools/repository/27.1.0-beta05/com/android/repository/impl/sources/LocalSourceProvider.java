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

package com.android.repository.impl.sources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.io.FileOp;
import com.android.repository.io.impl.FileOpImpl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Reads {@link RepositorySource}s saved locally. Allows sources to be saved, modified, and
 * deleted.
 */
public class LocalSourceProvider implements RepositorySourceProvider {

    private static final String KEY_COUNT = "count";

    private static final String KEY_SRC = "src";

    private static final String KEY_DISPLAY = "disp";

    private static final String KEY_ENABLED = "enabled";

    private final File mLocation;

    private List<RepositorySource> mSources;

    private static final Object LOCK = new Object();

    private final Collection<SchemaModule<?>> mAllowedModules;

    private final FileOp mFop;

    private RepoManager mRepoManager;

    /**
     * Create a new {@code LocalSourceProvider}.
     *
     * @param location       The file to load from and save to.
     * @param allowedModules The {@link SchemaModule}s that are allowed to be used by sources
     *                       provided by this provider.
     * @param fop            The {@link FileOp} to use for local file operations. For normal use
     *                       should probably be {@link FileOpImpl}.
     */
    public LocalSourceProvider(@NonNull File location,
            @NonNull Collection<SchemaModule<?>> allowedModules, @NonNull FileOp fop) {
        mAllowedModules = allowedModules;
        mLocation = location;
        mFop = fop;
    }

    /**
     * Sets the {@link RepoManager} that will use this provider.
     */
    public void setRepoManager(@NonNull RepoManager manager) {
        mRepoManager = manager;
    }

    /**
     * Load the source definitions (name, url, and enabled state) from {@link #mLocation}.
     */
    private void loadUserAddons(@NonNull ProgressIndicator progress) {
        // mRepoManager isn't actually used in this method, but we assert here to fail fast in
        // case someone forgets to set it. Otherwise we'll only fail if someone adds or removes
        // a source.
        assert mRepoManager != null;

        // Copied from SdkSources.
        // Implementation detail: synchronize on the sources list to make sure that
        // a- the source list doesn't change while we load/save it, and most important
        // b- to make sure it's not being saved while loaded or the reverse.
        // In most cases we do these operation from the UI thread so it's not really
        // that necessary. This is more a protection in case of someone calls this
        // from a worker thread by mistake.
        synchronized (LOCK) {
            List<RepositorySource> result = Lists.newArrayList();

            // Load new user sources from property file
            InputStream fis = null;
            try {
                if (mFop.exists(mLocation)) {
                    fis = mFop.newFileInputStream(mLocation);

                    Properties props = new Properties();
                    props.load(fis);

                    int count = Integer.parseInt(props.getProperty(KEY_COUNT, "0"));

                    for (int i = 0; i < count; i++) {
                        String url = props.getProperty(String.format("%s%02d", KEY_SRC, i));
                        String disp = props.getProperty(String.format("%s%02d", KEY_DISPLAY, i));
                        String enabledStr = props
                                .getProperty(String.format("%s%02d", KEY_ENABLED, i));
                        boolean enabled;
                        if (enabledStr == null) {
                            // for backward compatibility
                            enabled = true;
                        } else {
                            enabled = Boolean.parseBoolean(enabledStr);
                        }
                        if (url != null) {
                            result.add(new SimpleRepositorySource(url, disp, enabled,
                                    mAllowedModules, this));
                        }
                    }
                } else {
                    progress.logInfo(
                            "File " + mLocation.getAbsolutePath() + " could not be loaded.");
                }
            } catch (NumberFormatException | IOException e) {
                progress.logWarning("Failed to parse user addon file at " + mLocation, e);
            }
            finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        // nothing
                    }
                }
            }
            if (mSources == null) {
                mSources = Lists.newArrayList(result);
            } else {
                mSources.clear();
                mSources.addAll(result);
            }

        }
    }

    /**
     * Gets the {@link RepositorySource}s from this provider.
     *
     * @param downloader   Unused by this provider.
     * @param logger       A {@link ProgressIndicator} to be used for showing progress and logging.
     * @param forceRefresh If true, this provider should refresh its list of sources, rather than
     *                     return any cached sources.
     */
    @NonNull
    @Override
    public List<RepositorySource> getSources(@Nullable Downloader downloader,
            @NonNull ProgressIndicator logger, boolean forceRefresh) {
        synchronized (LOCK) {
            if (mSources == null || forceRefresh) {
                loadUserAddons(logger);
            }
        }
        return ImmutableList.copyOf(mSources);
    }

    /**
     * Add a source to this provider. Note that it won't be persisted until {@link
     * #save(ProgressIndicator)} is called.
     *
     * @param source The source to add.
     */
    @Override
    public boolean addSource(@NonNull RepositorySource source) {
        boolean result = mSources.add(source);
        mRepoManager.markInvalid();
        return result;
    }

    /**
     * Whether this source is modifiable.
     *
     * @return {@code true} for this class.
     */
    @Override
    public boolean isModifiable() {
        return true;
    }

    /**
     * Saves any changes in the sources to the specified file.
     *
     * @param progress {@link ProgressIndicator} for logging.
     */
    @Override
    public void save(@NonNull ProgressIndicator progress) {
        synchronized (LOCK) {
            try (OutputStream fos = mFop.newFileOutputStream(mLocation)) {

                Properties props = new Properties();

                int count = 0;

                for (RepositorySource s : mSources) {
                    props.setProperty(String.format("%s%02d", KEY_SRC, count), //$NON-NLS-1$
                            s.getUrl());
                    if (s.getDisplayName() != null) {
                        props.setProperty(String.format("%s%02d", KEY_DISPLAY, count), //$NON-NLS-1$
                                s.getDisplayName());
                    }
                    props.setProperty(String.format("%s%02d", KEY_ENABLED, count),
                            Boolean.toString(s.isEnabled()));
                    count++;
                }
                props.setProperty(KEY_COUNT, Integer.toString(count));

                props.store(fos, "## User Sources for Android Repository");  //$NON-NLS-1$

            } catch (IOException e) {
                progress.logWarning("failed to save sites", e);

            }
        }
    }

    /**
     * Remove the specified source from this provider. Note that the remove won't be persisted until
     * {@link #save(ProgressIndicator)} is called.
     *
     * @param source The source to remove.
     * @return {@code true} if a matching source was found and actually removed.
     */
    @Override
    public boolean removeSource(@NonNull RepositorySource source) {
        boolean result = mSources.remove(source);
        mRepoManager.markInvalid();
        return result;
    }
}
