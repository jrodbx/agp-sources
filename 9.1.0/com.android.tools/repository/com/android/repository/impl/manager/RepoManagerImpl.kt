/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.repository.impl.manager

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.repository.api.Downloader
import com.android.repository.api.FallbackLocalRepoLoader
import com.android.repository.api.FallbackRemoteRepoLoader
import com.android.repository.api.LocalPackage
import com.android.repository.api.PackageOperation
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.ProgressRunner
import com.android.repository.api.ProgressRunner.ProgressRunnable
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoPackage
import com.android.repository.api.RepositorySource
import com.android.repository.api.RepositorySourceProvider
import com.android.repository.api.SchemaModule
import com.android.repository.api.SettingsController
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.impl.meta.SchemaModuleUtil
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.TestOnly
import org.w3c.dom.ls.LSResourceResolver

/**
 * Main implementation of [RepoManager]. Loads local and remote [RepoPackage]s synchronously and asynchronously into a [RepositoryPackages]
 * instance from the given local path and from the registered [RepositorySourceProvider]s, using the registered [SchemaModule]s.
 *
 * @property localPath the path under which to look for installed packages.
 * @property localRepoLoader the implementation of local package loading
 * @property remoteRepoLoader the implementation of remote package loading
 */
class RepoManagerImpl
internal constructor(
  override val localPath: Path?,
  val localRepoLoader: LocalRepoLoader?,
  val remoteRepoLoader: RemoteRepoLoader,
  additionalSchemaModules: List<SchemaModule<*>> = emptyList(),
) : RepoManager() {

  /**
   * Constructor for production use, using the standard local and remote loaders.
   *
   * @param localPath the path under which to look for installed packages.
   * @param sourceProviders the [RepositorySourceProvider]s which [RemoteRepoLoaderImpl] will use to load remote repositories.
   * @param additionalSchemaModules schema modules to use to parse XML files, in addition to the always-included [commonModule] and
   *   [genericModule]
   * @param fallbackLocalRepoLoader the [FallbackLocalRepoLoader] to use if the normal [LocalRepoLoaderImpl] does not find the expected
   *   package.xml. This will detect packages in the legacy XML format, and also manually installed packages, and create a package.xml file
   *   for them.
   * @param fallbackRemoteRepoLoader the [FallbackRemoteRepoLoader] to use if the normal [RemoteRepoLoaderImpl] can't understand a
   *   downloaded repository xml file. (This is currently used for parsing the old repository XML format.)
   */
  constructor(
    localPath: Path?,
    sourceProviders: List<RepositorySourceProvider>,
    additionalSchemaModules: List<SchemaModule<*>> = emptyList(),
    fallbackLocalRepoLoader: FallbackLocalRepoLoader? = null,
    fallbackRemoteRepoLoader: FallbackRemoteRepoLoader? = null,
  ) : this(
    localPath = localPath,
    localRepoLoader =
      localPath?.let { LocalRepoLoaderImpl(it, setOf(commonModule, genericModule) + additionalSchemaModules, fallbackLocalRepoLoader) },
    remoteRepoLoader = RemoteRepoLoaderImpl(sourceProviders, fallbackRemoteRepoLoader),
    additionalSchemaModules = additionalSchemaModules,
  )

  @TestOnly constructor(localPath: Path?) : this(localPath, emptyList())

  /** The registered [SchemaModule]s. */
  override val schemaModules: Set<SchemaModule<*>> = setOf(commonModule, genericModule) + additionalSchemaModules

  /** The [RepositorySourceProvider]s from which to get [RepositorySource]s to load from. */
  override val sourceProviders
    get() = remoteRepoLoader.sourceProviders

  /** The loaded packages. */
  override val packages = RepositoryPackages()

  /** When we last loaded the remote packages. */
  private var lastRemoteRefreshMs: Long = 0

  /** When we last loaded the local packages. */
  private var lastLocalRefreshMs: Long = 0

  /** The task used to load packages. If non-null, a load is currently in progress. */
  @GuardedBy("taskLock") private var task: LocalLoadTask? = null

  private data class RemoteLoadTaskKey(val downloader: Downloader, val settings: SettingsController?)

  /** The task used to load remote packages. If non-null, a load is currently in progress. */
  @GuardedBy("taskLock") private val remoteTasks: MutableMap<RemoteLoadTaskKey, RemoteLoadTask> = mutableMapOf()

  /** Lock guarding [.task] and [.remoteTasks]. */
  private val taskLock = Any()

  /** Listeners that will be called when the known local packages change. */
  private val localListeners = CopyOnWriteArrayList<RepoLoadedListener>()

  /** Listeners that will be called when the known remote packages change. */
  private val remoteListeners = CopyOnWriteArrayList<RepoLoadedListener>()

  /** Install/uninstall operations that are currently running. */
  private val inProgressInstalls = mutableMapOf<RepoPackage, PackageOperation>()

  override fun getSources(downloader: Downloader?, progress: ProgressIndicator, forceRefresh: Boolean): List<RepositorySource> =
    sourceProviders.flatMap { it.getSources(downloader, progress, forceRefresh) }

  override fun markInvalid() {
    lastRemoteRefreshMs = 0
    lastLocalRefreshMs = 0
  }

  override fun markLocalCacheInvalid() {
    lastLocalRefreshMs = 0
  }

  override fun getResourceResolver(progress: ProgressIndicator): LSResourceResolver? {
    return SchemaModuleUtil.createResourceResolver(schemaModules, progress)
  }

  override fun loadSynchronously(
    cacheExpirationMs: Long,
    onLocalComplete: RepoLoadedListener?,
    onSuccess: RepoLoadedListener?,
    onError: Runnable?,
    runner: ProgressRunner,
    downloader: Downloader?,
    settings: SettingsController?,
  ) {
    load(cacheExpirationMs, onLocalComplete, onSuccess, onError, runner, downloader, settings, true)
  }

  override fun load(
    cacheExpirationMs: Long,
    onLocalComplete: RepoLoadedListener?,
    onSuccess: RepoLoadedListener?,
    onError: Runnable?,
    runner: ProgressRunner,
    downloader: Downloader?,
    settings: SettingsController?,
  ) {
    load(cacheExpirationMs, onLocalComplete, onSuccess, onError, runner, downloader, settings, false)
  }

  /**
   * Loads the local and remote repositories.
   *
   * @param cacheExpirationMs How long must have passed since the last load for us to reload. Specify `0` to reload immediately.
   * @param onLocalComplete When loading, the local repo load happens first, and should be relatively fast. When complete, the
   *   `onLocalComplete` [RepoLoadedListener] is run. Will be called with a [RepositoryPackages] that contains only the local packages.
   * @param onSuccess Callback that is run when the entire load (local and remote) has completed successfully. Called with an
   *   [RepositoryPackages] containing both the local and remote packages.
   * @param onError Callback that is run when there's an error at some point during the load.
   * @param runner The [ProgressRunner] to use for any tasks started during the load, including running the callbacks.
   * @param downloader The [Downloader] to use for downloading remote files, including any remote list of repo sources and the remote
   *   repositories themselves.
   * @param settings The settings to use during the load, including for example proxy settings used when fetching remote files.
   * @param sync If true, load synchronously. If false, load asynchronously (this method should return quickly, and the `onSuccess`
   *   callbacks can be used to process the completed results).
   */
  // TODO: fix up invalidation. It's annoying that you have to manually reload.
  // TODO: Maybe: when you load, instead of load as now, you get back a loader, which knows how
  //       to reload with same settings, and contains current valid or invalid packages as they
  //       are cached here.
  private fun load(
    cacheExpirationMs: Long,
    onLocalComplete: RepoLoadedListener?,
    onSuccess: RepoLoadedListener?,
    onError: Runnable?,
    runner: ProgressRunner,
    downloader: Downloader?,
    settings: SettingsController?,
    sync: Boolean,
  ) {
    // Build a task that will load the local and, if applicable, the remote repos, or wait for
    // existing tasks that are doing that.
    val runnable: ProgressRunnable
    val localRunnable = getOrCreateLocalLoadTask(cacheExpirationMs).wrapRun(onLocalComplete, onError)

    if (downloader == null) {
      runnable = localRunnable
    } else {
      val remoteRunnable = getOrCreateRemoteLoadTask(cacheExpirationMs, downloader, settings).wrapRun(onSuccess, onError)

      runnable = ProgressRunnable { indicator ->
        localRunnable.run(indicator)
        remoteRunnable.run(indicator)
      }
    }

    if (sync) {
      runner.runSyncWithProgress(runnable)
    } else {
      runner.runAsyncWithProgress(runnable)
    }
  }

  private fun getOrCreateLocalLoadTask(cacheExpirationMs: Long): LoadTask<LocalPackage> =
    synchronized(taskLock) {
      val existingLocalTask = this.task?.takeIfNotTimedOut()
      if (existingLocalTask == null) {
        return LocalLoadTask(cacheExpirationMs).also { this.task = it }
      } else {
        return existingLocalTask.newPiggybackTask { getOrCreateLocalLoadTask(cacheExpirationMs) }
      }
    }

  private fun getOrCreateRemoteLoadTask(
    cacheExpirationMs: Long,
    downloader: Downloader,
    settings: SettingsController?,
  ): LoadTask<RemotePackage> =
    synchronized(taskLock) {
      val remoteTaskKey = RemoteLoadTaskKey(downloader, settings)
      val existingRemoteTask = remoteTasks[remoteTaskKey]?.takeIfNotTimedOut()
      if (existingRemoteTask == null) {
        return RemoteLoadTask(cacheExpirationMs, downloader, settings).also { remoteTasks[remoteTaskKey] = it }
      } else {
        return existingRemoteTask.newPiggybackTask { getOrCreateRemoteLoadTask(cacheExpirationMs, downloader, settings) }
      }
    }

  override suspend fun loadLocalPackages(indicator: ProgressIndicator, cacheExpiration: Duration): List<LocalPackage> =
    getOrCreateLocalLoadTask(cacheExpiration.inWholeMilliseconds).load(indicator)

  override suspend fun loadRemotePackages(
    indicator: ProgressIndicator,
    downloader: Downloader,
    settings: SettingsController?,
    cacheExpiration: Duration,
  ): List<RemotePackage> = getOrCreateRemoteLoadTask(cacheExpiration.inWholeMilliseconds, downloader, settings).load(indicator)

  @Slow
  override fun reloadLocalIfNeeded(progress: ProgressIndicator) {
    localRepoLoader ?: return

    if (localRepoLoader.needsUpdate(lastLocalRefreshMs, true)) {
      lastLocalRefreshMs = 0
    }
    loadSynchronously(DEFAULT_EXPIRATION_PERIOD_MS, progress, null, null)
  }

  override fun addLocalChangeListener(listener: RepoLoadedListener) {
    localListeners.add(listener)
  }

  override fun removeLocalChangeListener(listener: RepoLoadedListener) {
    localListeners.remove(listener)
  }

  override fun addRemoteChangeListener(listener: RepoLoadedListener) {
    remoteListeners.add(listener)
  }

  override fun removeRemoteChangeListener(listener: RepoLoadedListener) {
    remoteListeners.remove(listener)
  }

  override fun installBeginning(remotePackage: RepoPackage, installer: PackageOperation) {
    inProgressInstalls.put(remotePackage, installer)
  }

  override fun installEnded(remotePackage: RepoPackage) {
    inProgressInstalls.remove(remotePackage)
  }

  override fun getInProgressInstallOperation(remotePackage: RepoPackage): PackageOperation? {
    return inProgressInstalls[remotePackage]
  }

  private fun interface LoadTask<T : RepoPackage> {
    suspend fun load(indicator: ProgressIndicator): List<T>
  }

  /** A task to load the local and remote repos. */
  private abstract inner class AbstractLoadTask<T : RepoPackage> : LoadTask<T> {

    /** The time at which this [AbstractLoadTask] was created. */
    val taskCreateTime: Instant = Clock.systemUTC().instant()

    private val result = CompletableDeferred<List<T>>()

    override suspend fun load(indicator: ProgressIndicator): List<T> {
      try {
        val wasIndeterminate = indicator.isIndeterminate()
        indicator.setIndeterminate(false)
        val result = runCatching { doLoad(indicator) }
        this.result.completeWith(result)
        indicator.setIndeterminate(wasIndeterminate)
        return result.getOrThrow()
      } finally {
        cleanUp()
      }
    }

    abstract suspend fun doLoad(indicator: ProgressIndicator): List<T>

    abstract fun cleanUp()

    /**
     * Returns a LoadTask that simply waits on the result of this LoadTask and returns it.
     *
     * The problem with sharing the results of an existing task is that the caller of the existing task may cancel it, even though our
     * caller still wants the result. Our caller may not even allow cancellation and may not be expecting it. So, if that happens, use
     * [fallback] to create a fresh LoadTask and use it.
     */
    fun newPiggybackTask(fallback: () -> LoadTask<T>): LoadTask<T> = LoadTask { indicator ->
      try {
        result.await()
      } catch (e: CancellationException) {
        // Two possibilities here: our own caller was cancelled, or the job we're waiting on was
        // cancelled. We only want to fallback in the latter case.
        if (indicator.isCanceled || !currentCoroutineContext().isActive) {
          throw e
        }
        fallback().load(indicator)
      }
    }
  }

  private fun <T : AbstractLoadTask<*>> T.takeIfNotTimedOut(): T? = takeIf {
    Clock.systemUTC().instant() < it.taskCreateTime + TASK_TIMEOUT
  }

  /** Produces a ProgressRunnable that invokes this LoadTask and then performs the given callbacks when finished. */
  private fun LoadTask<*>.wrapRun(onSuccess: RepoLoadedListener?, onError: Runnable?): ProgressRunnable = ProgressRunnable { indicator ->
    try {
      load(indicator)
      onSuccess?.loaded(packages)
    } catch (t: Throwable) {
      onError?.run()
      throw t
    }
  }

  private inner class LocalLoadTask(val cacheExpirationMs: Long) : AbstractLoadTask<LocalPackage>() {

    override suspend fun doLoad(indicator: ProgressIndicator): List<LocalPackage> {
      val result: List<LocalPackage>
      if (
        localRepoLoader != null &&
          (lastLocalRefreshMs + cacheExpirationMs <= System.currentTimeMillis() || localRepoLoader.needsUpdate(lastLocalRefreshMs, false))
      ) {
        indicator.setText("Loading local repository...")
        val newLocals = localRepoLoader.getPackages(indicator)
        val fireListeners = newLocals != packages.localPackages
        result = newLocals.values.toList()
        packages.setLocalPkgInfos(newLocals.values)
        lastLocalRefreshMs = System.currentTimeMillis()
        if (fireListeners) {
          for (listener in localListeners) {
            listener.loaded(packages)
          }
        }
      } else {
        result = packages.localPackages.values.toList()
      }
      indicator.setFraction(0.25)
      return result
    }

    override fun cleanUp() {
      synchronized(taskLock) { task = null }
    }
  }

  private inner class RemoteLoadTask(
    private val cacheExpirationMs: Long,
    private val downloader: Downloader,
    private val settings: SettingsController?,
  ) : AbstractLoadTask<RemotePackage>() {

    override suspend fun doLoad(indicator: ProgressIndicator): List<RemotePackage> {
      indicator.setText("Fetch remote repository...")
      indicator.setSecondaryText("")

      val result = loadRemote(indicator)

      indicator.setSecondaryText("")
      indicator.setFraction(1.0)

      return result
    }

    private fun loadRemote(indicator: ProgressIndicator): List<RemotePackage> {
      if (lastRemoteRefreshMs + cacheExpirationMs <= System.currentTimeMillis()) {
        val remotes = remoteRepoLoader.fetchPackages(indicator.createSubProgress(.75), downloader, settings)
        indicator.setText("Computing updates...")
        indicator.setFraction(0.75)
        val fireListeners = remotes != packages.remotePackages
        packages.setRemotePkgInfos(remotes.values)
        lastRemoteRefreshMs = System.currentTimeMillis()
        if (fireListeners) {
          for (callback in remoteListeners) {
            try {
              callback.loaded(packages)
            } catch (e: Exception) {
              indicator.logWarning("Processing remoteListener callback", e)
            }
          }
        }
        return remotes.values.toList()
      }
      return packages.remotePackages.values.toList()
    }

    override fun cleanUp() {
      synchronized(taskLock) { remoteTasks.remove(RemoteLoadTaskKey(downloader, settings)) }
    }
  }

  companion object {
    /** How long we should let a load task run before assuming that it's dead. */
    private val TASK_TIMEOUT = java.time.Duration.ofMinutes(3)
  }
}
