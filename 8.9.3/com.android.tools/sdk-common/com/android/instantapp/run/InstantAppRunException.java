/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.instantapp.run;

import com.android.annotations.NonNull;

/** Exception thrown in any step of the run or side load process. */
public class InstantAppRunException extends Exception {
    @NonNull private final ErrorType myErrorType;

    InstantAppRunException(@NonNull ErrorType errorType) {
        super(createMessageForError(errorType));
        myErrorType = errorType;
    }

    InstantAppRunException(@NonNull ErrorType errorType, @NonNull Throwable cause) {
        super(createMessageForError(errorType) + " Caused by: " + cause.getMessage(), cause);
        myErrorType = errorType;
    }

    InstantAppRunException(@NonNull ErrorType errorType, @NonNull String message) {
        super(createMessageForError(errorType) + " " + message);
        myErrorType = errorType;
    }

    InstantAppRunException(
            @NonNull ErrorType errorType, @NonNull String message, @NonNull Throwable cause) {
        super(
                createMessageForError(errorType)
                        + " "
                        + message
                        + " Caused by: "
                        + cause.getMessage(),
                cause);
        myErrorType = errorType;
    }

    @NonNull
    private static String createMessageForError(@NonNull ErrorType errorType) {
        switch (errorType) {
            case READ_IAPK_TIMEOUT:
                return "Reading bundle timed out.";
            case READ_IAPK_FAILED:
                return "Failure when trying to read bundle.";
            case NO_GOOGLE_ACCOUNT:
                return "There is no Google account on the target device. Please log in to a Google account and try again.";
            case SHELL_TIMEOUT:
                return "Shell adb command has timed out.";
            case ADB_FAILURE:
                return "ADB has failed.";
            case INSTALL_FAILED:
                return "Installing APK failed.";
            case CANCELLED:
                return "Cancelled by the user.";
            case UNKNOWN:
                return "Error while deploying Instant App.";
        }
        return null;
    }

    @NonNull
    public ErrorType getErrorType() {
        return myErrorType;
    }

    /**
     * Represents the possible errors while side loading or running an instant app. This can be used
     * to an automatic retry.
     */
    public enum ErrorType {
        READ_IAPK_TIMEOUT,
        READ_IAPK_FAILED,
        NO_GOOGLE_ACCOUNT,
        SHELL_TIMEOUT,
        ADB_FAILURE,
        INSTALL_FAILED,
        CANCELLED,
        UNKNOWN
    }
}
