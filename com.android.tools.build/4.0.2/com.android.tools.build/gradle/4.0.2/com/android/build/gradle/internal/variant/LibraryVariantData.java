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
package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.utils.StringHelper;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import org.gradle.api.Task;

/** Data about a variant that produce a Library bundle (.aar) */
public class LibraryVariantData extends BaseVariantData implements TestedVariantData {

    private final Map<VariantType, TestVariantData> testVariants;

    public LibraryVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull TaskManager taskManager,
            @NonNull VariantScope variantScope,
            @NonNull VariantDslInfoImpl variantDslInfo,
            @NonNull VariantImpl publicVariantApi,
            @NonNull VariantPropertiesImpl publicVariantPropertiesApi,
            @NonNull VariantSources variantSources) {

        super(
                globalScope,
                taskManager,
                variantScope,
                variantDslInfo,
                publicVariantApi,
                publicVariantPropertiesApi,
                variantSources);
        testVariants = Maps.newHashMap();

        // create default output
        getPublicVariantPropertiesApi()
                .addVariantOutput(
                        getOutputFactory()
                                .addMainOutput(
                                        globalScope.getProjectBaseName()
                                                + "-"
                                                + getVariantDslInfo().getBaseName()
                                                + "."
                                                + BuilderConstants.EXT_LIB_ARCHIVE));
    }

    @Override
    @NonNull
    public String getDescription() {
        final VariantDslInfo variantDslInfo = getVariantDslInfo();

        if (variantDslInfo.hasFlavors()) {
            StringBuilder sb = new StringBuilder(50);
            StringHelper.appendCapitalized(
                    sb, variantDslInfo.getComponentIdentity().getBuildType());
            sb.append(" build for flavor ");
            StringHelper.appendCapitalized(
                    sb, variantDslInfo.getComponentIdentity().getFlavorName());
            return sb.toString();
        } else {
            return StringHelper.capitalizeAndAppend(
                    variantDslInfo.getComponentIdentity().getBuildType(), " build");
        }
    }

    @Nullable
    @Override
    public TestVariantData getTestVariantData(@NonNull VariantType type) {
        return testVariants.get(type);
    }

    @Override
    public void setTestVariantData(
            @NonNull TestVariantData testVariantData, @NonNull VariantType type) {
        testVariants.put(type, testVariantData);
    }

    // Overridden to add source folders to a generateAnnotationsTask, if it exists.
    @Override
    public void registerJavaGeneratingTask(
            @NonNull Task task, @NonNull File... generatedSourceFolders) {
        super.registerJavaGeneratingTask(task, generatedSourceFolders);
        if (scope.getTaskContainer().getGenerateAnnotationsTask() != null) {
            for (File f : generatedSourceFolders) {
                // FIXME we need to revise this API as it force-configure the tasks
                scope.getTaskContainer().getGenerateAnnotationsTask().get().source(f);
            }
        }
    }

    // Overridden to add source folders to a generateAnnotationsTask, if it exists.
    @Override
    public void registerJavaGeneratingTask(
            @NonNull Task task, @NonNull Collection<File> generatedSourceFolders) {
        super.registerJavaGeneratingTask(task, generatedSourceFolders);
        if (scope.getTaskContainer().getGenerateAnnotationsTask() != null) {
            for (File f : generatedSourceFolders) {
                // FIXME we need to revise this API as it force-configure the tasks
                scope.getTaskContainer().getGenerateAnnotationsTask().get().source(f);
            }
        }
    }
}
