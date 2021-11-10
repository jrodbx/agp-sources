/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ide.common.resources

import com.android.ide.common.blame.MergingLog
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.common.util.concurrent.MoreExecutors
import java.io.File

/**
 * A {@link MergeWriter} for resources, using {@link ResourceMergerItem}. Also takes care of
 * compiling resources and stripping data binding from layout files.
 *
 * @param rootFolder merged resources directory to write to (e.g. {@code
 *     intermediates/res/merged/debug}).
 * @param publicFile File that we should write public.txt to.
 * @param blameLog merging log for rewriting error messages.
 * @param preprocessor preprocessor for merged resources, such as vector drawable rendering.
 * @param resourceCompilationService such as AAPT. The service is responsible for ensuring all
 *     compilation is complete before the task execution ends.
 * @param temporaryDirectory temporary directory for intermediate merged files.
 * @param dataBindingExpressionRemover removes data binding expressions from layout files.
 * @param notCompiledOutputDirectory for saved uncompiled resources for the resource shrinking
 *     transform and for unit testing with resources.
 * @param pseudoLocalesEnabled generate resources for pseudo-locales (en-XA and ar-XB).
 * @param crunchPng should we crunch PNG files.
 * @param moduleSourceSets for determining source set ordering when writing relative resource paths.
 */
class MergedResourceWriterRequest(
        val workerExecutor: WorkerExecutorFacade,
        val rootFolder: File,
        val publicFile: File?,
        val blameLog: MergingLog?,
        val preprocessor: ResourcePreprocessor,
        val resourceCompilationService: ResourceCompilationService,
        val temporaryDirectory: File,
        val dataBindingExpressionRemover: SingleFileProcessor?,
        val notCompiledOutputDirectory: File?,
        val pseudoLocalesEnabled: Boolean,
        val crunchPng: Boolean,
        val moduleSourceSets: Map<String, String>
)
