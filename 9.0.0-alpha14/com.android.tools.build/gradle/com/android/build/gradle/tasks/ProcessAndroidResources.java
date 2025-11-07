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

package com.android.build.gradle.tasks;

import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.gradle.internal.tasks.BuildAnalyzer;
import com.android.build.gradle.internal.tasks.NewIncrementalTask;
import com.android.buildanalyzer.common.TaskCategory;
import java.io.File;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

/** Base class for process resources / create R class task, to satisfy existing variants API. */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
public abstract class ProcessAndroidResources extends NewIncrementalTask {

    protected VariantOutputImpl mainSplit;

    // Used by the kotlin plugin.
    // Subclasses of this class should also declare this method as @Internal and have a separate
    // method/field that declares the output.
    @Internal
    @Deprecated
    public abstract File getSourceOutputDir();

    /**
     * The implementation of getManifestFile() requires it to stay compatible with past plugin and
     * crashlitics related plugins are using it.
     *
     * @return manifest file
     */
    @Internal
    @Deprecated
    public abstract File getManifestFile();
}
