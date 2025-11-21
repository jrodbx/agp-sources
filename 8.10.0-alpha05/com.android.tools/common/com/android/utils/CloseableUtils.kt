/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.utils

import kotlin.Pair

/**
 * Executes a block of code and closes 2 provided resources.
 *
 * This results in clearer code that resembles the Java `try-with-resources` construct.
 *
 * Note that the second resource is provided as a factory to handle the case where creating the
 * second resource throws, and we need to close the first one.
 */
inline fun <reified A : AutoCloseable, reified B : AutoCloseable, R> withResources(
    a: A,
    makeB: (A) -> B,
    block: (A, B) -> R
): R {
    return a.use {
        makeB(a).use { b ->
            block(a, b)
        }
    }
}

/**
 * Executes a block of code and closes 3 provided resources.
 *
 * This results in clearer code that resembles the Java `try-with-resources` construct.
 *
 * Note that the second and third resources are provided as factories to handle the case where
 * creating them throws, and we need to close the previous ones.
 *
 * Also note that the factory for the third resources is provided by a Pair rather than a lambda
 * with 2 parameters. This is so that the common case of independent resources can use the simpler
 * syntax of `{ C() }` rather than `{ _, _ -> C() }`
 */
inline fun <
        reified A : AutoCloseable,
        reified B : AutoCloseable,
        reified C : AutoCloseable,
        R> withResources(
    a: A,
    makeB: (A) -> B,
    makeC: (Pair<A, B>) -> C,
    block: (A, B, C) -> R
): R {
    return a.use {
        makeB(a).use { b ->
            makeC(Pair(a, b)).use { c ->
                block(a, b, c)
            }
        }
    }
}
