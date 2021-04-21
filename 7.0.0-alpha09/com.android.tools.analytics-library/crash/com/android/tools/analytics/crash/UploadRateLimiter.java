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

import com.google.common.util.concurrent.RateLimiter;

/** A wrapper interface on top of {@link RateLimiter} to facilitate testing. */
public interface UploadRateLimiter {
  boolean tryAcquire();

  static UploadRateLimiter create(double qps) {
    return new UploadRateLimiter() {
      private final RateLimiter myRateLimiter = RateLimiter.create(qps);

      @Override
      public boolean tryAcquire() {
        return myRateLimiter.tryAcquire();
      }
    };
  }
}
