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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.RepoPackageImpl;
import com.android.repository.impl.meta.TypeDetails;

import java.util.Collection;

import javax.xml.bind.annotation.XmlTransient;

/**
 * A local or remote repository package, uniquely identified by it's {@code version} and {@code
 * path}.
 */
@XmlTransient
public interface RepoPackage extends Comparable<RepoPackage> {

    char PATH_SEPARATOR = ';';

    /**
     * Gets the {@link TypeDetails} for this package.
     */
    @NonNull
    TypeDetails getTypeDetails();

    /**
     * Gets the {@link Revision} of this package.
     */
    @NonNull
    Revision getVersion();

    /**
     * Gets the user-friendly name of this package.
     */
    @NonNull
    String getDisplayName();

    /**
     * Gets the {@link License}, if any, associated with this package.
     */
    @Nullable
    License getLicense();

    /**
     * Gets information on what versions of what packages this package depends on.
     */
    @NonNull
    Collection<Dependency> getAllDependencies();

    /**
     * The install path (which also serves as unique id) for this package.
     */
    @NonNull
    String getPath();

    /**
     * Whether this package is obsolete.
     */
    boolean obsolete();

    /**
     * Creates a {@link CommonFactory} corresponding to the {@link SchemaModule.SchemaModuleVersion}
     * of this instance.
     */
    @NonNull
    CommonFactory createFactory();

    /**
     * Returns a package with the same content as this one that can be marshalled using JAXB
     * (or {@code this} if it already can be marshalled).
     */
    @NonNull
    RepoPackageImpl asMarshallable();

}
