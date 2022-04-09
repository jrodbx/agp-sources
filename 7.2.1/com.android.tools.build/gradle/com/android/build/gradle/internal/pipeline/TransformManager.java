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

package com.android.build.gradle.internal.pipeline;

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES;
import static com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.internal.InternalScope;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.tasks.factory.PreConfigAction;
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction;
import com.android.build.gradle.internal.tasks.factory.TaskFactory;
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskProvider;

/**
 * Manages the transforms for a variant.
 *
 * <p>The actual execution is handled by Gradle through the tasks.
 * Instead it's a means to more easily configure a series of transforms that consume each other's
 * inputs when several of these transform are optional.
 */
public class TransformManager extends FilterableStreamCollection {

    private static final boolean DEBUG = true;

    private static final String FD_TRANSFORMS = "transforms";

    public static final Set<ScopeType> EMPTY_SCOPES = ImmutableSet.of();

    public static final Set<ContentType> CONTENT_CLASS = ImmutableSet.of(CLASSES);
    public static final Set<ContentType> CONTENT_JARS = ImmutableSet.of(CLASSES, RESOURCES);
    public static final Set<ContentType> CONTENT_RESOURCES = ImmutableSet.of(RESOURCES);
    public static final Set<ContentType> CONTENT_DEX = ImmutableSet.of(ExtendedContentType.DEX);
    public static final Set<ContentType> CONTENT_DEX_WITH_RESOURCES =
            ImmutableSet.of(ExtendedContentType.DEX, RESOURCES);
    public static final Set<ScopeType> PROJECT_ONLY = ImmutableSet.of(Scope.PROJECT);
    public static final Set<ScopeType> SCOPE_FULL_PROJECT =
            ImmutableSet.of(Scope.PROJECT, Scope.SUB_PROJECTS, Scope.EXTERNAL_LIBRARIES);
    public static final Set<ScopeType> SCOPE_FULL_WITH_FEATURES =
            new ImmutableSet.Builder<ScopeType>()
                    .addAll(SCOPE_FULL_PROJECT)
                    .add(InternalScope.FEATURES)
                    .build();
    public static final Set<ScopeType> SCOPE_FEATURES = ImmutableSet.of(InternalScope.FEATURES);
    public static final Set<ScopeType> SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS =
            ImmutableSet.of(Scope.PROJECT, InternalScope.LOCAL_DEPS);
    public static final Set<ScopeType> SCOPE_FULL_PROJECT_WITH_LOCAL_JARS =
            new ImmutableSet.Builder<ScopeType>()
                    .addAll(SCOPE_FULL_PROJECT)
                    .add(InternalScope.LOCAL_DEPS)
                    .build();

    @NonNull
    private final Project project;
    @NonNull private final IssueReporter issueReporter;
    @NonNull private final Logger logger;

    /**
     * These are the streams that are available for new Transforms to consume.
     *
     * <p>Once a new transform is added, the streams that it consumes are removed from this list,
     * and the streams it produces are put instead.
     *
     * <p>When all the transforms have been added, the remaining streams should be consumed by
     * standard Tasks somehow.
     *
     * @see #getStreams(StreamFilter)
     */
    @NonNull private final List<TransformStream> streams = Lists.newArrayList();
    @NonNull
    private final List<Transform> transforms = Lists.newArrayList();

    public TransformManager(
            @NonNull Project project,
            @NonNull IssueReporter issueReporter) {
        this.project = project;
        this.issueReporter = issueReporter;
        this.logger = Logging.getLogger(TransformManager.class);
    }

    @NonNull
    @Override
    Project getProject() {
        return project;
    }

    public void addStream(@NonNull TransformStream stream) {
        streams.add(stream);
    }

    /**
     * Adds a Transform.
     *
     * <p>This makes the current transform consumes whatever Streams are currently available and
     * creates new ones for the transform output.
     *
     * <p>This also creates a {@link TransformTask} to run the transform and wire it up with the
     * dependencies of the consumed streams.
     *
     * @param taskFactory the task factory
     * @param creationConfig the current scope
     * @param transform the transform to add
     * @param <T> the type of the transform
     * @return {@code Optional<AndroidTask<Transform>>} containing the AndroidTask if it was able to
     *     create it
     */
    @NonNull
    public <T extends Transform> Optional<TaskProvider<TransformTask>> addTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantCreationConfig creationConfig,
            @NonNull T transform) {
        return addTransform(taskFactory, creationConfig, transform, null, null, null);
    }

    /**
     * Adds a Transform.
     *
     * <p>This makes the current transform consumes whatever Streams are currently available and
     * creates new ones for the transform output.
     *
     * <p>his also creates a {@link TransformTask} to run the transform and wire it up with the
     * dependencies of the consumed streams.
     *
     * @param taskFactory the task factory
     * @param creationConfig the current scope
     * @param transform the transform to add
     * @param <T> the type of the transform
     * @return {@code Optional<AndroidTask<TaskProvider<TransformTask>>>} containing the AndroidTask
     *     for the given transform task if it was able to create it
     */
    @NonNull
    public <T extends Transform> Optional<TaskProvider<TransformTask>> addTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantCreationConfig creationConfig,
            @NonNull T transform,
            @Nullable PreConfigAction preConfigAction,
            @Nullable TaskConfigAction<TransformTask> configAction,
            @Nullable TaskProviderCallback<TransformTask> providerCallback) {

        if (!validateTransform(transform)) {
            // validate either throws an exception, or records the problem during sync
            // so it's safe to just return null here.
            return Optional.empty();
        }

        if (!transform.applyToVariant(new VariantInfoImpl(creationConfig))) {
            return Optional.empty();
        }

        List<TransformStream> inputStreams = Lists.newArrayList();
        String taskName = creationConfig.computeTaskName(getTaskNamePrefix(transform), "");

        // get referenced-only streams
        List<TransformStream> referencedStreams = grabReferencedStreams(transform);

        // find input streams, and compute output streams for the transform.
        IntermediateStream outputStream =
                findTransformStreams(
                        transform,
                        creationConfig,
                        inputStreams,
                        taskName,
                        creationConfig.getServices().getProjectInfo().getBuildDir());

        if (inputStreams.isEmpty() && referencedStreams.isEmpty()) {
            // didn't find any match. Means there is a broken order somewhere in the streams.
            issueReporter.reportError(
                    Type.GENERIC,
                    String.format(
                            "Unable to add Transform '%s' on variant '%s': requested streams not available: %s+%s / %s",
                            transform.getName(),
                            creationConfig.getName(),
                            transform.getScopes(),
                            transform.getReferencedScopes(),
                            transform.getInputTypes()));
            return Optional.empty();
        }

        //noinspection PointlessBooleanExpression
        if (DEBUG && logger.isEnabled(LogLevel.DEBUG)) {
            logger.debug("ADDED TRANSFORM(" + creationConfig.getName() + "):");
            logger.debug("\tName: " + transform.getName());
            logger.debug("\tTask: " + taskName);
            for (TransformStream sd : inputStreams) {
                logger.debug("\tInputStream: " + sd);
            }
            for (TransformStream sd : referencedStreams) {
                logger.debug("\tRef'edStream: " + sd);
            }
            if (outputStream != null) {
                logger.debug("\tOutputStream: " + outputStream);
            }
        }

        transforms.add(transform);
        TaskConfigAction<TransformTask> wrappedConfigAction =
                t -> {
                    if (configAction != null) {
                        configAction.configure(t);
                    }
                };
        // create the task...
        return Optional.of(
                taskFactory.register(
                        new TransformTask.CreationAction<>(
                                creationConfig.getName(),
                                taskName,
                                transform,
                                inputStreams,
                                referencedStreams,
                                outputStream),
                        preConfigAction,
                        wrappedConfigAction,
                        providerCallback));
    }

    @Override
    @NonNull
    public List<TransformStream> getStreams() {
        return streams;
    }

    @VisibleForTesting
    @NonNull
    static String getTaskNamePrefix(@NonNull Transform transform) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("transform");

        sb.append(
                transform
                        .getInputTypes()
                        .stream()
                        .map(
                                inputType ->
                                        CaseFormat.UPPER_UNDERSCORE.to(
                                                CaseFormat.UPPER_CAMEL, inputType.name()))
                        .sorted() // Keep the order stable.
                        .collect(Collectors.joining("And")));
        sb.append("With");
        StringHelper.appendCapitalized(sb, transform.getName());
        sb.append("For");

        return sb.toString();
    }

    /**
     * Finds the stream(s) the transform consumes, and return them.
     *
     * <p>This also removes them from the instance list. They will be replaced with the output
     * stream(s) from the transform.
     *
     * <p>This returns an optional output stream.
     *
     * @param transform the transform.
     * @param creationConfig the scope the transform is applied to.
     * @param inputStreams the out list of input streams for the transform.
     * @param taskName the name of the task that will run the transform
     * @param buildDir the build dir of the project.
     * @return the output stream if any.
     */
    @Nullable
    private IntermediateStream findTransformStreams(
            @NonNull Transform transform,
            @NonNull VariantCreationConfig creationConfig,
            @NonNull List<TransformStream> inputStreams,
            @NonNull String taskName,
            @NonNull File buildDir) {

        Set<? super Scope> requestedScopes = transform.getScopes();
        if (requestedScopes.isEmpty()) {
            // this is a no-op transform.
            return null;
        }

        Set<ContentType> requestedTypes = transform.getInputTypes();
        consumeStreams(requestedScopes, requestedTypes, inputStreams);

        // create the output stream.
        // create single combined output stream for all types and scopes
        Set<ContentType> outputTypes = transform.getOutputTypes();

        File outRootFolder =
                FileUtils.join(
                        buildDir,
                        StringHelper.toStrings(
                                SdkConstants.FD_INTERMEDIATES,
                                FD_TRANSFORMS,
                                transform.getName(),
                                creationConfig.getVariantDslInfo().getDirectorySegments()));

        // create the output
        IntermediateStream outputStream =
                IntermediateStream.builder(
                                project,
                                transform.getName() + "-" + creationConfig.getName(),
                                taskName)
                        .addContentTypes(outputTypes)
                        .addScopes(requestedScopes)
                        .setRootLocation(outRootFolder)
                        .build();
        // and add it to the list of available streams for next transforms.
        streams.add(outputStream);

        return outputStream;
    }

    /**
     * <p>This method will remove all streams matching the specified scopes and types from the
     * available streams.
     *
     * @deprecated Use this method only for migration from transforms to tasks.
     */
    @Deprecated
    public void consumeStreams(
            @NonNull Set<? super Scope> requestedScopes, @NonNull Set<ContentType> requestedTypes) {
        consumeStreams(requestedScopes, requestedTypes, new ArrayList<>());
    }

    private void consumeStreams(
            @NonNull Set<? super Scope> requestedScopes,
            @NonNull Set<ContentType> requestedTypes,
            @NonNull List<TransformStream> inputStreams) {
        // list to hold the list of unused streams in the manager after everything is done.
        // they'll be put back in the streams collection, along with the new outputs.
        List<TransformStream> oldStreams = Lists.newArrayListWithExpectedSize(streams.size());

        for (TransformStream stream : streams) {
            // streams may contain more than we need. In this case, we'll make a copy of the stream
            // with the remaining types/scopes. It'll be up to the TransformTask to make
            // sure that the content of the stream is usable (for instance when a stream
            // may contain two scopes, these scopes could be combined or not, impacting consumption)
            Set<ContentType> availableTypes = stream.getContentTypes();
            Set<? super Scope> availableScopes = stream.getScopes();

            Set<ContentType> commonTypes = Sets.intersection(requestedTypes,
                    availableTypes);
            Set<? super Scope> commonScopes = Sets.intersection(requestedScopes, availableScopes);
            if (!commonTypes.isEmpty() && !commonScopes.isEmpty()) {

                // check if we need to make another stream from this one with less scopes/types.
                if (!commonScopes.equals(availableScopes) || !commonTypes.equals(availableTypes)) {
                    // first the stream that gets consumed. It consumes only the common types/scopes
                    inputStreams.add(stream.makeRestrictedCopy(commonTypes, commonScopes));

                    // Now we could have two more streams. One with the requestedScope but the remainingTypes, and the other one with the remaining scopes and all the types.
                    // compute remaining scopes/types.
                    Sets.SetView<ContentType> remainingTypes =
                            Sets.difference(availableTypes, commonTypes);
                    Sets.SetView<? super Scope> remainingScopes = Sets.difference(availableScopes, commonScopes);

                    if (!remainingTypes.isEmpty()) {
                        oldStreams.add(
                                stream.makeRestrictedCopy(
                                        remainingTypes.immutableCopy(), availableScopes));
                    }
                    if (!remainingScopes.isEmpty()) {
                        oldStreams.add(
                                stream.makeRestrictedCopy(
                                        availableTypes, remainingScopes.immutableCopy()));
                    }
                } else {
                    // stream is an exact match (or at least subset) for the request,
                    // so we add it as it.
                    inputStreams.add(stream);
                }
            } else {
                // stream is not used, keep it around.
                oldStreams.add(stream);
            }
        }

        // update the list of available streams.
        streams.clear();
        streams.addAll(oldStreams);
    }

    @NonNull
    private List<TransformStream> grabReferencedStreams(@NonNull Transform transform) {
        Set<? super Scope> requestedScopes = transform.getReferencedScopes();
        if (requestedScopes.isEmpty()) {
            return ImmutableList.of();
        }

        List<TransformStream> streamMatches = Lists.newArrayListWithExpectedSize(streams.size());

        Set<ContentType> requestedTypes = transform.getInputTypes();
        for (TransformStream stream : streams) {
            // streams may contain more than we need. In this case, we'll provide the whole
            // stream as-is since it's not actually consumed.
            // It'll be up to the TransformTask to make sure that the content of the stream is
            // usable (for instance when a stream
            // may contain two scopes, these scopes could be combined or not, impacting consumption)
            Set<ContentType> availableTypes = stream.getContentTypes();
            Set<? super Scope> availableScopes = stream.getScopes();

            Set<ContentType> commonTypes = Sets.intersection(requestedTypes,
                    availableTypes);
            Set<? super Scope> commonScopes = Sets.intersection(requestedScopes, availableScopes);

            if (!commonTypes.isEmpty() && !commonScopes.isEmpty()) {
                streamMatches.add(stream);
            }
        }

        return streamMatches;
    }

    private boolean validateTransform(@NonNull Transform transform) {
        // check the content type are of the right Type.
        if (!checkContentTypes(transform.getInputTypes(), transform)
                || !checkContentTypes(transform.getOutputTypes(), transform)) {
            return false;
        }

        // check some scopes are not consumed.
        Set<? super Scope> scopes = transform.getScopes();
        if (scopes.contains(Scope.PROVIDED_ONLY)) {
            issueReporter.reportError(
                    Type.GENERIC,
                    String.format(
                            "PROVIDED_ONLY scope cannot be consumed by Transform '%1$s'",
                            transform.getName()));
            return false;
        }
        if (scopes.contains(Scope.TESTED_CODE)) {
            issueReporter.reportError(
                    Type.GENERIC,
                    String.format(
                            "TESTED_CODE scope cannot be consumed by Transform '%1$s'",
                            transform.getName()));
            return false;
        }

        if (!transform
                .getClass()
                .getCanonicalName()
                .startsWith("com.android.build.gradle.internal.transforms")) {
            checkScopeDeprecation(transform.getScopes(), transform.getName());
            checkScopeDeprecation(transform.getReferencedScopes(), transform.getName());
        }

        return true;
    }

    @SuppressWarnings("deprecation")
    private void checkScopeDeprecation(
            @NonNull Set<? super Scope> scopes, @NonNull String transformName) {
        if (scopes.contains(Scope.PROJECT_LOCAL_DEPS)) {
            final String message =
                    String.format(
                            "Transform '%1$s' uses scope %2$s which is deprecated and replaced with %3$s",
                            transformName,
                            Scope.PROJECT_LOCAL_DEPS.name(),
                            Scope.EXTERNAL_LIBRARIES.name());
            if (!scopes.contains(Scope.EXTERNAL_LIBRARIES)) {
                issueReporter.reportError(Type.GENERIC, message);
            }
        }

        if (scopes.contains(Scope.SUB_PROJECTS_LOCAL_DEPS)) {
            final String message =
                    String.format(
                            "Transform '%1$s' uses scope %2$s which is deprecated and replaced with %3$s",
                            transformName,
                            Scope.SUB_PROJECTS_LOCAL_DEPS.name(),
                            Scope.EXTERNAL_LIBRARIES.name());
            if (!scopes.contains(Scope.EXTERNAL_LIBRARIES)) {
                issueReporter.reportError(Type.GENERIC, message);
            }
        }
    }

    private boolean checkContentTypes(
            @NonNull Set<ContentType> contentTypes,
            @NonNull Transform transform) {
        for (ContentType contentType : contentTypes) {
            if (!(contentType instanceof QualifiedContent.DefaultContentType
                    || contentType instanceof ExtendedContentType)) {
                issueReporter.reportError(
                        Type.GENERIC,
                        String.format(
                                "Custom content types (%1$s) are not supported in transforms (%2$s)",
                                contentType.getClass().getName(), transform.getName()));
                return false;
            }
        }
        return true;
    }
}
