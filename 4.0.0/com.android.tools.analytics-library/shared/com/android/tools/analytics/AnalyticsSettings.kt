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

import com.android.utils.DateProvider
import com.android.utils.ILogger
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.math.BigInteger
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Paths
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Settings related to analytics reporting. These settings are stored in
 * ~/.android/analytics.settings as a json file.
 */
object AnalyticsSettings {
  private val LOG = Logger.getLogger(AnalyticsSettings.javaClass.name)

  private const val DAYS_IN_LEAP_YEAR = 366
  private const val DAYS_IN_NON_LEAP_YEAR = 365
  private const val DAYS_TO_WAIT_FOR_REQUESTING_SENTIMENT_AGAIN = 7

  @JvmStatic
  var initialized = false
    private set

  @JvmStatic
  var exceptionThrown = false

  @JvmStatic
  val userId: String
    get() {
      return runIfAnalyticsSettingsUsable("") {
        instance?.userId ?: ""
      }
    }

  @JvmStatic
  var optedIn: Boolean
    get() {
      return runIfAnalyticsSettingsUsable(false) {
        instance?.optedIn ?: false
      }
    }

    set(value) {
      runIfAnalyticsSettingsUsable(Unit) {
        instance?.apply {
          optedIn = value
        }
      }
    }

  @JvmStatic
  private fun<T> runIfAnalyticsSettingsUsable(default: T, callback: () -> T): T {
    var throwable : Throwable? = null
    synchronized(gate) {
      if (exceptionThrown) {
        return default
      }
      ensureInitialized()
      try {
        return callback()
      } catch (t: Throwable) {
        exceptionThrown = true
        throwable = t
      }
    }
    if (throwable != null) {
      try {
        LOG.log(Level.SEVERE, throwable) { "AnalyticsSettings call failed" }
      } catch (ignored: Throwable) {
      }
    }
    return default
  }

  private fun ensureInitialized() {
    if (!initialized && java.lang.Boolean.getBoolean("idea.is.internal")) {
      // Android Studio Developers: If you hit this exception, you're trying to find out the status
      // of AnalyticsSettings before the system has been initialized. Please reach out the the owners
      // of this code to figure out how best to do these checks instead of getting null values.
      throw RuntimeException("call to AnalyticsSettings before initialization")
    }
  }

  @JvmStatic
  val debugDisablePublishing: Boolean
    get() {
      return runIfAnalyticsSettingsUsable(false) {
        instance?.debugDisablePublishing ?: false
      }
    }

  @JvmStatic
  var lastSentimentQuestionDate: Date?
    get() {
      return runIfAnalyticsSettingsUsable(null) {
        instance?.lastSentimentQuestionDate
      }
    }
    set(value) {
      runIfAnalyticsSettingsUsable(Unit) {
        instance?.lastSentimentQuestionDate = value
      }
    }

  @JvmStatic
  var lastSentimentAnswerDate: Date?
    get() {
      return runIfAnalyticsSettingsUsable(null) {
        instance?.lastSentimentAnswerDate
      }
    }
    set(value) {
      runIfAnalyticsSettingsUsable(Unit) {
        instance?.lastSentimentAnswerDate = value
      }
    }

  internal const val SALT_SKEW_NOT_INITIALIZED = -1

  @VisibleForTesting
  @JvmStatic
  var dateProvider: DateProvider = DateProvider.SYSTEM

  private val EPOCH = LocalDate.ofEpochDay(0)
  // the gate is used to ensure settings are accessed single-threaded
  private val gate = Any()

  @JvmStatic
  private var instance: AnalyticsSettingsData? = null

  /**
   * Gets the current salt skew, this is used by [.getSalt] to update the salt every 28
   * days with a consistent window. This window size allows 4 week and 1 week analyses.
   */
  @VisibleForTesting
  @JvmStatic
  fun currentSaltSkew(): Int {
    val now = LocalDate.from(
      Instant.ofEpochMilli(dateProvider.now().time).atZone(ZoneOffset.UTC))
    // Unix epoch was on a Thursday, but we want Monday to be the day the salt is refreshed.
    val days = ChronoUnit.DAYS.between(EPOCH, now) + 3
    return (days / 28).toInt()
  }

  /**
   * Loads an existing settings file from disk, or creates a new valid settings object if none
   * exists. In case of the latter, will try to load uid.txt for maintaining the same uid with
   * previous metrics reporting.
   *
   * @throws IOException if there are any issues reading the settings file.
   */
  @VisibleForTesting
  @Throws(IOException::class)
  @JvmStatic
  private fun loadSettingsData(logger: ILogger): AnalyticsSettingsData {
    val file = settingsFile
    if (!file.exists()) {
      return createNewAnalyticsSettingsData()
    }
    val channel = RandomAccessFile(file, "rw").channel
    try {
      val settings: AnalyticsSettingsData? = channel.tryLock().use {
        val inputStream = Channels.newInputStream(channel)
        val gson = GsonBuilder().create()
        gson.fromJson(InputStreamReader(inputStream), AnalyticsSettingsData::class.java)
      }
      if (settings == null || !isValid(settings)) {
        return createNewAnalyticsSettingsData()
      }
      return settings
    }
    catch (e: OverlappingFileLockException) {
      logger.warning("Unable to lock settings file %s: %s", file.toString(), e)
    }
    catch (e: JsonParseException) {
      logger.warning("Unable to parse settings file %s: %s", file.toString(), e)
    }
    catch (e: IllegalStateException) {
      logger.warning("Unable to parse settings file %s: %s", file.toString(), e)
    }
    var newSettings = AnalyticsSettingsData()
    newSettings.userId = UUID.randomUUID().toString()
    return newSettings
  }

  /**
   * Creates a new settings object and writes it to disk. Will try to load uid.txt for maintaining
   * the same uid with previous metrics reporting.
   *
   * @throws IOException if there are any issues writing the settings file.
   */
  @VisibleForTesting
  @JvmStatic
  @Throws(IOException::class)
  private fun createNewAnalyticsSettingsData(): AnalyticsSettingsData {
    val settings = AnalyticsSettingsData()

    val uidFile = Paths.get(AnalyticsPaths.getAndEnsureAndroidSettingsHome(), "uid.txt").toFile()
    if (uidFile.exists()) {
      try {
        val uid = Files.readFirstLine(uidFile, Charsets.UTF_8)
        settings.userId = uid
      }
      catch (e: IOException) {
        // Ignore and set new UID.
      }

    }
    if (settings.userId == null) {
      settings.userId = UUID.randomUUID().toString()
    }
    settings.saveSettings()
    return settings
  }

  @JvmStatic
  var googlePlayDateProvider: WebServerDateProvider? = null

  /**
   * Get or creates an instance of the settings. Uses the following strategies in order:
   *
   *
   *  * Use existing instance
   *  * Load existing 'analytics.settings' file from disk
   *  * Create new 'analytics.settings' file
   *  * Create instance without persistence
   *
   *
   * Any issues reading/writing the config file will be logged to the logger.
   */
  @JvmStatic
  @JvmOverloads
  fun initialize(logger: ILogger, scheduler: ScheduledExecutorService? = null) {
    synchronized(gate) {
      try {
        if (instance != null) {
          return
        }
        initialized = true
        instance = loadSettingsData(logger)
      } catch (e : IOException) {
        // null out metrics in case of failure to load.
        initialized = true
        instance = AnalyticsSettingsData()
        logger.warning("Unable to initialize metrics, ensure %s is writable, details: %s",
                       AnalyticsPaths.getAndEnsureAndroidSettingsHome(), e.message)
      }
    }
    scheduler?.submit {
      try {
        val gp = WebServerDateProvider(URL("https://play.google.com/"))
        dateProvider = gp
        googlePlayDateProvider = gp
      }
      catch (_: IOException) {
        logger.warning("Unable to get current time from Google's servers, using local system time instead.")
      }
    }
  }

  /**
   * initializes or updates AnalyticsSettings into a disabled state.
   */
  @JvmStatic
  fun disable() {
    synchronized(gate) {
      initialized = true
      instance = AnalyticsSettingsData()
      dateProvider = DateProvider.SYSTEM
      googlePlayDateProvider = null
    }
  }

  /**
   * Allows test to set a custom version of the AnalyticsSettings to test different setting
   * states.
   */
  @VisibleForTesting
  @JvmStatic
  fun setInstanceForTest(settings: AnalyticsSettingsData?) {
    synchronized(gate) {
      instance = settings
      initialized = instance != null
      exceptionThrown = false
    }
  }

  /**
   * Helper to get the file to read/write settings from based on the configured android settings
   * home.
   */
  internal val settingsFile: File
    get() = Paths.get(AnalyticsPaths.getAndEnsureAndroidSettingsHome(), "analytics.settings").toFile()


  /**
   * Gets a binary blob to ensure per user anonymization. Gets automatically rotated every 28
   * days. Primarily used by [Anonymizer].
   */
  val salt: ByteArray
    @Throws(IOException::class)
    get() = synchronized(AnalyticsSettings.gate) {
      var data: AnalyticsSettingsData = instance ?: return byteArrayOf()
      // Starting with Android Studio 3.5 we switch to 532 day rotation, this logic is to coincide with that change
      // starting with the rotation on 2018/11/26. 28*19 = 532 days and -11 is to offset with the 28 day rotation cycle starting on
      // 2018/11/26.
      var dataSkew532 : Int = ( data.saltSkew - 11 )/ 19
      var currentSaltSkew = com.android.tools.analytics.AnalyticsSettings.currentSaltSkew()
      val currentSaltSkew532: Int = (currentSaltSkew - 11) / 19
      if (dataSkew532 != currentSaltSkew532) {
        data.saltSkew = currentSaltSkew
        val random = SecureRandom()
        val blob = ByteArray(24)
        random.nextBytes(blob)
        data.saltValue = BigInteger(blob)
        saveSettings()
      }
      return data.saltValue.toByteArrayOfLength24()
    }

  /**
   * Writes this settings object to disk.
   * @throws IOException if there are any issues writing the settings file.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun saveSettings() {
    runIfAnalyticsSettingsUsable(Unit) {
      instance?.saveSettings()
    }
  }

  /** Checks if the AnalyticsSettings object is in a valid state.  */
  internal fun isValid(settings: AnalyticsSettingsData): Boolean {
    return settings.userId != null && (settings.saltSkew == AnalyticsSettings.SALT_SKEW_NOT_INITIALIZED || settings.saltValue != null)
  }

  fun shouldRequestUserSentiment(): Boolean {
    if (!optedIn) {
      return false
    }

    val lastSentimentAnswerDate = AnalyticsSettings.lastSentimentAnswerDate
    val lastSentimentQuestionDate = AnalyticsSettings.lastSentimentQuestionDate
    val now = dateProvider.now()

    var daysInYear = DAYS_IN_NON_LEAP_YEAR
    if (GregorianCalendar().isLeapYear(now.year + 1900)) {
      daysInYear = DAYS_IN_LEAP_YEAR
    }

    if (lastSentimentAnswerDate != null) {
      val calendar = Calendar.getInstance()
      calendar.time = now
      calendar.add(Calendar.DATE, -daysInYear)
      val lastYear = calendar.time
      if (lastSentimentAnswerDate.after(lastYear)) {
        return false
      }
    }

    // If we should ask the question based on dates, and asked but not answered then we should always prompt, even if this is
    // not the magic date for that user.
    if (lastSentimentQuestionDate != null) {
      val calendar = Calendar.getInstance()
      calendar.time = now
      calendar.add(Calendar.DATE, -DAYS_TO_WAIT_FOR_REQUESTING_SENTIMENT_AGAIN)
      val startOfWaitForRequest = calendar.time
      return !lastSentimentQuestionDate.after(startOfWaitForRequest)
    }

    val startOfYear = GregorianCalendar(now.year + 1900 ,0, 1)
    startOfYear.timeZone = TimeZone.getTimeZone(ZoneOffset.UTC)

    // Otherwise, only request on the magic date for the user, to spread user sentiment data throughout the year.
    var daysSinceJanFirst = ChronoUnit.DAYS.between(startOfYear.toInstant(), now.toInstant())
    var offset = Math.abs(Hashing.farmHashFingerprint64().hashString(AnalyticsSettings.userId, Charsets.UTF_8).asLong()) % daysInYear
    return daysSinceJanFirst == offset
  }
}

class AnalyticsSettingsData {
  fun saveSettings() {
    val file = AnalyticsSettings.settingsFile
    var dir = file.parentFile
    if (!dir.exists()) {
      dir.mkdirs()
    }
    try {
      RandomAccessFile(file, "rw").use { settingsFile ->
        settingsFile.channel.use { channel ->
          channel.tryLock().use { lock ->
            if (lock == null) {
              throw IOException("Unable to lock settings file " + file.toString())
            }
            val gson = GsonBuilder().create()
            val readStream = InputStreamReader(Channels.newInputStream(channel))
            val existingData = gson.fromJson(readStream, AnalyticsSettingsData::class.java)
            if (existingData?.saltSkew == saltSkew) {
              // The salt is apparently updated by some other process. In this case we read that on the disk rather than using our own in
              // order to make sure all processes use the same salt.
              saltValue = existingData.saltValue
            }
            channel.truncate(0)
            val outputStream = Channels.newOutputStream(channel)
            val writer = OutputStreamWriter(outputStream)
            gson.toJson(this, writer)
            writer.flush()
            outputStream.flush()
          }
        }
      }
    }
    catch (e: OverlappingFileLockException) {
      throw IOException("Unable to lock settings file " + file.toString(), e)
    }
  }

  /**
   * User id used for reporting analytics. This id is pseudo-anonymous.
   */
  @field:SerializedName("userId")
  var userId: String? = null

  @field:SerializedName("hasOptedIn")
  var optedIn: Boolean = false

  @field:SerializedName("debugDisablePublishing")
  val debugDisablePublishing: Boolean = false

  @field:SerializedName("saltValue")
  var saltValue = BigInteger.valueOf(0L)

  @field:SerializedName("saltSkew")
  var saltSkew = AnalyticsSettings.SALT_SKEW_NOT_INITIALIZED

  @field:SerializedName("lastSentimentQuestionDate")
  var lastSentimentQuestionDate : Date? = null

  @field:SerializedName("lastSentimentAnswerDate")
  var lastSentimentAnswerDate : Date? = null
}

fun BigInteger.toByteArrayOfLength24(): ByteArray {
  val blob = toByteArray()
  var fullBlob = blob
  if (blob.size < 24) {
    fullBlob = ByteArray(24)
    System.arraycopy(blob, 0, fullBlob, 0, blob.size)
  }
  return fullBlob
}
