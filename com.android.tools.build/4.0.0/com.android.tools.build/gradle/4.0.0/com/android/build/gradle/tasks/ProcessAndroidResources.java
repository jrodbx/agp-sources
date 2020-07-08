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

import com.android.SdkConstants;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import java.io.File;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Base class for process resources / create R class task, to satisfy existing variants API. */
public abstract class ProcessAndroidResources extends IncrementalTask {

    protected ApkData mainSplit;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getManifestFiles();

    // Used by the kotlin plugin.
    // Subclasses of this class should also declare this method as @Internal and have a separate
    // method/field that declares the output.
    @Internal
    public abstract File getSourceOutputDir();

    @Internal // getManifestFiles() is already marked as @InputFiles
    public File getManifestFile() {
        File manifestDirectory = getManifestFiles().get().getAsFile();
        Preconditions.checkNotNull(manifestDirectory);

        Preconditions.checkNotNull(mainSplit);
        return FileUtils.join(
                manifestDirectory, mainSplit.getDirName(), SdkConstants.ANDROID_MANIFEST_XML);
    }

    protected static boolean generatesProguardOutputFile(VariantScope variantScope) {
        return variantScope.getCodeShrinker() != null || variantScope.getType().isDynamicFeature();
    }
}
