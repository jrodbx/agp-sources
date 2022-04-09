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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.SigningConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.packaging.createDefaultDebugStore
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreSigningConfig
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.BuilderConstants
import com.android.builder.signing.DefaultSigningConfig
import com.android.builder.utils.SynchronizedFile
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions.checkState
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.ExecutionException

/**
 * A Gradle Task to check that the keystore file is present for this variant's signing config.
 *
 * If the keystore is the default debug keystore, it will be created if it is missing.
 *
 * This task has no explicit inputs, but is forced to run if the signing config keystore file is
 * not present.
 *
 * As the task has no Inputs or Outputs, enabling caching serves no useful purpose. So it is
 * disabled by default.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.VERIFICATION)
abstract class ValidateSigningTask : NonIncrementalTask() {

    /**
     * Output directory to allow this task to be up-to-date, despite the the signing config file
     * not being modelled directly as an input or an output.
     */
    @get:OutputDirectory
    abstract val dummyOutputDirectory: DirectoryProperty

    @get:Internal
    abstract val signingConfigData: Property<SigningConfigData>

    @get:Internal
    abstract val defaultDebugKeystoreLocation: Property<File>

    override fun doTaskAction() = when {
        signingConfigData.get().storeFile == null -> throw InvalidUserDataException(
                """Keystore file not set for signing config ${signingConfigData.get().name}""")
        isSigningConfigUsingTheDefaultDebugKeystore() ->
            /* Check if the debug keystore is being used rather than directly checking if it
               already exists. A "fast path" of returning true if the store file is present would
               allow one task to return while another validate task has only partially written the
               default debug keystore file, which could lead to confusing transient build errors. */
            createDefaultDebugKeystoreIfNeeded()
        signingConfigData.get().storeFile?.isFile == true -> {
            /* Keystore file is present, allow the build to continue. */
        }
        else -> throw InvalidUserDataException(
                """Keystore file '${signingConfigData.get().storeFile?.absolutePath}' """
                        + """not found for signing config '${signingConfigData.get().name}'.""")
    }

    @Throws(ExecutionException::class, IOException::class)
    private fun createDefaultDebugKeystoreIfNeeded() {
        checkState(
            defaultDebugKeystoreLocation.isPresent,
            "Debug keystore location is not specified."
        )
        // Synchronized file with multi process locking requires that the parent directory of the
        // default debug keystore is present.
        val location = defaultDebugKeystoreLocation.get()
        FileUtils.mkdirs(location.parentFile)

        if (!location.parentFile.canWrite()) {
            throw IOException("""Unable to create debug keystore in """
                    + """${location.parentFile.absolutePath} because it is not writable.""")
        }

        /* Creating the debug keystore is done with the multi process file locking,
           to avoid one validate signing task from exiting early while the keystore is in the
           process of being written.
           The keystore is not locked in the task input presence check or where it is used at
           application packaging.

           This is generally safe as the keystore is only automatically created,
           never automatically deleted.  */
        SynchronizedFile
                .getInstanceWithMultiProcessLocking(location)
                .createIfAbsent { createDefaultDebugStore(it, this.logger) }
    }


    private fun isSigningConfigUsingTheDefaultDebugKeystore(): Boolean {
        val signingConfig = signingConfigData.get()
        return signingConfig.name == BuilderConstants.DEBUG &&
                signingConfig.keyAlias == DefaultSigningConfig.DEFAULT_ALIAS &&
                signingConfig.keyPassword == DefaultSigningConfig.DEFAULT_PASSWORD &&
                signingConfig.storePassword == DefaultSigningConfig.DEFAULT_PASSWORD &&
                signingConfig.storeType == KeyStore.getDefaultType() &&
                signingConfig.storeFile?.isSameFile(defaultDebugKeystoreLocation.get()) == true
    }

    private fun File?.isSameFile(other: File?) =
            this != null && other != null && FileUtils.isSameFile(this, other)

    /**
     * Always re-run if the store file is not present to prevent the task being UP-TO-DATE
     * if the keystore is deleted after the first run. (See [CreationAction.execute])
     * Other changes, such as the first time it is run, or if the project is cleaned, or if
     * the plugin classpath is changed will also cause this task to be re-run.
     */
    @VisibleForTesting
    fun forceRerun(): Boolean {
        val storeFile: File? = signingConfigData.map { it.storeFile }.orNull
        return storeFile == null || !storeFile.isFile
    }

    class CreationForAssetPackBundleAction(
        private val artifacts: ArtifactsImpl,
        private val signingConfig: SigningConfig
    ) : TaskCreationAction<ValidateSigningTask>() {

        override val type = ValidateSigningTask::class.java
        override val name = "validateSigning"

        override fun handleProvider(taskProvider: TaskProvider<ValidateSigningTask>) {
            super.handleProvider(taskProvider)
            artifacts.setInitialProvider(
                taskProvider,
                ValidateSigningTask::dummyOutputDirectory
            ).on(InternalArtifactType.VALIDATE_SIGNING_CONFIG)
        }

        override fun configure(task: ValidateSigningTask) {
            task.signingConfigData.set(SigningConfigData.fromDslSigningConfig(signingConfig))
            task.outputs.upToDateWhen { !task.forceRerun() }
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig,
        private val defaultDebugKeystoreLocation: File
    ) :
        VariantTaskCreationAction<ValidateSigningTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("validateSigning")
        override val type: Class<ValidateSigningTask>
            get() = ValidateSigningTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ValidateSigningTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ValidateSigningTask::dummyOutputDirectory
            ).on(InternalArtifactType.VALIDATE_SIGNING_CONFIG)
        }

        override fun configure(
            task: ValidateSigningTask
        ) {
            super.configure(task)

            val signingConfig = creationConfig.signingConfigImpl ?: throw IllegalStateException(
                "No signing config configured for variant " + creationConfig.name
            )
            task.signingConfigData.set(
                creationConfig.services.provider {
                    SigningConfigData.fromSigningConfig(signingConfig)
                }
            )
            task.defaultDebugKeystoreLocation.set(defaultDebugKeystoreLocation)
            task.outputs.upToDateWhen { !task.forceRerun() }
        }
    }


    class PrivacySandboxSdkCreationAction(
            private val artifacts: ArtifactsImpl,
            private val services: BaseServices
    ) : TaskCreationAction<ValidateSigningTask>() {

        constructor(config: GlobalTaskCreationConfig) : this(config.globalArtifacts, config.services)
        constructor(scope: PrivacySandboxSdkVariantScope): this(scope.artifacts, scope.services)

        override val name: String
            get() = "validatePrivacySandboxSdkSigning"
        override val type: Class<ValidateSigningTask>
            get() = ValidateSigningTask::class.java

        override fun handleProvider(
                taskProvider: TaskProvider<ValidateSigningTask>
        ) {
            super.handleProvider(taskProvider)

            artifacts.setInitialProvider(
                    taskProvider,
                    ValidateSigningTask::dummyOutputDirectory
            ).on(InternalArtifactType.VALIDATE_SIGNING_CONFIG)
        }

        override fun configure(
                task: ValidateSigningTask
        ) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)


            val signingConfigDataProvider: Provider<SigningConfigData> = getBuildService(
                    services.buildServiceRegistry,
                    AndroidLocationsBuildService::class.java
            ).map { it.getDefaultDebugKeystoreSigningConfig() }
            task.signingConfigData.set(signingConfigDataProvider)
            task.defaultDebugKeystoreLocation.set(signingConfigDataProvider.map { it.storeFile!! })
            task.outputs.upToDateWhen { !task.forceRerun() }
        }
    }
}
