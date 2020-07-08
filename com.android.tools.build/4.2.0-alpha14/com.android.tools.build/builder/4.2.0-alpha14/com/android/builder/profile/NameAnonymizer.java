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

package com.android.builder.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.Pair;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBiMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class NameAnonymizer {

    static final long NO_VARIANT_SPECIFIED = 0;

    final LoadingCache<String, Project> mProjects =
            CacheBuilder.newBuilder().build(new ProjectCacheLoader());

    /**
     * Initialize the name anonymizer.
     */
    public NameAnonymizer() {
    }


    /**
     * Retrieve the ID for an absolute project path.
     *
     * <p>Maps unknown to 0.
     */
    public long anonymizeProjectPath(@NonNull String projectPath) {
        Preconditions.checkArgument(
                projectPath.startsWith(":"),
                "Project path '" + projectPath + "' should be absolute");
        try {
            return mProjects.get(projectPath).mId;
        } catch (ExecutionException e) {
            return 0;
        }
    }

    /**
     * Retrieve the ID for a variant name.
     *
     * Maps unknown project and null variant 0. Will generate a new id for each variant.
     */
    public long anonymizeVariant(@NonNull String projectName, @Nullable String variantName) {
        if (variantName == null) {
            return NO_VARIANT_SPECIFIED;
        }
        try {
            return mProjects.get(projectName).mVariantIds.get(variantName);
        } catch (ExecutionException e) {
            return NO_VARIANT_SPECIFIED;
        }
    }

    /**
     * Create a map for this build from anonymized ID back to project name.
     *
     * Used for the debugging and profiling json output only.
     */
    Map<Long, Pair<String, Map<Long,String>>> createDeanonymizer() {
        return mProjects.asMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getValue().mId,
                        entry -> Pair.of(
                                entry.getKey(),
                                HashBiMap.create(entry.getValue().mVariantIds.asMap()).inverse())));
    }




    private static class ProjectCacheLoader extends CacheLoader<String, Project> {
        private final AtomicLong nextProjectId = new AtomicLong(0);

        @Override
        public Project load(@NonNull String key) throws Exception {
            return new Project(nextProjectId.incrementAndGet());
        }
    }

    private static class Project {
        final long mId;
        final LoadingCache<String, Long> mVariantIds =
                CacheBuilder.newBuilder().build(new VariantIdCacheLoader());

        Project(long id) {
            this.mId = id;
        }
    }

    private static class VariantIdCacheLoader extends CacheLoader<String, Long> {
        private final AtomicLong nextVariantId = new AtomicLong(0);

        @Override
        public Long load(String key) throws Exception {
            return nextVariantId.incrementAndGet();
        }
    }
}
