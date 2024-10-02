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

package com.android.repository.impl.meta;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;

import javax.xml.bind.annotation.XmlTransient;

/**
 * Superclass for xjc-generated revision class. Probably shouldn't be needed outside the repository
 * framework: normally {@link Revision} should be used.
 */
@XmlTransient
public abstract class RevisionType {

    /**
     * The major component of the revision.
     */
    public int getMajor() {
        // Stub
        return 0;
    }

    /**
     * The minor component of the revision, or null if unspecified.
     */
    @Nullable
    public Integer getMinor() {
        // Stub
        return null;
    }

    /**
     * The micro component of the revision, or null if unspecified.
     */
    @Nullable
    public Integer getMicro() {
        // Stub
        return null;
    }

    /**
     * The preview component of the revision, or null if unspecified.
     * TODO: This segment might need to be more flexible.
     */
    @Nullable
    public Integer getPreview() {
        // Stub
        return null;
    }

    public void setMajor(int major) {
        // Stub
    }

    public void setMinor(@Nullable Integer minor) {
        // Stub
    }

    public void setMicro(@Nullable Integer micro) {
        // Stub
    }

    public void setPreview(@Nullable Integer preview) {
        // Stub
    }

    /**
     * Convenience method to convert this into a {@link Revision}.
     */
    @NonNull
    public Revision toRevision() {
        return new Revision(getMajor(), getMinor(), getMicro(), getPreview());
    }
}
