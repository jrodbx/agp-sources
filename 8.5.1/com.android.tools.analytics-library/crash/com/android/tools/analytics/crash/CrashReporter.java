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
package com.android.tools.analytics.crash;

import com.android.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.http.HttpEntity;

public interface CrashReporter {
  @NonNull
  CompletableFuture<String> submit(@NonNull CrashReport crashReport);

  @NonNull
  CompletableFuture<String> submit(@NonNull CrashReport crashReport, boolean userReported);

  @NonNull
  CompletableFuture<String> submit(@NonNull Map<String, String> kv);

  @NonNull
  CompletableFuture<String> submit(@NonNull HttpEntity entity);
}
