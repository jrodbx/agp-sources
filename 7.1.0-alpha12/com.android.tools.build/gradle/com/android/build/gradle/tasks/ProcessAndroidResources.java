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
import com.android.annotations.NonNull;
import com.android.build.api.variant.impl.VariantOutputConfigurationImplKt;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.component.ConsumableCreationConfig;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import java.io.File;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

/** Base class for process resources / create R class task, to satisfy existing variants API. */
@DisableCachingByDefault
public abstract class ProcessAndroidResources extends IncrementalTask {

    protected VariantOutputImpl mainSplit;

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getAaptFriendlyManifestFiles();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getManifestFiles();

    // This input in not required for the task to function properly.
    // However, the implementation of getManifestFile() requires it to stay compatible with past
    // plugin and crashlitics related plugins are using it.
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    @Deprecated
    public abstract DirectoryProperty getMergedManifestFiles();

    // Used by the kotlin plugin.
    // Subclasses of this class should also declare this method as @Internal and have a separate
    // method/field that declares the output.
    @Internal
    @Deprecated
    public abstract File getSourceOutputDir();

    @Internal // getManifestFiles() is already marked as @InputFiles
    @Deprecated
    public File getManifestFile() {
        File manifestDirectory;
        if (getAaptFriendlyManifestFiles().isPresent()) {
            manifestDirectory = getAaptFriendlyManifestFiles().get().getAsFile();
        } else {
            if (getMergedManifestFiles().isPresent()) {
                manifestDirectory = getMergedManifestFiles().get().getAsFile();
            } else {
                manifestDirectory = getManifestFiles().get().getAsFile();
            }
        }
        Preconditions.checkNotNull(manifestDirectory);

        Preconditions.checkNotNull(mainSplit);
        return FileUtils.join(
                manifestDirectory,
                VariantOutputConfigurationImplKt.dirName(mainSplit),
                SdkConstants.ANDROID_MANIFEST_XML);
    }

    protected static boolean generatesProguardOutputFile(
            @NonNull ComponentCreationConfig creationConfig) {
        return (creationConfig instanceof ConsumableCreationConfig
                        && ((ConsumableCreationConfig) creationConfig).getMinifiedEnabled())
                || creationConfig.getVariantType().isDynamicFeature();
    }
}
