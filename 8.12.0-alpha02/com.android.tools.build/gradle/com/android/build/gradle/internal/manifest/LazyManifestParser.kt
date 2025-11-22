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

package com.android.build.gradle.internal.manifest

import com.android.build.gradle.internal.services.ProjectServices
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.util.concurrent.atomic.AtomicBoolean

/**
 * a lazy manifest parser that can create a `Provider<ManifestData>`
 */
class LazyManifestParser(
    private val manifestFile: Provider<RegularFile>,
    private val manifestFileRequired: Boolean,
    private val manifestParsingAllowed: Provider<Boolean>,
    projectServices: ProjectServices,
): ManifestDataProvider {

    val logger = projectServices.logger
    var rawManifestData: ManifestData? = null
    val manifestDataCalculated = AtomicBoolean(false)
    val issueReporter: IssueReporter =
        // if we are in standard mode, use the logger directly as projectServices.issueReporter
        // is not compatible with configuration cache.
        if (projectServices.issueReporter.isInStandardEvaluationMode()) {
            object : IssueReporter() {
                val issues = mutableSetOf<Type>()
                override fun reportIssue(
                    type: Type,
                    severity: Severity,
                    exception: EvalIssueException
                ) {
                    issues.add(type)
                    if (severity == Severity.ERROR) {
                        logger.error(exception.message)
                    } else {
                        logger.warn(exception.message)
                    }
                }

                override fun hasIssue(type: Type): Boolean = issues.contains(type)
            }
        } else projectServices.issueReporter

    override val manifestData: Provider<ManifestData> =
        projectServices.providerFactory.of(ManifestValueSource::class.java) {
             it.parameters.manifestFile.set(manifestFile)
        }.map {
            if (!manifestDataCalculated.get()) {
                manifestDataCalculated.set(true)
                rawManifestData = parseManifest(
                    it,
                    manifestFile.get().asFile.absolutePath,
                    manifestFileRequired,
                    manifestParsingAllowed,
                    issueReporter,
                )
            }
            rawManifestData?: ManifestData("fake.package.name.for.sync")
        }

    override val manifestLocation: String
        get() = manifestFile.get().asFile.absolutePath
}
