/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.component.TestComponentProperties;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.component.impl.UnitTestPropertiesImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.SdkComponentsBuildService;
import com.android.build.gradle.internal.scope.BootClasspathBuilder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.VariantAwareTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.GenerateTestConfig;
import com.android.builder.core.VariantType;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.concurrent.Callable;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;

/** Patched version of {@link Test} that we need to use for local unit tests support. */
@CacheableTask
public abstract class AndroidUnitTest extends Test implements VariantAwareTask {

    private String variantName;

    @Nullable private GenerateTestConfig.TestConfigInputs testConfigInputs;

    @Internal
    @NonNull
    @Override
    public String getVariantName() {
        return variantName;
    }

    @Override
    public void setVariantName(@NonNull String name) {
        variantName = name;
    }

    @Nested
    @Optional
    public GenerateTestConfig.TestConfigInputs getTestConfigInputs() {
        return testConfigInputs;
    }

    public static class CreationAction
            extends VariantTaskCreationAction<AndroidUnitTest, ComponentPropertiesImpl> {

        @NonNull private final UnitTestPropertiesImpl unitTestProperties;

        public CreationAction(@NonNull UnitTestPropertiesImpl unitTestProperties) {
            super(unitTestProperties);
            this.unitTestProperties = unitTestProperties;
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName(VariantType.UNIT_TEST_PREFIX);
        }

        @NonNull
        @Override
        public Class<AndroidUnitTest> getType() {
            return AndroidUnitTest.class;
        }

        @Override
        public void configure(@NonNull AndroidUnitTest task) {
            super.configure(task);

            GlobalScope globalScope = creationConfig.getGlobalScope();
            BaseExtension extension = globalScope.getExtension();

            VariantPropertiesImpl testedVariant =
                    (VariantPropertiesImpl)
                            ((TestComponentProperties) creationConfig).getTestedVariant();

            boolean includeAndroidResources =
                    extension.getTestOptions().getUnitTests().isIncludeAndroidResources();
            boolean useRelativePathInTestConfig =
                    creationConfig
                            .getServices()
                            .getProjectOptions()
                            .get(BooleanOption.USE_RELATIVE_PATH_IN_TEST_CONFIG);

            // we run by default in headless mode, so the forked JVM doesn't steal focus.
            task.systemProperty("java.awt.headless", "true");

            task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Run unit tests for the " + testedVariant.getName() + " build.");

            task.setTestClassesDirs(creationConfig.getArtifacts().getAllClasses());
            task.setClasspath(computeClasspath(creationConfig, includeAndroidResources));

            if (includeAndroidResources) {
                // When computing the classpath above, we made sure this task depends on the output
                // of the GenerateTestConfig task. However, it is not enough. The GenerateTestConfig
                // task has 2 types of inputs: direct inputs and indirect inputs. Only the direct
                // inputs are registered with Gradle, whereas the indirect inputs are not (see that
                // class for details).
                // Since this task also depends on the indirect inputs to the GenerateTestConfig
                // task, we also need to register those inputs with Gradle.
                task.testConfigInputs = new GenerateTestConfig.TestConfigInputs(unitTestProperties);
            }

            // Put the variant name in the report path, so that different testing tasks don't
            // overwrite each other's reports. For component model plugin, the report tasks are not
            // yet configured.  We get a hardcoded value matching Gradle's default. This will
            // eventually be replaced with the new Java plugin.
            TestTaskReports testTaskReports = task.getReports();
            ConfigurableReport xmlReport = testTaskReports.getJunitXml();
            xmlReport.setDestination(new File(globalScope.getTestResultsFolder(), task.getName()));

            ConfigurableReport htmlReport = testTaskReports.getHtml();
            htmlReport.setDestination(new File(globalScope.getTestReportFolder(), task.getName()));

            extension.getTestOptions().getUnitTests().applyConfiguration(task);

            // The task is not yet cacheable when includeAndroidResources=true and
            // android.testConfig.useRelativePath=false (bug 115873047). We set it explicitly here
            // so Gradle doesn't have to store cache entries that won't be reused.
            task.getOutputs()
                    .doNotCacheIf(
                            "AndroidUnitTest task is not yet cacheable"
                                    + " when includeAndroidResources=true"
                                    + " and android.testConfig.useRelativePath=false",
                            (thisTask) -> includeAndroidResources && !useRelativePathInTestConfig);
        }

        @NonNull
        private ConfigurableFileCollection computeClasspath(
                ComponentPropertiesImpl component, boolean includeAndroidResources) {
            GlobalScope globalScope = component.getGlobalScope();
            ArtifactsImpl artifacts = component.getArtifacts();

            ConfigurableFileCollection collection = component.getServices().fileCollection();

            // the test classpath is made up of:
            // 1. the config file
            if (includeAndroidResources) {
                collection.from(
                        artifacts.get(InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY.INSTANCE));
            }

            // 2. the test component classes and java_res
            collection.from(component.getArtifacts().getAllClasses());
            // TODO is this the right thing? this doesn't include the res merging via transform
            // AFAIK
            collection.from(artifacts.get(InternalArtifactType.JAVA_RES.INSTANCE));

            // 3. the runtime dependencies for both CLASSES and JAVA_RES type
            collection.from(
                    component
                            .getVariantDependencies()
                            .getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, CLASSES_JAR));
            collection.from(
                    component
                            .getVariantDependencies()
                            .getArtifactFileCollection(
                                    RUNTIME_CLASSPATH, ALL, ArtifactType.JAVA_RES));

            // 4. The separately compile R class, if applicable.
            if (!globalScope.getExtension().getAaptOptions().getNamespaced()) {
                collection.from(component.getVariantScope().getRJarForUnitTests());
            }

            // 5. Any additional or requested optional libraries
            collection.from(getAdditionalAndRequestedOptionalLibraries(component.getGlobalScope()));

            // 6. Mockable JAR is last, to make sure you can shadow the classes with
            // dependencies.
            collection.from(component.getGlobalScope().getMockableJarArtifact());

            return collection;
        }

        /**
         * Returns the list of additional and requested optional library jar files
         *
         * @return the list of files from the additional and optional libraries which appear in the
         *     filtered boot classpath
         */
        @NonNull
        private ConfigurableFileCollection getAdditionalAndRequestedOptionalLibraries(
                GlobalScope globalScope) {
            return creationConfig
                    .getServices()
                    .fileCollection(
                            (Callable)
                                    () ->
                                            BootClasspathBuilder.INSTANCE
                                                    .computeAdditionalAndRequestedOptionalLibraries(
                                                            globalScope.getProject(),
                                                            globalScope
                                                                    .getSdkComponents()
                                                                    .flatMap(
                                                                            SdkComponentsBuildService
                                                                                    ::getAdditionalLibrariesProvider)
                                                                    .get(),
                                                            globalScope
                                                                    .getSdkComponents()
                                                                    .flatMap(
                                                                            SdkComponentsBuildService
                                                                                    ::getOptionalLibrariesProvider)
                                                                    .get(),
                                                            false,
                                                            ImmutableList.copyOf(
                                                                    globalScope
                                                                            .getExtension()
                                                                            .getLibraryRequests()),
                                                            creationConfig
                                                                    .getServices()
                                                                    .getIssueReporter()));
        }
    }
}
