/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ddmlib.testrunner;

/**
 * Identifies a parsed instrumentation test.
 */
public class TestIdentifier {

    private final String mClassName;
    private final String mTestName;

    // An index of this test.
    // Test suite may have duplicated test cases (the same test case may be run twice). This
    // property is used to distinguish them.
    private final int mTestIndex;

    /**
     * Creates a test identifier.
     *
     * @param className fully qualified class name of the test. Cannot be null.
     * @param testName name of the test. Cannot be null.
     */
    public TestIdentifier(String className, String testName) {
        this(className, testName, /*testIndex=*/ -1);
    }

    /**
     * Creates a test identifier with a test index.
     *
     * @param className fully qualified class name of the test. Cannot be null.
     * @param testName name of the test. Cannot be null.
     * @param testIndex an index of this test run.
     */
    public TestIdentifier(String className, String testName, int testIndex) {
        if (className == null || testName == null) {
            throw new IllegalArgumentException("className and testName must " + "be non-null");
        }
        mClassName = className;
        mTestName = testName;
        mTestIndex = testIndex;
    }

    /**
     * Returns the fully qualified class name of the test.
     */
    public String getClassName() {
        return mClassName;
    }

    /**
     * Returns the name of the test.
     */
    public String getTestName() {
        return mTestName;
    }

    /** Returns the index of the test. */
    public int getTestIndex() {
        return mTestIndex;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mClassName == null) ? 0 : mClassName.hashCode());
        result = prime * result + ((mTestName == null) ? 0 : mTestName.hashCode());
        result = prime * result + mTestIndex;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TestIdentifier other = (TestIdentifier) obj;
        if (mClassName == null) {
            if (other.mClassName != null)
                return false;
        } else if (!mClassName.equals(other.mClassName))
            return false;
        if (mTestName == null) {
            if (other.mTestName != null)
                return false;
        } else if (!mTestName.equals(other.mTestName))
            return false;
        return mTestIndex == other.mTestIndex;
    }

    @Override
    public String toString() {
        return String.format("%s#%s", getClassName(), getTestName());
    }
}
