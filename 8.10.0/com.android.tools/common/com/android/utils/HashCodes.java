/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.utils;

/**
 * Static methods useful for implementing {@code hashCode()} methods.
 *
 * <p>For example:
 * <pre><code>
 * public class Foo {
 *    {@literal @}NonNull private String a;
 *    {@literal @}Nullable private String b;
 *     private boolean c;
 *     private int d;
 *
 *    {@literal @}Override
 *     public int hashCode() {
 *         return HashCodes.mix(a.hashCode(), Objects.hashCode(b), Boolean.hashCode(c), d);
 *     }
 * }
 * </code></pre>
 */
public class HashCodes {
    /**
     * Combines two hash codes into one. Can be used for computing the hash code of an object
     * containing two fields.
     */
    public static int mix(int hashCode1, int hashCode2) {
        return hashCode1 * 31 + hashCode2;
    }

    /**
     * Combines three hash codes into one. Can be used for computing the hash code of an object
     * containing three fields.
     */
    public static int mix(int hashCode1, int hashCode2, int hashCode3) {
        return (hashCode1 * 31 + hashCode2) * 31 + hashCode3;
    }

    /**
     * Combines four hash codes into one. Can be used for computing the hash code of an object
     * containing four fields.
     */
    public static int mix(int hashCode1, int hashCode2, int hashCode3, int hashCode4) {
        return ((hashCode1 * 31 + hashCode2) * 31 + hashCode3) * 31 + hashCode4;
    }

    /**
     * Combines five hash codes into one. Can be used for computing the hash code of an object
     * containing five fields.
     */
    public static int mix(int hashCode1, int hashCode2, int hashCode3, int hashCode4,
            int hashCode5) {
        return (((hashCode1 * 31 + hashCode2) * 31 + hashCode3) * 31 + hashCode4) * 31 + hashCode5;
    }

    /**
     * Combines six hash codes into one. Can be used for computing the hash code of an object
     * containing six fields.
     */
    public static int mix(int hashCode1, int hashCode2, int hashCode3, int hashCode4, int hashCode5,
            int hashCode6) {
        return ((((hashCode1 * 31 + hashCode2) * 31 + hashCode3) * 31 + hashCode4) * 31 + hashCode5)
                * 31 + hashCode6;
    }

    /**
     * Combines seven hash codes into one. Can be used for computing the hash code of an object
     * containing seven fields.
     */
    public static int mix(int hashCode1, int hashCode2, int hashCode3, int hashCode4, int hashCode5,
            int hashCode6, int hashCode7) {
        return (((((hashCode1 * 31 + hashCode2) * 31 + hashCode3) * 31 + hashCode4)
                * 31 + hashCode5) * 31 + hashCode6) * 31 + hashCode7;
    }

    /**
     * Combines eight hash codes into one. Can be used for computing the hash code of an object
     * containing eight fields.
     */
    public static int mix(int hashCode1, int hashCode2, int hashCode3, int hashCode4, int hashCode5,
            int hashCode6, int hashCode7, int hashCode8) {
        return ((((((hashCode1 * 31 + hashCode2) * 31 + hashCode3) * 31 + hashCode4)
                * 31 + hashCode5) * 31 + hashCode6) * 31 + hashCode7) * 31 + hashCode8;
    }

    /**
     * Combines nine hash codes into one. Can be used for computing the hash code of an object
     * containing nine fields.
     */
    public static int mix(int hashCode1, int hashCode2, int hashCode3, int hashCode4, int hashCode5,
            int hashCode6, int hashCode7, int hashCode8, int hashCode9) {
        return (((((((hashCode1 * 31 + hashCode2) * 31 + hashCode3) * 31 + hashCode4)
                * 31 + hashCode5) * 31 + hashCode6) * 31 + hashCode7) * 31 + hashCode8)
                * 31 + hashCode9;
    }

    /**
     * Combines ten hash codes into one. Can be used for computing the hash code of an object
     * containing ten fields.
     */
    public static int mix(int hashCode1, int hashCode2, int hashCode3, int hashCode4, int hashCode5,
            int hashCode6, int hashCode7, int hashCode8, int hashCode9, int hashCode10) {
        return ((((((((hashCode1 * 31 + hashCode2) * 31 + hashCode3) * 31 + hashCode4)
                * 31 + hashCode5) * 31 + hashCode6) * 31 + hashCode7) * 31 + hashCode8)
                * 31 + hashCode9) * 31 + hashCode10;
    }

    /** Do not instantiate. All methods are static. */
    private HashCodes() {}
}
