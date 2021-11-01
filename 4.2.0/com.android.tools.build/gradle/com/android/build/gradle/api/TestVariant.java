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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

/**
 * A Build variant and all its public data.
 */
public interface TestVariant extends ApkVariant {

    /**
     * Returns the build variant that is tested by this variant.
     */
    @NonNull
    BaseVariant getTestedVariant();

    /**
     * Returns the task to run the tests.
     *
     * <p>Only valid for test project.
     *
     * @deprecated Use {@link #getConnectedInstrumentTestProvider()}
     */
    @Nullable
    @Deprecated
    DefaultTask getConnectedInstrumentTest();

    /**
     * Returns the {@link TaskProvider} for the task to run the tests.
     *
     * <p>Only valid for test project.
     *
     * <p>Prefer this to {@link #getConnectedInstrumentTest()} as it triggers eager configuration of
     * the task.
     */
    @Nullable
    TaskProvider<Task> getConnectedInstrumentTestProvider();

    /**
     * Returns the tasks to run the tests.
     *
     * <p>Only valid for test project.
     *
     * @deprecated Use {@link #getProviderInstrumentTestProviders()}
     */
    @NonNull
    List<? extends DefaultTask> getProviderInstrumentTests();

    /**
     * Returns the {@link TaskProvider}s for the tasks to run the tests.
     *
     * <p>Only valid for test project.
     *
     * <p>Prefer this to {@link #getProviderInstrumentTests()} as it triggers eager configuration of
     * the tasks.
     */
    @NonNull
    List<TaskProvider<Task>> getProviderInstrumentTestProviders();

}
