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
package com.android.tools.analytics

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.google.wireless.android.sdk.stats.*
import com.google.wireless.android.sdk.stats.DeviceInfo.ApplicationBinaryInterface
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File
import java.util.*
import java.util.regex.Pattern

private const val TRANSLATED = 1
private const val IMAGE_FILE_MACHINE_ARM64 = 0xAA64

/** Calculates common pieces of metrics data, used in various Android DevTools. */
object CommonMetricsData {

  const val VM_OPTION_XMS = "-Xms"
  const val VM_OPTION_XMX = "-Xmx"
  const val VM_OPTION_MAX_PERM_SIZE = "-XX:MaxPermSize="
  const val VM_OPTION_RESERVED_CODE_CACHE_SIZE = "-XX:ReservedCodeCacheSize="
  const val VM_OPTION_SOFT_REF_LRU_POLICY_MS_PER_MB = "-XX:SoftRefLRUPolicyMSPerMB="
  const val KILOBYTE = 1024L
  const val MEGABYTE = KILOBYTE * 1024
  const val GIGABYTE = MEGABYTE * 1024
  const val TERABYTE = GIGABYTE * 1024
  const val NO_DIGITS = -1
  const val INVALID_POSTFIX = -2
  const val INVALID_NUMBER = -3
  const val EMPTY_SIZE = -4

  const val OS_NAME_FREE_BSD = "freebsd"
  const val OS_NAME_LINUX = "linux"
  const val OS_NAME_MAC = "macosx"
  const val OS_NAME_WINDOWS = "windows"
  const val OS_NAME_CHROMIUM = "chromium"

  @VisibleForTesting
  @JvmStatic
  val garbageCollectionStatsCache: HashMap<String, GarbageCollectionStatsDiffs> = HashMap()

  /**
   * Detects and returns the OS architecture: x86, x86_64, ppc, arm, or arm on jvm. This may differ
   * or be equal to the JVM architecture in the sense that a 64-bit OS can run a 32-bit JVM.
   */
  @JvmStatic
  val osArchitecture: ProductDetails.CpuArchitecture
    get() {
      val jvmArchitecture = jvmArchitecture
      val os =
        Environment.instance
          .getSystemProperty(Environment.SystemProperty.OS_NAME)!!
          .lowercase(Locale.getDefault())

      if (jvmArchitecture == ProductDetails.CpuArchitecture.X86_64) {
        // An x86 jvm running on an M1 chip will be translated to ARM using Rosetta. Checking for
        // Rosetta requires jna. The current version of jna (net.java.dev.jna:jna-5.6.0)  will
        // fail if we're running on an ARM jvm. Only call isRosetta if we're using an x86 jvm
        if ((os.startsWith("mac") && isRosetta()) || (os.startsWith("win") && isWindowsArm64())) {
          return ProductDetails.CpuArchitecture.X86_ON_ARM
        }
      }

      if (jvmArchitecture == ProductDetails.CpuArchitecture.X86) {

        if (os.startsWith("win")) {
          val w6432 =
            Environment.instance.getVariable(Environment.EnvironmentVariable.PROCESSOR_ARCHITEW6432)
          // This is the misleading case: the JVM is 32-bit but the OS
          // might be either 32 or 64. We can't tell just from this
          // property.
          // Macs are always on 64-bit, so we just need to figure it
          // out for Windows and Linux.
          // When WOW64 emulates a 32-bit environment under a 64-bit OS,
          // it sets PROCESSOR_ARCHITEW6432 to AMD64 or IA64 accordingly.
          // Ref: http://msdn.microsoft.com/en-us/library/aa384274(v=vs.85).aspx
          // Let's try the obvious. This works in Ubuntu and Debian
          if (w6432 != null && w6432.contains("64")) {
            return ProductDetails.CpuArchitecture.X86_64
          }
        } else if (os.startsWith("linux")) {
          val s = Environment.instance.getVariable(Environment.EnvironmentVariable.HOSTTYPE)
          return cpuArchitectureFromString(s)
        }
      }
      return jvmArchitecture
    }

  /**
   * Gets the JVM Architecture, NOTE this might not be the same as OS architecture. See
   * [ ][.getOsArchitecture] if OS architecture is needed.
   */
  @JvmStatic
  val jvmArchitecture: ProductDetails.CpuArchitecture
    get() {
      val arch = Environment.instance.getSystemProperty(Environment.SystemProperty.OS_ARCH)
      return cpuArchitectureFromString(arch)
    }

  /** Gets a normalized version of the os name that this code is running on. */
  // Unknown -- send it verbatim so we can see it
  // but protect against arbitrarily long values
  @JvmStatic
  val osName: String
    get() {
      var os: String? = Environment.instance.getSystemProperty(Environment.SystemProperty.OS_NAME)

      if (os == null || os.isEmpty()) {
        return "unknown"
      }

      val osLower = os.toLowerCase(Locale.US)

      when {
        osLower.startsWith("mac") -> os = OS_NAME_MAC
        osLower.startsWith("win") -> os = OS_NAME_WINDOWS
        osLower.startsWith("linux") -> {
          if (File("/dev/.cros_milestone").exists()) {
            os = OS_NAME_CHROMIUM
          } else {
            os = OS_NAME_LINUX
          }
        }
        os.length > 32 -> os = os.substring(0, 32)
      }
      return os
    }

  /** Extracts the major os version that this code is running on in the form of '[0-9]+\.[0-9]+' */
  @JvmStatic
  val majorOsVersion: String?
    get() {
      if (osName == "chromium") {
        return File("/dev/.cros_milestone").readText(Charsets.UTF_8)
      }

      val p = Pattern.compile("(\\d+)\\.(\\d+).*")
      val osVers = Environment.instance.getSystemProperty(Environment.SystemProperty.OS_VERSION)
      if (osVers != null && osVers.isNotEmpty()) {
        val m = p.matcher(osVers)
        if (m.matches()) {
          return "${m.group(1)}${'.'}${m.group(2)}"
        }
      }
      return null
    }

  /** Gets information about the jvm this code is running in. */
  @JvmStatic
  val jvmDetails: JvmDetails
    get() {
      val runtime = HostData.runtimeBean!!

      val builder =
        JvmDetails.newBuilder()
          .setName(Strings.nullToEmpty(runtime.vmName))
          .setVendor(Strings.nullToEmpty(runtime.vmVendor))
          .setVersion(Strings.nullToEmpty(runtime.vmVersion))

      for (vmOption in runtime.inputArguments) {
        parseVmOption(vmOption, builder)
      }

      return builder.build()
    }

  /** Gets stats about the current process java runtime. */
  @JvmStatic
  val javaProcessStats: JavaProcessStats
    get() {
      val memoryBean = HostData.memoryBean!!
      val classLoadingBean = HostData.classLoadingBean!!

      return JavaProcessStats.newBuilder()
        .setHeapMemoryUsage(memoryBean.heapMemoryUsage.used)
        .setNonHeapMemoryUsage(memoryBean.nonHeapMemoryUsage.used)
        .setLoadedClassCount(classLoadingBean.loadedClassCount)
        .addAllGarbageCollectionStats(garbageCollectionStats)
        .setThreadCount(HostData.threadBean!!.threadCount)
        .build()
    }

  /**
   * Gets stats about the current process' Garbage Collectors. Instead of returning cumulative data
   * since process was started, it reports stats since the last call to this method.
   */
  @JvmStatic
  @VisibleForTesting
  val garbageCollectionStats: List<GarbageCollectionStats>
    get() {
      val stats = ArrayList<GarbageCollectionStats>()
      for (gc in HostData.garbageCollectorBeans!!) {
        val name = gc.name
        var previous: GarbageCollectionStatsDiffs? = garbageCollectionStatsCache[name]
        if (previous == null) {
          previous = GarbageCollectionStatsDiffs()
        }
        val current = GarbageCollectionStatsDiffs()
        current.collections = gc.collectionCount
        val collectionsDiff = current.collections - previous.collections

        current.time = gc.collectionTime
        val timeDiff = current.time - previous.time
        garbageCollectionStatsCache[name] = current

        stats.add(
          GarbageCollectionStats.newBuilder()
            .setName(gc.name)
            .setGcCollections(collectionsDiff)
            .setGcTime(timeDiff)
            .build()
        )
      }
      return stats
    }

  /** Used to calculate diffs between different reports of Garbage Collection stats. */
  @VisibleForTesting
  class GarbageCollectionStatsDiffs {
    @Volatile var collections: Long = 0
    @Volatile var time: Long = 0
  }

  /**
   * Builds a [ProductDetails.CpuArchitecture] instance based on the provided string (e.g.
   * "x86_64").
   */
  @JvmStatic
  fun cpuArchitectureFromString(cpuArchitecture: String?): ProductDetails.CpuArchitecture {
    if (cpuArchitecture == null || cpuArchitecture.isEmpty()) {
      return ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE
    }

    if (
      cpuArchitecture.equals("x86_64", ignoreCase = true) ||
        cpuArchitecture.equals("ia64", ignoreCase = true) ||
        cpuArchitecture.equals("amd64", ignoreCase = true)
    ) {
      return ProductDetails.CpuArchitecture.X86_64
    }

    if (cpuArchitecture.equals("x86", ignoreCase = true)) {
      return ProductDetails.CpuArchitecture.X86
    }

    if (cpuArchitecture.equals("aarch64", ignoreCase = true)) {
      return ProductDetails.CpuArchitecture.ARM
    }

    return if (
      cpuArchitecture.length == 4 && cpuArchitecture[0] == 'i' && cpuArchitecture.indexOf("86") == 2
    ) {
      // Any variation of iX86 counts as x86 (i386, i486, i686).
      ProductDetails.CpuArchitecture.X86
    } else ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE
  }

  @JvmStatic
  fun applicationBinaryInterfaceFromString(value: String?): ApplicationBinaryInterface {
    if (value == null) {
      return ApplicationBinaryInterface.UNKNOWN_ABI
    }
    return when (value) {
      "armeabi-v6j" -> ApplicationBinaryInterface.ARME_ABI_V6J
      "armeabi-v6l" -> ApplicationBinaryInterface.ARME_ABI_V6L
      "armeabi-v7a" -> ApplicationBinaryInterface.ARME_ABI_V7A
      "armeabi" -> ApplicationBinaryInterface.ARME_ABI
      "arm64-v8a" -> ApplicationBinaryInterface.ARM64_V8A_ABI
      "mips" -> ApplicationBinaryInterface.MIPS_ABI
      "mips-r2" -> ApplicationBinaryInterface.MIPS_R2_ABI
      "x86" -> ApplicationBinaryInterface.X86_ABI
      "x86_64" -> ApplicationBinaryInterface.X86_64_ABI
      else -> ApplicationBinaryInterface.UNKNOWN_ABI
    }
  }

  /** Parses known VM options into a [JvmDetails.Builder] */
  @JvmStatic
  private fun parseVmOption(vmOption: String, builder: JvmDetails.Builder) {
    when {
      vmOption.startsWith(VM_OPTION_XMS) ->
        builder.minimumHeapSize = parseVmOptionSize(vmOption.substring(VM_OPTION_XMS.length))
      vmOption.startsWith(VM_OPTION_XMX) ->
        builder.maximumHeapSize = parseVmOptionSize(vmOption.substring(VM_OPTION_XMX.length))
      vmOption.startsWith(VM_OPTION_MAX_PERM_SIZE) ->
        builder.maximumPermanentSpaceSize =
          parseVmOptionSize(vmOption.substring(VM_OPTION_MAX_PERM_SIZE.length))
      vmOption.startsWith(VM_OPTION_RESERVED_CODE_CACHE_SIZE) ->
        builder.maximumCodeCacheSize =
          parseVmOptionSize(vmOption.substring(VM_OPTION_RESERVED_CODE_CACHE_SIZE.length))
      vmOption.startsWith(VM_OPTION_SOFT_REF_LRU_POLICY_MS_PER_MB) ->
        builder.softReferenceLruPolicy =
          parseVmOptionSize(vmOption.substring(VM_OPTION_SOFT_REF_LRU_POLICY_MS_PER_MB.length))
    }

    when (vmOption) {
      "-XX:+UseConcMarkSweepGC" ->
        builder.garbageCollector = JvmDetails.GarbageCollector.CONCURRENT_MARK_SWEEP_GC
      "-XX:+UseParallelGC" -> builder.garbageCollector = JvmDetails.GarbageCollector.PARALLEL_GC
      "-XX:+UseParallelOldGC" ->
        builder.garbageCollector = JvmDetails.GarbageCollector.PARALLEL_OLD_GC
      "-XX:+UseSerialGC" -> builder.garbageCollector = JvmDetails.GarbageCollector.SERIAL_GC
      "-XX:+UseG1GC" -> builder.garbageCollector = JvmDetails.GarbageCollector.SERIAL_GC
    }
  }

  /** Parses VM options size formatted as "[0-9]+[GgMmKk]?" into a long. */
  @VisibleForTesting
  @JvmStatic
  fun parseVmOptionSize(vmOptionSize: String): Long {
    if (Strings.isNullOrEmpty(vmOptionSize)) {
      return EMPTY_SIZE.toLong()
    }
    try {
      for (i in 0 until vmOptionSize.length) {
        val c = vmOptionSize[i]
        if (!Character.isDigit(c)) {
          if (i == 0) {
            return NO_DIGITS.toLong()
          }
          val digits = vmOptionSize.substring(0, i)
          val value = java.lang.Long.parseLong(digits)
          return when (c) {
            't',
            'T' -> value * TERABYTE
            'g',
            'G' -> value * GIGABYTE
            'm',
            'M' -> value * MEGABYTE
            'k',
            'K' -> value * KILOBYTE
            else -> INVALID_POSTFIX.toLong()
          }
        }
      }
      return java.lang.Long.parseLong(vmOptionSize)
    } catch (e: NumberFormatException) {
      return INVALID_NUMBER.toLong()
    }
  }
}

/**
 * Determines if the current process is being translated to ARM by Rosetta.
 *
 * Processes running under Rosetta translation return 1 when sysctlbyname is called with
 * sysctl.proc_translated ref:
 * https://developer.apple.com/documentation/apple_silicon/about_the_rosetta_translation_environment
 */
fun isRosetta(): Boolean {
  val clazz =
    try {
      @Suppress("UNCHECKED_CAST")
      Class.forName("com.sun.jna.platform.mac.SystemB") as? Class<Library> ?: return false
    } catch (e: ClassNotFoundException) {
      return false
    } catch (e: LinkageError) {
      return false
    }

  val instanceField =
    try {
      clazz.getField("INSTANCE")
    } catch (e: NoSuchFieldException) {
      return false
    }

  val instance =
    try {
      instanceField.get(null)
    } catch (e: IllegalArgumentException) {
      return false
    }

  val sysctlbyname =
    try {
      clazz.getMethod(
        "sysctlbyname",
        String::class.java,
        Pointer::class.java,
        IntByReference::class.java,
        Pointer::class.java,
        Int::class.java,
      )
    } catch (e: NoSuchMethodException) {
      return false
    }

  val memory = Memory(4)
  val retSize = IntByReference(4)

  val errorCode =
    try {
      sysctlbyname.invoke(instance, "sysctl.proc_translated", memory, retSize, null, 0)
    } catch (e: Exception) {
      return false
    }

  return errorCode == 0 && memory.getInt(0) == TRANSLATED
}

fun isWindowsArm64(): Boolean {
  try {
    // https://learn.microsoft.com/en-us/windows/arm/apps-on-arm-x86-emulation#detecting-emulation
    val pidHandle = Kernel32.INSTANCE.GetCurrentProcess()
    val lib = Native.load("kernel32", NativeIsWow64::class.java, W32APIOptions.DEFAULT_OPTIONS);
    val processMachine = WinDef.USHORTByReference()
    val nativeMachine = WinDef.USHORTByReference()
    val result = lib.IsWow64Process2(pidHandle, processMachine, nativeMachine)

    // https://learn.microsoft.com/en-us/windows/win32/sysinfo/image-file-machine-constants
    return result && (nativeMachine.value.toInt() == IMAGE_FILE_MACHINE_ARM64)
  } catch (_: Throwable) {
    return false
  }
}

private interface NativeIsWow64 : StdCallLibrary {

  fun IsWow64Process2(
    hProcess: WinNT.HANDLE,
    processMachine: WinDef.USHORTByReference,
    nativeMachine: WinDef.USHORTByReference
  ): Boolean
}
