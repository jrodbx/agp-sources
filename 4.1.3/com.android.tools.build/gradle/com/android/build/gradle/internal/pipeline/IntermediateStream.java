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

import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

/**
 * Version of TransformStream handling outputs of transforms.
 */
class IntermediateStream extends TransformStream {

    @NonNull private final String taskName;

    static Builder builder(
            @NonNull Project project, @NonNull String name, @NonNull String taskName) {
        return new Builder(project, name, taskName);
    }

    static final class Builder {

        @NonNull private final Project project;
        @NonNull private final String name;
        @NonNull private final String taskName;
        private Set<ContentType> contentTypes = Sets.newHashSet();
        private Set<QualifiedContent.ScopeType> scopes = Sets.newHashSet();
        private File rootLocation;

        public Builder(@NonNull Project project, @NonNull String name, @NonNull String taskName) {
            this.project = project;
            this.name = name;
            this.taskName = taskName;
        }

        public IntermediateStream build() {
            Preconditions.checkNotNull(rootLocation);
            Preconditions.checkNotNull(taskName);
            Preconditions.checkState(!contentTypes.isEmpty());
            Preconditions.checkState(!scopes.isEmpty());

            // create a file collection with the files and the dependencies.
            FileCollection fileCollection = project.files(rootLocation).builtBy(taskName);

            return new IntermediateStream(
                    name,
                    taskName,
                    ImmutableSet.copyOf(contentTypes),
                    ImmutableSet.copyOf(scopes),
                    fileCollection);
        }

        Builder addContentTypes(@NonNull Set<ContentType> types) {
            this.contentTypes.addAll(types);
            return this;
        }

        Builder addContentTypes(@NonNull ContentType... types) {
            this.contentTypes.addAll(Arrays.asList(types));
            return this;
        }

        Builder addScopes(@NonNull Set<? super Scope> scopes) {
            for (Object scope : scopes) {
                this.scopes.add((QualifiedContent.ScopeType) scope);
            }
            return this;
        }

        Builder addScopes(@NonNull Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return this;
        }

        Builder setRootLocation(@NonNull final File rootLocation) {
            this.rootLocation = rootLocation;
            return this;
        }
    }

    private IntermediateStream(
            @NonNull String name,
            @NonNull String taskName,
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes,
            @NonNull FileCollection fileCollection) {
        super(name, contentTypes, scopes, fileCollection);
        this.taskName = taskName;
    }

    private IntermediateFolderUtils folderUtils = null;
    private List<IntermediateStream> copies = null;

    /**
     * Returns the files that make up the streams. The callable allows for resolving this lazily.
     */
    @NonNull
    File getRootLocation() {
        return getFileCollection().getSingleFile();
    }

    /** Returns a new view of this content as a {@link TransformOutputProvider}. */
    @NonNull
    TransformOutputProvider asOutput() throws IOException {
        init(true);
        return new TransformOutputProviderImpl(folderUtils);
    }

    void save() throws IOException {
        folderUtils.save();
        reloadCopies();
    }

    @NonNull
    @Override
    TransformInput asNonIncrementalInput() {
        init(false);
        return folderUtils.computeNonIncrementalInputFromFolder();
    }

    @NonNull
    @Override
    IncrementalTransformInput asIncrementalInput() {
        init(false);
        return folderUtils.computeIncrementalInputFromFolder();
    }

    @NonNull
    @Override
    TransformStream makeRestrictedCopy(
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes) {
        final IntermediateStream copy =
                new IntermediateStream(
                        getName() + "-restricted-copy",
                        taskName,
                        types,
                        scopes,
                        getFileCollection());

        // record the copies. This is so that when the original stream gets content from the
        // transform that write into it, we can notify the copies to reload the json files so that
        // their sub-stream list is up to date for consumption by the downstream transforms.
        if (copies == null) {
            copies = Lists.newArrayList();
        }
        copies.add(copy);
        return copy;
    }

    @Override
    @NonNull
    FileCollection getOutputFileCollection(@NonNull Project project, @NonNull StreamFilter streamFilter) {
        // create a collection that only returns the requested content type/scope,
        // and contain the dependency information.

        // the collection inside this type of stream cannot be used as is. This is because it
        // contains the root location rather that the actual inputs of the stream. Therefore
        // we need to go through them and create a single collection that contains the actual
        // inputs.
        // However the content of the intermediate root folder isn't known at configuration
        // time so we need to pass a callable that will compute the files dynamically.
        Callable<Collection<File>> supplier =
                () -> {
                    // If the task has not been executed, return an empty list; otherwise, gradle
                    // will try to resolve the output files before task execution.
                    // TaskState::getExecuted below will return true if task was executed or if it
                    // is UP_TO_DATE, FROM_CACHE, SKIPPED, or NO_SOURCE.
                    if (!project.getTasks().getByName(taskName).getState().getExecuted()) {
                        return ImmutableList.of();
                    }
                    init(false);
                    return folderUtils.getFiles(streamFilter);
                };

        return project.files(supplier).builtBy(getFileCollection().getBuildDependencies());
    }

    private void init(boolean ignoreUnexpectedScopes) {
        if (folderUtils == null) {
            folderUtils =
                    new IntermediateFolderUtils(
                            getRootLocation(),
                            getContentTypes(),
                            getScopes(),
                            ignoreUnexpectedScopes);
        }
    }

    private void reload() {
        if (folderUtils != null) {
            folderUtils.reload();
        }
        reloadCopies();
    }

    private void reloadCopies() {
        // need to notify the restricted copies to reload their substreams from the newly
        // generated content json file.
        if (copies != null) {
            for (IntermediateStream copy : copies) {
                copy.reload();
            }
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scopes", getScopes())
                .add("contentTypes", getContentTypes())
                .add("fileCollection", getFileCollection())
                .toString();
    }
}
