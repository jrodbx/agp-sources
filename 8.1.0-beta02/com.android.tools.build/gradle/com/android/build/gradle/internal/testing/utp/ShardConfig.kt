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

package com.android.build.gradle.internal.testing.utp

/**
 * Class for keeping track of all sharding information to invoke a single shard.
 *
 * @param totalCount The total number of shards in this test invocation.
 * @param index The index of the this shard, should be in the range 0 to ([totalCount] - 1)
 */
data class ShardConfig(val totalCount: Int, val index: Int)
