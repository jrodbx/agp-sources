/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

/** DSL object for configuring aapt options. */
public class AaptOptions implements com.android.build.api.dsl.AaptOptions {

    @Inject
    public AaptOptions(boolean namespaced) {
        this.namespaced = namespaced;
    }

    private boolean namespaced;

    @Nullable
    private String ignoreAssetsPattern;

    private final List<String> noCompressList = new ArrayList<>();

    @Nullable private Boolean cruncherEnabled;

    private boolean failOnMissingConfigEntry = false;

    private final List<String> additionalParameters = new ArrayList<>();

    private int cruncherProcesses = 0;

    public void setIgnoreAssets(@Nullable String ignoreAssetsPattern) {
        setIgnoreAssetsPattern(ignoreAssetsPattern);
    }

    /**
     * Pattern describing assets to be ignore.
     *
     * <p>See <code>aapt --help</code>
     */
    public String getIgnoreAssets() {
        return ignoreAssetsPattern;
    }

    @Override
    public void setIgnoreAssetsPattern(@Nullable String ignoreAssetsPattern) {
        this.ignoreAssetsPattern = ignoreAssetsPattern;
    }

    @Override
    @Nullable
    public String getIgnoreAssetsPattern() {
        return ignoreAssetsPattern;
    }

    public void setNoCompress(String noCompress) {
        setNoCompress(new String[] { noCompress });
    }

    public void setNoCompress(Iterable<String> noCompress) {
        setNoCompress(Iterables.toArray(noCompress, String.class));
    }

    public void setNoCompress(String... noCompress) {
        for (String p : noCompress) {
            if (p.equals("\"\"")) {
                LoggerWrapper.getLogger(AaptOptions.class).warning("noCompress pattern '\"\"' "
                        + "no longer matches every file. It now matches exactly two double quote "
                        + "characters. Please use '' instead.");
            }
        }
        noCompressList.clear();
        Collections.addAll(noCompressList, noCompress);
    }

    @NonNull
    @Override
    public Collection<String> getNoCompress() {
        return noCompressList;
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void useNewCruncher(boolean value) {
        LoggerWrapper.getLogger(AaptOptions.class).warning("useNewCruncher has been deprecated. "
                + "It will be removed in a future version of the gradle plugin. New cruncher is "
                + "now always enabled.");
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void setUseNewCruncher(boolean value) {
        LoggerWrapper.getLogger(AaptOptions.class).warning("useNewCruncher has been deprecated. "
                + "It will be removed in a future version of the gradle plugin. New cruncher is "
                + "now always enabled.");
    }

    public void setCruncherEnabled(boolean value) {
        cruncherEnabled = value;
    }

    /**
     * Whether to crunch PNGs.
     *
     * <p>This will reduce the size of the APK if PNGs resources are not already optimally
     * compressed, at the cost of extra time to build.
     *
     * <p>PNG crunching is enabled by default in the release build type and disabled by default in
     * the debug build type.
     *
     * <p>This is replaced by {@link BuildType#isCrunchPngs()}.
     */
    @Deprecated
    public boolean getCruncherEnabled() {
        // Simulate true if unset. This is not really correct, but changing it to be a tri-state
        // nullable Boolean is potentially a breaking change if the getter was being used by build
        // scripts or third party plugins.
        return cruncherEnabled == null ? true : cruncherEnabled;
    }

    public Boolean getCruncherEnabledOverride() {
        return cruncherEnabled;
    }

    /**
     * Whether to use the new cruncher.
     */
    public boolean getUseNewCruncher() {
        return true;
    }

    public void failOnMissingConfigEntry(boolean value) {
        failOnMissingConfigEntry = value;
    }

    @Override
    public void setFailOnMissingConfigEntry(boolean value) {
        failOnMissingConfigEntry = value;
    }

    @Override
    public boolean getFailOnMissingConfigEntry() {
        return failOnMissingConfigEntry;
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    @Override
    public void noCompress(@NonNull String noCompress) {
        noCompressList.add(noCompress);
    }

    @Override
    public void noCompress(@NonNull String... noCompress) {
        Collections.addAll(noCompressList, noCompress);
    }

    @Override
    public void additionalParameters(@NonNull String param) {
        additionalParameters.add(param);
    }

    @Override
    public void additionalParameters(@NonNull String... params) {
        Collections.addAll(additionalParameters, params);
    }

    public void setAdditionalParameters(@NonNull List<String> parameters) {
        additionalParameters.clear();
        additionalParameters.addAll(parameters);
    }

    @Override
    @NonNull
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setCruncherProcesses(int cruncherProcesses) {
        this.cruncherProcesses = cruncherProcesses;
    }

    /**
     * Obtains the number of cruncher processes to use. More cruncher processes will crunch files
     * faster, but will require more memory and CPU.
     *
     * @return the number of cruncher processes, {@code 0} to use the default
     */
    public int getCruncherProcesses() {
        return cruncherProcesses;
    }

    @Override
    public boolean getNamespaced() {
        return namespaced;
    }

    @Override
    public void setNamespaced(boolean namespaced) {
        this.namespaced = namespaced;
    }

    public void namespaced(boolean namespaced) {
        this.namespaced = namespaced;
    }
}
