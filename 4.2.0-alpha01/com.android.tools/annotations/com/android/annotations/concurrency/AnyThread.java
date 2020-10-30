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
package com.android.annotations.concurrency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the target method may be called on any thread, including the UI thread (by the
 * platform or other IDE components), so should not call any {@link Slow} methods. On the other hand
 * they may be called on a non-UI thread so should not call {@link UiThread} methods directly
 * either, instead scheduling such code to be executed on the UI thread.
 *
 * <p>If the annotated element is a class, then all methods in the class obey the contract described
 * above.
 *
 * <p>If the annotated element is a method parameter, e.g. a {@link Runnable}, it means the callback
 * may be invoked on any thread and needs to obey hte contract described above.
 *
 * @see UiThread
 * @see Slow
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
public @interface AnyThread {}
