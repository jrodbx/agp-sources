/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import java.util.List;
import java.util.Set;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

/** Base task for tasks that require an NdkConfig */
@DisableCachingByDefault
public abstract class NdkTask extends NonIncrementalTask {

    @Nullable
    private CoreNdkOptions ndkConfig;

    @Nullable
    @Internal("Temporary to suppress Gradle warnings (bug 135900510), may need more investigation")
    public CoreNdkOptions getNdkConfig() {
        return ndkConfig;
    }

    public void setNdkConfig(@Nullable CoreNdkOptions ndkConfig) {
        this.ndkConfig = ndkConfig;
    }

    @Input @Optional
    public String getModuleName() {
        if (getNdkConfig() == null) {
            return null;
        }
        return getNdkConfig().getModuleName();
    }

    @Input @Optional
    public String getcFlags() {
        if (getNdkConfig() == null) {
            return null;
        }
        return getNdkConfig().getcFlags();
    }

    @Input @Optional
    public List<String> getLdLibs() {
        if (getNdkConfig() == null) {
            return null;
        }
        return getNdkConfig().getLdLibs();
    }

    @Input @Optional
    public Set<String> getAbiFilters() {
        if (getNdkConfig() == null) {
            return null;
        }
        return getNdkConfig().getAbiFilters();
    }

    @Input @Optional
    public String getStl() {
        if (getNdkConfig() == null) {
            return null;
        }
        return getNdkConfig().getStl();
    }
}
