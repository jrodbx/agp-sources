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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.variant.TestVariantData;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * implementation of the {@link TestVariant} interface around an {@link TestVariantData} object.
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public class TestVariantImpl extends ApkVariantImpl implements TestVariant {

    @NonNull
    private final TestVariantData variantData;
    @NonNull
    private final BaseVariant testedVariantData;

    @Inject
    public TestVariantImpl(
            @NonNull TestVariantData variantData,
            @NonNull BaseVariant testedVariantData,
            @NonNull ObjectFactory objectFactory,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(objectFactory, readOnlyObjectProvider, outputs);
        this.variantData = variantData;
        this.testedVariantData = testedVariantData;
    }

    @Override
    @NonNull
    public TestVariantData getVariantData() {
        return variantData;
    }

    @Override
    @NonNull
    public BaseVariant getTestedVariant() {
        return testedVariantData;
    }

    @Override
    @Nullable
    public DefaultTask getConnectedInstrumentTest() {
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getConnectedInstrumentTestProvider()",
                        "variant.getConnectedInstrumentTest()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getConnectedTestTask().getOrNull();
    }

    @Nullable
    @Override
    public TaskProvider<Task> getConnectedInstrumentTestProvider() {
        // Double cast needed to satisfy the compiler
        //noinspection unchecked
        return (TaskProvider<Task>)
                (TaskProvider<?>) variantData.getTaskContainer().getConnectedTestTask();
    }

    @NonNull
    @Override
    public List<? extends DefaultTask> getProviderInstrumentTests() {
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getProviderInstrumentTestProviders()",
                        "variant.getProviderInstrumentTests()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData
                .getTaskContainer()
                .getProviderTestTaskList()
                .stream()
                .filter(TaskProvider::isPresent)
                .map(Provider::get)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public List<TaskProvider<Task>> getProviderInstrumentTestProviders() {
        return variantData
                .getTaskContainer()
                .getProviderTestTaskList()
                .stream()
                .filter(TaskProvider::isPresent)
                .map(
                        taskProvider -> {
                            // Double cast needed to satisfy the compiler
                            //noinspection unchecked
                            return (TaskProvider<Task>) (TaskProvider<?>) taskProvider;
                        })
                .collect(Collectors.toList());
    }
}
