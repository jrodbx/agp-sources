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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AgpTestSuite
import com.android.build.api.dsl.TargetSdkSpec
import com.android.build.api.dsl.TargetSdkVersion
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.utils.updateIfChanged
import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.apiVersionFromString
import com.android.builder.errors.IssueReporter
import com.android.builder.model.ApiVersion
import com.android.builder.model.TestOptions.Execution
import com.android.builder.model.v2.ide.ProjectType
import com.android.utils.HelpfulEnumConverter
import com.google.common.base.Preconditions
import com.google.common.base.Verify
import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.tasks.testing.Test
import javax.inject.Inject

abstract class TestOptions @Inject constructor(
    private val dslServices: DslServices
) : com.android.build.api.dsl.TestOptions {

    private val executionConverter = HelpfulEnumConverter<Execution>(Execution::class.java)

    private var _execution = Execution.HOST

    override val unitTests: UnitTestOptions =
        dslServices.newInstance(UnitTestOptions::class.java, dslServices)

    override var resultsDir: String? = null
    override var reportDir: String? = null
    override var animationsDisabled: Boolean = false

    override val managedDevices: ManagedDevices =
        dslServices.newInstance(ManagedDevices::class.java, dslServices)

    // (Implementing interface for kotlin)
    override fun managedDevices(action: com.android.build.api.dsl.ManagedDevices.() -> Unit) {
        action.invoke(managedDevices)
    }

    // Runtime only for groovy decorator to generate the closure based block
    fun managedDevices(action: Action<com.android.build.api.dsl.ManagedDevices>) {
        action.execute(managedDevices)
    }

    override var execution: String
        get() = Verify.verifyNotNull(
            executionConverter.reverse().convert(_execution),
            "No string representation for enum."
        )
        set(value) {
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            _execution = Preconditions.checkNotNull(
                executionConverter.convert(value),
                "The value of `execution` cannot be null."
            )
        }

    @Incubating
    override val emulatorControl: com.android.build.api.dsl.EmulatorControl  =
        dslServices.newDecoratedInstance(EmulatorControl::class.java, dslServices)

    override fun unitTests(action: com.android.build.api.dsl.UnitTestOptions.() -> Unit) {
        action.invoke(unitTests)
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2.0
     */
    fun unitTests(action: Action<UnitTestOptions>) {
        action.execute(unitTests)
    }

    fun getExecutionEnum(): Execution = _execution

    abstract class UnitTestOptions @Inject constructor(dslServices: DslServices) :
        com.android.build.api.dsl.UnitTestOptions {
        // Used by testTasks.all below, DSL docs generator can't handle diamond operator.
        private val testTasks = dslServices.domainObjectSet(Test::class.java)

        override var isReturnDefaultValues: Boolean = false
        override var isIncludeAndroidResources: Boolean = false

        fun all(configAction: Action<Test>) {
            testTasks.configureEach(configAction)
        }

        override fun all(configAction: (Test) -> Unit) {
            testTasks.configureEach(configAction)
        }

        /**
         * Configures a given test task. The configuration closures that were passed to {@link
         * #all(Closure)} will be applied to it.
         *
         * <p>Not meant to be called from build scripts. The reason it exists is that tasks are
         * created after the build scripts are evaluated, so users have to "register" their
         * configuration closures first and we can only apply them later.
         *
         * @since 1.2.0
         */
        fun applyConfiguration(task: Test) {
            testTasks.add(task)
        }
    }

    // (Implementing interface for kotlin)
    override fun emulatorControl(action: com.android.build.api.dsl.EmulatorControl.() -> Unit) {
        action.invoke(emulatorControl)
    }

    // Runtime only for groovy decorator to generate the closure based block
    fun emulatorControl(action: Action<com.android.build.api.dsl.EmulatorControl>) {
        action.execute(emulatorControl)
    }

    private var targetSdkVersion: TargetSdkVersion? = null

    override var targetSdk: Int?
        get() = targetSdkVersion?.apiLevel
        set(value) {
            if(dslServices.projectType != ProjectType.LIBRARY){
                dslServices.issueReporter.reportError(IssueReporter.Type.GENERIC,
                    RuntimeException("targetSdk is set as $value in testOptions for non library module"))
            }
            targetSdk {
                version = value?.let{ release(it) }
            }
        }
    override var targetSdkPreview: String?
        get() = targetSdkVersion?.codeName
        set(value) {
            if(dslServices.projectType != ProjectType.LIBRARY){
                dslServices.issueReporter.reportError(IssueReporter.Type.GENERIC,
                    RuntimeException("targetSdkPreview is set as $value in testOptions for non library module"))
            }
            targetSdk {
                version = value?.let { preview(it) }
            }
        }

    //TODO(b/421964815): remove the support for groovy space assignment(e.g `targetSdk 24`).
    @Deprecated(
        "To be removed after Gradle drops space assignment support",
        ReplaceWith("targetSdk { version = release(value) }")
    )
    open fun targetSdk(version: Int?) {
        this.targetSdk = version
    }

    override fun targetSdk(action: TargetSdkSpec.() -> Unit) {
        createTargetSdkSpec().also {
            action.invoke(it)
            updateIfChanged(targetSdkVersion, it.version ) { version ->
                checkNonLibraryModule(version)
                targetSdkVersion = version
            }
        }
    }

    open fun targetSdk(action: Action<TargetSdkSpec>) {
        createTargetSdkSpec().also {
            action.execute(it)
            updateIfChanged(targetSdkVersion, it.version) { version ->
                checkNonLibraryModule(version)
                targetSdkVersion = version
            }
        }
    }


    override val suites: ExtensiblePolymorphicDomainObjectContainer< AgpTestSuite> by lazy {
        class AgpTestSuiteFactory : NamedDomainObjectFactory<AgpTestSuite> {

            override fun create(name: String): AgpTestSuite {
                return dslServices.newInstance(
                    AgpTestSuiteImpl::class.java,
                    name,
                    dslServices,
                    unitTests.isIncludeAndroidResources
                )
            }

        }
        dslServices.polymorphicDomainObjectContainer(AgpTestSuite::class.java).apply {
            registerFactory(
                AgpTestSuite::class.java,
                AgpTestSuiteFactory()
            )
        }
    }

    private fun createTargetSdkSpec(): TargetSdkSpecImpl {
        return dslServices.newDecoratedInstance(TargetSdkSpecImpl::class.java, dslServices).also {
            it.version = targetSdkVersion
        }
    }

    private fun checkNonLibraryModule(version: TargetSdkVersion?) {
        if(dslServices.projectType != ProjectType.LIBRARY && version != null){
            val representation = if (version.codeName != null) "preview(\"${version.codeName}\")" else "release(${version.apiLevel})"
            dslServices.issueReporter.reportError(IssueReporter.Type.GENERIC,
                RuntimeException("targetSdk is set as version = $representation in testOptions for non library module"))
        }
    }
}
