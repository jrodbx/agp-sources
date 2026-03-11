/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.api.testsuites

import org.gradle.api.Incubating
import java.io.FileReader
import java.util.Properties

/**
 * Simple API to retrieve the execution context set by the Test Task before running
 * the junit test engine.
 *
 * Input parameters can be requested in the DSL by adding [TestEngineInputProperty] to the
 * [com.android.build.api.dsl.JUnitEngineSpec.inputs].
 *
 * At execution time, test engines can retrieve the requested parameters values using the
 * [inputParameters] or the [getInputParameter] APIs.
 */
@Incubating
class TestSuiteExecutionClient(
    /**
     * full list of resolved input parameters.
     */
    val inputParameters: Collection<TestEngineInputProperty>
) {

    /**
     * Retrieve a single input parameter using its name
     */
    @Incubating
    fun getInputParameter(key: String): String = inputParameters.firstOrNull { it.name == key }?.value
        ?: throw RuntimeException("Missing value for key $key")

    companion object {

        /**
         * Name of the environment property the junit engine expect to be set. This environment
         * property will point to a file location which content will be an instance of
         * [TestEngineInputProperties] serialized as a Json object.
         */
        @get:Incubating
        const val DEFAULT_ENV_VARIABLE = "com.android.junit.engine.input.parameters"

        /**
         * Load the context using the default environment variable set by the test task
         */
        @Incubating
        fun default() = withEnvVariable(DEFAULT_ENV_VARIABLE)

        /**
         * Load the context using a specific environment variable.
         */
        @Incubating
        fun withEnvVariable(variableName: String) = TestSuiteExecutionClient(
            Properties().also {
                it.load(FileReader(System.getenv(variableName)))
            }.map {
                TestEngineInputProperty(it.key as String, it.value as String)
            }
        )
    }
}
