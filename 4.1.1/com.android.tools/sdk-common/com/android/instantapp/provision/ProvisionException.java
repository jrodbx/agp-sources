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
package com.android.instantapp.provision;

import com.android.annotations.NonNull;

/** Exception thrown in any step of the provisioning process. */
public class ProvisionException extends Exception {
    @NonNull private final ErrorType myErrorType;

    ProvisionException(@NonNull ErrorType errorType) {
        super(createMessageForError(errorType));
        myErrorType = errorType;
    }

    ProvisionException(@NonNull ErrorType errorType, @NonNull Throwable cause) {
        super(createMessageForError(errorType) + " Caused by: " + cause.getMessage(), cause);
        myErrorType = errorType;
    }

    ProvisionException(@NonNull ErrorType errorType, @NonNull String message) {
        super(createMessageForError(errorType) + " " + message);
        myErrorType = errorType;
    }

    ProvisionException(
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
            case ARCH_NOT_SUPPORTED:
                return "The device architecture is not supported for Instant Apps.";
            case DEVICE_NOT_SUPPORTED:
                return "The device used is not whitelisted for Instant Apps deployment.";
            case NO_GOOGLE_ACCOUNT:
                return "There is no Google account on the target device. Please log in to a Google account and try again.";
            case SHELL_TIMEOUT:
                return "Shell adb command has timed out.";
            case ADB_FAILURE:
                return "ADB has failed.";
            case INVALID_SDK:
                return "The provided SDK file is not valid.";
            case INSTALL_FAILED:
                return "Installing APK failed.";
            case UNINSTALL_FAILED:
                return "Uninstalling APK failed.";
            case CANCELLED:
                return "Cancelled by the user.";
            case UNKNOWN:
                return "Error while provisioning device.";
        }
        return null;
    }

    @NonNull
    public ErrorType getErrorType() {
        return myErrorType;
    }

    /**
     * Represents the possible errors while provisioning. This can be used to an automatic retry.
     */
    public enum ErrorType {
        ARCH_NOT_SUPPORTED,
        DEVICE_NOT_SUPPORTED,
        NO_GOOGLE_ACCOUNT,
        SHELL_TIMEOUT,
        ADB_FAILURE,
        INVALID_SDK,
        INSTALL_FAILED,
        UNINSTALL_FAILED,
        CANCELLED,
        UNKNOWN
    }
}
