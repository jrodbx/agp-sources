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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * Concrete implementation of DexOptions.
 */
@SuppressWarnings("WeakerAccess") // Exposed in the DSL.
public class DefaultDexOptions implements DexOptions {

    public static final String OPTIMIZE_WARNING =
            "Disabling dex optimization produces wrong local debug info, b.android.com/82031.";

    private boolean preDexLibraries = true;

    private boolean jumboMode = false;

    // By default, all dexing will happen in process to get maximum feedback quickly.
    private boolean dexInProcess = true;

    private boolean keepRuntimeAnnotatedClasses = true;

    private Integer threadCount = null;

    private String javaMaxHeapSize;

    private List<String> additionalParameters = Lists.newArrayList();

    private volatile Integer maxProcessCount;

    public static DefaultDexOptions copyOf(DexOptions dexOptions) {
        DefaultDexOptions result = new DefaultDexOptions();

        result.setPreDexLibraries(dexOptions.getPreDexLibraries());
        result.setJumboMode(dexOptions.getJumboMode());
        result.setDexInProcess(dexOptions.getDexInProcess());
        result.setThreadCount(dexOptions.getThreadCount());
        result.setJavaMaxHeapSize(dexOptions.getJavaMaxHeapSize());
        result.setAdditionalParameters(dexOptions.getAdditionalParameters());
        result.setMaxProcessCount(dexOptions.getMaxProcessCount());
        result.setKeepRuntimeAnnotatedClasses(
                dexOptions.getKeepRuntimeAnnotatedClasses());

        return result;
    }

    /**
     * Whether to pre-dex libraries. This can improve incremental builds, but clean builds may
     * be slower.
     */
    @Override
    public boolean getPreDexLibraries() {
        return preDexLibraries;
    }

    public void setPreDexLibraries(boolean preDexLibraries) {
        this.preDexLibraries = preDexLibraries;
    }

    /**
     * Enable jumbo mode in dx ({@code --force-jumbo}).
     */
    @Override
    public boolean getJumboMode() {
        return jumboMode;
    }

    public void setJumboMode(boolean jumboMode) {
        this.jumboMode = jumboMode;
    }

    /**
     * Whether to run the {@code dx} compiler as a separate process or inside the Gradle daemon JVM.
     *
     * <p>Running {@code dx} in-process can greatly improve performance, but is still experimental.
     */
    @Override
    public boolean getDexInProcess() {
        return dexInProcess;
    }

    public void setDexInProcess(boolean dexInProcess) {
        this.dexInProcess = dexInProcess;
    }


    /**
     * Keep all classes with runtime annotations in the main dex in legacy multidex.
     *
     * <p>This is enabled by default and works around an issue that will cause the app to crash
     * when using java.lang.reflect.Field.getDeclaredAnnotations on older android versions.
     *
     * <p>This can be disabled for for apps that do not use reflection and need more space in their
     * main dex.
     *
     * <p>See <a href="http://b.android.com/78144">http://b.android.com/78144</a>.
     */
    @Override
    public boolean getKeepRuntimeAnnotatedClasses() {
        return keepRuntimeAnnotatedClasses;
    }

    public void setKeepRuntimeAnnotatedClasses(
            boolean keepRuntimeAnnotatedClasses) {
        this.keepRuntimeAnnotatedClasses = keepRuntimeAnnotatedClasses;
    }

    /**
     * Number of threads to use when running dx. Defaults to 4.
     */
    @Nullable
    @Override
    public Integer getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(Integer threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Specifies the {@code -Xmx} value when calling dx. Example value is {@code "2048m"}.
     */
    @Nullable
    @Override
    public String getJavaMaxHeapSize() {
        return javaMaxHeapSize;
    }

    public void setJavaMaxHeapSize(String javaMaxHeapSize) {
        this.javaMaxHeapSize = javaMaxHeapSize;
    }

    /**
     * List of additional parameters to be passed to {@code dx}.
     */
    @NonNull
    @Override
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setAdditionalParameters(@NonNull List<String> additionalParameters) {
        this.additionalParameters = Lists.newArrayList(additionalParameters);
    }

    /**
     * Returns the maximum number of concurrent processes that can be used to dex. Defaults to 4.
     *
     * <p>Be aware that the number of concurrent process times the memory requirement represent the
     * minimum amount of memory that will be used by the dx processes:
     *
     * <p>{@code Total Memory = maxProcessCount * javaMaxHeapSize}
     *
     * <p>To avoid thrashing, keep these two settings appropriate for your configuration.
     * @return the max number of concurrent dx processes.
     */
    @Nullable
    @Override
    public Integer getMaxProcessCount() {
        return maxProcessCount;
    }

    public void setMaxProcessCount(Integer maxProcessCount) {
        this.maxProcessCount = maxProcessCount;
    }

    /**
     * Whether to run the {@code dx} compiler with the {@code --no-optimize} flag.
     *
     * @deprecated Dex is always optimized. This returns {@code true} always.
     */
    @Deprecated
    @Nullable
    public Boolean getOptimize() {
        return true;
    }
}
