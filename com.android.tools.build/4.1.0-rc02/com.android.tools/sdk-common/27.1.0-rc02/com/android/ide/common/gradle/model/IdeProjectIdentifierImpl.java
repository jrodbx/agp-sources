/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.ide.common.gradle.model;

import com.android.annotations.NonNull;
import com.android.builder.model.Dependencies;
import java.io.Serializable;
import java.util.Objects;

public class IdeProjectIdentifierImpl implements IdeProjectIdentifier, Serializable {

    @NonNull private final String buildId;
    @NonNull private final String projectPath;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    public IdeProjectIdentifierImpl() {
        buildId = "";
        projectPath = "";
    }

    public IdeProjectIdentifierImpl(@NonNull Dependencies.ProjectIdentifier projectIdentifier) {
        this.buildId = projectIdentifier.getBuildId();
        this.projectPath = projectIdentifier.getProjectPath();
    }

    @NonNull
    @Override
    public String getBuildId() {
        return buildId;
    }

    @NonNull
    @Override
    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IdeProjectIdentifierImpl that = (IdeProjectIdentifierImpl) o;
        return Objects.equals(buildId, that.buildId)
                && Objects.equals(projectPath, that.projectPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildId, projectPath);
    }

    @Override
    public String toString() {
        return "IdeProjectIdentifierImpl{"
                + "buildId='"
                + buildId
                + '\''
                + ", projectPath='"
                + projectPath
                + '\''
                + '}';
    }
}
