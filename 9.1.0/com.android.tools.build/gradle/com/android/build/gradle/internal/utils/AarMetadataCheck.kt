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

package com.android.build.gradle.internal.utils

import com.android.repository.Revision

/** Utils for checking desugar library requirements from AAR metadata */
fun checkDesugarJdkVariant(
  variantFromAar: String,
  variantFromConsumer: String,
  errorMessages: MutableList<String>,
  dependency: String,
  projectPath: String,
) {
  val parsedVariantFromAar = parseDesugarJdkVariant(variantFromAar)
  val parsedVariantFromConsumer = parseDesugarJdkVariant(variantFromConsumer)
  if (parsedVariantFromAar.priority > parsedVariantFromConsumer.priority) {
    errorMessages.add(
      """
                        Dependency '$dependency' requires desugar_jdk_libs flavor to be at least
                        $variantFromAar for $projectPath, which is currently $variantFromConsumer

                        See https://d.android.com/r/tools/api-desugaring-flavors
                        for more details.
                    """
        .trimIndent()
    )
  }
}

fun checkDesugarJdkVersion(
  versionFromAar: String,
  versionFromConsumer: String,
  errorMessages: MutableList<String>,
  dependency: String,
  projectPath: String,
) {
  val parsedVersionFromAar = Revision.parseRevision(versionFromAar)
  val parsedVersionFromConsumer = Revision.parseRevision(versionFromConsumer)
  if (parsedVersionFromAar > parsedVersionFromConsumer) {
    errorMessages.add(
      """
                        Dependency '$dependency' requires desugar_jdk_libs version to be
                        $versionFromAar or above for $projectPath, which is currently $versionFromConsumer

                        See https://d.android.com/studio/build/library-desugaring for more
                        details.
                    """
        .trimIndent()
    )
  }
}
