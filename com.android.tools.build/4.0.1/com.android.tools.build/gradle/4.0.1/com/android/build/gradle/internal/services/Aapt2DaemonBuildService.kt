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

@file:JvmName("Aapt2Daemon")

package com.android.build.gradle.internal.services

import com.android.SdkConstants
import com.android.annotations.concurrency.GuardedBy
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.builder.internal.aapt.v2.Aapt2DaemonImpl
import com.android.builder.internal.aapt.v2.Aapt2DaemonManager
import com.android.builder.internal.aapt.v2.Aapt2DaemonTimeouts
import com.android.ide.common.process.ProcessException
import com.android.utils.ILogger
import com.google.common.io.Closer
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Used to get unique build service name. Each class loader will initialize its own version. */
private val AAPT2_DAEMON_BUILD_SERVICE_NAME = "aapt2-daemon-build-service" + UUID.randomUUID().toString()

/**
 * Registers aapt2 daemon build services. This makes it available for querying, by using
 * [getAapt2DaemonBuildService] method.
 */
fun registerAapt2DaemonBuildService(project: Project) {
    project.gradle.sharedServices.registerIfAbsent(
        AAPT2_DAEMON_BUILD_SERVICE_NAME,
        Aapt2DaemonBuildService::class.java
    ) {}
}

/**
 * Returns registered instance of [Aapt2DaemonBuildService] that can be used to access AAPT2 daemon
 * instances.
 */
@Suppress("UNCHECKED_CAST")
fun getAapt2DaemonBuildService(project: Project): Provider<out Aapt2DaemonBuildService> =
    (project.gradle.sharedServices.registrations.getByName(AAPT2_DAEMON_BUILD_SERVICE_NAME)
            as BuildServiceRegistration<Aapt2DaemonBuildService, BuildServiceParameters.None>)
        .service

/**
 * Service registry used to store AAPT2 daemon services so they are accessible from the worker
 * actions.
 */
var aapt2DaemonServiceRegistry: WorkerActionServiceRegistry = WorkerActionServiceRegistry()

/** Intended for use from worker actions. */
@Throws(ProcessException::class, IOException::class)
fun <T: Any>useAaptDaemon(
    aapt2ServiceKey: Aapt2DaemonServiceKey,
    serviceRegistry: WorkerActionServiceRegistry = aapt2DaemonServiceRegistry,
    block: (Aapt2DaemonManager.LeasedAaptDaemon) -> T) : T {
    return getAaptDaemon(aapt2ServiceKey, serviceRegistry).use(block)
}

/** Intended for use from java worker actions. */
@JvmOverloads
fun getAaptDaemon(
    aapt2ServiceKey: Aapt2DaemonServiceKey,
    serviceRegistry: WorkerActionServiceRegistry = aapt2DaemonServiceRegistry)
        : Aapt2DaemonManager.LeasedAaptDaemon =
    serviceRegistry.getService(aapt2ServiceKey).service.leaseDaemon()

data class  Aapt2DaemonServiceKey(val file: File): WorkerActionServiceRegistry.ServiceKey<Aapt2DaemonManager> {
    override val type: Class<Aapt2DaemonManager> get() = Aapt2DaemonManager::class.java
}

/** Build service used to access AAPT2 daemons. */
abstract class Aapt2DaemonBuildService : BuildService<BuildServiceParameters.None>,
    AutoCloseable {

    private val registeredServices = mutableSetOf<Aapt2DaemonServiceKey>()
    private val closer = Closer.create()

    @JvmOverloads
    @Synchronized
    fun registerAaptService(
        aapt2FromMaven: File,
        logger: ILogger,
        serviceRegistry: WorkerActionServiceRegistry = aapt2DaemonServiceRegistry
    ): Aapt2DaemonServiceKey {
        val key = Aapt2DaemonServiceKey(aapt2FromMaven)
        val aaptExecutablePath = aapt2FromMaven.toPath().resolve(SdkConstants.FN_AAPT2)

        if (!Files.exists(aaptExecutablePath)) {
            throw InvalidUserDataException(
                "Specified AAPT2 executable does not exist: $aaptExecutablePath. "
                        + "Must supply one of aapt2 from maven or custom location."
            )
        }

        if (registeredServices.add(key)) {
            val manager = Aapt2DaemonManager(
                logger = logger,
                daemonFactory = { displayId ->
                    Aapt2DaemonImpl(
                        displayId = "#$displayId",
                        aaptExecutable = aaptExecutablePath,
                        daemonTimeouts = daemonTimeouts,
                        logger = logger
                    )
                },
                expiryTime = daemonExpiryTimeSeconds,
                expiryTimeUnit = TimeUnit.SECONDS,
                listener = Aapt2DaemonManagerMaintainer()
            )
            closer.register(Closeable { manager.shutdown() })
            closer.register(serviceRegistry.registerServiceAsCloseable(key, manager))
        }
        return key
    }

    override fun close() {
        closer.close()
    }
}

/**
 * Responsible for scheduling maintenance on the Aapt2Service.
 *
 * There are three ways the daemons can all be shut down.
 * 1. An explicit call of [Aapt2DaemonManager.shutdown]. (e.g. at the end of each build invocation.)
 * 2. All the daemons being timed out by the logic in [Aapt2DaemonManager.maintain].
 *    Calls to maintain are scheduled below, and only while there are daemons running to avoid
 *    leaking a thread.
 * 3. The JVM shutdown hook, which like (2) is only kept registered while daemons are running.
 */
private class Aapt2DaemonManagerMaintainer : Aapt2DaemonManager.Listener {
    @GuardedBy("this")
    private var maintainExecutor: ScheduledExecutorService? = null
    @GuardedBy("this")
    private var maintainAction: ScheduledFuture<*>? = null
    @GuardedBy("this")
    private var shutdownHook: Thread? = null

    @Synchronized
    override fun firstDaemonStarted(manager: Aapt2DaemonManager) {
        maintainExecutor = Executors.newSingleThreadScheduledExecutor()
        maintainAction = maintainExecutor!!.
            scheduleAtFixedRate(
                manager::maintain,
                daemonExpiryTimeSeconds + maintenanceIntervalSeconds,
                maintenanceIntervalSeconds,
                TimeUnit.SECONDS)
        shutdownHook = Thread { shutdown(manager) }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    @Synchronized
    override fun lastDaemonStopped() {
        maintainAction!!.cancel(false)
        maintainExecutor!!.shutdown()
        maintainAction = null
        maintainExecutor = null
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook!!)
            shutdownHook = null
        }
    }

    private fun shutdown(manager: Aapt2DaemonManager) {
        // Unregister the hook, as shutting down the daemon manager will trigger lastDaemonStopped()
        // and removeShutdownHook throws if called during shutdown.
        synchronized(this) {
            this.shutdownHook = null
        }
        manager.shutdown()
    }
}

private val daemonTimeouts = Aapt2DaemonTimeouts()
private val daemonExpiryTimeSeconds = TimeUnit.MINUTES.toSeconds(3)
private val maintenanceIntervalSeconds = TimeUnit.MINUTES.toSeconds(1)