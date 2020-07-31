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
import com.android.repository.api.Dependency;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Repository;

import java.io.File;

import javax.xml.bind.annotation.XmlTransient;

/**
 * Implementation of {@link LocalPackage} that can be saved and loaded using JAXB.
 */
@XmlTransient
public abstract class LocalPackageImpl extends RepoPackageImpl implements LocalPackage {
    @XmlTransient
    private File mInstalledPath;

    @Override
    @NonNull
    public File getLocation() {
        return mInstalledPath;
    }

    @Override
    public void setInstalledPath(@NonNull File path) {
        mInstalledPath = path;
    }

    /**
     * Creates a {@link LocalPackageImpl} from an arbitrary {@link RepoPackage}. Useful if you
     * have a {@link RepoPackage} of unknown concrete type and want to marshal it using JAXB.
     */
    @NonNull
    public static LocalPackageImpl create(@NonNull RepoPackage repoPackage) {
        if (repoPackage instanceof LocalPackageImpl) {
            return (LocalPackageImpl)repoPackage;
        }
        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        LocalPackageImpl result = factory.createLocalPackage();
        result.setVersion(repoPackage.getVersion());
        result.setLicense(repoPackage.getLicense());
        result.setPath(repoPackage.getPath());
        for (Dependency d : repoPackage.getAllDependencies()) {
            Dependency newDep = factory.createDependencyType();
            newDep.setPath(d.getPath());
            newDep.setMinRevision(d.getMinRevision());
            result.addDependency(newDep);
        }
        result.setObsolete(repoPackage.obsolete());
        result.setTypeDetails(repoPackage.getTypeDetails());
        result.setDisplayName(repoPackage.getDisplayName());
        return result;
    }

    @NonNull
    @Override
    public RepoPackageImpl asMarshallable() {
        return this;
    }

    @Override
    public void addTo(@NonNull Repository repo) {
        repo.setLocalPackage(this);
    }
}
