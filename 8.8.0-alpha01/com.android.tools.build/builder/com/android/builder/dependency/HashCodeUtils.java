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

package com.android.builder.dependency;

/**
 * Utilities to calculate hashcodes without creating temporary Object[] instances.
 * @see com.google.common.base.Objects#hashCode()
 */
public class HashCodeUtils {

    public static int hashCode(Object a) {
        if (a == null)
            return 0;

        int result = 1;

        result = 31 * result + a.hashCode();

        return result;
    }

    public static int hashCode(Object a, Object b) {
        if (a == null && b == null)
            return 0;

        int result = 1;

        result = 31 * result + (a == null ? 0 : a.hashCode());
        result = 31 * result + (b == null ? 0 : b.hashCode());

        return result;
    }

    public static int hashCode(Object a, Object b, Object c) {
        if (a == null && b == null && c == null)
            return 0;

        int result = 1;

        result = 31 * result + (a == null ? 0 : a.hashCode());
        result = 31 * result + (b == null ? 0 : b.hashCode());
        result = 31 * result + (c == null ? 0 : c.hashCode());

        return result;
    }

    public static int hashCode(Object a, Object b, Object c, Object d) {
        if (a == null && b == null && c == null && d == null)
            return 0;

        int result = 1;

        result = 31 * result + (a == null ? 0 : a.hashCode());
        result = 31 * result + (b == null ? 0 : b.hashCode());
        result = 31 * result + (c == null ? 0 : c.hashCode());
        result = 31 * result + (d == null ? 0 : d.hashCode());

        return result;
    }

    public static int hashCode(Object a, Object b, Object c, Object d, Object e) {
        if (a == null && b == null && c == null && d == null && e == null)
            return 0;

        int result = 1;

        result = 31 * result + (a == null ? 0 : a.hashCode());
        result = 31 * result + (b == null ? 0 : b.hashCode());
        result = 31 * result + (c == null ? 0 : c.hashCode());
        result = 31 * result + (d == null ? 0 : d.hashCode());
        result = 31 * result + (e == null ? 0 : e.hashCode());

        return result;
    }

    public static int hashCode(Object a, Object b, Object c, Object d, Object e, Object f) {
        if (a == null && b == null && c == null && d == null && e == null && f ==null)
            return 0;

        int result = 1;

        result = 31 * result + (a == null ? 0 : a.hashCode());
        result = 31 * result + (b == null ? 0 : b.hashCode());
        result = 31 * result + (c == null ? 0 : c.hashCode());
        result = 31 * result + (d == null ? 0 : d.hashCode());
        result = 31 * result + (e == null ? 0 : e.hashCode());
        result = 31 * result + (f == null ? 0 : f.hashCode());

        return result;
    }

    public static int hashCode(Object a, Object b, Object c, Object d, Object e, Object f, Object g, Object h, Object i) {
        if (a == null && b == null && c == null && d == null && e == null && f == null && g == null && h == null && i == null)
            return 0;

        int result = 1;

        result = 31 * result + (a == null ? 0 : a.hashCode());
        result = 31 * result + (b == null ? 0 : b.hashCode());
        result = 31 * result + (c == null ? 0 : c.hashCode());
        result = 31 * result + (d == null ? 0 : d.hashCode());
        result = 31 * result + (e == null ? 0 : e.hashCode());
        result = 31 * result + (f == null ? 0 : f.hashCode());
        result = 31 * result + (g == null ? 0 : g.hashCode());
        result = 31 * result + (h == null ? 0 : h.hashCode());
        result = 31 * result + (i == null ? 0 : i.hashCode());

        return result;
    }
}
