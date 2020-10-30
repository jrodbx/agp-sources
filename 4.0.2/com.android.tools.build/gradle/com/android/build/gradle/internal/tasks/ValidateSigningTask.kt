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

import com.android.build.gradle.internal.dsl.SigningConfig
import com.google.common.annotations.VisibleForTesting
import com.android.build.gradle.internal.packaging.createDefaultDebugStore
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.BuilderConstants
import com.android.builder.signing.DefaultSigningConfig
import com.android.builder.utils.SynchronizedFile
import com.android.utils.FileUtils
import com.google.common.base.Preconditions.checkState
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
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
 */
@CacheableTask
abstract class ValidateSigningTask : NonIncrementalTask() {

    /**
     * Output directory to allow this task to be up-to-date, despite the the signing config file
     * not being modelled directly as an input or an output.
     */
    @get:OutputDirectory
    abstract val dummyOutputDirectory: DirectoryProperty

    @get:Internal internal lateinit var signingConfig: SigningConfig
    @get:Internal internal lateinit var defaultDebugKeystoreLocation: File

    override fun doTaskAction() = when {
        signingConfig.storeFile == null -> throw InvalidUserDataException(
                """Keystore file not set for signing config ${signingConfig.name}""")
        isSigningConfigUsingTheDefaultDebugKeystore() ->
            /* Check if the debug keystore is being used rather than directly checking if it
               already exists. A "fast path" of returning true if the store file is present would
               allow one task to return while another validate task has only partially written the
               default debug keystore file, which could lead to confusing transient build errors. */
            createDefaultDebugKeystoreIfNeeded()
        signingConfig.storeFile?.isFile == true -> {
            /* Keystore file is present, allow the build to continue. */
        }
        else -> throw InvalidUserDataException(
                """Keystore file '${signingConfig.storeFile?.absolutePath}' """
                        + """not found for signing config '${signingConfig.name}'.""")
    }

    @Throws(ExecutionException::class, IOException::class)
    private fun createDefaultDebugKeystoreIfNeeded() {

        checkState(signingConfig.isSigningReady, "Debug signing config not ready.")
        if (!defaultDebugKeystoreLocation.parentFile.canWrite()) {
            throw IOException("""Unable to create debug keystore in """
                    + """${defaultDebugKeystoreLocation.parentFile.absolutePath} because it is not writable.""")
        }

        /* Synchronized file with multi process locking requires that the parent directory of the
           default debug keystore is present.
           It is created as part of KeystoreHelper.defaultDebugKeystoreLocation() */
        checkState(FileUtils.parentDirExists(defaultDebugKeystoreLocation),
                "Parent directory of the default debug keystore '%s' does not exist",
                defaultDebugKeystoreLocation)
        /* Creating the debug keystore is done with the multi process file locking,
           to avoid one validate signing task from exiting early while the keystore is in the
           process of being written.
           The keystore is not locked in the task input presence check or where it is used at
           application packaging.

           This is generally safe as the keystore is only automatically created,
           never automatically deleted.  */
        SynchronizedFile
                .getInstanceWithMultiProcessLocking(defaultDebugKeystoreLocation)
                .createIfAbsent { createDefaultDebugStore(it, this.logger) }
    }


    private fun isSigningConfigUsingTheDefaultDebugKeystore(): Boolean {
        return signingConfig.name == BuilderConstants.DEBUG &&
                signingConfig.keyAlias == DefaultSigningConfig.DEFAULT_ALIAS &&
                signingConfig.keyPassword == DefaultSigningConfig.DEFAULT_PASSWORD &&
                signingConfig.storePassword == DefaultSigningConfig.DEFAULT_PASSWORD &&
                signingConfig.storeType == KeyStore.getDefaultType() &&
                signingConfig.storeFile.isSameFile(defaultDebugKeystoreLocation)
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
    fun forceRerun() = signingConfig.storeFile?.isFile != true

    class CreationAction(
        variantScope: VariantScope,
        private val defaultDebugKeystoreLocation: File
    ) :
        VariantTaskCreationAction<ValidateSigningTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("validateSigning")
        override val type: Class<ValidateSigningTask>
            get() = ValidateSigningTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ValidateSigningTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.VALIDATE_SIGNING_CONFIG,
                taskProvider,
                ValidateSigningTask::dummyOutputDirectory
            )
        }

        override fun configure(task: ValidateSigningTask) {
            super.configure(task)

            task.signingConfig = variantScope.variantDslInfo.signingConfig ?: throw IllegalStateException(
                "No signing config configured for variant " + variantScope.name
            )
            task.defaultDebugKeystoreLocation = defaultDebugKeystoreLocation
            task.outputs.upToDateWhen { !task.forceRerun() }
        }
    }
}
