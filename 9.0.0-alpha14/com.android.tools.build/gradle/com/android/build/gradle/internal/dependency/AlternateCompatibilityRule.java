/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.build.api.attributes.BuildTypeAttr;
import com.android.build.api.attributes.ProductFlavorAttr;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.Named;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;

/** alternate-based Compat rule to handle the different values of attributes. */
public class AlternateCompatibilityRule<T extends Named> implements AttributeCompatibilityRule<T> {

    @NonNull private final Map<String, List<String>> alternates;

    protected AlternateCompatibilityRule(@NonNull Map<String, List<String>> alternates) {
        this.alternates = alternates;
    }

    @Override
    public void execute(CompatibilityCheckDetails<T> details) {
        final T producerValue = details.getProducerValue();
        final T consumerValue = details.getConsumerValue();
        if (producerValue.equals(consumerValue)) {
            details.compatible();
        } else {
            List<String> alternatesForValue = alternates.get(consumerValue.getName());
            if (alternatesForValue != null
                    && alternatesForValue.contains(producerValue.getName())) {
                details.compatible();
            }
        }
    }

    public static class BuildTypeRule extends AlternateCompatibilityRule<BuildTypeAttr> {

        @Inject
        public BuildTypeRule(@NonNull Map<String, List<String>> alternates) {
            super(alternates);
        }
    }

    public static class ProductFlavorRule extends AlternateCompatibilityRule<ProductFlavorAttr> {

        @Inject
        public ProductFlavorRule(@NonNull Map<String, List<String>> alternates) {
            super(alternates);
        }
    }
}
