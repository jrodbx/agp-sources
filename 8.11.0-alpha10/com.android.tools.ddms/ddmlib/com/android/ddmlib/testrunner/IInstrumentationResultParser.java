/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.ddmlib.IShellOutputReceiver;
import java.util.HashSet;
import java.util.Set;

/** Interface for parsing the results of an instrumentation test run from shell. */
public interface IInstrumentationResultParser extends IShellOutputReceiver {

    /** Relevant test status keys. */
    class StatusKeys {
        // Status keys which "am instrument" command uses.
        public static final String TEST = "test";
        public static final String CLASS = "class";
        public static final String STACK = "stack";
        public static final String NUMTESTS = "numtests";
        public static final String ERROR = "Error";
        public static final String SHORTMSG = "shortMsg";
        public static final String STREAM = "stream";
        public static final String CURRENT = "current";
        public static final String ID = "id";

        /** Additional status keys which Ddmlib uses to emit extra test metrics. */
        public static final String DDMLIB_LOGCAT = "com.android.ddmlib.testrunner.logcat";

        /**
         * The set of expected status keys. Used to filter which keys should be stored as metrics
         */
        public static final Set<String> KNOWN_KEYS = new HashSet<>();

        static {
            KNOWN_KEYS.add(TEST);
            KNOWN_KEYS.add(CLASS);
            KNOWN_KEYS.add(STACK);
            KNOWN_KEYS.add(NUMTESTS);
            KNOWN_KEYS.add(ERROR);
            KNOWN_KEYS.add(SHORTMSG);
            KNOWN_KEYS.add(STREAM);
            KNOWN_KEYS.add(CURRENT);
            KNOWN_KEYS.add(ID);
            KNOWN_KEYS.add(DDMLIB_LOGCAT);
        }
    }

    /** Test result status codes. */
    class StatusCodes {
        public static final int START = 1;
        public static final int IN_PROGRESS = 2;

        // codes used for test completed
        public static final int ASSUMPTION_FAILURE = -4;
        public static final int IGNORED = -3;
        public static final int FAILURE = -2;
        public static final int ERROR = -1;
        public static final int OK = 0;

        public static boolean isTerminalState(int statusCode) {
            return statusCode <= 0;
        }
    }

    /** Am instrument session result codes. This code is passed by ActivityManagerService. */
    class SessionResultCodes {
        // All tests execution finished. (Some tests may have failed).
        public static final int FINISHED = -1;

        // Test session has been stopped due to an error.
        public static final int ERROR = 0;
    }

    /** Requests cancellation of test run. */
    void cancel();

    /**
     * This method is called when "am instrument" command crashes with an exception. All registered
     * listeners should be notified {@link ITestRunListener#testRunFailed} followed by {@link
     * ITestRunListener#testRunEnded} if the crash happens during the test execution.
     */
    void handleTestRunFailed(@NonNull String errorMsg);
}
