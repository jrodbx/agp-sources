/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.gradle.internal.profile.AnalyticsService;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.tasks.BuildAnalyzer;
import com.android.builder.profile.Recorder;
import com.android.build.gradle.internal.tasks.TaskCategory;
import com.android.ide.common.util.ReferenceHolder;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.io.IOException;
import java.util.List;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = {TaskCategory.SOURCE_PROCESSING})
public abstract class NonIncrementalTransformTask extends TransformTask {
    @TaskAction
    void transform() throws IOException, TransformException, InterruptedException {

        final ReferenceHolder<List<TransformInput>> consumedInputs = ReferenceHolder.empty();
        final ReferenceHolder<List<TransformInput>> referencedInputs = ReferenceHolder.empty();

        GradleTransformExecution preExecutionInfo =
                GradleTransformExecution.newBuilder()
                        .setType(
                                AnalyticsUtil.getTransformType(getTransform().getClass())
                                        .getNumber())
                        .setIsIncremental(false)
                        .setTransformClassName(getTransform().getClass().getName())
                        .build();

        AnalyticsService analyticsService = getAnalyticsService().get();
        analyticsService.recordBlock(
                GradleBuildProfileSpan.ExecutionType.TASK_TRANSFORM_PREPARATION,
                preExecutionInfo,
                getProjectPath().get(),
                getVariantName(),
                new Recorder.VoidBlock() {
                    @Override
                    public void call() {
                        consumedInputs.setValue(computeNonIncTransformInput(consumedInputStreams));
                        referencedInputs.setValue(
                                computeNonIncTransformInput(referencedInputStreams));
                    }
                });

        runTransform(
                consumedInputs.getValue(),
                referencedInputs.getValue(),
                /*isIncremental=*/ false,
                ImmutableList.of(),
                preExecutionInfo,
                analyticsService);
    }
}
