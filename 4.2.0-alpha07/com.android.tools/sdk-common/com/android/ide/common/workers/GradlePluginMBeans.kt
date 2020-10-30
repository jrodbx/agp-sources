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

package com.android.ide.common.workers

import java.lang.management.ManagementFactory
import javax.management.MBeanServerInvocationHandler
import javax.management.ObjectName

/**
 * Singleton object for [ProfileMBean] related services.
 */
object GradlePluginMBeans {

    const val domainName = "domain:type=Gradle.agp"
    const val profilingRootObjectName = "$domainName,name=profiling"
    private val mbs = ManagementFactory.getPlatformMBeanServer()

    enum class MBeanName {
        BUILD,
        PROFILING;

        val objectName by lazy {
            ObjectName("$domainName,name=$name")
        }

        /**
         * Compose a [ObjectName] for a specific project under which the [ProfileMBean] will be
         * registered in the [javax.management.MBeanServer]
         *
         * @param projectName the project name.
         */
        fun getProjectSpecificObjectName(projectName: String):ObjectName {
            val name= "$domainName,name=$name,project=$projectName"
            return try {
                ObjectName(name)
            } catch (t: Throwable) {
                ObjectName(profilingRootObjectName)
            }
        }
    }

    /**
     * Return the [BuildMBean] for this build session.
     *
     * There is only one instance of this mbean per build irrespective of how many projects this
     * build is composed of. That's also true for composite builds.
     */
    fun getBuildMBean(): BuildMBean? =
        getMBean(MBeanName.BUILD.objectName, BuildMBean::class.java)

    /**
     * Return the [ProfileMBean] for this project. Can be null in unit tests or if the
     * profiling has been disabled.
     *
     * @param projectName the project name.
     * @return the project's [ProfileMBean] or null
     */
    fun getProfileMBean(projectName: String): ProfileMBean? =
        getMBean(MBeanName.PROFILING.getProjectSpecificObjectName(projectName),
            ProfileMBean::class.java)

    private fun <T> getMBean(objectName: ObjectName, type: Class<T>): T? =
        if (!mbs.queryMBeans(objectName, null).isEmpty()) {
            MBeanServerInvocationHandler.newProxyInstance(
                mbs,
                objectName,
                type,
                false
            )
        } else null
}