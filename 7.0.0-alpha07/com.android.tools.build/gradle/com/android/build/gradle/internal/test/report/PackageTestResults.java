/*
 * Copyright 2011 the original author or authors.
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
package com.android.build.gradle.internal.test.report;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Custom PackageTestResults based on Gradle's PackageTestResults
 */
class PackageTestResults extends CompositeTestResults {

    private static final String DEFAULT_PACKAGE = "default-package";
    private final String name;
    private final Map<String, ClassTestResults> classes = new TreeMap<>();

    public PackageTestResults(String name, AllTestResults model) {
        super(model);
        this.name = name.isEmpty() ? DEFAULT_PACKAGE : name;
    }

    @Override
    public String getTitle() {
        return name.equals(DEFAULT_PACKAGE) ? "Default package" : String.format("Package %s", name);
    }

    @Override
    public String getName() {
        return name;
    }

    public Collection<ClassTestResults> getClasses() {
        return classes.values();
    }

    public TestResult addTest(String className, String testName, long duration,
                              String device, String project, String flavor) {
        ClassTestResults classResults = addClass(className);
        TestResult testResult = addTest(
                classResults.addTest(testName, duration, device, project, flavor));

        addDevice(device, testResult);
        addVariant(project, flavor, testResult);

        return testResult;
    }

    public ClassTestResults addClass(String className) {
        ClassTestResults classResults = classes.get(className);
        if (classResults == null) {
            classResults = new ClassTestResults(className, this);
            classes.put(className, classResults);
        }
        return classResults;
    }
}
