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

package com.android.build.gradle.internal.profile

import com.android.ide.common.workers.ProfileMBean
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import java.time.Duration
import java.time.Instant

/**
 * Implementation of [ProfileMBean] that will be registered in the MBean server and
 * be available as a singleton per jvm.
 */
class ProfileMBeanImpl(private val buildListener: RecordingBuildListener):
    ProfileMBean {

    override fun workerStarted(taskPath: String, workerKey: String) {
        val workerRecord = buildListener.getWorkerRecord(taskPath, workerKey)
        workerRecord?.executionStarted()
    }

    override fun workerFinished(taskPath: String, workerKey: String) {
        val workerRecord = buildListener.getWorkerRecord(taskPath, workerKey)
        if (workerRecord!=null) {
            workerRecord.executionFinished()
            buildListener.getTaskRecord(taskPath)?.workerFinished(workerRecord)
        }
    }

    override fun registerSpan(taskPath:String?, builder: GradleBuildProfileSpan.Builder) {
        if (taskPath==null) {
            buildListener.recordAnonymousSpan(builder)
        } else {
            val taskRecord = buildListener.getTaskRecord(taskPath)
            taskRecord?.addSpan(builder)
        }
    }
}