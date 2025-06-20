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
package com.android.ddmlib.utils;

import com.android.annotations.NonNull;
import com.android.ddmlib.Log;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class providing file permission handling.
 */
public class FilePermissionUtil {

    /**
     * Helper to get bits value given the permission (rwx) values.
     */
    private static int numericalPermission(@NonNull PosixFilePermission p) {
        switch (p) {
            case OWNER_READ:
                return 0400;
            case OWNER_WRITE:
                return 0200;
            case OWNER_EXECUTE:
                return 0100;
            case GROUP_READ:
                return 040;
            case GROUP_WRITE:
                return 020;
            case GROUP_EXECUTE:
                return 010;
            case OTHERS_READ:
                return 04;
            case OTHERS_WRITE:
                return 02;
            case OTHERS_EXECUTE:
                return 01;
            default:
                return 00;
        }
    }

    /**
     * Returns a file's POSIX file permissions.
     * @param file path of the file which permissions should be used.
     * @return permission returns the standard numerical representation of permissions. Returns
     * 0644 if permissions cannot be determined.
     */
    public static int getFilePosixPermission(@NonNull File file) {
        Set<PosixFilePermission> perms = new HashSet<>();
        try {
            perms = Files.getPosixFilePermissions(file.toPath());
        } catch (IOException e) {
            Log.e("ddms", "Error when reading file permission: " + e.getMessage());
            // fallback to a default permission.
            return 0644;
        } catch (UnsupportedOperationException e) {
            // Windows special handling, we still try to figure out the permissions.
            if (file.canRead()) {
                perms.add(PosixFilePermission.OWNER_READ);
            }
            if (file.canWrite()) {
                perms.add(PosixFilePermission.OWNER_WRITE);
            }
            if (file.canExecute()) {
                perms.add(PosixFilePermission.OWNER_EXECUTE);
            }
            // In case we fail to read on Windows we fall back to 0644 again.
            if (perms.isEmpty()) {
                return 0644;
            }
        }
        Log.d(
                "ddms",
                String.format(
                        "Reading file permission of %s as: %s",
                        file.getAbsoluteFile(), PosixFilePermissions.toString(perms)));
        int result = 0;
        for (PosixFilePermission p : perms) {
            result += numericalPermission(p);
        }
        return result;
    }
}
