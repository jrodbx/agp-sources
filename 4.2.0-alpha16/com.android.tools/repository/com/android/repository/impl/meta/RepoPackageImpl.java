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
import com.android.repository.api.Dependency;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Repository;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

/**
 * A local or remote {@link RepoPackage}. Primarily a superclass for xjc-generated classes.
 */
@SuppressWarnings("MethodMayBeStatic")
@XmlTransient
public abstract class RepoPackageImpl implements RepoPackage {

    @Override
    @NonNull
    @XmlTransient
    public abstract TypeDetails getTypeDetails();

    @NonNull
    @Override
    @XmlTransient
    public Revision getVersion() {
        return getRevision().toRevision();
    }

    @NonNull
    protected abstract RevisionType getRevision();

    @Override
    @XmlTransient
    @NonNull
    public abstract String getDisplayName();

    @Nullable
    protected UsesLicense getUsesLicense() {
        // Stub
        return null;
    }

    @Override
    @XmlTransient
    @Nullable
    public License getLicense() {
        UsesLicense usesLicense = getUsesLicense();
        if (usesLicense != null) {
            return (License) usesLicense.getRef();
        }
        return null;
    }

    protected void setUsesLicense(@Nullable UsesLicense license) {
        // Stub
    }

    /**
     * Convenience method to add a reference to the given license to this package.
     */
    public void setLicense(@Nullable License l) {
        UsesLicense ul = null;
        if (l != null) {
            ul = createFactory().createLicenseRefType();
            ul.setRef(l);
        }
        setUsesLicense(ul);
    }

    @Nullable
    protected Dependencies getDependencies() {
        // Stub
        return null;
    }

    @Override
    @NonNull
    public Collection<Dependency> getAllDependencies() {
        Dependencies dependencies = getDependencies();
        if (dependencies == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(dependencies.getDependency());
    }


    @NonNull
    @Override
    @XmlTransient
    public String getPath() {
        // Stub
        return "";
    }

    /**
     * Convenience method for getting the obsolete status, defaulting {@code null} from the
     * underlying {@link #isObsolete()} to {@code false}.
     */
    @Override
    public boolean obsolete() {
        return isObsolete() != null && isObsolete();
    }

    @Nullable
    protected Boolean isObsolete() {
        // Stub
        return null;
    }

    // TODO: reevaluate if we want to include other info in comparison
    @Override
    public int compareTo(@NonNull RepoPackage o) {
        int result = ComparisonChain.start()
                .compare(getPath(), o.getPath())
                .compare(getVersion(), o.getVersion())
                .result();
        if (result != 0) {
            return result;
        }
        if ((this instanceof LocalPackage ^ o instanceof LocalPackage) ||
            (this instanceof RemotePackage ^ o instanceof RemotePackage)) {
            return getClass().getName().compareTo(o.getClass().getName());
        }
        return 0;
    }

    /**
     * Adds this package to the given {@link Repository}, overwriting existing packages if
     * necessary.
     */
    public abstract void addTo(@NonNull Repository repo);

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RepoPackage && compareTo((RepoPackage)obj) == 0;
    }

    @Override
    public int hashCode() {
        return getPath().hashCode() * 37 + getVersion().hashCode();
    }

    protected void setRevision(@NonNull RevisionType revision) {
        // Stub
    }

    /**
     * Convenience method to set this package's {@link RevisionType} based on a
     * {@link Revision}.
     */
    public void setVersion(@NonNull Revision revision) {
        setRevision(createFactory().createRevisionType(revision));
    }

    public void setDependencies(@Nullable Dependencies dependencies) {
        // Stub
    }

    public void setTypeDetails(@Nullable TypeDetails details) {
        // Stub
    }

    public void setDisplayName(@NonNull String name) {
        // Stub
    }

    public void setPath(@NonNull String path) {
        // Stub
    }

    public void setObsolete(@Nullable Boolean obsolete) {
        // Stub
    }

    /**
     * Convenience method to add a {@link Dependency} to this package's list of dependencies.
     */
    public void addDependency(@NonNull Dependency dep) {
        Dependencies dependencies = getDependencies();
        if (dependencies == null) {
            dependencies = createFactory().createDependenciesType();
            setDependencies(dependencies);
        }
        getDependencies().getDependency().add(dep);
    }

    /**
     * List of {@link Archive}s.
     */
    public abstract static class Archives {
        @NonNull
        public abstract List<Archive> getArchive();
    }

    /**
     * Reference to a {@link License}.
     */
    @XmlTransient
    public abstract static class UsesLicense {
        @NonNull
        public abstract License getRef();

        public void setRef(@NonNull License ref) {
            // Stub
        }
    }

    /**
     * List of {@link Dependency}s.
     */
    @XmlTransient
    public abstract static class Dependencies {
        @NonNull
        public abstract List<Dependency> getDependency();
    }

}
