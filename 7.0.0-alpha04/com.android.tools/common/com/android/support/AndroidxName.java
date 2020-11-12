/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.support;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Type representing an Android Support Library name. This type contains both the old name and the
 * new name of a class. The type also returns a "default name" that will be changed from the old to
 * new eventually.
 */
public class AndroidxName {
    private final String myOldName;
    private final String myNewName;

    private AndroidxName(@NonNull String oldName, @NonNull String newName) {
        myOldName = oldName;
        myNewName = newName;
    }

    /** Creates a new instance for the package in the {@code com.android.support} and class name */
    @NonNull
    public static AndroidxName of(@NonNull String oldPackage, @NonNull String simpleClassName) {
        assert oldPackage.endsWith(".") : oldPackage + " needs to end with a dot.";
        assert oldPackage.startsWith(AndroidxNameUtils.ANDROID_SUPPORT_PKG)
                || oldPackage.startsWith(AndroidxNameUtils.ANDROID_ARCH_PKG)
                || oldPackage.startsWith(AndroidxNameUtils.ANDROID_DATABINDING_PKG);

        // We first check if there is a specific class name mapping. If there is not, we'll try to find a mapping
        // for the package name
        String fullOldName = oldPackage + simpleClassName;

        return new AndroidxName(fullOldName, AndroidxNameUtils.getNewName(fullOldName));
    }

    /** Creates a new instance for the given package and class name */
    @NonNull
    public static AndroidxName of(@NonNull AndroidxName pkg, @NonNull String simpleClassName) {
        assert !simpleClassName.contains(".");

        return new AndroidxName(
                pkg.oldName() + (pkg.oldName().endsWith(".") ? "" : ".") + simpleClassName,
                pkg.newName() + (pkg.newName().endsWith(".") ? "" : ".") + simpleClassName);
    }

    /** Creates a new package instance for for the package in the {@code com.android.support} */
    @NonNull
    public static AndroidxName of(@NonNull String oldPackageName) {
        assert oldPackageName.endsWith(".");
        assert oldPackageName.startsWith(AndroidxNameUtils.ANDROID_SUPPORT_PKG)
                || oldPackageName.startsWith(AndroidxNameUtils.ANDROID_ARCH_PKG)
                || oldPackageName.startsWith(AndroidxNameUtils.ANDROID_DATABINDING_PKG);

        return new AndroidxName(
                oldPackageName, AndroidxNameUtils.getPackageMapping(oldPackageName, true));
    }

    /** Returns the {@code com.android.support} version of the name */
    @NonNull
    public String oldName() {
        return myOldName;
    }

    /** Returns the {@code androidx} version of the name */
    @NonNull
    public String newName() {
        return myNewName;
    }

    /**
     * Returns the {@code com.android.support} version of the name. This method will start returning
     * to the {@code androidx} once those are the default versions to be added to the project. You
     * should avoid using this method when possible and use {@link #oldName()} or {@link #newName()}
     * depending on the dependencies of the module.
     */
    @NonNull
    public String defaultName() {
        return myOldName;
    }

    /** Returns if the current name is a prefix of the given name. */
    public boolean isPrefix(@Nullable String name) {
        return isPrefix(name, false);
    }

    /**
     * Returns if the current name is a prefix of the given name.
     *
     * @param name the name to check
     * @param strict true if the name needs to be strictly longer than the prefix
     */
    public boolean isPrefix(@Nullable String name, boolean strict) {
        if (name == null) {
            return false;
        }

        if (name.startsWith(oldName())) {
            return !strict || oldName().length() < name.length();
        } else if (name.startsWith(newName())) {
            return !strict || newName().length() < name.length();
        }

        return false;
    }

    /**
     * Removes the current name from the given qualified name.
     *
     * <p>For example, if the <code>AndroidxName</code> is "android" and the passed qualifiedName is
     * "android.TestClass", this method, will return "TestClass"
     */
    public String removeFrom(@NonNull String qualifiedName) {
        if (qualifiedName.startsWith(oldName())) {
            return qualifiedName.substring(oldName().length());
        } else if (qualifiedName.startsWith(newName())) {
            return qualifiedName.substring(newName().length());
        }

        return qualifiedName;
    }

    /** Compares the current name with the given string */
    public boolean isEquals(@Nullable String strName) {
        if (strName == null) {
            return false;
        }

        return myOldName.equals(strName) || myNewName.equals(strName);
    }

    /** Compares the current name with the given string ignoring case sensitivity */
    public boolean isEqualsIgnoreCase(@Nullable String strName) {
        if (strName == null) {
            return false;
        }

        return myOldName.equalsIgnoreCase(strName) || myNewName.equalsIgnoreCase(strName);
    }

    @Override
    public String toString() {
        assert false : "toString can not be used on AndroidxName";
        return super.toString();
    }
}
