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
import java.util.Objects;
import javax.xml.bind.annotation.XmlTransient;

/** The checksum of an archive. */
@XmlTransient
public abstract class Checksum implements Comparable<Checksum> {

    /** Create a new Checksum object with the given value and type. */
    @NonNull
    public static Checksum create(@NonNull String checksum, @NonNull String type) {
        return RepoManager.getCommonModule().createLatestFactory().createChecksum(checksum, type);
    }

    /** Gets the hex string for this checksum. */
    @NonNull
    public abstract String getValue();

    /** Sets the hex string for this checksum. */
    public abstract void setValue(@Nullable String checksum);

    /** Gets the checksum type for this checksum (sha-1 or sha-256). */
    @NonNull
    public abstract String getType();

    /** Sets the checksum type for this checksum (sha-1 or sha-256). */
    @NonNull
    public abstract void setType(@NonNull String type);

    @Override
    public int compareTo(@NonNull Checksum o) {
        int result = getType().compareTo(o.getType());
        if (result != 0) {
            return result;
        }
        return getValue().compareTo(o.getValue());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof Checksum)) {
            return false;
        }
        return ((Checksum) obj).getType().equals(getType())
                && ((Checksum) obj).getValue().equals(getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue(), getType());
    }
}
