/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp.worker

import com.android.tools.utp.gradle.api.RunUtpWorkParameters
import com.android.tools.utp.gradle.api.UtpAction
import java.util.ServiceLoader
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.provider.ProviderFactory
import org.gradle.workers.WorkAction

/** Gradle WorkAction that executes the Unified Test Platform (UTP) runner. */
abstract class RunUtpWorkAction : WorkAction<RunUtpWorkParameters> {

  @get:Inject abstract val provider: ProviderFactory

  override fun execute() {
    val actionImpl = ServiceLoader.load(UtpAction::class.java).findFirst()
    if (actionImpl.isEmpty) {
      throw GradleException("UtpAction implementation class is missing.")
    }
    actionImpl.get().run(parameters, provider)
  }
}
