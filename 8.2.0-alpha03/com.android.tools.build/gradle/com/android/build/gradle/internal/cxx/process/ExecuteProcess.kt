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
package com.android.build.gradle.internal.cxx.process
import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions.BUILD_STDOUT
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions.CLEAN_STDOUT
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions.CONFIGURE_STDOUT
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions.PREFAB_STDOUT
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions.VERBOSE
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.logStructured
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.logsFolder
import com.android.utils.cxx.os.bat
import com.android.utils.cxx.os.quoteCommandLineArgument
import com.android.build.gradle.internal.cxx.timing.time
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfoBuilder
import com.google.common.annotations.VisibleForTesting
import org.gradle.process.ExecOperations
import java.io.File

/**
 * Describes the different types of process that can be spawned by the CXX build system.
 */
enum class ExecuteProcessType(
    val logFilePrefix: String,
    val outputOptions: NativeBuildOutputOptions) {
    PREFAB_PROCESS("prefab", PREFAB_STDOUT),
    CONFIGURE_PROCESS("configure", CONFIGURE_STDOUT),
    BUILD_PROCESS("build", BUILD_STDOUT),
    CLEAN_PROCESS("clean", CLEAN_STDOUT)
}

data class ExecuteProcessResult(
    val stdout : File,
    val stderr : File
)

/**
 * Execute a process of type [ExecuteProcessType] in the context of this [CxxAbiModel].
 *
 * This is a wrapper over [ExecuteProcessCommand::executeProcess] that is specific to the
 * C\C++ build system such as:
 * - logs are written to predefined locations for this [CxxAbiModel].
 * - the process being executed is specifically one of [ExecuteProcessType]
 * - STDOUT verbosity is determined by flags passed by the user via [NativeBuildOutputOptions].
 * - Process description is set to "C++ build system"
 * - The process is timed and the result is stored in a location that is per-ABI and
 *   per-[ExecuteProcessType].
 *
 * [processType] defines what sort of CXX build system process it is. This is mainly to control
 * the names of log files and whether output should be elevated to 'lifecycle' due to gradle
 * command-line flags.
 *
 * [command] defines executable, arguments, etc. of the process to launch.
 *
 * [logFileSuffix] if present, will be added to the end of the log file name. This is to designate
 * a subcategory. For example, when building C++, the suffix may be the name of the individual
 * target that is being built.
 *
 * [processStderr] and [processStdout] are optional post-process functions. When present, output
 * will not be logged to gradle build output. Instead, the function ([processStderr] or
 * [processStdout]) will be called with a file containing the output. These functions will not
 * be called if a build process exception was thrown. Instead, they will be appended to the
 * [ProcessException] message text.
 */
fun CxxAbiModel.executeProcess(
    processType: ExecuteProcessType,
    command: ExecuteProcessCommand,
    ops: ExecOperations,
    logFileSuffix: String? = null,
    processStderr: ((File) -> Unit)? = null,
    processStdout: ((File) -> Unit)? = null
) : ExecuteProcessResult {
    val suffix = logFileSuffix?.let { "_$it"} ?: ""
    val options = variant.module.outputOptions
    val final = command.copy(
        workingDirectory = variant.module.moduleRootFolder,
        description = "C++ build system [${processType.logFilePrefix}]",
        commandFile = logsFolder.resolve("${processType.logFilePrefix}_command$suffix$bat"),
        stdout = logsFolder.resolve("${processType.logFilePrefix}_stdout$suffix.txt"),
        stderr = logsFolder.resolve("${processType.logFilePrefix}_stderr$suffix.txt"),
        logStdout = processStdout == null,
        logStderr = processStderr == null,
        verbose =  options.contains(VERBOSE) || options.contains(processType.outputOptions))

    time("exec-${processType.logFilePrefix}") {
        infoln("$command")
        final.execute(ops)
    }

    // Post-process stdout if requested
    if (processStdout != null) {
        processStdout(final.stdout)
    }

    // Post-process stderr if requested
    if (processStderr != null) {
        processStderr(final.stderr)
    }

    return ExecuteProcessResult(final.stdout, final.stderr)
}

/**
 * Create a non-JVM [ExecuteProcessCommand] builder.
 */
fun createExecuteProcessCommand(executable : String) = createExecuteProcessCommand(File(executable))
fun createExecuteProcessCommand(executable : File) = ExecuteProcessCommand(executable)

/**
 * Create a JVM [ExecuteProcessCommand] builder.
 *
 * [classPath] is the list of jars or other entities that will be passed to java.exe via the
 * --class-path flag.
 *
 * [main] is the name of the main entry point to use.
 *
 * [javaExe] is the path to java.exe to use. If not specified then the current JVM's java.exe
 * will be used.
 *
 */
fun createJavaExecuteProcessCommand(
    classPath: String,
    main: String,
    javaExe: File = File(System.getProperty("java.home")).resolve("bin/java")
) = createExecuteProcessCommand(javaExe)
    .copy(useScript = true)
    .addArgs("--class-path", classPath, main)

/**
 * Immutable specification that describes a process to execute.
 *
 * [executable] path to the executable to execute.
 *
 * [description] description of this process for logs.
 *
 * [workingDirectory] the directory the process will be executed in.
 *
 * [args] the arguments to execute the process with.
 *
 * [environment] the set of environment variables to execute the process with.
 *
 * [commandFile] path to a shell script (like command.bat) that the command will be written to.
 * If [useScript] is true then this is also the script that will be executed.
 *
 * [stdout] file to write STDOUT to.
 *
 * [stderr] file to write STDERR to.
 *
 * [logStdout] if true, then STDOUT output will be logged to the console.
 *
 * [logStderr] if true, then STDERR output will be logged to the console.
 *
 * [verbose] if true, then STDOUT will be logged to lifecycle which is visible even if the user
 * didn't pass ---info flag.
 *
 * [useScript] if true, then a shell script is written containing the command and the shell script
 * is executed. This will enable shell-specific behaviors such as 'my-script' matching
 * 'my-script.bat'.
 */
data class ExecuteProcessCommand(
    val executable : File,
    val description : String = "",
    val workingDirectory : File = File("."),
    val args : List<String> = listOf(),
    val environment : Map<String, String> = mapOf(),
    val commandFile : File = File("command$bat"),
    val stdout : File = File("stdout.txt"),
    val stderr : File = File("stderr.txt"),
    val logStdout : Boolean = true,
    val logStderr : Boolean = true,
    val verbose : Boolean = false,
    val useScript : Boolean = false
) {
    fun addArgs(vararg arg : String) = copy(args = args + arg)
    fun addArgs(arg : List<String>) = copy(args = args + arg)
    fun addEnvironments(env : Map<String, String>) = copy(environment = environment + env)

    /**
     * Return just the args each listed on a separate line.
     */
    fun argsText() = args.joinToString("\n")

    /**
     * Returns a string containing a shell script that would execute the command.
     */
    override fun toString() : String {
        val cont = if (CURRENT_PLATFORM == PLATFORM_WINDOWS) "^\n" else "\\\n"
        val sb = StringBuilder()
        if (CURRENT_PLATFORM == PLATFORM_WINDOWS) {
            sb.appendLine("@echo off")
        }
        sb.append("${quoteCommandLineArgument(executable.path)} $cont")
        sb.appendLine(args
            // Quote each line (and escape literal quotes)
            .map(::quoteCommandLineArgument)
            // Join each parameter on a separate line that ends with an os/shell specific line
            // continuation character.
            .joinToString(" $cont") { "  $it" })
        return sb.toString()
    }
}

/**
 * Execute the command specified by this [ExecuteProcessCommand]
 */
@VisibleForTesting
fun ExecuteProcessCommand.execute(ops: ExecOperations) {
    try {
        // Write the command to a file.
        commandFile.parentFile.mkdirs()
        commandFile.writeText(toString())
        commandFile.setExecutable(true)

        // Delete prior STDOUT and STDERR
        stdout.delete()
        stderr.delete()

        // Execute the command file that was written
        val process = if (useScript) {
            ProcessInfoBuilder()
                .setExecutable(commandFile)
                .addEnvironments(environment)
                .createProcess()
        } else {
            ProcessInfoBuilder()
                .setExecutable(executable)
                .addArgs(args)
                .addEnvironments(environment)
                .createProcess()
        }

        // Execute the process
        val result =
            GradleProcessExecutor(ops::exec).execute(
                process,
                DefaultProcessOutputHandler(
                    stderrFile = stderr,
                    stdoutFile = stdout,
                    logPrefix = "",
                    logStderr = logStderr,
                    logStdout = logStdout,
                    logFullStdout = verbose))

        // Log the result
        logResult(result.exitValue)

        // Check result and maybe throw
        result
            .rethrowFailure()
            .assertNormalExitValue()
    } catch (e: ProcessException) {
        // Some processes, notably ninja.exe calling clang.exe, put relevant errors in STDOUT.
        // We'd like to make sure they end up in STDERR too, so we attach them in the exception
        // message.
        throw ProcessException(processErrorMessage, e)
    }
}

/**
 * Log the result to structured log.
 */
private fun ExecuteProcessCommand.logResult(exitValue : Int) {
    logStructured { encoder ->
        with(encoder) {
            EncodedExecuteProcess.newBuilder().apply {
                descriptionId = encode(description)
                argsId = encodeList(args)
                environmentKeysId = encodeList(environment.map { it.key })
                environmentValuesId = encodeList(environment.map { it.value })
                exitCode = exitValue
                executableId = encode(executable.path)
            }.build()
        }
    }
}

/**
 * Construct a [ProcessException] error message.
 * Weird indent is because [stdout] and [stderr] expand to lines without indent, so we can't
 * use [String::trimIndent].
 */
private val ExecuteProcessCommand.processErrorMessage get() =
    """
${stdout.readText()}
$description failed while executing:
${toString()
        .split('\n', '\r')
        .filter(String::isNotBlank)
        .joinToString("\n") { "    $it" }}
  from $workingDirectory
${stderr.readText()}
""".trim('\n')
