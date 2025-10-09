/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.sdklib.repository

import com.android.SdkConstants
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.prefs.AndroidLocationsProvider
import com.android.repository.Revision
import com.android.repository.api.ConstantSourceProvider
import com.android.repository.api.LocalPackage
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RemoteListSourceProvider
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoPackage
import com.android.repository.api.RepositorySource
import com.android.repository.api.RepositorySourceProvider
import com.android.repository.api.SchemaModule
import com.android.repository.impl.sources.LocalSourceProvider
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.legacy.LegacyLocalRepoLoader
import com.android.sdklib.repository.legacy.LegacyRemoteRepoLoader
import com.android.sdklib.repository.meta.AddonFactory
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.sdklib.repository.meta.RepoFactory
import com.android.sdklib.repository.meta.SdkCommonFactory
import com.android.sdklib.repository.meta.SysImgFactory
import com.android.sdklib.repository.sources.RemoteSiteType
import com.android.sdklib.repository.targets.AndroidTargetManager
import com.android.sdklib.repository.targets.SystemImageManager
import com.android.sdklib.util.CacheByCanonicalPath
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.net.URISyntaxException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Predicate
import org.jetbrains.annotations.TestOnly

/**
 * Android SDK interface to [RepoManager]. Ensures that the proper android sdk-specific schemas and
 * source providers are registered, and provides android sdk-specific package logic (pending as
 * adoption continues).
 *
 * @constructor Don't use this, use [getInstance], unless you're in a unit test and need to specify
 *   a custom [androidFolder], [repoManager], or [userSourceProvider].
 * @property location Location of the local SDK.
 * @property androidFolder Location of the .android folder; see
 *   [AndroidLocationsProvider.prefsLocation]
 * @property repoManager The [RepoManager] initialized with our [SchemaModule]s,
 *   [RepositorySourceProvider]s, and local SDK path.
 * @property userSourceProvider provider for user-specified [RepositorySource]s.
 */
class AndroidSdkHandler
@VisibleForTesting
@JvmOverloads
constructor(
  val location: Path?,
  val androidFolder: Path?,
  @GuardedBy("lock") private var repoManager: RepoManager? = null,
  private var userSourceProvider: LocalSourceProvider? = null,
) {
  /** Lock for synchronizing changes to our [RepoManager]. */
  private val lock = Any()

  /**  */
  // @GuardedBy("lock") private var repoManager: RepoManager? = customRepoManager

  /** Finds all [SystemImageManager]s in packages known to [repoManager]; */
  @GuardedBy("lock") private var systemImageManager: SystemImageManager? = null

  /** Creates [IAndroidTarget]s based on the platforms and addons known to [repoManager]. */
  @GuardedBy("lock") private var androidTargetManager: AndroidTargetManager? = null

  /** Reference to our latest build tool package. */
  @GuardedBy("lock") private var latestBuildTool: BuildToolInfo? = null

  /**
   * Fetches a [RepoManager], creating it if necessary, then loads local packages synchronously.
   * This involves scanning the disk, and may be slow.
   *
   * Its lifetime is the same as that of this AndroidSdkHandler; thus, it should not be cached for
   * longer than this AndroidSdkHandler remains valid. For example, if the local SDK path in Studio
   * is changed, a new AndroidSdkHandler and a new RepoManager will be needed.
   */
  @Slow
  fun getRepoManagerAndLoadSynchronously(progress: ProgressIndicator): RepoManager {
    val result = getRepoManager(progress)

    if (location != null) {
      result.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, null, null)
    }

    return result
  }

  /**
   * Fetches a [RepoManager], creating it if necessary.
   *
   * Its lifetime is the same as that of this AndroidSdkHandler; thus, it should not be cached for
   * longer than this AndroidSdkHandler remains valid. For example, if the local SDK path in Studio
   * is changed, a new AndroidSdkHandler and a new RepoManager will be needed.
   */
  fun getRepoManager(progress: ProgressIndicator): RepoManager {
    synchronized(lock) {
      repoManager?.let {
        return it
      }

      systemImageManager = null
      androidTargetManager = null
      latestBuildTool = null

      val newRepoManager =
        getRepoConfig(progress).createRepoManager(location, getUserSourceProvider(progress))
      // Invalidate system images, targets, the latest build tool, and the legacy local
      // package manager when local packages change
      newRepoManager.addLocalChangeListener {
        synchronized(lock) {
          systemImageManager = null
          androidTargetManager = null
          latestBuildTool = null
        }
      }
      repoManager = newRepoManager
      return newRepoManager
    }
  }

  /** Gets (and creates if necessary) a [SystemImageManager] based on our local sdk packages. */
  fun getSystemImageManager(progress: ProgressIndicator): SystemImageManager {
    synchronized(lock) {
      systemImageManager?.let {
        return it
      }
    }

    // Initialize the repoManager outside of the lock, since it can be slow.
    val rm = getRepoManagerAndLoadSynchronously(progress)

    synchronized(lock) {
      return systemImageManager
        ?: SystemImageManager(rm, sysImgModule.createLatestFactory()).also {
          systemImageManager = it
        }
    }
  }

  /** Clears cache of the [SystemImageManager]. */
  fun clearSystemImageManagerCache() {
    synchronized(lock) { systemImageManager?.clearCache() }
  }

  /** Gets (and creates if necessary) an [AndroidTargetManager] based on our local sdk packages. */
  fun getAndroidTargetManager(progress: ProgressIndicator): AndroidTargetManager {
    synchronized(lock) {
      return androidTargetManager ?: AndroidTargetManager(this).also { androidTargetManager = it }
    }
  }

  /** Convenience to get a package from the local repo. */
  fun getLocalPackage(path: String, progress: ProgressIndicator): LocalPackage? {
    return getRepoManagerAndLoadSynchronously(progress).packages.localPackages[path]
  }

  /**
   * Suppose that `prefix` is `p`, and we have these local packages: `p;1.1`, `p;1.2`, `p;2.1` What
   * this should return is the package `p;2.1`. We operate on the path suffix since we have no
   * guarantee that the package revision is the same as used in the path. We also have no guarantee
   * that the format of the path even matches, so we ignore the packages that don't fit the format.
   *
   * @see Revision.safeParseRevision
   */
  fun getLatestLocalPackageForPrefix(
    prefix: String,
    filter: Predicate<Revision>?,
    allowPreview: Boolean,
    progress: ProgressIndicator,
  ): LocalPackage? {
    return getLatestPackageFromPrefixCollection(
      getRepoManagerAndLoadSynchronously(progress).packages.getLocalPackagesForPrefix(prefix),
      filter.toFunction(),
      allowPreview,
      Revision::safeParseRevision,
    )
  }

  fun getLatestRemotePackageForPrefix(
    prefix: String,
    filter: Predicate<Revision>?,
    allowPreview: Boolean,
    progress: ProgressIndicator,
  ): RemotePackage? {
    return getLatestPackageFromPrefixCollection(
      getRepoManager(progress).packages.getRemotePackagesForPrefix(prefix),
      filter.toFunction(),
      allowPreview,
      Revision::safeParseRevision,
    )
  }

  @VisibleForTesting
  fun getRemoteListSourceProvider(progress: ProgressIndicator): RemoteListSourceProvider {
    return getRepoConfig(progress).remoteListSourceProvider
  }

  /**
   * Gets the customizable [RepositorySourceProvider]. Can be null if there's a problem with the
   * user's environment.
   */
  fun getUserSourceProvider(progress: ProgressIndicator): LocalSourceProvider? {
    synchronized(lock) {
      if (userSourceProvider == null && androidFolder != null) {
        userSourceProvider =
          createUserSourceProvider(androidFolder).also { repoManager?.let(it::setRepoManager) }
      }
      return userSourceProvider
    }
  }

  /**
   * Class containing the repository configuration we can (lazily) create statically, as well as a
   * method to create a new [RepoManager] based on that configuration.
   *
   * Instances of this class may be shared between [AndroidSdkHandler] instances.
   */
  private class RepoConfig(progress: ProgressIndicator) {
    /** Provider for a list of [RepositorySource]s fetched from google. */
    private val addonsListSourceProvider: RemoteListSourceProvider?

    /** Provider for the main new-style [RepositorySource] */
    private val repositorySourceProvider: ConstantSourceProvider

    /**
     * Provider for the previous version of the main new-style [RepositorySource], useful during
     * transition to the new version.
     */
    private val prevRepositorySourceProvider: ConstantSourceProvider?

    /** Sets up our [SchemaModule]s and [RepositorySourceProvider]s if they haven't been yet. */
    init {
      // Schema module for the list of update sites we download
      val addonListModule: SchemaModule<*> =
        SchemaModule<Any>(
          "com.android.sdklib.repository.sources.generated.v%d.ObjectFactory",
          "/xsd/sources/sdk-sites-list-%d.xsd",
          RemoteSiteType::class.java,
        )

      addonsListSourceProvider =
        try {
          RemoteListSourceProvider.create(
            getAddonListUrl(progress),
            addonListModule,
            // Specify what modules are allowed to be used by what sites.
            mapOf(
              RemoteSiteType.AddonSiteType::class.java to setOf(addonModule),
              RemoteSiteType.SysImgSiteType::class.java to setOf(sysImgModule),
            ),
          )
        } catch (e: URISyntaxException) {
          progress.logError("Failed to set up addons source provider", e)
          null
        }

      repositorySourceProvider =
        ConstantSourceProvider(
          getRepoUrl(progress, repositoryModule.namespaceVersionMap.size),
          "Android Repository",
          setOf(repositoryModule, RepoManager.genericModule),
        )

      val prevRev: Int = repositoryModule.namespaceVersionMap.size - 1
      prevRepositorySourceProvider =
        if (prevRev <= 0) null
        else {
          ConstantSourceProvider(
            getRepoUrl(progress, prevRev),
            "Android Repository v$prevRev",
            setOf(repositoryModule, RepoManager.genericModule),
          )
        }
    }

    val remoteListSourceProvider: RemoteListSourceProvider
      get() = addonsListSourceProvider!!

    @Slow
    fun createRepoManager(localLocation: Path?, userProvider: LocalSourceProvider?): RepoManager {
      val sourceProviders = mutableListOf<RepositorySourceProvider>(repositorySourceProvider)
      prevRepositorySourceProvider?.let { sourceProviders.add(it) }

      val customSourceUrl = System.getProperty(CUSTOM_SOURCE_PROPERTY)
      if (!customSourceUrl.isNullOrEmpty()) {
        sourceProviders.add(
          ConstantSourceProvider(customSourceUrl, "Custom Provider", getAllModules())
        )
      }
      addonsListSourceProvider?.let { sourceProviders.add(it) }
      userProvider?.let { sourceProviders.add(it) }

      val result =
        RepoManager.createRepoManager(
          localLocation,
          getAllModules(),
          sourceProviders,
          // If we have a local sdk path set, set up the old-style loader so we can parse any legacy
          // packages.
          localLocation?.let { LegacyLocalRepoLoader(it) },
          LegacyRemoteRepoLoader(),
        )

      userProvider?.setRepoManager(result)

      return result
    }

    companion object {
      /**
       * Gets the default url (without the actual filename or specific final part of the path (e.g.
       * sys-img)). This will be either the value of [SDK_TEST_BASE_URL_ENV_VAR],
       * [URL_GOOGLE_SDK_SITE] or [SDK_TEST_BASE_URL_PROPERTY] JVM property.
       */
      private fun getBaseUrl(progress: ProgressIndicator): String {
        val baseUrl =
          System.getenv(SDK_TEST_BASE_URL_ENV_VAR) ?: System.getProperty(SDK_TEST_BASE_URL_PROPERTY)
        if (baseUrl != null) {
          if (baseUrl.isNotEmpty() && baseUrl.endsWith("/")) {
            return baseUrl
          } else {
            progress.logWarning("Ignoring invalid SDK_TEST_BASE_URL: $baseUrl")
          }
        }
        return URL_GOOGLE_SDK_SITE
      }

      private fun getAddonListUrl(progress: ProgressIndicator): String {
        return getBaseUrl(progress) + DEFAULT_SITE_LIST_FILENAME_PATTERN
      }

      private fun getRepoUrl(progress: ProgressIndicator, version: Int) =
        "${getBaseUrl(progress)}repository2-$version.xml"
    }
  }

  /**
   * Returns a [BuildToolInfo] corresponding to the newest installed build tool [LocalPackage], or
   * `null` if none are installed (or if the `allowPreview` parameter is false and there was
   * non-preview version available)
   *
   * @param progress a progress indicator
   * @param allowPreview ignore preview build tools version unless this parameter is true
   */
  fun getLatestBuildTool(progress: ProgressIndicator, allowPreview: Boolean): BuildToolInfo? {
    return getLatestBuildTool(progress, null, allowPreview)
  }

  /**
   * Returns a [BuildToolInfo] corresponding to the newest installed build tool [LocalPackage], or
   * `null` if none are installed (or if the `allowPreview` parameter is false and there was
   * non-preview version available)
   *
   * @param progress a progress indicator
   * @param filter the revision predicate to satisfy
   * @param allowPreview ignore preview build tools version unless this parameter is true
   */
  fun getLatestBuildTool(
    progress: ProgressIndicator,
    filter: Predicate<Revision>?,
    allowPreview: Boolean,
  ): BuildToolInfo? {
    synchronized(lock) {
      if (!allowPreview && latestBuildTool != null) {
        return latestBuildTool
      }
    }

    val latestBuildToolPackage =
      getLatestLocalPackageForPrefix(SdkConstants.FD_BUILD_TOOLS, filter, allowPreview, progress)
        ?: return null

    val latestBuildTool = BuildToolInfo.fromLocalPackage(latestBuildToolPackage)

    // Don't cache if preview.
    if (!latestBuildToolPackage.version.isPreview) {
      synchronized(lock) { this.latestBuildTool = latestBuildTool }
    }

    return latestBuildTool
  }

  /**
   * Creates a the [BuildToolInfo] for the specified build tools revision, if available.
   *
   * @param revision The build tools revision requested
   * @param progress [ProgressIndicator] for logging.
   * @return The [BuildToolInfo] corresponding to the specified build tools package, or `null` if
   *   that revision is not installed.
   */
  fun getBuildToolInfo(revision: Revision, progress: ProgressIndicator): BuildToolInfo? {
    return getRepoManagerAndLoadSynchronously(progress)
      .packages
      .localPackages[DetailsTypes.getBuildToolsPath(revision)]
      ?.let { BuildToolInfo.fromLocalPackage(it) }
  }

  /** Converts a `File` into a `Path` on the `FileSystem` used by this SDK. */
  fun toCompatiblePath(file: File): Path {
    if (location != null) {
      return location.fileSystem.getPath(file.path)
    }
    return file.toPath()
  }

  /** Converts a `String` into a `Path` on the `FileSystem` used by this SDK. */
  fun toCompatiblePath(file: String): Path {
    if (location != null) {
      return location.fileSystem.getPath(file)
    }
    return Paths.get(file)
  }

  fun interface InstanceProvider {
    fun getInstance(locationProvider: AndroidLocationsProvider, localPath: Path?): AndroidSdkHandler

    fun reset() {}
  }

  object DefaultInstanceProvider : InstanceProvider {
    private val instances = CacheByCanonicalPath<AndroidSdkHandler>()

    override fun getInstance(
      locationProvider: AndroidLocationsProvider,
      localPath: Path?,
    ): AndroidSdkHandler {
      return instances.computeIfAbsent(localPath) { canonicalKey ->
        val androidFolder = runCatching { locationProvider.prefsLocation }.getOrNull()
        AndroidSdkHandler(canonicalKey, androidFolder)
      }
    }

    override fun reset() {
      instances.clear()
    }
  }

  companion object {
    /**
     * @return The [SchemaModule] containing the metadata for addon-type repositories. See
     *   sdk-addon-XX.xsd.
     */
    /** Schema module containing the package type information to be used in addon repos. */
    @JvmStatic
    val addonModule: SchemaModule<AddonFactory> =
      SchemaModule(
        "com.android.sdklib.repository.generated.addon.v%d.ObjectFactory",
        "/xsd/sdk-addon-%02d.xsd",
        AndroidSdkHandler::class.java,
      )

    /**
     * @return The [SchemaModule] containing the metadata for the primary android SDK (containing
     *   platforms etc.). See sdk-repository-XX.xsd.
     */
    /** Schema module containing the package type information to be used in the primary repo. */
    @JvmStatic
    val repositoryModule: SchemaModule<RepoFactory> =
      SchemaModule(
        "com.android.sdklib.repository.generated.repository.v%d.ObjectFactory",
        "/xsd/sdk-repository-%02d.xsd",
        AndroidSdkHandler::class.java,
      )

    /**
     * @return The [SchemaModule] containing the metadata for system image-type repositories. See
     *   sdk-sys-img-XX.xsd.
     */
    /** Schema module containing the package type information to be used in system image repos. */
    @JvmStatic
    val sysImgModule: SchemaModule<SysImgFactory> =
      SchemaModule(
        "com.android.sdklib.repository.generated.sysimg.v%d.ObjectFactory",
        "/xsd/sdk-sys-img-%02d.xsd",
        AndroidSdkHandler::class.java,
      )

    /**
     * @return The [SchemaModule] containing the common sdk-specific metadata. See
     *   sdk-common-XX.xsd.
     */
    /** Common schema module used by the other sdk-specific modules. */
    @JvmStatic
    val commonModule: SchemaModule<SdkCommonFactory> =
      SchemaModule(
        "com.android.sdklib.repository.generated.common.v%d.ObjectFactory",
        "/xsd/sdk-common-%02d.xsd",
        AndroidSdkHandler::class.java,
      )

    /**
     * The URL of the official Google sdk-repository site. The URL ends with a /, allowing easy
     * concatenation.
     */
    private const val URL_GOOGLE_SDK_SITE = "https://dl.google.com/android/repository/"

    /** A system property than can be used to add an extra fully-privileged update site. */
    private const val CUSTOM_SOURCE_PROPERTY = "android.sdk.custom.url"

    /**
     * The name of the environment variable used to override the url of the primary repository, for
     * testing.
     */
    const val SDK_TEST_BASE_URL_ENV_VAR = "SDK_TEST_BASE_URL"

    /**
     * The name of the system property used to override the url of the primary repository, for
     * testing.
     */
    const val SDK_TEST_BASE_URL_PROPERTY = "sdk.test.base.url"

    /** The name of the file containing user-specified remote repositories. */
    @VisibleForTesting const val LOCAL_ADDONS_FILENAME = "repositories.cfg"

    /**
     * Pattern for the name of a (remote) file containing a list of urls to check for repositories.
     *
     * @see RemoteListSourceProvider
     */
    private const val DEFAULT_SITE_LIST_FILENAME_PATTERN = "addons_list-%d.xml"

    /** Implementation of [getInstance]; can be overridden for testing via AndroidSdkHandlerRule. */
    @set:TestOnly internal var instanceProvider: InstanceProvider = DefaultInstanceProvider

    /**
     * Lazily-initialized class containing our static repository configuration, shared between
     * AndroidSdkHandler instances.
     */
    private var repoConfig: RepoConfig? = null

    /**
     * Get an [AndroidSdkHandler] instance.
     *
     * @param locationProvider a location provider to get the path to the .android folder.
     * @param localPath The path to the local SDK. If null, this handler will only be used for
     *   remote operations.
     */
    @JvmStatic
    fun getInstance(
      locationProvider: AndroidLocationsProvider,
      localPath: Path?,
    ): AndroidSdkHandler = instanceProvider.getInstance(locationProvider, localPath)

    /**
     * Force removal of any cached {@code AndroidSdkHandler} instances. This will force a reparsing
     * of the SDK next time a component is looked up.
     */
    @JvmStatic
    fun reset() {
      instanceProvider.reset()
    }

    /**
     * @param packages a [Collection] of packages which share a common `prefix`, from which we wish
     *   to extract the "Latest" package, as sorted in natural order with `mapper`.
     * @param filter the revision predicate that has to be satisfied by the returned package
     * @param allowPreview whether we allow returning a preview package.
     * @param mapper maps from path suffix to a [Comparable], so that we can sort the packages by
     *   suffix in natural order.
     * @param P package type; [LocalPackage] or [RemotePackage]
     * @param T [Comparable] that we map the suffix to.
     * @return the "Latest" package from the [Collection], as sorted with `mapper` on the last path
     *   component.
     */
    @JvmStatic
    fun <P : RepoPackage, T : Comparable<T>> getLatestPackageFromPrefixCollection(
      packages: Collection<P>,
      filter: (Revision) -> Boolean,
      allowPreview: Boolean,
      mapper: (String) -> T?,
    ): P? {
      return packages
        .filter { filter(it.version) && (allowPreview || !it.version.isPreview) }
        .mapNotNull { pkg ->
          val suffix = pkg.path.substring(pkg.path.lastIndexOf(RepoPackage.PATH_SEPARATOR) + 1)
          mapper(suffix)?.let { pkg to it }
        }
        .maxByOrNull { it.second }
        ?.first
    }

    @JvmStatic
    fun <P : RepoPackage, T : Comparable<T>> getLatestPackageFromPrefixCollection(
      packages: Collection<P>,
      allowPreview: Boolean,
      mapper: (String) -> T?,
    ): P? = getLatestPackageFromPrefixCollection(packages, { _ -> true }, allowPreview, mapper)

    @JvmStatic
    fun getAllModules(): List<SchemaModule<*>> =
      listOf(
        repositoryModule,
        addonModule,
        sysImgModule,
        commonModule,
        RepoManager.commonModule,
        RepoManager.genericModule,
      )

    /** Creates a customizable [RepositorySourceProvider]. */
    @JvmStatic
    fun createUserSourceProvider(androidFolder: Path): LocalSourceProvider {
      return LocalSourceProvider(
        androidFolder.resolve(LOCAL_ADDONS_FILENAME),
        listOf(sysImgModule, addonModule),
        listOf(sysImgModule, addonModule, repositoryModule, commonModule),
      )
    }

    /**
     * Gets or creates the single static RepoConfig instance. Synchronized to ensure that only one
     * is created.
     */
    @Synchronized
    private fun getRepoConfig(progress: ProgressIndicator): RepoConfig {
      return repoConfig ?: RepoConfig(progress).also { repoConfig = it }
    }
  }
}

private fun <T> Predicate<T>?.toFunction(): (T) -> Boolean =
  this?.let { this::test } ?: { _ -> true }
