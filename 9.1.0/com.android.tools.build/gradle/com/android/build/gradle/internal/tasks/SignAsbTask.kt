/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.signing.SigningConfigDataProvider
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.internal.packaging.AabFlinger
import com.android.ide.common.signing.KeystoreHelper
import com.android.utils.FileUtils
import java.util.Locale
import java.util.zip.Deflater
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.BUNDLE_PACKAGING)
abstract class SignAsbTask : NonIncrementalGlobalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val inputAsb: RegularFileProperty

  @get:Nested abstract val signingConfig: Property<SigningConfigDataProvider>

  @get:OutputFile abstract val outputSignedAsb: RegularFileProperty

  override fun doTaskAction() {
    signingConfig.get().let {
      it.signingConfigData.orNull?.let { signingConfig ->
        val certificateInfo =
          KeystoreHelper.getCertificateInfo(
            signingConfig.storeType,
            signingConfig.storeFile,
            signingConfig.storePassword,
            signingConfig.keyPassword,
            signingConfig.keyAlias,
          )
        AabFlinger(
            outputFile = outputSignedAsb.asFile.get(),
            signerName = signingConfig.keyAlias?.uppercase(Locale.US)!!,
            privateKey = certificateInfo.key,
            certificates = listOf(certificateInfo.certificate),
            minSdkVersion = 18, // So that RSA + SHA256 are used
          )
          .use { aabFlinger -> aabFlinger.writeZip(inputAsb.get().asFile, Deflater.DEFAULT_COMPRESSION) }
      } ?: FileUtils.copyFile(inputAsb.get().asFile, outputSignedAsb.get().asFile)
    }
  }
}
