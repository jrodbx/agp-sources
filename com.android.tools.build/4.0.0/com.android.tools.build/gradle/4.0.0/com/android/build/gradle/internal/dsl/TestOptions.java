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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.builder.model.TestOptions.Execution;
import com.android.utils.HelpfulEnumConverter;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import groovy.lang.Closure;
import javax.inject.Inject;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.ConfigureUtil;

/** Options for running tests. */
@SuppressWarnings("unused") // Exposed in the DSL.
public class TestOptions
        implements com.android.build.api.dsl.TestOptions<TestOptions.UnitTestOptions> {
    private static final HelpfulEnumConverter<Execution> EXECUTION_CONVERTER =
            new HelpfulEnumConverter<>(Execution.class);

    @Nullable private String resultsDir;

    @Nullable private String reportDir;

    private boolean animationsDisabled;

    @NonNull private Execution execution = Execution.HOST;

    /**
     * Options for controlling unit tests execution.
     *
     * @since 1.1.0
     */
    @NonNull private final UnitTestOptions unitTests;

    @Inject
    public TestOptions(DslScope dslScope) {
        this.unitTests = dslScope.getObjectFactory().newInstance(UnitTestOptions.class, dslScope);
    }

    public void unitTests(Action<UnitTestOptions> action) {
        action.execute(unitTests);
    }

    @Override
    public void unitTests(Function1<? super UnitTestOptions, Unit> action) {
        action.invoke(unitTests);
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2.0
     */
    public void unitTests(Closure closure) {
        ConfigureUtil.configure(closure, unitTests);
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2.0
     */
    @Override
    @NonNull
    public UnitTestOptions getUnitTests() {
        return unitTests;
    }

    /** Name of the results directory. */
    @Nullable
    public String getResultsDir() {
        return resultsDir;
    }

    public void setResultsDir(@Nullable String resultsDir) {
        this.resultsDir = resultsDir;
    }

    /** Name of the reports directory. */
    @Nullable
    public String getReportDir() {
        return reportDir;
    }

    public void setReportDir(@Nullable String reportDir) {
        this.reportDir = reportDir;
    }

    /**
     * Disables animations during instrumented tests you run from the cammand line.
     *
     * <p>If you set this property to {@code true}, running instrumented tests with Gradle from the
     * command line executes {@code am instrument} with the {@code --no-window-animation} flag.
     * By default, this property is set to {@code false}.</p>
     *
     * <p>This property does not affect tests that you run using Android Studio. To learn more about
     * running tests from the command line, see
     * <a href="https://d.android.com/studio/test/command-line.html">Test from the Command Line</a>.
     * </p>
     */
    public boolean getAnimationsDisabled() {
        return animationsDisabled;
    }

    public void setAnimationsDisabled(boolean animationsDisabled) {
        this.animationsDisabled = animationsDisabled;
    }

    /**
     * Specifies whether to use on-device test orchestration.
     *
     * <p>If you want to <a
     * href="https://developer.android.com/training/testing/junit-runner.html#using-android-test-orchestrator">use
     * Android Test Orchestrator</a>, you need to specify <code>"ANDROID_TEST_ORCHESTRATOR"</code>,
     * as shown below. By default, this property is set to <code>"HOST"</code>, which disables
     * on-device orchestration.
     *
     * <pre>
     * android {
     *   testOptions {
     *     execution 'ANDROID_TEST_ORCHESTRATOR'
     *   }
     * }
     * </pre>
     *
     * @since 3.0.0
     */
    @NonNull
    public String getExecution() {
        return Verify.verifyNotNull(
                EXECUTION_CONVERTER.reverse().convert(execution),
                "No string representation for enum.");
    }

    @NonNull
    public Execution getExecutionEnum() {
        return execution;
    }

    public void setExecution(@NonNull String execution) {
        this.execution =
                Preconditions.checkNotNull(
                        EXECUTION_CONVERTER.convert(execution),
                        "The value of `execution` cannot be null.");
    }

    /** Options for controlling unit tests execution. */
    public static class UnitTestOptions implements com.android.build.api.dsl.UnitTestOptions {
        // Used by testTasks.all below, DSL docs generator can't handle diamond operator.
        private final DomainObjectSet<Test> testTasks;

        private boolean returnDefaultValues;
        private boolean includeAndroidResources;

        @Inject
        public UnitTestOptions(@NonNull DslScope dslScope) {
            testTasks = dslScope.getObjectFactory().domainObjectSet(Test.class);
        }

        /**
         * Whether unmocked methods from android.jar should throw exceptions or return default
         * values (i.e. zero or null).
         *
         * <p>See <a href="https://developer.android.com/studio/test/index.html">Test Your App</a>
         * for details.
         *
         * @since 1.1.0
         */
        public boolean isReturnDefaultValues() {
            return returnDefaultValues;
        }

        public void setReturnDefaultValues(boolean returnDefaultValues) {
            this.returnDefaultValues = returnDefaultValues;
        }

        /**
         * Enables unit tests to use Android resources, assets, and manifests.
         *
         * <p>If you set this property to <code>true</code>, the plugin performs resource, asset,
         * and manifest merging before running your unit tests. Your tests can then inspect a file
         * called {@code com/android/tools/test_config.properties} on the classpath, which is a Java
         * properties file with the following keys:
         *
         * <ul>
         *   <li><code>android_resource_apk</code>: the path to the APK-like zip file containing
         *       merged resources, which includes all the resources from the current subproject and
         *       all its dependencies. This property is available by default, or if the Gradle
         *       property <code>android.enableUnitTestBinaryResources</code> is set to <code>true
         *       </code>.
         *   <li><code>android_merged_resources</code>: the path to the directory containing merged
         *       resources, which includes all the resources from the current subproject and all its
         *       dependencies. This property is available only if the Gradle property <code>
         *       android.enableUnitTestBinaryResources</code> is set to <code>false</code>.
         *   <li><code>android_merged_assets</code>: the path to the directory containing merged
         *       assets. For app subprojects, the merged assets directory contains assets from the
         *       current subproject and its dependencies. For library subprojects, the merged assets
         *       directory contains only assets from the current subproject.
         *   <li><code>android_merged_manifest</code>: the path to the merged manifest file. Only
         *       app subprojects have the manifest merged from their dependencies. Library
         *       subprojects do not include manifest components from their dependencies.
         *   <li><code>android_custom_package</code>: the package name of the final R class. If you
         *       modify the application ID in your build scripts, this package name may not match
         *       the <code>package</code> attribute in the final app manifest.
         * </ul>
         *
         * <p>Note that starting with version 3.5.0, if the Gradle property <code>
         * android.testConfig.useRelativePath</code> is set to <code>true</code>, the paths above
         * will be relative paths (relative to the current project directory, not the root project
         * directory); otherwise, they will be absolute paths. Prior to version 3.5.0, the paths are
         * all absolute paths.
         *
         * @since 3.0.0
         */
        public boolean isIncludeAndroidResources() {
            return includeAndroidResources;
        }

        public void setIncludeAndroidResources(boolean includeAndroidResources) {
            this.includeAndroidResources = includeAndroidResources;
        }

        /**
         * Configures all unit testing tasks.
         *
         * <p>See {@link Test} for available options.
         *
         * <p>Inside the closure you can check the name of the task to configure only some test
         * tasks, e.g.
         *
         * <pre>
         * android {
         *     testOptions {
         *         unitTests.all {
         *             if (it.name == 'testDebug') {
         *                 systemProperty 'debug', 'true'
         *             }
         *         }
         *     }
         * }
         * </pre>
         *
         * @since 1.2.0
         */
        public void all(final Closure<Test> configClosure) {
            //noinspection Convert2Lambda - DSL docs generator can't handle lambdas.
            testTasks.all(
                    new Action<Test>() {
                        @Override
                        public void execute(Test testTask) {
                            ConfigureUtil.configure(configClosure, testTask);
                        }
                    });
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
        public void applyConfiguration(@NonNull Test task) {
            this.testTasks.add(task);
        }
    }
}
