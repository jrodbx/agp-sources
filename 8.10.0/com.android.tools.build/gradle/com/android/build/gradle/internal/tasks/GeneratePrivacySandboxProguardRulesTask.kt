/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.PrivacySandboxSdkVariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import java.nio.file.Files
import java.util.SortedSet

/**
 * Generates the Proguard keep rules file `sandbox_proguard_rules.txt` using information
 * from the privacy sandbox extension DSL
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class GeneratePrivacySandboxProguardRulesTask : NonIncrementalGlobalTask() {

    @get:Input
    @get:Optional
    abstract val compatSdkProviderClassName: Property<String>

    @get:Input
    @get:Optional
    abstract val sdkProviderClassName: Property<String>

    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputFile
    abstract val proguardOutputFile: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            GenerateProguardRulesWorkAction::class.java
        ) {
            it.initializeFromBaseTask(this)
            it.compatSdkProviderClassName.set(compatSdkProviderClassName)
            it.sdkProviderClassName.set(sdkProviderClassName)
            it.applicationId.set(applicationId)
            it.proguardOutputFile.set(proguardOutputFile)
        }
    }

    abstract class GenerateProguardRulesWorkAction : ProfileAwareWorkAction<GenerateProguardRulesWorkAction.Params>()
    {
        abstract class Params : Parameters() {
            abstract val applicationId: Property<String>
            abstract val sdkProviderClassName: Property<String>
            abstract val compatSdkProviderClassName: Property<String>
            abstract val proguardOutputFile: RegularFileProperty
        }

        override fun run() {
            runTask(parameters)
        }
    }

    class CreationAction(val creationConfig: PrivacySandboxSdkVariantScope) :
        GlobalTaskCreationAction<GeneratePrivacySandboxProguardRulesTask>() {

        override val name: String
            get() = "generatePrivacySandboxProguardRules"
        override val type: Class<GeneratePrivacySandboxProguardRulesTask>
            get() = GeneratePrivacySandboxProguardRulesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<GeneratePrivacySandboxProguardRulesTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GeneratePrivacySandboxProguardRulesTask::proguardOutputFile
            ).withName("sandbox_proguard_rules.txt")
                .on(PrivacySandboxSdkInternalArtifactType.GENERATED_PROGUARD_FILE)
        }

        override fun configure(task: GeneratePrivacySandboxProguardRulesTask) {
            super.configure(task)

            task.compatSdkProviderClassName.setDisallowChanges(creationConfig.bundle.compatSdkProviderClassName)
            task.sdkProviderClassName.setDisallowChanges(creationConfig.bundle.sdkProviderClassName)
            task.applicationId.setDisallowChanges(creationConfig.bundle.applicationId)
        }
    }
}

internal fun runTask(
    parameters: GeneratePrivacySandboxProguardRulesTask.GenerateProguardRulesWorkAction.Params
) {
    Files.write(
        parameters.proguardOutputFile.get().asFile.toPath(),
        generateSandboxKeepRules(
            parameters.applicationId.get(),
            parameters.sdkProviderClassName.orNull,
            parameters.compatSdkProviderClassName.orNull
        )
    )
}

fun generateSandboxKeepRules(
    applicationId: String,
    sdkProviderClassName: String?,
    compatSdkProviderClassName: String?
): Set<String> {
    val rules: SortedSet<String> = sortedSetOf()
    rules.add("# Generated by the privacy sandbox gradle plugin")
    rules.addAll(getInterfaceKeepRules())

    rules.add("-ignorewarnings")
    sdkProviderClassName.let { rules.add("$KEEP_RULE_CLASS_PREFIX$it$KEEP_RULE_SUFFIX") }
    compatSdkProviderClassName.let { rules.add("${KEEP_RULE_CLASS_PREFIX}$it$KEEP_RULE_SUFFIX") }
    rules.add("${KEEP_RULE_CLASS_PREFIX}$applicationId.RPackage$KEEP_RULE_SUFFIX")

    return rules
}

private fun getInterfaceKeepRules(): Set<String> {
    return sortedSetOf(
        "${KEEP_RULE_PREFIX}@androidx.privacysandbox.tools.PrivacySandboxValue interface **$KEEP_RULE_SUFFIX",
        "${KEEP_RULE_PREFIX}@androidx.privacysandbox.tools.PrivacySandboxInterface interface **$KEEP_RULE_SUFFIX",
        "${KEEP_RULE_PREFIX}@androidx.privacysandbox.tools.PrivacySandboxService interface **$KEEP_RULE_SUFFIX",
        "${KEEP_RULE_PREFIX}@androidx.privacysandbox.tools.PrivacySandboxCallback interface **$KEEP_RULE_SUFFIX",
        "${KEEP_RULE_PREFIX}@androidx.privacysandbox.tools.PrivacySandboxValue class **$KEEP_RULE_SUFFIX",
        "${KEEP_RULE_PREFIX}@androidx.privacysandbox.tools.PrivacySandboxInterface class **$KEEP_RULE_SUFFIX",
        "${KEEP_RULE_PREFIX}@androidx.privacysandbox.tools.PrivacySandboxService class **$KEEP_RULE_SUFFIX",
        "${KEEP_RULE_PREFIX}@androidx.privacysandbox.tools.PrivacySandboxCallback class **$KEEP_RULE_SUFFIX",
    )
}

private const val KEEP_RULE_CLASS_PREFIX = "-keep class "
private const val KEEP_RULE_PREFIX = "-keep "
private const val KEEP_RULE_SUFFIX = " { *; }"
