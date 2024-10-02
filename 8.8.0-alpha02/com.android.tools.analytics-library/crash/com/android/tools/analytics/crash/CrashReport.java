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
import com.android.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.entity.mime.MultipartEntityBuilder;

public abstract class CrashReport {
  @NonNull private final String productId;
  @Nullable private final String version;
  @Nullable private final Map<String, String> productData;
  @NonNull private final String type;

  public CrashReport(
      @NonNull String productId,
      @Nullable String version,
      @Nullable Map<String, String> productData,
      @NonNull String type) {
    this.productId = productId;
    this.version = version;
    this.productData = productData;
    this.type = type;
  }

  public void serialize(@NonNull MultipartEntityBuilder builder) {
    builder.addTextBody("type", type);

    if (productData != null) {
      productData.forEach(builder::addTextBody);
    }

    serializeTo(builder);
  }

  @NonNull
  public String getProductId() {
    return productId;
  }

  @NonNull
  public String getType() {
    return type;
  }

  @Nullable
  public String getVersion() {
    return version;
  }

  protected abstract void serializeTo(@NonNull MultipartEntityBuilder builder);

  /** Report can alter default parameters before they are sent out. */
  protected void overrideDefaultParameters(Map<String, String> parameters) {}

  public abstract static class BaseBuilder<T extends CrashReport, B extends BaseBuilder> {
    private String productId;
    private String version;
    private Map<String, String> productData;

    @NonNull
    public B setProduct(@NonNull String productId) {
      this.productId = productId;
      return getThis();
    }

    @NonNull
    public B setVersion(@NonNull String version) {
      this.version = version;
      return getThis();
    }

    @NonNull
    public B addProductData(@NonNull Map<String, String> kv) {
      if (productData == null) {
        productData = new HashMap<>();
      }

      productData.putAll(kv);
      return getThis();
    }

    protected String getProductId() {
      return productId;
    }

    protected String getVersion() {
      return version;
    }

    protected Map<String, String> getProductData() {
      return productData;
    }

    protected abstract B getThis();

    public abstract T build();
  }
}
