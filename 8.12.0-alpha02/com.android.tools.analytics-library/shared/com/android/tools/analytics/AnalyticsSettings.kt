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
import com.google.common.io.Files
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.math.BigInteger
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Paths
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Settings related to analytics reporting. These settings are stored in
 * ~/.android/analytics.settings as a json file.
 */
object AnalyticsSettings {
  private const val DAYS_IN_LEAP_YEAR = 366
  private const val DAYS_IN_NON_LEAP_YEAR = 365

  private val LOG = Logger.getLogger(AnalyticsSettings.javaClass.name)

  @JvmStatic
  var initialized = false
    private set

  @JvmStatic var exceptionThrown = false

  @JvmStatic
  val userId: String
    get() {
      return runIfAnalyticsSettingsUsable("") { instance?.userId ?: "" }
    }

  @JvmStatic
  var optedIn: Boolean
    get() {
      return runIfAnalyticsSettingsUsable(false) { instance?.optedIn ?: false }
    }
    set(value) {
      runIfAnalyticsSettingsUsable(Unit) { instance?.apply { optedIn = value } }
    }

  @JvmStatic
  private fun <T> runIfAnalyticsSettingsUsable(default: T, callback: () -> T): T {
    var throwable: Throwable? = null
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
      } catch (ignored: Throwable) {}
    }
    return default
  }

  private fun ensureInitialized() {
    if (!initialized && java.lang.Boolean.getBoolean("idea.is.internal")) {
      // Android Studio Developers: If you hit this exception, you're trying to find out the status
      // of AnalyticsSettings before the system has been initialized. Please reach out to the owners
      // of this code to figure out how best to do these checks instead of getting null values.
      throw RuntimeException("call to AnalyticsSettings before initialization")
    }
  }

  @JvmStatic
  val debugDisablePublishing: Boolean
    get() {
      return runIfAnalyticsSettingsUsable(false) { instance?.debugDisablePublishing ?: false }
    }

  @JvmStatic
  var popSentimentQuestionFrequency: Int
    get() {
      return runIfAnalyticsSettingsUsable(0) { instance?.popSentimentQuestionFrequency ?: 0 }
    }
    set(value) {
      runIfAnalyticsSettingsUsable(Unit) { instance?.popSentimentQuestionFrequency = value }
    }

  @JvmStatic
  var lastSentimentQuestionDate: Date?
    get() {
      return runIfAnalyticsSettingsUsable(null) { instance?.lastSentimentQuestionDate }
    }
    set(value) {
      runIfAnalyticsSettingsUsable(Unit) { instance?.lastSentimentQuestionDate = value }
    }

  @JvmStatic
  var lastSentimentAnswerDate: Date?
    get() {
      return runIfAnalyticsSettingsUsable(null) { instance?.lastSentimentAnswerDate }
    }
    set(value) {
      runIfAnalyticsSettingsUsable(Unit) { instance?.lastSentimentAnswerDate = value }
    }

  @JvmStatic
  var nextFeatureSurveyDate: Date?
    get() {
      return runIfAnalyticsSettingsUsable(null) { instance?.nextFeatureSurveyDate }
    }
    set(value) {
      runIfAnalyticsSettingsUsable(Unit) { instance?.nextFeatureSurveyDate = value }
    }

  @JvmStatic
  var nextFeatureSurveyDateMap: MutableMap<String, Date>?
    get() {
      return runIfAnalyticsSettingsUsable(null) { instance?.nextFeatureSurveyDateMap }
    }
    set(value) {
      runIfAnalyticsSettingsUsable(Unit) { instance?.nextFeatureSurveyDateMap = value }
    }

  @JvmStatic
  var lastOptinPromptVersion: String?
    get() {
      return runIfAnalyticsSettingsUsable(null) { instance?.lastOptinPromptVersion }
    }
    set(value) {
      runIfAnalyticsSettingsUsable(Unit) { instance?.lastOptinPromptVersion = value }
    }

  internal const val SALT_SKEW_NOT_INITIALIZED = -1

  @VisibleForTesting @JvmStatic var dateProvider: DateProvider = DateProvider.SYSTEM

  private val EPOCH = LocalDate.ofEpochDay(0)

  // the gate is used to ensure settings are accessed single-threaded
  private val gate = Any()

  @JvmStatic private var instance: AnalyticsSettingsData? = null

  /**
   * Gets the current salt skew, this is used by [.getSalt] to update the salt every 28 days with a
   * consistent window. This window size allows 4 week and 1 week analyses.
   */
  @VisibleForTesting
  @JvmStatic
  fun currentSaltSkew(): Int {
    val now = LocalDate.from(Instant.ofEpochMilli(dateProvider.now().time).atZone(ZoneOffset.UTC))
    // Unix epoch was on a Thursday, but we want Monday to be the day the salt is refreshed.
    val days = ChronoUnit.DAYS.between(EPOCH, now) + 3
    return (days / 28).toInt()
  }

  /* Returns true if the user has already been prompted to opt in to metrics
   * for this major release
   */
  @JvmStatic
  public fun hasUserBeenPromptedForOptin(
    currentMajorVersion: String,
    currentMinorVersion: String,
  ): Boolean {
    val currentMajor = currentMajorVersion.toIntOrNull() ?: return true
    val currentMinor = currentMinorVersion.toIntOrNull() ?: return true

    val last = instance?.lastOptinPromptVersion ?: return false

    val tokens = last.split('.')
    if (tokens.size != 2) {
      return false
    }

    val lastMajor = tokens[0].toIntOrNull() ?: return false
    val lastMinor = tokens[1].toIntOrNull() ?: return false

    return when (currentMajor) {
      lastMajor -> currentMinor <= lastMinor
      else -> currentMajor <= lastMajor
    }
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
    if (file.exists()) {
      val channel = RandomAccessFile(file, "rw").channel
      try {
        channel.tryLock().use {
          AnalyticsSettingsData.parseSettingsData(channel, file, logger)?.let {
            if (isValid(it)) {
              return it
            }
          }
        }
      } catch (e: OverlappingFileLockException) {
        logger.warning("Unable to lock settings file %s: %s", file.toString(), e)

        val newSettings = AnalyticsSettingsData()
        newSettings.userId = UUID.randomUUID().toString()
        return newSettings
      }
    }

    return createNewAnalyticsSettingsData(logger)
  }

  fun resetUserId() {
    runIfAnalyticsSettingsUsable(Unit) {
      instance?.userId = UUID.randomUUID().toString()
      saveSettings()
    }
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
  private fun createNewAnalyticsSettingsData(logger: ILogger): AnalyticsSettingsData {
    val settings = AnalyticsSettingsData()

    val uidFile = Paths.get(AnalyticsPaths.getAndEnsureAndroidSettingsHome(), "uid.txt").toFile()
    if (uidFile.exists()) {
      try {
        val uid = Files.readFirstLine(uidFile, Charsets.UTF_8)
        settings.userId = uid
      } catch (e: IOException) {
        // Ignore and set new UID.
      }
    }
    if (settings.userId == null) {
      settings.userId = UUID.randomUUID().toString()
    }
    settings.saveSettings(logger)
    return settings
  }

  @JvmStatic var googlePlayDateProvider: WebServerDateProvider? = null

  /**
   * Get or creates an instance of the settings. Uses the following strategies in order:
   * * Use existing instance
   * * Load existing 'analytics.settings' file from disk
   * * Create new 'analytics.settings' file
   * * Create instance without persistence
   *
   * Any issues reading/writing the config file will be logged to the logger.
   */
  @JvmStatic
  @JvmOverloads
  fun initialize(
    logger: ILogger,
    scheduler: ScheduledExecutorService? = null,
    environment: Environment? = null,
  ) {
    synchronized(gate) {
      try {
        environment?.let { Environment.instance = environment }
        if (instance != null) {
          return
        }
        initialized = true
        instance = loadSettingsData(logger)
      } catch (e: IOException) {
        // null out metrics in case of failure to load.
        initialized = true
        instance = AnalyticsSettingsData()
        logger.warning(
          "Unable to initialize metrics, ensure %s is writable, details: %s",
          AnalyticsPaths.getAndEnsureAndroidSettingsHome(),
          e.message,
        )
      }
    }
    scheduler?.submit {
      try {
        val gp = WebServerDateProvider(URL("http://play.google.com/"))
        dateProvider = gp
        googlePlayDateProvider = gp
      } catch (_: IOException) {
        logger.warning(
          "Unable to get current time from Google's servers, using local system time instead."
        )
      }
    }
  }

  /** initializes or updates AnalyticsSettings into a disabled state. */
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
   * Allows test to set a custom version of the AnalyticsSettings to test different setting states.
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

  private const val ANALYTICS_SETTINGS = "analytics.settings"

  /**
   * Helper to get the file to read/write settings from based on the configured android settings
   * home.
   */
  internal val settingsFile: File
    get() = Paths.get(AnalyticsPaths.getAndEnsureAndroidSettingsHome(), ANALYTICS_SETTINGS).toFile()

  /**
   * Gets a binary blob to ensure per user anonymization. Gets automatically rotated every 28 days.
   * Primarily used by [Anonymizer].
   */
  val salt: ByteArray
    @Throws(IOException::class)
    get() =
      synchronized(AnalyticsSettings.gate) {
        val data: AnalyticsSettingsData = instance ?: return byteArrayOf()
        // Starting with Android Studio 3.5 we switch to 532 day rotation, this logic is to coincide
        // with that change
        // starting with the rotation on 2018/11/26. 28*19 = 532 days and -11 is to offset with the
        // 28 day rotation cycle starting on
        // 2018/11/26.
        val dataSkew532: Int = (data.saltSkew - 11) / 19
        val currentSaltSkew = com.android.tools.analytics.AnalyticsSettings.currentSaltSkew()
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
   *
   * @throws IOException if there are any issues writing the settings file.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun saveSettings() {
    runIfAnalyticsSettingsUsable(Unit) { instance?.saveSettings() }
  }

  /** Checks if the AnalyticsSettings object is in a valid state. */
  internal fun isValid(settings: AnalyticsSettingsData): Boolean {
    return settings.userId != null &&
      (settings.saltSkew == AnalyticsSettings.SALT_SKEW_NOT_INITIALIZED ||
        settings.saltValue != null)
  }

  fun daysInYear(): Int {
    val calendar = Calendar.getInstance()
    calendar.time = dateProvider.now()
    return if (GregorianCalendar().isLeapYear(calendar[Calendar.YEAR])) DAYS_IN_LEAP_YEAR
    else DAYS_IN_NON_LEAP_YEAR
  }
}

class AnalyticsSettingsData {

  fun saveSettings(logger: ILogger? = null) {
    val file = AnalyticsSettings.settingsFile
    val dir = file.parentFile
    if (!dir.exists()) {
      dir.mkdirs()
    }
    try {
      RandomAccessFile(file, "rw").use { settingsFile ->
        settingsFile.channel.use { channel ->
          channel.tryLock().use { lock ->
            if (lock == null) {
              throw IOException("Unable to lock settings file $file")
            }
            val existingData = parseSettingsData(channel, file, null)
            if (existingData?.saltSkew == saltSkew) {
              // The salt is apparently updated by some other process. In this case we read that on
              // the disk rather than using our own in
              // order to make sure all processes use the same salt.
              saltValue = existingData.saltValue
            }
            channel.truncate(0)
            val outputStream = Channels.newOutputStream(channel)
            val writer = OutputStreamWriter(outputStream)
            DataTypeAdapter.write(JsonWriter(writer), this)
            writer.flush()
            outputStream.flush()
          }
        }
      }
    } catch (e: OverlappingFileLockException) {
      throw IOException("Unable to lock settings file $file", e)
    }
  }

  /** User id used for reporting analytics. This id is pseudo-anonymous. */
  var userId: String? = null
  var optedIn: Boolean = false
  var debugDisablePublishing: Boolean = false
    private set

  var saltValue = BigInteger.valueOf(0L)
  var saltSkew = AnalyticsSettings.SALT_SKEW_NOT_INITIALIZED
  var lastSentimentQuestionDate: Date? = null
  var lastSentimentAnswerDate: Date? = null
  var nextFeatureSurveyDate: Date? = null
  var nextFeatureSurveyDateMap: MutableMap<String, Date>? = null
  var lastOptinPromptVersion: String? = null
  var popSentimentQuestionFrequency: Int = 0

  companion object {

    fun parseSettingsData(
      channel: FileChannel,
      file: File,
      logger: ILogger? = null,
    ): AnalyticsSettingsData? {
      if (channel.size() == 0L) return null
      val inputStream = Channels.newInputStream(channel)
      return try {
        val reader = JsonReader(InputStreamReader(inputStream))
        reader.isLenient = true
        DataTypeAdapter.read(reader)
      } catch (t: Throwable) {
        logger?.warning("Unable to parse settings file %s: %s", file.toString(), t)
        null
      }
    }
  }

  internal object DataTypeAdapter : TypeAdapter<AnalyticsSettingsData>() {

    private val datePatternJava8 = SimpleDateFormat("MMM d, y h:mm:ss a", Locale.US)

    override fun write(writer: JsonWriter, data: AnalyticsSettingsData) {
      writer.beginObject()
      data.userId?.let { writer.name("userId").value(it) }
      writer.name("hasOptedIn").value(data.optedIn)
      writer.name("debugDisablePublishing").value(data.debugDisablePublishing)
      writer.name("saltValue").value(data.saltValue)
      writer.name("saltSkew").value(data.saltSkew)
      data.lastSentimentQuestionDate?.let {
        writer.name("lastSentimentQuestionDate").value(format(it))
      }
      data.lastSentimentAnswerDate?.let { writer.name("lastSentimentAnswerDate").value(format(it)) }
      data.nextFeatureSurveyDate?.let { writer.name("lastFeatureSurveyDate").value(format(it)) }
      data.nextFeatureSurveyDateMap?.let {
        writer.name("lastFeatureSurveyDateMap")
        writer.beginObject()
        it.forEach { (key, value) -> writer.name(key).value(format(value)) }
        writer.endObject()
      }
      data.lastOptinPromptVersion?.let { writer.name("lastOptinPromptVersion").value(it) }
      writer.endObject()
    }

    // Write out using pre-Java9 date format to let older releases read the file correctly.
    private fun format(it: Date): String = datePatternJava8.format(it)

    override fun read(reader: JsonReader): AnalyticsSettingsData {
      val data = AnalyticsSettingsData()
      if (!reader.hasNext()) {
        return data
      }
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "userId" -> data.userId = reader.nextString()
          "hasOptedIn" -> data.optedIn = reader.nextBoolean()
          "debugDisablePublishing" -> data.debugDisablePublishing = reader.nextBoolean()
          "saltValue" -> data.saltValue = BigInteger(reader.nextString())
          "saltSkew" -> data.saltSkew = reader.nextInt()
          "lastSentimentQuestionDate" ->
            data.lastSentimentQuestionDate = parseDate(reader.nextString())
          "lastSentimentAnswerDate" -> data.lastSentimentAnswerDate = parseDate(reader.nextString())
          "lastFeatureSurveyDate" -> data.nextFeatureSurveyDate = parseDate(reader.nextString())
          "lastFeatureSurveyDateMap" -> {
            val map = data.nextFeatureSurveyDateMap ?: mutableMapOf()
            reader.beginObject()
            while (reader.hasNext()) {
              map[reader.nextName()] = parseDate(reader.nextString())
            }
            reader.endObject()
            data.nextFeatureSurveyDateMap = map
          }
          "lastOptinPromptVersion" -> data.lastOptinPromptVersion = reader.nextString()
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      return data
    }

    private fun parseDate(string: String): Date {
      return datePatternJava8.parse(string)
    }
  }
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
