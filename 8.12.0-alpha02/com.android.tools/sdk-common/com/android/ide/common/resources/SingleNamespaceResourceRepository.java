/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * A resource repository that contains resources from a single app or library. This means that all
 * resources are in the same namespace if namespaces are used by the project.
 */
public interface SingleNamespaceResourceRepository extends ResourceRepository {
    /**
     * The namespace that all items in this repository belong to. This is {@link
     * ResourceNamespace#RES_AUTO} in non-namespaced projects or the namespace corresponding to the
     * package name returned by {@link #getPackageName()} in namespaced projects.
     */
    @NonNull
    ResourceNamespace getNamespace();

    /**
     * The package name from the manifest corresponding to this repository.
     *
     * <p>When the project is namespaced, this corresponds to the namespace returned by
     * {@link #getNamespace()}. In a non-namespaced project, the namespace is
     * {@link ResourceNamespace#RES_AUTO} but the value returned from this method can be used when
     * automatically migrating a project to use namespaces.
     *
     * @return the package name, or null in the unlikely case it cannot be determined
     */
    @Nullable
    String getPackageName();

    @Override
    @NonNull
    default Set<ResourceNamespace> getNamespaces() {
        return Collections.singleton(getNamespace());
    }

    @Override
    @NonNull
    default Collection<SingleNamespaceResourceRepository> getLeafResourceRepositories() {
        return Collections.singletonList(this);
    }
}
