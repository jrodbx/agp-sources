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
package com.android.repository.api

import com.android.annotations.concurrency.Slow
import com.android.repository.api.ProgressRunner.ProgressRunnable
import com.android.repository.impl.manager.RepoManagerImpl
import com.android.repository.impl.meta.CommonFactory
import com.android.repository.impl.meta.GenericFactory
import com.android.repository.impl.meta.RepositoryPackages
import com.google.common.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.w3c.dom.ls.LSResourceResolver
import kotlin.time.Duration

/**
 * Primary interface for interacting with repository packages.
 *
 * To set up a `RepoManager`:
 * * Pass the path where the repo is installed locally to the constructor.
 * * Register the [SchemaModule]s used to parse the package.xml files and remote repositories used
 *   by this repo using [registerSchemaModule]
 * * If your local repo might contain packages created by a previous system, set a
 *   [FallbackLocalRepoLoader] that can recognize and convert those packages using
 *   [setFallbackLocalRepoLoader].
 * * Add [RepositorySourceProvider]s to provide URLs for remotely-available packages.
 * * If some sources might be in a format used by a previous system, set a
 *   [FallbackRemoteRepoLoader] that can read and convert them.
 *
 * To load the local and remote packages, use [load].
 *
 * TODO: it would be nice if this could be redesigned such that load didn't need to be called
 *   explicitly, or there was a better way to know if packages were or need to be loaded.
 *
 * To use the loaded packages, get a [RepositoryPackages] object from [packages].
 */
abstract class RepoManager {
  /** Register an [SchemaModule] that can be used when parsing XML for this repo. */
  abstract fun registerSchemaModule(module: SchemaModule<*>)

  /**
   * Gets the currently-registered [SchemaModule]s. This probably shouldn't be used except by code
   * within the RepoManager or unit tests.
   */
  abstract val schemaModules: List<SchemaModule<*>>

  /**
   * Gets the path to the local repository root. This probably shouldn't be needed except by the
   * repository manager and unit tests.
   */
  abstract val localPath: Path?

  /** Sets the [FallbackLocalRepoLoader] to use when scanning the local repository for packages. */
  abstract fun setFallbackLocalRepoLoader(local: FallbackLocalRepoLoader?)

  /**
   * Adds a [RepositorySourceProvider] from which to get [RepositorySource]s from which to download
   * lists of available repository packages.
   */
  abstract fun registerSourceProvider(provider: RepositorySourceProvider)

  @get:VisibleForTesting abstract val sourceProviders: List<RepositorySourceProvider>

  /**
   * Gets the actual [RepositorySource]s from the registered [RepositorySourceProvider]s.
   *
   * Probably should only be needed by a repository UI.
   *
   * @param downloader The [Downloader] to use for downloading source lists, if needed.
   * @param progress A [ProgressIndicator] for source providers to use to show their progress and
   *   for logging.
   * @param forceRefresh Individual [RepositorySourceProvider]s may cache their results. If
   *   `forceRefresh` is true, specifies that they should reload rather than returning cached
   *   results.
   * @return The [RepositorySource]s obtained from the providers.
   */
  abstract fun getSources(
    downloader: Downloader?,
    progress: ProgressIndicator,
    forceRefresh: Boolean,
  ): List<RepositorySource>

  /**
   * Sets the [FallbackRemoteRepoLoader] to try when we encounter a remote xml file that the
   * RepoManager can't read.
   */
  abstract fun setFallbackRemoteRepoLoader(remote: FallbackRemoteRepoLoader?)

  /**
   * Loads the local and remote repositories asynchronously.
   *
   * @param cacheExpirationMs How long must have passed since the last load for us to reload.
   *   Specify `0` to reload immediately.
   * @param onLocalComplete When loading, the local repo load happens first, and should be
   *   relatively fast. When complete, the `onLocalComplete` [RepoLoadedListener] is run. Will be
   *   called with a [RepositoryPackages] that contains only the local packages.
   * @param onSuccess Callback that is run when the entire load (local and remote) has completed
   *   successfully. Called with an [RepositoryPackages] containing both the local and remote
   *   packages.
   * @param onError Callback that is run when there's an error at some point during the load.
   * @param runner The [ProgressRunner] to use for any tasks started during the load, including
   *   running the callbacks.
   * @param downloader The [Downloader] to use for downloading remote files, including any remote
   *   list of repo sources and the remote repositories themselves.
   * @param settings The settings to use during the load, including for example proxy settings used
   *   when fetching remote files.
   *
   * TODO: throw exception if cancelled
   */
  abstract fun load(
    cacheExpirationMs: Long,
    onLocalComplete: RepoLoadedListener? = null,
    onSuccess: RepoLoadedListener? = null,
    onError: Runnable? = null,
    runner: ProgressRunner,
    downloader: Downloader? = null,
    settings: SettingsController? = null,
  )

  /**
   * Loads the local and remote repositories synchronously.
   *
   * In callbacks, be careful of invoking tasks synchronously on other threads (e.g. the Swing UI
   * thread), since they might also be used by the [ProgressRunner] passed in.
   *
   * @param cacheExpirationMs How long must have passed since the last load for us to reload.
   *   Specify `0` to reload immediately.
   * @param onLocalComplete When loading, the local repo load happens first, and should be
   *   relatively fast. When complete, the `onLocalComplete` [RepoLoadedListener] is run. Will be
   *   called with a [RepositoryPackages] that contains only the local packages.
   * @param onSuccess Callback that is run when the entire load (local and remote) has completed
   *   successfully. Called with an [RepositoryPackages] containing both the local and remote
   *   packages.
   * @param onError Callback that is run when there's an error at some point during the load.
   * @param runner The [ProgressRunner] to use for any tasks started during the load, including
   *   running the callbacks.
   * @param downloader The [Downloader] to use for downloading remote files, including any remote
   *   list of repo sources and the remote repositories themselves.
   * @param settings The settings to use during the load, including for example proxy settings used
   *   when fetching remote files.
   */
  @Slow
  abstract fun loadSynchronously(
    cacheExpirationMs: Long,
    onLocalComplete: RepoLoadedListener? = null,
    onSuccess: RepoLoadedListener? = null,
    onError: Runnable? = null,
    runner: ProgressRunner,
    downloader: Downloader? = null,
    settings: SettingsController? = null,
  )

  /**
   * Loads the local and remote repositories synchronously.
   *
   * @param cacheExpirationMs How long must have passed since the last load for us to reload.
   *   Specify `0` to reload immediately.
   * @param progress The [ProgressIndicator] to use for showing progress and logging.
   * @param downloader The [Downloader] to use for downloading remote files, including any remote
   *   list of repo sources and the remote repositories themselves.
   * @param settings The settings to use during the load, including for example proxy settings used
   *   when fetching remote files.
   * @return `true` if the load was successful (including if cached results were returned), false
   *   otherwise.
   */
  @Slow
  fun loadSynchronously(
    cacheExpirationMs: Long,
    progress: ProgressIndicator,
    downloader: Downloader? = null,
    settings: SettingsController? = null,
  ): Boolean {
    val result = AtomicBoolean(true)
    loadSynchronously(
      cacheExpirationMs = cacheExpirationMs,
      onError = { result.set(false) },
      runner = DirectProgressRunner(progress),
      downloader = downloader,
      settings = settings,
    )
    return result.get()
  }

  abstract suspend fun loadLocalPackages(
    indicator: ProgressIndicator,
    cacheExpiration: Duration,
  ): List<LocalPackage>

  abstract suspend fun loadRemotePackages(
    indicator: ProgressIndicator,
    cacheExpiration: Duration,
    downloader: Downloader,
    settings: SettingsController?,
  ): List<RemotePackage>

  /**
   * Causes cached results to be considered expired. The next time [load] is called, a complete load
   * will be done.
   */
  abstract fun markInvalid()

  /**
   * Causes the cached results of the local repositories to be considered expired. The next time
   * [load] is called, the load will be done only for the local repositories, the remotes being
   * loaded from the cache if possible.
   */
  abstract fun markLocalCacheInvalid()

  /**
   * Check to see if there have been any changes to the local repo since the last load. This
   * includes scanning the local repo for packages, but does not involve any reading or parsing of
   * package metadata files. If there have been any changes, or if the cache is older than the
   * default timeout, the local packages will be reloaded.
   *
   * @return `true` if the load was successful, `false` otherwise.
   */
  abstract fun reloadLocalIfNeeded(progress: ProgressIndicator): Boolean

  /** Gets the currently-loaded [RepositoryPackages]. */
  abstract val packages: RepositoryPackages

  /**
   * Gets an [LSResourceResolver] that can find the XSDs for all versions of the
   * currently-registered [SchemaModule]s by namespace. Returns null if there is an error.
   */
  abstract fun getResourceResolver(progress: ProgressIndicator): LSResourceResolver?

  /**
   * Registers a listener that will be called whenever the local packages are reloaded and have
   * changed. The [RepositoryPackages] instance passed to the callback will contain only the local
   * packages.
   */
  abstract fun addLocalChangeListener(listener: RepoLoadedListener)

  /** Removes the listener previously added by calling [addLocalChangeListener]. */
  abstract fun removeLocalChangeListener(listener: RepoLoadedListener)

  /**
   * Register a listener that will be called whenever the remote packages are reloaded and have
   * changed. The [RepositoryPackages] instance will contain the remote and local packages.
   */
  abstract fun addRemoteChangeListener(listener: RepoLoadedListener)

  /** Removes the listener previously added by calling [addRemoteChangeListener]. */
  abstract fun removeRemoteChangeListener(listener: RepoLoadedListener)

  /** Record that the given package is in the process of being installed by the given installer. */
  abstract fun installBeginning(repoPackage: RepoPackage, installer: PackageOperation)

  /**
   * Record that the given package is no longer in the process of being installed (that is, install
   * completed either successfully or unsuccessfully).
   */
  abstract fun installEnded(repoPackage: RepoPackage)

  /**
   * Gets the previously-registered installer that is currently installing the given package, or
   * `null` if there is none.
   */
  abstract fun getInProgressInstallOperation(remotePackage: RepoPackage): PackageOperation?

  /** Callback for when repository load is completed/partially completed. */
  fun interface RepoLoadedListener {
    /**
     * @param packages The packages that have been loaded so far. When this listener is used in the
     *   `onLocalComplete` argument to [load] `packages` will only include local packages.
     */
    fun loaded(packages: RepositoryPackages)
  }

  /**
   * A ProgressRunner that immediately runs tasks on the current thread (analogous to
   * MoreExecutors.directExecutor()).
   */
  protected class DirectProgressRunner(private val progress: ProgressIndicator) : ProgressRunner {
    // This class is only for use in loadSynchronously; this method is unneeded.
    override fun runAsyncWithProgress(r: ProgressRunnable) = throw UnsupportedOperationException()

    override fun runSyncWithProgress(r: ProgressRunnable) {
      runBlocking { r.run(progress) }
    }
  }

  companion object {
    /**
     * After loading the repository, this is the amount of time that must pass before we consider it
     * to be stale and need to be reloaded.
     */
    @JvmField val DEFAULT_EXPIRATION_PERIOD_MS: Long = TimeUnit.DAYS.toMillis(1)

    /** Pattern for name of the xsd file used in [commonModule]. */
    private const val COMMON_XSD_PATTERN = "/xsd/repo-common-%02d.xsd"

    /** Pattern for fully-qualified name of the `ObjectFactory` used in [commonModule]. */
    private const val COMMON_OBJECT_FACTORY_PATTERN =
      "com.android.repository.impl.generated.v%d.ObjectFactory"

    /** Pattern for name of the xsd file used in [genericModule]. */
    private const val GENERIC_XSD_PATTERN = "/xsd/generic-%02d.xsd"

    /** Pattern for fully-qualified name of the `ObjectFactory` used in [genericModule]. */
    private const val GENERIC_OBJECT_FACTORY_PATTERN =
      "com.android.repository.impl.generated.generic.v%d.ObjectFactory"

    /**
     * The core [SchemaModule] created by the RepoManager itself. Contains the base definition of
     * repository, package, revision, etc.
     */
    @JvmStatic
    val commonModule =
      SchemaModule<CommonFactory>(
        COMMON_OBJECT_FACTORY_PATTERN,
        COMMON_XSD_PATTERN,
        RepoManager::class.java,
      )

    /**
     * The [SchemaModule] created by the RepoManager that includes the trivial generic `typeDetails`
     * type.
     */
    @JvmStatic
    val genericModule =
      SchemaModule<GenericFactory>(
        GENERIC_OBJECT_FACTORY_PATTERN,
        GENERIC_XSD_PATTERN,
        RepoManager::class.java,
      )

    /** @return A new `RepoManager`. */
    @JvmStatic
    fun create(localPath: Path?): RepoManager {
      return RepoManagerImpl(localPath)
    }
  }
}
