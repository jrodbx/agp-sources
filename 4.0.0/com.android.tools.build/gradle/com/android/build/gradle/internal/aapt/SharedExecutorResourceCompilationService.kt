/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.aapt

import com.android.aaptcompiler.canCompileResourceInJvm
import com.android.build.gradle.internal.res.Aapt2CompileRunnable
import com.android.build.gradle.internal.res.ResourceCompilerRunnable
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.Aapt2WorkersBuildService
import com.android.build.gradle.internal.services.aapt2WorkersServiceRegistry
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.ResourceCompilationService
import com.android.ide.common.workers.WorkerExecutorFacade
import java.io.File

/**
 * Resource compilation service built on top of a Aapt2Daemon that uses a static common thread pool.
 */
class SharedExecutorResourceCompilationService(
  projectName: String,
  owner: String,
  aapt2WorkersBuildServiceKey: WorkerActionServiceRegistry.ServiceKey<Aapt2WorkersBuildService>,
  private val aapt2ServiceKey: Aapt2DaemonServiceKey,
  private val errorFormatMode: SyncOptions.ErrorFormatMode,
  private val useJvmResourceCompiler: Boolean
) : ResourceCompilationService {

  private val workerExecutor: WorkerExecutorFacade =
    aapt2WorkersServiceRegistry
      .getService(aapt2WorkersBuildServiceKey)
      .service
      .getSharedExecutorForAapt2(projectName, owner)
  private val resourceCompilerExecutor = Workers.withThreads(projectName, owner)

  override fun submitCompile(request: CompileResourceRequest) {
    if (useJvmResourceCompiler &&
        canCompileResourceInJvm(request.inputFile, request.isPngCrunching)) {
      resourceCompilerExecutor.submit(
        ResourceCompilerRunnable::class.java,
        ResourceCompilerRunnable.Params(request)
      )
    } else {
      workerExecutor.submit(
        Aapt2CompileRunnable::class.java,
        Aapt2CompileRunnable.Params(aapt2ServiceKey, listOf(request), errorFormatMode, true)
      )
    }
  }

  fun submitCompile(requestList: List<CompileResourceRequest>) {
    requestList.forEach(this::submitCompile)
  }

  override fun compileOutputFor(request: CompileResourceRequest): File {
    return File(
      request.outputDirectory,
      Aapt2RenamingConventions.compilationRename(request.inputFile)
    )
  }

  override fun close() {
    resourceCompilerExecutor.close()
    workerExecutor.close()
  }
}
