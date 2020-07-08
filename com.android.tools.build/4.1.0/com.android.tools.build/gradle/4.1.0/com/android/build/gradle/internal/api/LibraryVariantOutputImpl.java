/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import static com.android.build.gradle.internal.api.BaseVariantImpl.TASK_ACCESS_DEPRECATION_URL;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.gradle.api.LibraryVariantOutput;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.scope.TaskContainer;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.builder.core.VariantType;
import java.io.File;
import javax.inject.Inject;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Implementation of variant output for library variants.
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public class LibraryVariantOutputImpl extends BaseVariantOutputImpl implements LibraryVariantOutput {

    @Inject
    public LibraryVariantOutputImpl(
            @NonNull TaskContainer taskContainer,
            @NonNull BaseServices services,
            @NonNull VariantOutputImpl variantOutput,
            @NonNull VariantType ignored) {
        super(taskContainer, services, variantOutput);
    }

    @Nullable
    @Override
    public Zip getPackageLibrary() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getPackageLibraryProvider()",
                        "variantOutput.getPackageLibrary()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return taskContainer.getBundleLibraryTask().getOrNull();
    }

    @NonNull
    @Override
    public File getOutputFile() {
        Zip packageTask = getPackageLibrary();
        if (packageTask != null) {
            return new File(
                    packageTask.getDestinationDirectory().get().getAsFile(),
                    variantOutput.getOutputFileName().get());
        } else {
            return super.getOutputFile();
        }
    }

    @Override
    public int getVersionCode() {
        throw new RuntimeException("Libraries are not versioned");
    }
}
