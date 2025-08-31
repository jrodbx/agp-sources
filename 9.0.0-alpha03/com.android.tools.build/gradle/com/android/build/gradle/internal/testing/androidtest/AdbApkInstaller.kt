/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.androidtest

import com.android.utils.GrabProcessOutput
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * A helper for executing Android Debug Bridge (adb) commands for a specific device.
 *
 * This class encapsulates common operations like installing and uninstalling APKs. Its logic is
 * designed to mirror the UTP (Unified Test Platform) `AndroidTestApkInstallerPlugin` for
 * consistent behavior, but without any UTP dependencies.
 *
 * @property adb The [File] pointing to the ADB executable.
 * @property aapt The [File] pointing to the AAPT executable, used for parsing APKs.
 * @property deviceSerial The serial number of the target Android device.
 * @property deviceApiLevel The API level of the target device.
 * @property installTimeoutMs The maximum time to wait for an installation to complete, in
 * milliseconds. A value of 0 means wait indefinitely.
 * @property logger The logger instance for recording command outputs and warnings.
 * @property processBuilder A factory for creating [ProcessBuilder] instances, primarily for testing.
 */
class AdbApkInstaller(
    private val adb: File,
    private val aapt: File,
    private val deviceSerial: String,
    private val deviceApiLevel: Int,
    private val installTimeoutMs: Long,
    private val logger: Logger = Logging.getLogger(AdbApkInstaller::class.java),
    private val processBuilder: (command: List<String>) -> ProcessBuilder = { ProcessBuilder(it) }
) {

    companion object {
        private val packageNameRegex = "package:\\sname='(\\S*)'.*$".toRegex()
    }

    /**
     * Encapsulates options for an APK installation command.
     *
     * @property grantPermissions Grants all runtime permissions automatically on installation
     * (`-g` flag). Requires API 23+.
     * @property forceQueryable Makes the app queryable by any other app on the device
     * (`--force-queryable` flag). Requires API 30+.
     * @property forceReinstall Reinstalls an existing app, allowing version code downgrades
     * (`-r` and `-d` flags).
     * @property forceCompilation Specifies an AOT compilation mode to run after installation.
     * Requires API 24+.
     * @property extraArgs A list of extra string arguments to pass to the `adb install` command.
     */
    data class InstallOptions(
        val grantPermissions: Boolean = false,
        val forceQueryable: Boolean = false,
        val forceReinstall: Boolean = false,
        val forceCompilation: ForceCompilation = ForceCompilation.NO_FORCE_COMPILATION,
        val extraArgs: List<String> = emptyList()
    )

    /** Specifies the AOT compilation mode to be forced after installation. */
    enum class ForceCompilation {
        /** Do not force any compilation mode. */
        NO_FORCE_COMPILATION,
        /** Force full AOT compilation using `cmd package compile -m speed`. */
        FULL_COMPILATION,
        /** Force profile-guided AOT compilation using `cmd package compile -m speed-profile`. */
        PROFILE_BASED_COMPILATION
    }

    /** Centralizes minimum API levels required for specific ADB features. */
    private enum class MinFeatureApiLevel(val apiLevel: Int) {
        SPLIT_APK(21),
        USER_ID(24),
        FORCE_COMPILATION(24),
        GRANT_PERMISSIONS(23),
        FORCE_QUERYABLE(30),
        DISABLE_VERIFIER(33),
        SET_DEBUG_APP(35)
    }

    /**
     * Lazily fetches the current user ID, for devices that support multiple users (API 24+).
     * Returns `null` if the device doesn't support multiple users or if the ID cannot be determined.
     */
    private val userId: String? by lazy {
        if (deviceApiLevel < MinFeatureApiLevel.USER_ID.apiLevel) return@lazy null
        val result = runAdbShellCommand(listOf("am", "get-current-user"))
        if (result.exitCode != 0) {
            logger.warn("Failed to get current user ID from device $deviceSerial.")
            return@lazy null
        }
        val userId = result.output.trim()
        return@lazy if (userId.toIntOrNull() != null) userId else {
            logger.warn("Unexpected output from 'get-current-user': $userId")
            null
        }
    }

    /**
     * Executes pre-installation commands on the device.
     *
     * This may include disabling the adb installation verifier and setting the debug app,
     * depending on the device API level.
     *
     * @param instrumentationTargetPackageId The package ID of the app to be debugged.
     */
    fun preInstallationSetup(instrumentationTargetPackageId: String) {
        if (deviceApiLevel >= MinFeatureApiLevel.DISABLE_VERIFIER.apiLevel) {
            runAdbShellCommand(listOf("settings", "put", "global", "verifier_verify_adb_installs", "0"))
        }
        if (deviceApiLevel >= MinFeatureApiLevel.SET_DEBUG_APP.apiLevel) {
            val result = runAdbShellCommand(listOf("am", "set-debug-app", instrumentationTargetPackageId))
            if (result.exitCode != 0) {
                logger.warn(
                    "Failed to set debug app '$instrumentationTargetPackageId'. " +
                            "Output: ${result.output} \n Error Output: ${result.errorOutput}")
            }
        }
    }

    /**
     * Installs a single APK file onto the target device.
     *
     * @param apk The APK file to be installed.
     * @param options The [InstallOptions] to use for this installation.
     * @throws RuntimeException if installation fails or an option is not supported by the device's
     * API level.
     */
    fun installApk(apk: File, options: InstallOptions) {
        val timeout = if (installTimeoutMs > 0) Duration.ofMillis(installTimeoutMs) else null

        val installCmd = getInstallCmd(options, useMultipleInstall = false)
        val fullCommand = installCmd + apk.absolutePath
        logger.info("Installing with command: adb ${fullCommand.joinToString(" ")}")
        val result = runAdbCommand(fullCommand, timeout)
        if (result.exitCode != 0) {
            throw RuntimeException(
                "Failed to install APK $apk: ${result.output} ${result.errorOutput}")
        }

        // Handle post-install AOT compilation if requested.
        if (deviceApiLevel >= MinFeatureApiLevel.FORCE_COMPILATION.apiLevel &&
            options.forceCompilation != ForceCompilation.NO_FORCE_COMPILATION
        ) {
            forceCompilationApk(apk, options.forceCompilation)
        }
    }

    /**
     * Installs a set of split APKs for a single application.
     *
     * The `adb install-multiple` command is used, which requires API 21+.
     * **Note:** This method cannot be used to install multiple, separate applications at once.
     * All APKs in the list must belong to the same package.
     *
     * @param apks A list of APK files to install, where the first element is typically the base APK.
     * @param options The [InstallOptions] to use for this installation.
     * @throws RuntimeException if installation fails or the device API level is below 21.
     */
    fun installSplitApk(apks: List<File>, options: InstallOptions) {
        check(apks.isNotEmpty()) { "No APKs provided for installation." }

        if (deviceApiLevel < MinFeatureApiLevel.SPLIT_APK.apiLevel) {
            throw RuntimeException(
                "Split APK installation requires API level ${MinFeatureApiLevel.SPLIT_APK.apiLevel}, " +
                        "but device is API level $deviceApiLevel."
            )
        }

        val timeout = if (installTimeoutMs > 0) Duration.ofMillis(installTimeoutMs) else null

        val installCmd = getInstallCmd(options, useMultipleInstall = true)
        val fullCommand = installCmd + apks.map { it.absolutePath }
        logger.info("Installing split-apk with command: adb ${fullCommand.joinToString(" ")}")
        val result = runAdbCommand(fullCommand, timeout)
        if (result.exitCode != 0) {
            throw RuntimeException(
                "Failed to install APKs ${apks.joinToString()}: " +
                        "${result.output} ${result.errorOutput}")
        }

        // Handle post-install AOT compilation if requested.
        if (deviceApiLevel >= MinFeatureApiLevel.FORCE_COMPILATION.apiLevel &&
            options.forceCompilation != ForceCompilation.NO_FORCE_COMPILATION
        ) {
            forceCompilationApk(apks.first(), options.forceCompilation)
        }
    }

    /**
     * Uninstalls the application package associated with the given APK file.
     *
     * This method first parses the APK to determine its package name, then executes `adb uninstall`.
     * If the package name cannot be determined, a warning is logged and no action is taken.
     *
     * @param apk The APK file used to identify the package to uninstall.
     */
    fun uninstallApk(apk: File) {
        getPackageNameFromApk(apk.absolutePath)?.let { packageName ->
            uninstallPackage(packageName)
        } ?: logger.warn("Could not get package name from ${apk.path} to uninstall.")
    }

    /** Uninstalls the specified package from the device using `adb uninstall`. */
    private fun uninstallPackage(packageName: String) {
        logger.info("Uninstalling $packageName from device $deviceSerial.")
        val result = runAdbCommand(listOf("uninstall", packageName))
        if (result.exitCode != 0) {
            logger.warn(
                "Failed to uninstall package $packageName. " +
                        "Output: ${result.output} \n Error Output: ${result.errorOutput}")
        }
    }

    /**
     * Executes post-test cleanup commands on the device.
     *
     * Currently, this clears the debug app setting set by [preInstallationSetup].
     */
    fun postTestCleanup() {
        runAdbShellCommand(listOf("am", "clear-debug-app")).let { result ->
            if (result.exitCode != 0) {
                logger.info(
                    "Failed to execute clear-debug-app command. " +
                            "It may not be supported on this device.")
            }
        }
    }

    /**
     * Executes an external command and captures its output.
     *
     * @param command The command and its arguments to execute.
     * @param timeout An optional timeout for the process. If `null`, waits indefinitely.
     * @return A [CommandResult] with the process exit code, stdout, and stderr.
     */
    private fun runCommand(command: List<String>, timeout: Duration?): CommandResult {
        val process = processBuilder(command).start()
        val outputLines = mutableListOf<String>()
        val errorLines = mutableListOf<String>()

        val handler = object: GrabProcessOutput.IProcessOutput {
            override fun out(line: String?) { line?.let { outputLines.add(it) } }
            override fun err(line: String?) { line?.let { errorLines.add(it) } }
        }

        GrabProcessOutput.grabProcessOutput(
            process,
            GrabProcessOutput.Wait.WAIT_FOR_READERS,
            handler,
            timeout?.toMillis(),
            TimeUnit.MILLISECONDS)

        return CommandResult(
            process.exitValue(),
            outputLines.joinToString("\n"),
            errorLines.joinToString("\n"))
    }

    /** Encapsulates the result of a command execution. */
    private data class CommandResult(val exitCode: Int, val output: String, val errorOutput: String)

    /** Constructs and runs an adb command targeting the specified device. */
    private fun runAdbCommand(args: List<String>, timeout: Duration? = null): CommandResult {
        val command = listOf(adb.absolutePath, "-s", deviceSerial) + args
        return runCommand(command, timeout)
    }

    /** Constructs and runs an `adb shell` command. */
    private fun runAdbShellCommand(args: List<String>, timeout: Duration? = null): CommandResult {
        return runAdbCommand(listOf("shell") + args, timeout)
    }

    /**
     * Constructs the base `adb install` or `adb install-multiple` command list based on the
     * given options.
     */
    private fun getInstallCmd(options: InstallOptions, useMultipleInstall: Boolean): List<String> {
        // "install-multiple" is specifically for installing split APKs for a single application.
        // Despite the name, it cannot install multiple different apps at once.
        val command = if (useMultipleInstall) "install-multiple" else "install"
        val cmd = mutableListOf(command)

        if (deviceApiLevel >= 34) cmd.add("--bypass-low-target-sdk-block")
        cmd.add("-t") // Allows installation of APKs marked testOnly.
        if (options.forceReinstall) cmd.addAll(listOf("-r", "-d"))
        if (options.grantPermissions && deviceApiLevel >= MinFeatureApiLevel.GRANT_PERMISSIONS.apiLevel) {
            cmd.add("-g")
        }
        if (options.forceQueryable && deviceApiLevel >= MinFeatureApiLevel.FORCE_QUERYABLE.apiLevel) {
            cmd.add("--force-queryable")
        }
        userId?.let { cmd.addAll(listOf("--user", it)) }
        cmd.addAll(options.extraArgs)
        return cmd
    }

    /** Parses an APK file using AAPT to find its package name. */
    private fun getPackageNameFromApk(apkPath: String): String? {
        val result = runCommand(listOf(aapt.absolutePath, "dump", "badging", apkPath), null)
        return result.output.lineSequence().mapNotNull { line ->
            packageNameRegex.find(line)?.groupValues?.get(1)
        }.firstOrNull()
    }

    /** Triggers forced AOT compilation for the package associated with the given APK. */
    private fun forceCompilationApk(apk: File, mode: ForceCompilation) {
        getPackageNameFromApk(apk.absolutePath)?.let { packageName ->
            forceCompilationPackage(packageName, mode)
        } ?: logger.warn("Could not get package name from ${apk.path} to force compilation.")
    }

    /** Runs the `cmd package compile` shell command for a given package and compilation mode. */
    private fun forceCompilationPackage(packageName: String, mode: ForceCompilation) {
        val compileMode = when (mode) {
            ForceCompilation.FULL_COMPILATION -> "speed"
            ForceCompilation.PROFILE_BASED_COMPILATION -> "speed-profile"
            ForceCompilation.NO_FORCE_COMPILATION -> return
        }
        logger.info("Running force AOT compilation ($compileMode) for $packageName")
        val command = listOf("cmd", "package", "compile", "-m", compileMode, "-f", packageName)
        runAdbShellCommand(command)
    }
}
