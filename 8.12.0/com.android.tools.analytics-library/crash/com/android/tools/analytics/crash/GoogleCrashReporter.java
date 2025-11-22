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
package com.android.tools.analytics.crash;

import com.android.annotations.NonNull;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.entity.GzipCompressingEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * {@link GoogleCrashReporter} provides APIs to upload crash reports to Google crash reporting
 * service.
 *
 * @see <a href="http://go/studio-g3doc/implementation/crash">Crash Backend</a> for more
 *     information.
 */
public class GoogleCrashReporter implements CrashReporter {

  // Send crashes during development to the staging backend
  private static final String CRASH_URL = "https://clients2.google.com/cr/report";
  private static final String STAGING_CRASH_URL = "https://clients2.google.com/cr/staging_report";

  private static final String SYSTEM_PROPERTY_USE_STAGING_CRASH_URL = "use.staging.crash.url";

  // Crash has a limit of 250 * 1024 bytes for field values
  private static final int MAX_BYTES_FOR_VALUE = 250 * 1024;
  private static final int MAX_BYTES_FOR_FILE = 1258291; // Crash limits uploaded files to 1.2MB
  private static final String TRUNCATION_INDICATOR = "[truncated]";

  private static final String LOCALE =
      Locale.getDefault() == null ? "unknown" : Locale.getDefault().toString();

  private static final int REJECTED_UPLOAD_TRIGGER_COUNT = 20;
  private static final AtomicInteger ourRejectedExecutionCount = new AtomicInteger();

  /**
   * Executor to use when uploading crash events. Earlier versions relied on the ForkJoin pool, but
   * this causes issues if we generate lots of exceptions within a short time - See
   * https://code.google.com/p/android/issues/detail?id=230109. This executor is configured such
   * that it only allows a maximum of 5 threads to ever be alive for the purpose of uploading
   * events, with a backlog of 30 more in the queue. If the queue is full, then subsequent
   * submissions to the queue are rejected.
   */
  private static final ExecutorService ourExecutor =
      new ThreadPoolExecutor(
          1,
          5,
          1,
          TimeUnit.MINUTES,
          new LinkedBlockingDeque<>(30),
          new ThreadFactoryBuilder().setDaemon(true).setNameFormat("google-crash-pool-%d").build(),
          (r, executor) -> {
            ourRejectedExecutionCount.incrementAndGet();
            if (ourRejectedExecutionCount.compareAndSet(REJECTED_UPLOAD_TRIGGER_COUNT, 0)) {
              Logger.getLogger(GoogleCrashReporter.class.getName())
                  .info(
                      "Lost " + REJECTED_UPLOAD_TRIGGER_COUNT + " crash events due to full queue.");
            }
          });

  // The standard keys expected by crash backend. The product id and version are required, others
  // are optional.
  protected static final String KEY_PRODUCT_ID = "productId";
  protected static final String KEY_VERSION = "version";

  // We allow reporting a max of 1 crash per minute
  private static final double MAX_CRASHES_PER_SEC = 1.0 / 60.0;

  private final boolean isUnitTestMode;
  private final boolean isDebugBuild;
  @NonNull private final String crashUrl;
  @NonNull private final UploadRateLimiter rateLimiter;

  public GoogleCrashReporter(boolean isUnitTestMode, boolean isDebugBuild) {
    this(
        (isUnitTestMode
                || isDebugBuild
                || java.lang.Boolean.getBoolean(SYSTEM_PROPERTY_USE_STAGING_CRASH_URL))
            ? STAGING_CRASH_URL
            : CRASH_URL,
        UploadRateLimiter.create(MAX_CRASHES_PER_SEC),
        isUnitTestMode,
        isDebugBuild);
  }

  @VisibleForTesting
  GoogleCrashReporter(
      @NonNull String crashUrl,
      @NonNull UploadRateLimiter rateLimiter,
      boolean isUnitTestMode,
      boolean isDebugBuild) {
    this.crashUrl = crashUrl;
    this.rateLimiter = rateLimiter;
    this.isUnitTestMode = isUnitTestMode;
    this.isDebugBuild = isDebugBuild;
  }

  @VisibleForTesting
  @NonNull
  public String getCrashUrl() {
    return crashUrl;
  }

  @Override
  @NonNull
  public CompletableFuture<String> submit(@NonNull CrashReport report) {
    return submit(report, false);
  }

  @Override
  @NonNull
  public CompletableFuture<String> submit(@NonNull CrashReport report, boolean skipLimiter) {
    if (!skipLimiter) { // non-user reported crash events are rate limited on the client side
      if (!rateLimiter.tryAcquire()) {
        CompletableFuture<String> f = new CompletableFuture<>();
        f.completeExceptionally(
            new RuntimeException("Exceeded Quota of crashes that can be reported"));
        return f;
      }
    }

    Map<String, String> parameters = getDefaultParameters();
    if (report.getVersion() != null) {
      parameters.put(KEY_VERSION, report.getVersion());
    }
    parameters.put(KEY_PRODUCT_ID, report.getProductId());
    report.overrideDefaultParameters(parameters);

    MultipartEntityBuilder builder = newMultipartEntityBuilderWithKv(parameters);
    report.serialize(builder);
    return submit(builder.build());
  }

  @NonNull
  @Override
  public CompletableFuture<String> submit(@NonNull Map<String, String> kv) {
    Map<String, String> parameters = getDefaultParameters();
    parameters.putAll(kv);
    return submit(newMultipartEntityBuilderWithKv(parameters).build());
  }

  @NonNull
  @Override
  public CompletableFuture<String> submit(@NonNull final HttpEntity requestEntity) {
    CompletableFuture<String> future = new CompletableFuture<>();

    try {
      ourExecutor.submit(
          () -> {
            try (CloseableHttpClient client = HttpClients.createSystem()) {
              HttpEntity entity = requestEntity;
              if (!isUnitTestMode) {
                // The test server used in testing doesn't handle gzip compression (netty requires
                // jcraft jzlib for gzip decompression)
                entity = new GzipCompressingEntity(requestEntity);
              }

              HttpPost post = new HttpPost(crashUrl);
              post.setEntity(entity);
              HttpResponse response = client.execute(post);
              StatusLine statusLine = response.getStatusLine();
              if (statusLine.getStatusCode() >= 300) {
                future.completeExceptionally(
                    new HttpResponseException(
                        statusLine.getStatusCode(), statusLine.getReasonPhrase()));
                return;
              }

              entity = response.getEntity();
              if (entity == null) {
                future.completeExceptionally(new NullPointerException("Empty response entity"));
                return;
              }

              String reportId = EntityUtils.toString(entity);
              if (isDebugBuild) {
                //noinspection UseOfSystemOutOrSystemErr
                System.out.println("Report submitted: http://go/crash-staging/" + reportId);
              }
              future.complete(reportId);
            } catch (IOException e) {
              future.completeExceptionally(e);
            }
          });
    } catch (RejectedExecutionException ignore) {
      // handled by the rejected execution handler associated with ourExecutor
    }

    return future;
  }

  @NonNull
  private static MultipartEntityBuilder newMultipartEntityBuilderWithKv(
      @NonNull Map<String, String> kv) {
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    kv.forEach((key, value) -> addBodyToBuilder(builder, key, value));
    return builder;
  }

  public static void addBodyToBuilder(MultipartEntityBuilder builder, String key, String value) {
    addBodyToBuilder(builder, key, value, ContentType.DEFAULT_TEXT);
  }

  /** Ensures fields too long for Crash are attached to the request as a file. */
  public static void addBodyToBuilder(
      MultipartEntityBuilder builder, String key, String value, ContentType contentType) {
    builder.addTextBody(
        key, Ascii.truncate(value, MAX_BYTES_FOR_VALUE, TRUNCATION_INDICATOR), contentType);
    // only upload the full text as a file if it will fit in the Crash file size limit - if it
    // doesn't,
    // Crash will discard all files attached to this report.
    if (value.length() > MAX_BYTES_FOR_VALUE && value.length() <= MAX_BYTES_FOR_FILE) {
      builder.addBinaryBody(key + "-full", value.getBytes(), contentType, key + ".txt");
    }
  }

  @NonNull
  private Map<String, String> getDefaultParameters() {
    Map<String, String> map = new HashMap<>();

    RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    map.put("ptime", Long.toString(runtimeMXBean.getUptime()));

    map.put("osName", Strings.nullToEmpty(StandardSystemProperty.OS_NAME.value()));
    map.put("osVersion", Strings.nullToEmpty(StandardSystemProperty.OS_VERSION.value()));
    map.put("osArch", Strings.nullToEmpty(StandardSystemProperty.OS_ARCH.value()));
    map.put("locale", Strings.nullToEmpty(LOCALE));

    map.put("vmName", Strings.nullToEmpty(runtimeMXBean.getVmName()));
    map.put("vmVendor", Strings.nullToEmpty(runtimeMXBean.getVmVendor()));
    map.put("vmVersion", Strings.nullToEmpty(runtimeMXBean.getVmVersion()));

    MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    map.put("heapUsed", Long.toString(usage.getUsed()));
    map.put("heapCommitted", Long.toString(usage.getCommitted()));
    map.put("heapMax", Long.toString(usage.getMax()));

    map.putAll(getProductSpecificParams());

    return map;
  }

  @NonNull
  protected Map<String, String> getProductSpecificParams() {
    return Collections.emptyMap();
  }
}
