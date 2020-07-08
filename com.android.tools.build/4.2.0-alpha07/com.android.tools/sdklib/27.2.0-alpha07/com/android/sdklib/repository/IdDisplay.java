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

package com.android.sdklib.repository;

import com.android.annotations.NonNull;
import com.android.sdklib.repository.meta.SdkCommonFactory;

import javax.xml.bind.annotation.XmlTransient;
import java.util.Locale;

/**
 * A string with both user-friendly and easily-parsed versions.
 * Contains stubs to be overridden by xjc-generated classes.
 */
@SuppressWarnings("NullableProblems")
@XmlTransient
public abstract class IdDisplay implements Comparable<IdDisplay> {

    public static IdDisplay create(@NonNull String id, @NonNull String display) {
        SdkCommonFactory factory = AndroidSdkHandler.getCommonModule().createLatestFactory();
        IdDisplay result = factory.createIdDisplayType();
        result.setId(id);
        result.setDisplay(display);
        return result;
    }

    /**
     * Sets the machine-friendly version of the string.
     */
    public abstract void setId(@NonNull String id);

    /**
     * Sets the user-friendly version of the string.
     */
    public abstract void setDisplay(@NonNull String display);

    /**
     * Gets the machine-friendly version of the string.
     */
    @NonNull
    public abstract String getId();

    /**
     * Gets the user-friendly version of the string.
     */
    @NonNull
    public abstract String getDisplay();

    @Override
    public int compareTo(IdDisplay o) {
        return getId().compareTo(o.getId());
    }

    public boolean equals(Object o) {
        if (!(o instanceof IdDisplay)) {
            return false;
        }
        return compareTo((IdDisplay)o) == 0;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    /**
     * Returns a string representation for *debug* purposes only, not for UI display.
     */
    @Override
    public String toString() {
        return String.format("%1$s [%2$s]", getId(), getDisplay());
    }

    /**
     * Computes a display-friendly tag string based on the id.
     * This is typically used when there's no display attribute.
     *
     * @param id A non-null id to sanitize for display.
     * @return The id with all non-alphanum symbols replaced by spaces and trimmed.
     */
    @NonNull
    public static String idToDisplay(@NonNull String id) {
        String name;
        name = id.replaceAll("[^A-Za-z0-9]+", " ");
        name = name.replaceAll(" +", " ");
        name = name.trim();

        if (!name.isEmpty()) {
            char c = name.charAt(0);
            if (!Character.isUpperCase(c)) {
                StringBuilder sb = new StringBuilder(name);
                sb.replace(0, 1, String.valueOf(c).toUpperCase(Locale.US));
                name = sb.toString();
            }
        }
        return name;
    }

}
