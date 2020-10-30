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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.io.FileOp;
import com.google.common.base.Objects;
import com.google.common.hash.Hashing;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Comparator;
import javax.xml.bind.annotation.XmlTransient;

/**
 * License text, with an optional license XML reference.
 */
@SuppressWarnings("MethodMayBeStatic")
@XmlTransient
public abstract class License implements Comparable<License> {

    /**
     * The name of the directory used to store tokens indicating approval of licenses.
     */
    public static final String LICENSE_DIR = "licenses";

    /**
     * Gets the ID of this license, used to refer to it from within a {@link RepoPackage}.
     */
    @NonNull
    public abstract String getId();

    /**
     * Sets the ID of this license, used to refer to it from within a {@link RepoPackage}.
     */
    public abstract void setId(@NonNull String id);

    /**
     * Gets the text of the license.
     */
    @NonNull
    public abstract String getValue();

    /**
     * Sets the text of the license.
     */
    public abstract void setValue(String value);

    /**
     * Gets the type of the license. Currently only {@code "text"} is valid.
     */
    @Nullable
    public String getType() {
        // Stub
        return null;
    }

    /**
     * Sets the type of the license. Currently only {@code "text"} is valid.
     */
    public void setType(@Nullable String type) {
        // Stub
    }

    /**
     * Returns the hash of the license text, used for persisting acceptance. Never null.
     */
    @NonNull
    public String getLicenseHash() {
        return Hashing.sha1().hashBytes(getValue().getBytes(UTF_8)).toString();
    }

    /**
     * Returns a string representation of the license, useful for debugging.
     * This is not designed to be shown to the user.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<License ref:")
                .append(getId())
                .append(", text:")
                .append(getValue())
                .append(">");
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((getValue() == null) ? 0 : getValue().hashCode());
        result = prime * result
                + ((getId() == null) ? 0 : getId().hashCode());
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof License)) {
            return false;
        }
        License other = (License) obj;
        return Objects.equal(getValue(), other.getValue()) &&
                Objects.equal(getId(), other.getId());
    }

    /**
     * Checks whether this license has previously been accepted.
     * @param repositoryRoot The root directory of the repository
     * @return true if this license has already been accepted
     */
    public boolean checkAccepted(@Nullable File repositoryRoot, @NonNull FileOp fop) {
        if (repositoryRoot == null) {
            return false;
        }
        File licenseDir = new File(repositoryRoot, LICENSE_DIR);
        File licenseFile = new File(licenseDir, getId() == null ? getLicenseHash() : getId());
        if (!fop.exists(licenseFile)) {
            return false;
        }
        try (InputStreamReader licenseReader = new InputStreamReader(
                fop.newFileInputStream(licenseFile))) {
            for (String hash : CharStreams.readLines(licenseReader)) {
                if (hash.equals(getLicenseHash())) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * Marks this license as accepted.
     *
     * @param repositoryRoot The root directory of the repository
     * @return true if the acceptance was persisted successfully.
     */
    public boolean setAccepted(@Nullable File repositoryRoot, @NonNull FileOp fop) {
        if (repositoryRoot == null) {
            return false;
        }
        if (checkAccepted(repositoryRoot, fop)) {
            return true;
        }
        File licenseDir = new File(repositoryRoot, LICENSE_DIR);
        if (fop.exists(licenseDir) && !fop.isDirectory(licenseDir)) {
            return false;
        }
        if (!fop.exists(licenseDir)) {
            if (!fop.mkdirs(licenseDir)) {
                return false;
            }
        }
        File licenseFile = new File(licenseDir, getId() == null ? getLicenseHash(): getId());
        try (OutputStream os = fop.newFileOutputStream(licenseFile, true)) {
            os.write(String.format("%n%s", getLicenseHash()).getBytes());
        }
        catch (IOException e) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(License otherLicense) {
        return Comparator.comparing(License::getId)
                .thenComparing(License::getValue)
                .compare(this, otherLicense);
    }
}

