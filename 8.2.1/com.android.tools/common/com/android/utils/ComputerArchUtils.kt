/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.util.Locale

private const val OS_MAC_PREFIX = "mac"
private const val OS_WIN_PREFIX = "win"
private const val OS_LINUX_PREFIX = "linux"
private const val TRANSLATED = 1

enum class CpuArchitecture {
    X86,
    X86_64,
    ARM,
    X86_ON_ARM,
    UNKNOWN
}

abstract class Environment {

    enum class EnvironmentVariable(val key: String) {
        HOST_TYPE("HOSTTYPE"),
        PROCESSOR_ARCHITEW6432("PROCESSOR_ARCHITEW6432"),
    }

    enum class  SystemProperty(val key: String) {
        OS_ARCH("os.arch"),
        OS_NAME("os.name"),
    }

    abstract fun getVariable(name: EnvironmentVariable): String?

    open fun getSystemProperty(name: SystemProperty): String? = System.getProperty(name.key)

    /**
     * Whether or not Rosetta is being run on a m1 device. See [computeIsRosetta]
     */
    open val isRosetta: Boolean
    	get() = computeIsRosetta()

    companion object {
        val SYSTEM: Environment = object : Environment() {
            override fun getVariable(name: EnvironmentVariable) = System.getenv(name.key)

            override val isRosetta
                get() = computeIsRosetta()
        }

        var instance = SYSTEM
        var initialized = false

        @JvmStatic
        fun initialize(customEnvironment: Environment? = null) {
            initialized = true
            customEnvironment?.let { instance = it }
        }
    }
}

private fun ensureInitialized() {
    if (!Environment.initialized) {
        // TODO(b/283002867): log info to let test environments know that the environment was
        // not initialized.
        Environment.initialize()
    }
}

val jvmArchitecture: CpuArchitecture
    get() {
        ensureInitialized()
        val arch = Environment.instance.getSystemProperty(Environment.SystemProperty.OS_ARCH)
        return architectureFromString(arch)
    }

val osArchitecture: CpuArchitecture
    get() {
        val jvmArchitecture = jvmArchitecture

        val os = Environment.instance.getSystemProperty(Environment.SystemProperty.OS_NAME)
            ?.lowercase(Locale.US) ?: return jvmArchitecture

        // When running the JVM in x86, then that means either we are running a 32 bit environment
        // under a 64 bit OS or a 32 bit environment on a 32 bit OS. This can't happen on mac, as
        // they always run in a 64 bit environment. So we need to take a look at Windows and Linux.
        if (jvmArchitecture == CpuArchitecture.X86) {
            when {
                os.startsWith(OS_WIN_PREFIX) -> {
                    // When WOW64 emulates a 32 bit environment under a 64 bit,
                    // it sets PROCESSOR_ARCHITEW6432 to AMD64 or IA64.
                    // Ref: http://msdn.microsoft.com/en-us/library/aa384274(v=vs.85).aspx
                    val w6432 = Environment.instance
                        .getVariable(Environment.EnvironmentVariable.PROCESSOR_ARCHITEW6432)
                    if (w6432 != null && w6432.contains("64")) {
                        return CpuArchitecture.X86_64
                    }
                }
                os.startsWith(OS_LINUX_PREFIX) -> {
                    // Trying the obvious by querying HOSTTYPE, this works on debian and ubuntu
                    val hostType = Environment.instance
                        .getVariable(Environment.EnvironmentVariable.HOST_TYPE)
                    return architectureFromString(hostType)
                }
            }
        }

        // An x86_64 jvm running on an M1 chip will be tranlated to ARM using Rosetta. In order
        // to determine if running Rosetta we need to use jna (see [computeIsRosetta]).
        // Note: we check architecture first, as jna fails in an arm jvm.
        if (jvmArchitecture == CpuArchitecture.X86_64
            && os.startsWith(OS_MAC_PREFIX)
            && Environment.instance.isRosetta) {
            return CpuArchitecture.X86_ON_ARM
        }

        return jvmArchitecture
    }

/**
 * Determines if the current process is being translated to ARM by Rosetta.
 *
 * Processes running under Rosetta translation return 1 when sysctlbyname is called with
 * sysctl.proc_translated
 * ref: https://developer.apple.com/documentation/apple_silicon/about_the_rosetta_translation_environment
 *
 * Checking for Rosetta requires jna. The current version of jna (net.java.dev.jna:jna-5.60) will
 * fail if running on an arm jvm. Only call if we're in an x86_64 jvm.
 */
private fun computeIsRosetta(): Boolean {
    val clazz = try {
        @Suppress("UNCHECKED_CAST")
        Class.forName("com.sun.jna.platform.mac.SystemB") as? Class<Library> ?: return false
    }
    catch (e: ClassNotFoundException) {
        return false
    }
    catch (e: LinkageError) {
        return false
    }

    val instanceField = try {
        clazz.getField("INSTANCE")
    }
    catch (e: NoSuchFieldException) {
        return false
    }

    val instance = try {
        instanceField.get(null)
    }
    catch (e: IllegalArgumentException) {
        return false
    }

    val sysctlbyname = try {
        clazz.getMethod("sysctlbyname", String::class.java, Pointer::class.java, IntByReference::class.java, Pointer::class.java, Int::class.java)
    }
    catch(e: NoSuchMethodException) {
        return false
    }

    val memory = Memory(4)
    val retSize = IntByReference(4)

    val errorCode = try {
        sysctlbyname.invoke(instance, "sysctl.proc_translated", memory, retSize, null, 0)
    }
    catch(e: Exception) {
        return false
    }

    return errorCode == 0 && memory.getInt(0) == TRANSLATED
}

/**
 * Extracts the CpuArchitecture based on the provided architecture string.
 */
fun architectureFromString(cpuArchName: String?): CpuArchitecture =
    when {
        cpuArchName.isNullOrEmpty() -> CpuArchitecture.UNKNOWN
        cpuArchName.equals("x86_64", ignoreCase = true) -> CpuArchitecture.X86_64
        cpuArchName.equals("ia64", ignoreCase = true) -> CpuArchitecture.X86_64
        cpuArchName.equals("amd64", ignoreCase = true) -> CpuArchitecture.X86_64
        cpuArchName.equals("x86", ignoreCase = true) -> CpuArchitecture.X86
        cpuArchName.equals("aarch64", ignoreCase = true) -> CpuArchitecture.ARM
        // Any variation of iX86 is x86 (i386, i486, etc.)
        cpuArchName.length == 4
                && cpuArchName[0] == 'i'
                && cpuArchName.endsWith("86") -> CpuArchitecture.X86
        else -> CpuArchitecture.UNKNOWN
    }
