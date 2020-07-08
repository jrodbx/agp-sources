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

package com.android.builder.profile;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.analytics.CommonMetricsData;
import com.android.tools.analytics.UsageTracker;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleBuildMemorySample;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records profile information for a build.
 *
 * <p>There is only ever one ProcessProfileWriter per build invocation, even though there will be
 * multiple android plugin applications. See {@code ProfilerInitializer} for logic that creates one
 * per build and finalizes it at the end of the build.
 *
 * <p>The methods implemented from {@link ProfileRecordWriter} will be called from multiple threads
 * during the build, storing execution spans.
 */
public final class ProcessProfileWriter implements ProfileRecordWriter {

    private boolean finished = false;

    private final GradleBuildMemorySample mStartMemoryStats;

    private final NameAnonymizer mNameAnonymizer;

    private final GradleBuildProfile.Builder mBuild;

    private final List<AndroidStudioEvent.Builder> otherEvents =
            Collections.synchronizedList(new ArrayList<>());

    private final LoadingCache<String, Project> mProjects;

    private final boolean mEnableChromeTracingOutput;

    private final AtomicLong lastRecordId = new AtomicLong(1);

    private final ConcurrentLinkedQueue<GradleBuildProfileSpan> spans;

    private final List<java.util.function.Supplier<String>> applicationIdSuppliers =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public long allocateRecordId() {
        return lastRecordId.incrementAndGet();
    }

    @NonNull
    public static ProcessProfileWriter get() {
        return ProcessProfileWriterFactory.getFactory().get();
    }


    ProcessProfileWriter(boolean enableChromeTracingOutput) {
        mEnableChromeTracingOutput = enableChromeTracingOutput;
        mNameAnonymizer = new NameAnonymizer();
        mBuild = GradleBuildProfile.newBuilder();
        mStartMemoryStats = createAndRecordMemorySample();
        mProjects = CacheBuilder.newBuilder().build(new ProjectCacheLoader(mNameAnonymizer));
        spans = new ConcurrentLinkedQueue<>();
    }

    /** Append a span record to the build profile. Thread safe. */
    @Override
    public void writeRecord(
            @NonNull String project,
            @Nullable String variant,
            @NonNull final GradleBuildProfileSpan.Builder executionRecord,
            @NonNull List<GradleBuildProfileSpan> taskExecutionPhases) {

        executionRecord.setProject(mNameAnonymizer.anonymizeProjectPath(project));
        executionRecord.setVariant(mNameAnonymizer.anonymizeVariant(project, variant));
        spans.add(executionRecord.build());
        if (!taskExecutionPhases.isEmpty()) {
            GradleBuildProfileSpan firstPhase = taskExecutionPhases.get(0);
            // add the gradle snapshot calculation span.
            spans.add(
                    GradleBuildProfileSpan.newBuilder()
                            .setType(GradleBuildProfileSpan.ExecutionType.GRADLE_PRE_TASK_SPAN)
                            .setParentId(executionRecord.getId())
                            .setThreadId(executionRecord.getThreadId())
                            .setStartTimeInMs(executionRecord.getStartTimeInMs())
                            .setDurationInMs(
                                    firstPhase.getStartTimeInMs()
                                            - executionRecord.getStartTimeInMs())
                            .build());
        }
        spans.addAll(taskExecutionPhases);
    }

    /** Appends a generic event (e.g. test execution record) to be uploaded. */
    public void recordEvent(@NonNull AndroidStudioEvent.Builder event) {
        // TODO: do this per EVENT_TYPE?
        Preconditions.checkArgument(event.hasProductDetails(), "Product details not set.");
        otherEvents.add(event);
    }
    /**
     * Finishes processing the outstanding {@link GradleBuildProfileSpan} publication and shuts down
     * the processing queue. Write the final output file to the given path.
     *
     * <p>If chrome tracing output is enabled, this method will also create a second file, with a
     * {@code .json} extension, in the same directory.
     *
     * <p>Either finishAndWrite or finish() should be called exactly once
     */
    synchronized void finishAndWrite(@NonNull Path outputFile) {
        finish();

        // Write benchmark file into build directory
        try {
            Files.createDirectories(outputFile.getParent());
            try (BufferedOutputStream outputStream =
                    new BufferedOutputStream(
                            Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW))) {
                mBuild.build().writeTo(outputStream);
            }

            if (mEnableChromeTracingOutput) {
                ChromeTracingProfileConverter.toJson(outputFile);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Finishes processing the outstanding {@link GradleBuildProfileSpan} publication and shuts down
     * the processing queue.
     *
     * <p>Either finishAndWrite or finish() should be called exactly once
     */
    synchronized void finish() {
        checkState(!finished, "Already finished");
        finished = true;

        // This will not throw ConcurrentModificationException if writeRecord() calls are still
        // happening. ConcurrentLinkedQueue iterators are instead weakly consistent.
        mBuild.addAllSpan(spans);
        GradleBuildMemorySample memoryStats = createAndRecordMemorySample();
        mBuild.setBuildTime(
                memoryStats.getTimestamp() - mStartMemoryStats.getTimestamp());
        mBuild.setGcCount(
                memoryStats.getGcCount() - mStartMemoryStats.getGcCount());
        mBuild.setGcTime(
                memoryStats.getGcTimeMs() - mStartMemoryStats.getGcTimeMs());

        for (Project project : mProjects.asMap().values()) {
            for (GradleBuildVariant.Builder variant : project.variants.values()) {
                project.properties.addVariant(variant);
            }
            if (project.properties != null) {
                mBuild.addProject(project.properties);
            }
        }

        mBuild.addAllRawProjectId(getApplicationIds());

        // Public build profile.
        UsageTracker.log(
                AndroidStudioEvent.newBuilder()
                        .setCategory(AndroidStudioEvent.EventCategory.GRADLE)
                        .setKind(AndroidStudioEvent.EventKind.GRADLE_BUILD_PROFILE)
                        .setGradleBuildProfile(mBuild.build())
                        .setJavaProcessStats(CommonMetricsData.getJavaProcessStats())
                        .setJvmDetails(CommonMetricsData.getJvmDetails()));

        for (AndroidStudioEvent.Builder otherEvent : otherEvents) {
            UsageTracker.log(otherEvent);
        }
    }

    @NonNull
    private synchronized List<String> getApplicationIds() {
        HashSet<String> applicationIds = new HashSet<>(applicationIdSuppliers.size());
        for (java.util.function.Supplier<String> applicationIdSupplier : applicationIdSuppliers) {
            applicationIds.add(applicationIdSupplier.get());
        }
        return applicationIds.stream().sorted().collect(ImmutableList.toImmutableList());
    }

    /** Properties and statistics global to this build invocation. */
    @NonNull
    public static GradleBuildProfile.Builder getGlobalProperties() {
        return get().getProperties();
    }

    @NonNull
    GradleBuildProfile.Builder getProperties() {
        return mBuild;
    }

    @NonNull
    public static GradleBuildProject.Builder getProject(@NonNull String projectPath) {
        return get().mProjects.getUnchecked(projectPath).properties;
    }

    public static GradleBuildVariant.Builder getOrCreateVariant(
            @NonNull String projectPath, @NonNull String variantName) {
        return get().addVariant(projectPath, variantName);
    }

    // Idempotent.
    private GradleBuildVariant.Builder addVariant(
            @NonNull String projectPath, @NonNull String variantName) {
        Project project = mProjects.getUnchecked(projectPath);
        GradleBuildVariant.Builder properties = project.variants.get(variantName);
        if (properties == null) {
            properties = GradleBuildVariant.newBuilder();
            properties.setId(mNameAnonymizer.anonymizeVariant(projectPath, variantName));
            project.variants.put(variantName, properties);
        }
        return properties;
    }

    private GradleBuildMemorySample createAndRecordMemorySample() {

        GradleBuildMemorySample stats =
                GradleBuildMemorySample.newBuilder()
                        .setJavaProcessStats(CommonMetricsData.getJavaProcessStats())
                        .setTimestamp(System.currentTimeMillis())
                        .build();
        if (stats != null) {
            mBuild.addMemorySample(stats);
        }
        return stats;
    }

    public static void recordMemorySample() {
        get().createAndRecordMemorySample();
    }

    public void recordApplicationId(@NonNull java.util.function.Supplier<String> applicationId) {
        applicationIdSuppliers.add(applicationId);
    }

    private static class ProjectCacheLoader extends CacheLoader<String, Project> {

        @NonNull
        private final NameAnonymizer mNameAnonymizer;

        ProjectCacheLoader(@NonNull NameAnonymizer nameAnonymizer) {
            mNameAnonymizer = nameAnonymizer;
        }

        @Override
        public Project load(@NonNull String name) throws Exception {
            return new Project(mNameAnonymizer.anonymizeProjectPath(name));
        }
    }

    private static class Project {

        Project(long id) {
            properties = GradleBuildProject.newBuilder();
            properties.setId(id);
        }

        final Map<String, GradleBuildVariant.Builder> variants = Maps.newConcurrentMap();
        final GradleBuildProject.Builder properties;
    }

}
