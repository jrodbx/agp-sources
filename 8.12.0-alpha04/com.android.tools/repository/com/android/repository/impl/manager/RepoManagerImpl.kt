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
import com.android.repository.api.PackageOperation
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.ProgressRunner
import com.android.repository.api.ProgressRunner.ProgressRunnable
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoPackage
import com.android.repository.api.RepositorySource
import com.android.repository.api.RepositorySourceProvider
import com.android.repository.api.SchemaModule
import com.android.repository.api.SettingsController
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.impl.meta.SchemaModuleUtil
import com.google.common.annotations.VisibleForTesting
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import org.w3c.dom.ls.LSResourceResolver

/**
 * Main implementation of [RepoManager]. Loads local and remote [RepoPackage]s synchronously and
 * asynchronously into a [RepositoryPackages] instance from the given local path and from the
 * registered [RepositorySourceProvider]s, using the registered [SchemaModule]s.
 */
class RepoManagerImpl
@VisibleForTesting
internal constructor(
  /** The path under which to look for installed packages. */
  override val localPath: Path?,
  localFactory: LocalRepoLoaderFactory?,
  remoteFactory: RemoteRepoLoaderFactory?,
) : RepoManager() {
  /** The registered [SchemaModule]s. */
  override val schemaModules = mutableListOf<SchemaModule<*>>()

  /** The [FallbackLocalRepoLoader] to use when loading local packages. */
  private var fallbackLocalRepoLoader: FallbackLocalRepoLoader? = null

  /**
   * The [FallbackRemoteRepoLoader] to use if the normal [RemoteRepoLoaderImpl] can't understand a
   * downloaded repository xml file.
   */
  private var fallbackRemoteRepoLoader: FallbackRemoteRepoLoader? = null

  /** The [RepositorySourceProvider]s from which to get [RepositorySource]s to load from. */
  override val sourceProviders = mutableListOf<RepositorySourceProvider>()

  /** The loaded packages. */
  override val packages = RepositoryPackages()

  /** When we last loaded the remote packages. */
  private var lastRemoteRefreshMs: Long = 0

  /** When we last loaded the local packages. */
  private var lastLocalRefreshMs: Long = 0

  /** The task used to load packages. If non-null, a load is currently in progress. */
  @GuardedBy("taskLock") private var task: LoadTask? = null

  /** The time at which our current [LoadTask] was created. */
  @GuardedBy("taskLock") private var taskCreateTime: Instant = Instant.EPOCH

  /** Lock used when setting [.task]. */
  private val taskLock = Any()

  /** Listeners that will be called when the known local packages change. */
  private val localListeners = CopyOnWriteArrayList<RepoLoadedListener>()

  /** Listeners that will be called when the known remote packages change. */
  private val remoteListeners = CopyOnWriteArrayList<RepoLoadedListener>()

  /** Install/uninstall operations that are currently running. */
  private val inProgressInstalls = mutableMapOf<RepoPackage, PackageOperation>()

  /** A facility for creating [LocalRepoLoader]s. By default, [LocalRepoLoaderFactoryImpl]. */
  private val localRepoLoaderFactory: LocalRepoLoaderFactory

  /** A facility for creating [RemoteRepoLoader]s. By default, [RemoteRepoLoaderFactoryImpl]. */
  private val remoteRepoLoaderFactory: RemoteRepoLoaderFactory

  /**
   * Create a new `RepoManagerImpl`. Before anything can be loaded, at least a local path and/or at
   * least one [RepositorySourceProvider] must be set.
   */
  constructor(localPath: Path?) : this(localPath, localFactory = null, remoteFactory = null)

  /**
   * @param localPath The base directory of the SDK.
   * @param localFactory If `null`, [LocalRepoLoaderFactoryImpl] will be used. Can be non-null for
   *   testing.
   * @param remoteFactory If `null`, [RemoteRepoLoaderFactoryImpl] will be used. Can be non-null for
   *   testing.
   */
  init {
    registerSchemaModule(commonModule)
    registerSchemaModule(genericModule)
    localRepoLoaderFactory = localFactory ?: LocalRepoLoaderFactoryImpl()
    remoteRepoLoaderFactory = remoteFactory ?: RemoteRepoLoaderFactoryImpl()
  }

  /**
   * {@inheritDoc} This calls [.markInvalid], so a complete load will occur the next time [.load] is
   * called.
   */
  override fun setFallbackLocalRepoLoader(fallback: FallbackLocalRepoLoader?) {
    fallbackLocalRepoLoader = fallback
    markInvalid()
  }

  /**
   * {@inheritDoc} This calls [.markInvalid], so a complete load will occur the next time [.load] is
   * called.
   */
  override fun setFallbackRemoteRepoLoader(remote: FallbackRemoteRepoLoader?) {
    fallbackRemoteRepoLoader = remote
    markInvalid()
  }

  /**
   * {@inheritDoc} This calls [.markInvalid], so a complete load will occur the next time [.load] is
   * called.
   */
  override fun registerSourceProvider(provider: RepositorySourceProvider) {
    sourceProviders.add(provider)
    markInvalid()
  }

  override fun getSources(
    downloader: Downloader?,
    progress: ProgressIndicator,
    forceRefresh: Boolean,
  ): List<RepositorySource> =
    sourceProviders.flatMap { it.getSources(downloader, progress, forceRefresh) }

  /**
   * {@inheritDoc} This calls [.markInvalid], so a complete load will occur the next time [.load] is
   * called.
   */
  override fun registerSchemaModule(module: SchemaModule<*>) {
    schemaModules.add(module)
    markInvalid()
  }

  override fun markInvalid() {
    lastRemoteRefreshMs = 0
    lastLocalRefreshMs = 0
  }

  override fun markLocalCacheInvalid() {
    lastLocalRefreshMs = 0
  }

  override fun getResourceResolver(progress: ProgressIndicator): LSResourceResolver? {
    val allModules = (schemaModules + commonModule + genericModule).toSet()
    return SchemaModuleUtil.createResourceResolver(allModules, progress)
  }

  override fun loadSynchronously(
    cacheExpirationMs: Long,
    onLocalComplete: List<RepoLoadedListener>?,
    onSuccess: List<RepoLoadedListener>?,
    onError: List<Runnable>?,
    runner: ProgressRunner,
    downloader: Downloader?,
    settings: SettingsController?,
  ) {
    load(cacheExpirationMs, onLocalComplete, onSuccess, onError, runner, downloader, settings, true)
  }

  override fun load(
    cacheExpirationMs: Long,
    onLocalComplete: List<RepoLoadedListener>?,
    onSuccess: List<RepoLoadedListener>?,
    onError: List<Runnable>?,
    runner: ProgressRunner,
    downloader: Downloader?,
    settings: SettingsController?,
  ) {
    load(
      cacheExpirationMs,
      onLocalComplete,
      onSuccess,
      onError,
      runner,
      downloader,
      settings,
      false,
    )
  }

  /**
   * Loads the local and remote repositories.
   *
   * @param cacheExpirationMs How long must have passed since the last load for us to reload.
   *   Specify `0` to reload immediately.
   * @param onLocalComplete When loading, the local repo load happens first, and should be
   *   relatively fast. When complete, the `onLocalComplete` [RepoLoadedListener]s are run. Will be
   *   called with a [RepositoryPackages] that contains only the local packages.
   * @param onSuccess Callbacks that are run when the entire load (local and remote) has completed
   *   successfully. Called with an [RepositoryPackages] containing both the local and remote
   *   packages.
   * @param onError Callbacks that are run when there's an error at some point during the load.
   * @param runner The [ProgressRunner] to use for any tasks started during the load, including
   *   running the callbacks.
   * @param downloader The [Downloader] to use for downloading remote files, including any remote
   *   list of repo sources and the remote repositories themselves.
   * @param settings The settings to use during the load, including for example proxy settings used
   *   when fetching remote files.
   * @param sync If true, load synchronously. If false, load asynchronously (this method should
   *   return quickly, and the `onSuccess` callbacks can be used to process the completed results).
   */
  // TODO: fix up invalidation. It's annoying that you have to manually reload.
  // TODO: Maybe: when you load, instead of load as now, you get back a loader, which knows how
  //       to reload with same settings, and contains current valid or invalid packages as they
  //       are cached here.
  private fun load(
    cacheExpirationMs: Long,
    onLocalComplete: List<RepoLoadedListener>?,
    onSuccess: List<RepoLoadedListener>?,
    onError: List<Runnable>?,
    runner: ProgressRunner,
    downloader: Downloader?,
    settings: SettingsController?,
    sync: Boolean,
  ) {
    val onLocalComplete = onLocalComplete ?: emptyList()
    val onSuccess = onSuccess ?: emptyList()
    val onError = onError ?: emptyList()

    // So we can block until complete in the synchronous case.
    val isComplete = CompletableDeferred<Unit>()

    // If we created the currently running task, we need to clean it up at the end.
    val createNewTask: Boolean
    val task: LoadTask
    synchronized(taskLock) {
      val currentTask =
        this.task?.takeIf { Clock.systemUTC().instant() < taskCreateTime + TASK_TIMEOUT }
      createNewTask = currentTask == null
      if (createNewTask) {
        task = LoadTask(cacheExpirationMs, downloader, settings)
        task.addCallbacks(onLocalComplete, onSuccess, onError)
        this.task = task
        taskCreateTime = Clock.systemUTC().instant()
      } else {
        task = currentTask
        // If there's a task running already, just add our callbacks to it.
        task.addCallbacks(onLocalComplete, onSuccess, onError)
        if (sync) {
          // If we're running synchronously, signal completion after the run completes.
          task.addCallbacks(
            onLocalComplete = emptyList(),
            onSuccess = listOf(RepoLoadedListener { isComplete.complete(Unit) }),
            onError = listOf(Runnable { isComplete.complete(Unit) }),
          )
        }
      }
    }

    if (createNewTask) {
      // If we created a task, run it.
      if (sync) {
        runner.runSyncWithProgress(task)
      } else {
        runner.runAsyncWithProgress(task)
      }
    } else if (sync) {
      // Wait for the task to complete if we're running synchronously.
      runner.runSyncWithProgress { _, _ -> isComplete.await() }
    }
  }

  @Slow
  override fun reloadLocalIfNeeded(progress: ProgressIndicator): Boolean {
    // TODO: there should be a nice interface whereby we can do this check without creating a
    // new LocalRepoLoader instance.
    val local = localRepoLoaderFactory.createLocalRepoLoader()
    if (local == null) {
      return false
    }

    if (local.needsUpdate(lastLocalRefreshMs, true)) {
      lastLocalRefreshMs = 0
    }
    return loadSynchronously(DEFAULT_EXPIRATION_PERIOD_MS, progress, null, null)
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

  /** A task to load the local and remote repos. */
  private inner class LoadTask(
    private val cacheExpirationMs: Long,
    private val downloader: Downloader?,
    private val settings: SettingsController?,
  ) : ProgressRunnable {
    @GuardedBy("taskLock") private val onSuccesses = mutableListOf<RepoLoadedListener>()
    @GuardedBy("taskLock") private val onErrors = mutableListOf<Runnable>()
    // Must be synchronized since new elements can be added while the task is still in progress
    // (that is, before task is set to null).
    private val onLocalCompletes: Queue<RepoLoadedListener> =
      ConcurrentLinkedQueue<RepoLoadedListener>()

    /**
     * Add callbacks to this task (if e.g. [.load] is called again while a task is already running).
     */
    fun addCallbacks(
      onLocalComplete: List<RepoLoadedListener>,
      onSuccess: List<RepoLoadedListener>,
      onError: List<Runnable>,
    ) {
      for (local in onLocalComplete) {
        onLocalCompletes.add(local)
      }
      for (success in onSuccess) {
        onSuccesses.add(success)
      }
      onErrors.addAll(onError)
    }

    /**
     * Do the actual load.
     *
     * @param indicator [ProgressIndicator] for logging and showing actual progress
     * @param runner [ProgressRunner] for running asynchronous tasks and callbacks.
     */
    override suspend fun run(indicator: ProgressIndicator, runner: ProgressRunner) {
      var success = false
      var localSuccess = false
      val wasIndeterminate = indicator.isIndeterminate()
      indicator.setIndeterminate(false)
      try {
        val local = localRepoLoaderFactory.createLocalRepoLoader()
        if (
          local != null &&
            (lastLocalRefreshMs + cacheExpirationMs <= System.currentTimeMillis() ||
              local.needsUpdate(lastLocalRefreshMs, false))
        ) {
          fallbackLocalRepoLoader?.refresh()
          indicator.setText("Loading local repository...")
          val newLocals = local.getPackages(indicator)
          val fireListeners = newLocals != packages.localPackages
          packages.setLocalPkgInfos(newLocals.values)
          lastLocalRefreshMs = System.currentTimeMillis()
          if (fireListeners) {
            for (listener in localListeners) {
              listener.loaded(packages)
            }
          }
        }
        indicator.setFraction(0.25)
        if (indicator.isCanceled()) {
          return
        }
        // Set to true even if we didn't reload locals: the no-op is complete.
        localSuccess = true

        // Access using the synchronized queue interface so we don't have to worry about
        // more elements getting added while we're in the middle of processing.
        while (true) {
          when (val onLocalComplete = onLocalCompletes.poll()) {
            null -> break
            else -> onLocalComplete.loaded(packages)
          }
        }
        indicator.setText("Fetch remote repository...")
        indicator.setSecondaryText("")

        if (
          !sourceProviders.isEmpty() &&
            downloader != null &&
            lastRemoteRefreshMs + cacheExpirationMs <= System.currentTimeMillis()
        ) {
          val remoteLoader = remoteRepoLoaderFactory.createRemoteRepoLoader(indicator)
          val remotes =
            remoteLoader.fetchPackages(indicator.createSubProgress(.75), downloader, settings)
          indicator.setText("Computing updates...")
          indicator.setFraction(0.75)
          val fireListeners = remotes != packages.remotePackages
          packages.setRemotePkgInfos(remotes.values)
          lastRemoteRefreshMs = System.currentTimeMillis()
          if (fireListeners) {
            for (callback in remoteListeners) {
              callback.loaded(packages)
            }
          }
        }

        if (indicator.isCanceled()) {
          return
        }
        indicator.setSecondaryText("")
        indicator.setFraction(1.0)

        if (indicator.isCanceled()) {
          return
        }
        success = true
      } finally {
        indicator.setIndeterminate(wasIndeterminate)
        val onSuccesses: List<RepoLoadedListener>
        val onErrors: List<Runnable>
        synchronized(taskLock) {
          // The processing of the task is now complete.
          // To ensure that no more callbacks are added, and to allow another task to be
          // kicked off when needed, set task to null.
          task = null
          onSuccesses = this.onSuccesses
          onErrors = this.onErrors
        }

        // Note: in theory it's possible that another task could now be started and modify
        // packages before the callbacks are run below, since we're out of the synchronized
        // block. Since RepositoryPackages itself is synchronized, though, that should be
        // ok.

        // in case some were added by another call in the interim.
        if (localSuccess) {
          for (onLocalComplete in onLocalCompletes) {
            onLocalComplete.loaded(packages)
          }
        }
        if (success) {
          for (onSuccess in onSuccesses) {
            onSuccess.loaded(packages)
          }
        } else {
          for (onError in onErrors) {
            onError.run()
          }
        }
      }
    }
  }

  internal interface LocalRepoLoaderFactory {
    fun createLocalRepoLoader(): LocalRepoLoader?
  }

  @VisibleForTesting
  interface RemoteRepoLoaderFactory {
    fun createRemoteRepoLoader(progress: ProgressIndicator): RemoteRepoLoader
  }

  private inner class LocalRepoLoaderFactoryImpl : LocalRepoLoaderFactory {
    /**
     * @return A new [LocalRepoLoaderImpl] with our settings, or `null` if we don't have a local
     *   path set.
     */
    override fun createLocalRepoLoader(): LocalRepoLoader? =
      localPath?.let { LocalRepoLoaderImpl(it, this@RepoManagerImpl, fallbackLocalRepoLoader) }
  }

  private inner class RemoteRepoLoaderFactoryImpl : RemoteRepoLoaderFactory {
    override fun createRemoteRepoLoader(progress: ProgressIndicator): RemoteRepoLoader =
      RemoteRepoLoaderImpl(sourceProviders, fallbackRemoteRepoLoader)
  }

  companion object {
    /** How long we should let a load task run before assuming that it's dead. */
    private val TASK_TIMEOUT = Duration.ofMinutes(3)
  }
}
