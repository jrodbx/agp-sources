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
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.RevisionType;

import javax.xml.bind.annotation.XmlTransient;

/**
 * A dependency of one package on another. Concrete instances should be created by
 * {@link CommonFactory}.
 */
@XmlTransient
public abstract class Dependency {

    /**
     * @return The minimum revision of the other package required.
     */
    @XmlTransient
    @Nullable
    public RevisionType getMinRevision() {
        // Stub.
        return null;
    }

    /**
     * @param revision The minimum revision of the other package required.
     */
    public void setMinRevision(@Nullable RevisionType revision) {
        // Stub
    }

    /**
     * @return The path uniquely identifying the other package.
     */
    @XmlTransient
    @NonNull
    public abstract String getPath();

    /**
     * @param id The path uniquely identifying the other package.
     */
    public void setPath(@NonNull String id) {
        // Stub
    }

}
