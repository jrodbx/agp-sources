/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins

import com.android.Version
import com.android.build.api.dsl.ExecutionProfile
import com.android.build.api.dsl.SettingsExtension
import com.android.build.gradle.internal.attribution.BuildAnalyzerConfiguratorService
import com.android.build.gradle.internal.attribution.BuildAnalyzerService
import com.android.build.gradle.internal.configurationCacheActive
import com.android.build.gradle.internal.core.DEFAULT_EXECUTION_PROFILE
import com.android.build.gradle.internal.core.ExecutionProfileOptions
import com.android.build.gradle.internal.core.SettingsOptions
import com.android.build.gradle.internal.core.ToolExecutionOptions
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.lint.LintFromMaven.Companion.from
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.profile.NoOpAnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.NoOpAnalyticsService
import com.android.build.gradle.internal.projectIsolationActive
import com.android.build.gradle.internal.registerDependencyCheck
import com.android.build.gradle.internal.res.Aapt2FromMaven.Companion.create
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.utils.MUTUALLY_EXCLUSIVE_ANDROID_GRADLE_PLUGINS
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptionService
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.errors.IssueReporter.Type
import com.google.common.base.CharMatcher
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.tasks.StopExecutionException
import org.gradle.build.event.BuildEventsListenerRegistry
import java.util.Locale

abstract class AndroidPluginBaseServices(
    private val listenerRegistry: BuildEventsListenerRegistry
) {

    private val optionService: ProjectOptionService by lazy {
        withProject("optionService") {
            ProjectOptionService.RegistrationAction(it).execute().get()
        }
    }

    protected val syncIssueReporter: SyncIssueReporterImpl by lazy {
        withProject("syncIssueReporter") {
            SyncIssueReporterImpl(
                SyncOptions.getModelQueryMode(optionService.projectOptions),
                SyncOptions.getErrorFormatMode(optionService.projectOptions),
                it.logger
            )
        }
    }

    @JvmField
    protected var project: Project? = null

    protected val projectServices: ProjectServices by lazy {
        withProject("projectServices") { project ->
            val projectOptions = optionService.projectOptions
            ProjectServices(
                syncIssueReporter,
                DeprecationReporterImpl(syncIssueReporter, projectOptions, project.path),
                project.objects,
                project.logger,
                project.providers,
                project.layout,
                projectOptions,
                project.gradle.sharedServices,
                from(project, projectOptions, syncIssueReporter),
                create(project, projectOptions::get),
                project.gradle.startParameter.maxWorkerCount,
                ProjectInfo(project),
                { o: Any -> project.file(o) },
                project.configurations,
                project.dependencies,
                project.extensions.extraProperties,
                { name: String -> project.tasks.register(name) }
            )
        }
    }

    protected val configuratorService: AnalyticsConfiguratorService by lazy {
        withProject("configuratorService") { project ->
            val projectOptions: ProjectOptions = projectServices.projectOptions
            if (projectOptions.isAnalyticsEnabled) {
                AnalyticsConfiguratorService.RegistrationAction(project).execute().get()
            } else {
                NoOpAnalyticsConfiguratorService.RegistrationAction(project).execute().get()
            }
        }
    }

    protected open fun basePluginApply(project: Project, buildFeatures: BuildFeatures) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true")

        this.project = project
        AndroidLocationsBuildService.RegistrationAction(project).execute()
        checkPluginsCompatibility(project)
        checkMinJvmVersion()
        val projectOptions: ProjectOptions = projectServices.projectOptions
        if (projectOptions.isAnalyticsEnabled) {
            AnalyticsService.RegistrationAction(
                project,
                configuratorService,
                listenerRegistry,
                buildFeatures.configurationCacheActive(),
                buildFeatures.projectIsolationActive(),
            ).execute()
        } else {
            NoOpAnalyticsService.RegistrationAction(project).execute()
        }

        SyncIssueReporterImpl.GlobalSyncIssueService.RegistrationAction(
            project,
            SyncOptions.getModelQueryMode(projectOptions),
            SyncOptions.getErrorFormatMode(projectOptions)
        ).execute()

        registerDependencyCheck(project, projectOptions)
        checkPathForErrors()
        val attributionFileLocation =
            projectOptions.get(StringOption.IDE_ATTRIBUTION_FILE_LOCATION)
        if (attributionFileLocation != null) {
            BuildAnalyzerService.RegistrationAction(
                project,
                attributionFileLocation,
                listenerRegistry,
                BuildAnalyzerConfiguratorService.RegistrationAction(
                    project
                ).execute().get()
            ).execute()
        }
        configuratorService.getProjectBuilder(project.path)?.let {
            it
                .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setAndroidPlugin(getAnalyticsPluginType())
                .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST).options =
                AnalyticsUtil.toProto(projectOptions)
        }
        configuratorService.recordBlock(
            ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
            project.path,
            null
        ) { configureProject(project) }
        configuratorService.recordBlock(
            ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
            project.path,
            null,
        ) {
            configureExtension(project)
        }
        configuratorService.recordBlock(
            ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
            project.path,
            null,
        ) {
            createTasks(project)
        }
    }

    private fun checkPluginsCompatibility(project: Project) {
        val currentPlugin = MUTUALLY_EXCLUSIVE_ANDROID_GRADLE_PLUGINS[this::class.java]
        val incompatiblePlugin = currentPlugin?.let {
            MUTUALLY_EXCLUSIVE_ANDROID_GRADLE_PLUGINS.entries.firstOrNull {
                it.value != currentPlugin && project.pluginManager.hasPlugin(it.value)
            }
        }

        if (incompatiblePlugin != null) {
            error(
                "'$currentPlugin' and '${incompatiblePlugin.value}' plugins cannot be applied in the same project."
            )
        }
    }

    protected val settingsExtension: SettingsExtension? by lazy(LazyThreadSafetyMode.NONE) {
        // Query for the settings extension via extra properties.
        // This is deposited here by the SettingsPlugin
        val properties = project?.extensions?.extraProperties
        if (properties?.has("_android_settings") == true) {
            properties.get("_android_settings") as? SettingsExtension
        } else {
            null
        }
    }

    // Create settings options, to be used in the global config,
    // with values from the android settings extension
    protected fun createSettingsOptions(
        dslServices: DslServices
    ): SettingsOptions {
        // resolve settings extension
        val actualSettingsExtension = settingsExtension ?: run {
            dslServices.logger.info("Using default execution profile")
            return SettingsOptions(DEFAULT_EXECUTION_PROFILE)
        }

        // Map the profiles to make it easier to look them up
        val executionProfiles = actualSettingsExtension.execution.profiles.associateBy { profile -> profile.name }

        val buildProfileOptions = { profile: ExecutionProfile ->
            ExecutionProfileOptions(
                name = profile.name,
                r8Options = profile.r8.let { r8 ->
                    ToolExecutionOptions(
                        jvmArgs = r8.jvmOptions,
                        runInSeparateProcess = r8.runInSeparateProcess
                    )
                }
            )
        }

        // If the string option is set use that one instead
        val actualProfileName =
            dslServices.projectOptions[StringOption.EXECUTION_PROFILE_SELECTION] ?:
            actualSettingsExtension.execution.defaultProfile
        // Find the selected (or the only) profile
        val executionProfile =
            if (actualProfileName == null) {
                if (executionProfiles.isEmpty()) { // No profiles declared, and none selected, return default
                    dslServices.logger.info("Using default execution profile")
                    DEFAULT_EXECUTION_PROFILE
                } else if (executionProfiles.size == 1) { // if there is exactly one profile use that
                    dslServices.logger.info("Using only execution profile '${executionProfiles.keys.first()}'")
                    buildProfileOptions(executionProfiles.values.first())
                } else { // no profile selected
                    dslServices.issueReporter.reportError(Type.GENERIC, "Found ${executionProfiles.size} execution profiles ${executionProfiles.keys}, but no profile was selected.\n")
                    null
                }
            } else {
                if (!executionProfiles.containsKey(actualProfileName)) { // invalid profile selected
                    dslServices.issueReporter.reportError(Type.GENERIC,"Selected profile '$actualProfileName' does not exist")
                    null
                } else {
                    if (actualProfileName == dslServices.projectOptions[StringOption.EXECUTION_PROFILE_SELECTION]) {
                        dslServices.logger.info("Using execution profile from android.settings.executionProfile '$actualProfileName'")
                    } else {
                        dslServices.logger.info("Using execution profile from dsl '$actualProfileName'")
                    }

                    buildProfileOptions(executionProfiles[actualProfileName]!!)
                }
            }

        return SettingsOptions(executionProfile = executionProfile)
    }

    private fun checkPathForErrors() {
        // See if we're on Windows:
        if (!System.getProperty("os.name").lowercase(Locale.US).contains("windows")) {
            return
        }

        // See if the user disabled the check:
        if (projectServices.projectOptions.get(BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY)) {
            return
        }

        // See if the path contains non-ASCII characters.
        if (CharMatcher.ascii().matchesAllOf(project!!.rootDir.absolutePath)) {
            return
        }
        val message = ("Your project path contains non-ASCII characters. This will most likely "
                + "cause the build to fail on Windows. Please move your project to a different "
                + "directory. See http://b.android.com/95744 for details. "
                + "This warning can be disabled by adding the line '"
                + BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY.propertyName
                + "=true' to gradle.properties file in the project directory.")
        throw StopExecutionException(message)
    }

    protected open fun checkMinJvmVersion() {
        val current: JavaVersion = JavaVersion.current()
        val minRequired: JavaVersion = JavaVersion.VERSION_17
        if (!current.isCompatibleWith(minRequired)) {
            syncIssueReporter.reportError(
                Type.AGP_USED_JAVA_VERSION_TOO_LOW,
                "Android Gradle plugin requires Java $minRequired to run. " +
                        "You are currently using Java $current.\n Your current JDK is located " +
                        "in ${System.getProperty("java.home")}\n " +
                        "You can try some of the following options:\n" +
                        "  - changing the IDE settings.\n" +
                        "  - changing the JAVA_HOME environment variable.\n" +
                        "  - changing `org.gradle.java.home` in `gradle.properties`."
            )
        }
    }

    protected abstract fun configureProject(project: Project)

    protected abstract fun configureExtension(project: Project)

    protected abstract fun createTasks(project: Project)

    protected abstract fun getAnalyticsPluginType(): GradleBuildProject.PluginType?

    /**
     * Runs a lambda function if [project] has been initialized and return the function's result or
     * generate an exception if [project] is null.
     *
     * This is useful to have not nullable val field that depends on [project] being initialized.
     */
    protected fun <T> withProject(context: String, action: (project: Project) -> T): T =
        project?.let {
            action(it)
        } ?: throw IllegalStateException("Cannot obtain $context until Project is known")
}
