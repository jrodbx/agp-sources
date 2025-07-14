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
package com.android.tools.analytics;

import com.google.wireless.android.sdk.stats.PercentileBucket;
import com.google.wireless.android.sdk.stats.PercentileEstimator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Percentiles creates an estimation of the value at target percentiles from a data stream. It is
 * based on the P-square algorithm found at:
 * http://pierrechainais.ec-lille.fr/Centrale/Option_DAD/IMPACT_files/Dynamic%20quantiles%20calcultation%20-%20P2%20Algorythm.pdf
 * with extensions to allow monitoring multiple percentiles and merging estimations.
 */
public class Percentiles {

  private double[] mInitialData;

  private double[] mTargets;

  private Bucket[] mBuckets;

  private long mCount = 0;

  private final int mNumBuckets;

  private final int mRawDataSize;

  /**
   * Construct an empty estimator
   *
   * @param targets Percentiles to monitor.
   * @param rawDataSize Number of samples before interpolating
   */
  public Percentiles(double[] targets, int rawDataSize) {
    mTargets = Arrays.copyOf(targets, targets.length);
    Arrays.sort(mTargets);
    mNumBuckets = mTargets.length * 2 + 3;
    mRawDataSize = Math.max(rawDataSize, mNumBuckets);
    mInitialData = new double[mRawDataSize];
  }

  /** Adds a sample to the estimator, updating buckets if necessary. */
  public void addSample(double sample) {
    if (mCount < mRawDataSize) {
      mInitialData[(int) mCount++] = sample;
      return;
    } else if (mCount == mRawDataSize) {
      createBuckets();
    }

    mCount++;

    if (mBuckets[0].value > sample) {
      mBuckets[0].value = sample;
    }
    if (mBuckets[mNumBuckets - 1].value < sample) {
      mBuckets[mNumBuckets - 1].value = sample;
    }

    for (int i = 1; i < mNumBuckets - 1; i++) {
      mBuckets[i].optimalCount += mBuckets[i].target;
      if (mBuckets[i].value > sample) {
        mBuckets[i].count++;
      }
    }

    mBuckets[mNumBuckets - 1].optimalCount += mBuckets[mNumBuckets - 1].target;
    mBuckets[mNumBuckets - 1].count++;

    interpolateIfNecessary();
  }

  /**
   * Gets the estimated value at a percentile.
   *
   * @param target Requested percentile.
   * @return Approximate value for the requested percentile
   */
  public double getApproximateValue(double target) {
    if (mBuckets == null) {
      // We didn't have enough data to interpolate yet.
      assert (mInitialData != null);
      if (mCount == 0) {
        return Double.NaN;
      } else {
        Arrays.sort(mInitialData);
        return mInitialData[(int) (mCount * target)];
      }
    }
    for (Bucket b : mBuckets) {
      if (b.target == target) {
        return b.value;
      }
    }
    return Double.NaN;
  }

  public static class MergeException extends Exception {

    public MergeException(String s) {
      super(s);
    }
  }

  /**
   * Merges many estimators to form a new estimator.
   *
   * @param targets Percentiles in the new estimator.
   * @param toMerge List of estimators to merge into the new estimator.
   * @param rawDataSize Number of raw samples to store, only relevant if no estimator in toMerge is
   *     interpolated.
   */
  public static Percentiles merge(double[] targets, List<Percentiles> toMerge, int rawDataSize)
      throws MergeException {
    Percentiles p = new Percentiles(targets, rawDataSize);
    p.mBuckets = new Bucket[p.mNumBuckets];
    int currentBucket = 1;

    double[] markers = computeNonExtremeMarkers(targets);

    double minValue = Double.POSITIVE_INFINITY;
    double maxValue = Double.NEGATIVE_INFINITY;
    long totalCount = 0;

    PriorityQueue<MergeHelper> queue = new PriorityQueue<>(toMerge.size());

    ArrayList<Percentiles> uninterpolatedEstimators = new ArrayList<>();
    for (Percentiles input : toMerge) {
      if (input.mBuckets == null) {
        uninterpolatedEstimators.add(input);
      } else {
        queue.add(new MergeHelper(input));
        minValue = Math.min(minValue, input.mBuckets[0].value);
        maxValue = Math.max(maxValue, input.mBuckets[input.mNumBuckets - 1].value);
        totalCount += input.mCount;
      }
    }

    p.mBuckets[0] = new Bucket(0.0, minValue, 0, totalCount);
    p.mBuckets[p.mNumBuckets - 1] = new Bucket(1.0, maxValue, totalCount, totalCount);
    p.mCount = totalCount;

    if (totalCount > p.mRawDataSize) {
      p.mInitialData = null;
    } else if (!queue.isEmpty()) {
      // I think it's fine to just fail here. We don't want to return an estimator that is of lower
      // quality than requested..
      throw new MergeException("At least one interpolated estimator, but not of large enough size");
    } else {
      // All had just raw data. Just create a new estimator and add all the samples individually.
      return mergeFromRaw(new Percentiles(targets, rawDataSize), uninterpolatedEstimators);
    }

    long countFromConsumedEstimators = 0;

    for (double targetMarker : markers) {
      long targetCount = (long) (targetMarker * totalCount);
      long lastCount = 0;
      double lastMarkerValue = minValue;

      // Iterate through markers by value, estimating the count at each one. Once the estimated
      // count is too high for the target,
      // linearly interpolate the value for the marker.
      // Note that iterating over a PriorityQueue is not in any specified order, so is cheap.
      while (!queue.isEmpty()) {
        MergeHelper m = queue.poll();

        double markerValue = m.value();
        long countAtValue = m.count() + countFromConsumedEstimators;
        for (MergeHelper h : queue) {
          countAtValue += h.estimatedCountAtValue(markerValue);
        }

        if (countAtValue < targetCount) {
          lastCount = countAtValue;
          lastMarkerValue = markerValue;
          if (m.increment()) {
            queue.add(m);
          } else {
            countFromConsumedEstimators += m.p.mCount;
          }
          continue;
        }

        if (countAtValue == targetCount) {
          // We got really lucky, and this is the value we should set the target marker to.
          p.mBuckets[currentBucket++] =
              new Bucket(targetMarker, markerValue, countAtValue, totalCount);
          if (m.increment()) {
            queue.add(m);
          } else {
            countFromConsumedEstimators += m.p.mCount;
          }
          break;
        }

        // countAtValue > targetCount
        // Note that we do not move to the next marker in m here, since it's current segment could
        // span > 1 target percentile
        queue.add(m);

        // Linear interpolate value for the target marker.
        double ratio = ((double) (targetCount - lastCount)) / ((double) (countAtValue - lastCount));
        double estimatedMarkerValue = lastMarkerValue + (markerValue - lastMarkerValue) * ratio;

        p.mBuckets[currentBucket++] =
            new Bucket(targetMarker, estimatedMarkerValue, targetCount, totalCount);

        break;
      }

      assert (!queue.isEmpty());
    }

    return mergeFromRaw(p, uninterpolatedEstimators);
  }

  public PercentileEstimator export() {
    PercentileEstimator.Builder builder = PercentileEstimator.newBuilder();
    if (mBuckets == null) {
      for (int i = 0; i < mCount; ++i) {
        builder.addRawSample(mInitialData[i]);
      }
    } else {
      for (Bucket b : mBuckets) {
        builder
            .addBucketBuilder()
            .setTargetPercentile(b.target)
            .setValue(b.value)
            .setCount(b.count);
      }
    }
    return builder.build();
  }

  public static class MismatchedTargetsException extends Exception {

    public MismatchedTargetsException(String s) {
      super(s);
    }
  }

  public static Percentiles fromProto(PercentileEstimator e, double[] targets, int rawDataSize)
      throws MismatchedTargetsException {
    Percentiles r = new Percentiles(targets, rawDataSize);

    if (e.getBucketCount() > 0) {
      double[] markers = computeNonExtremeMarkers(targets);
      assert (markers.length == r.mNumBuckets - 2);
      if (r.mNumBuckets != e.getBucketCount()) {
        throw new MismatchedTargetsException("Mismatched target lengths");
      }
      if (e.getBucket(0).getTargetPercentile() != 0.0) {
        throw new MismatchedTargetsException("First bucket target percentile was not 0.0");
      }
      if (e.getBucket(e.getBucketCount() - 1).getTargetPercentile() != 1.0) {
        throw new MismatchedTargetsException("Last bucket target percentile was not 1.0");
      }
      for (int i = 1; i < r.mNumBuckets - 1; ++i) {
        PercentileBucket b = e.getBucket(i);
        if (b.getTargetPercentile() != markers[i - 1]) {
          throw new MismatchedTargetsException("Mismatched targets at index " + i);
        }
      }
    }

    if (e.getRawSampleCount() > 0) {
      assert (e.getBucketCount() == 0);
      for (int i = 0; i < e.getRawSampleCount(); ++i) {
        r.addSample(e.getRawSample(i));
      }
      return r;
    } else if (e.getBucketCount() > 0) {
      r.mInitialData = null;
      r.mBuckets = new Bucket[e.getBucketCount()];
      r.mCount = e.getBucket(e.getBucketCount() - 1).getCount();
      for (int i = 0; i < e.getBucketCount(); ++i) {
        PercentileBucket bucket = e.getBucket(i);
        r.mBuckets[i] =
            new Bucket(
                bucket.getTargetPercentile(), bucket.getValue(), bucket.getCount(), r.mCount);
      }
    }
    return r;
  }

  /** Creates buckets for each marker with values and counts based on samples received so far. */
  private void createBuckets() {
    Arrays.sort(mInitialData);
    mBuckets = new Bucket[mNumBuckets];
    double last = 0.0;
    mBuckets[0] = new Bucket(0.0, mInitialData[0], 0, mRawDataSize);
    int currentBucketIndex = 1;
    for (double t : mTargets) {
      double target = (last + t) / 2;
      int index = (int) (target * mRawDataSize);
      mBuckets[currentBucketIndex] = new Bucket(target, mInitialData[index], index, mRawDataSize);
      currentBucketIndex++;
      target = t;
      index = (int) (target * mRawDataSize);
      mBuckets[currentBucketIndex] = new Bucket(target, mInitialData[index], index, mRawDataSize);
      currentBucketIndex++;
      last = t;
    }
    assert (currentBucketIndex == mTargets.length * 2 + 1);
    double target = (1.0 + last) / 2;
    int index = (int) (target * mRawDataSize);
    mBuckets[currentBucketIndex] = new Bucket(target, mInitialData[index], index, mRawDataSize);
    currentBucketIndex++;
    mBuckets[currentBucketIndex] =
        new Bucket(1.0, mInitialData[mRawDataSize - 1], mRawDataSize, mRawDataSize);
    assert (currentBucketIndex + 1 == mNumBuckets);

    mInitialData = null;
  }

  private void interpolateIfNecessary() {
    for (int i = 1; i < mNumBuckets - 1; i++) {
      Bucket b = mBuckets[i];
      Bucket prev = mBuckets[i - 1];
      Bucket next = mBuckets[i + 1];
      double delta = b.optimalCount - b.count;
      if (delta < -1.0 && prev.count - b.count < -1) {
        update(b, prev, next, -1.0);
      } else if (delta > 1.0 && next.count - b.count > 1) {
        update(b, prev, next, 1.0);
      }
    }
  }

  private static void update(Bucket b, Bucket prev, Bucket next, double d) {
    // First try to update using quadratic interpolation.
    double numerator =
        ((b.count - prev.count + d) * (next.value - b.value) / (next.count - b.count))
            + ((next.count - b.count - d) * (b.value - prev.value) / (b.count - prev.count));
    double newValue = b.value + d * numerator / (next.count - prev.count);
    if (prev.value < newValue && newValue < next.value) {
      b.value = newValue;
    } else {
      // Linear interpolation instead..
      if (d < 0) {
        newValue = b.value - (b.value - prev.value) / (b.count - prev.count);
      } else {
        newValue = b.value + (next.value - b.value) / (next.count - b.count);
      }
      b.value = newValue;
    }
    b.count += (long) d;
  }

  private static class Bucket {

    public Bucket(double target, double value, long count, long totalCount) {
      this.target = target;
      this.value = value;
      this.count = count;
      optimalCount = totalCount * target;
    }

    public double target;

    public double value;

    public long count;

    public double optimalCount;
  }

  static class MergeHelper implements Comparable<MergeHelper> {

    private Percentiles p;

    private int markerIndex = 0;

    private double segmentLinearRate = 0.0;

    private double segmentStartValue = Double.NEGATIVE_INFINITY;

    private long segmentStartCount = Long.MIN_VALUE;

    public MergeHelper(Percentiles p) {
      this.p = p;
      increment();
    }

    public boolean done() {
      return markerIndex >= p.mBuckets.length;
    }

    public long estimatedCountAtValue(double value) {
      if (value < segmentStartValue) {
        return 0;
      }
      return segmentStartCount + (long) ((value - segmentStartValue) / segmentLinearRate);
    }

    public boolean increment() {
      markerIndex++;
      if (!done()) {
        segmentStartValue = p.mBuckets[markerIndex - 1].value;
        segmentStartCount = p.mBuckets[markerIndex - 1].count;
        segmentLinearRate =
            (p.mBuckets[markerIndex].value - segmentStartValue)
                / (p.mBuckets[markerIndex].count - segmentStartCount);
        return true;
      }
      return false;
    }

    public double target() {
      return p.mBuckets[markerIndex].target;
    }

    public double value() {
      return p.mBuckets[markerIndex].value;
    }

    public long count() {
      return p.mBuckets[markerIndex].count;
    }

    @Override
    public int compareTo(MergeHelper o) {
      return (int) Math.signum(value() - o.value());
    }
  }

  private static double[] computeNonExtremeMarkers(double[] targets) {
    double[] ret = new double[2 * targets.length + 1];
    int c = 0;
    double last = 0.0;
    for (double t : targets) {
      ret[c++] = (last + t) / 2;
      ret[c++] = t;
      last = t;
    }
    ret[c] = (last + 1.0) / 2;
    return ret;
  }

  private static Percentiles mergeFromRaw(Percentiles p, List<Percentiles> raw) {
    for (Percentiles input : raw) {
      for (int i = 0; i < input.mCount; ++i) {
        p.addSample(input.mInitialData[i]);
      }
    }
    return p;
  }
}
