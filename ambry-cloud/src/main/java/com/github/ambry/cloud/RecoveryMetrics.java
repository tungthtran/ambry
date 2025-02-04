/**
 * Copyright 2024 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.github.ambry.cloud;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;


public class RecoveryMetrics {
  public final Counter recoveryTokenError;
  public final Counter listBlobsError;
  public final Counter metadataError;
  public final Counter recoveryRequestError;
  public final Meter listBlobsSuccessRate;

  public RecoveryMetrics(MetricRegistry registry) {
    recoveryTokenError = registry.counter(MetricRegistry.name(RecoveryMetrics.class, "RecoveryTokenError"));
    listBlobsError = registry.counter(MetricRegistry.name(RecoveryMetrics.class, "ListBlobsError"));
    metadataError = registry.counter(MetricRegistry.name(RecoveryMetrics.class, "MetadataError"));
    recoveryRequestError = registry.counter(MetricRegistry.name(RecoveryMetrics.class, "RecoveryRequestError"));
    listBlobsSuccessRate = registry.meter(MetricRegistry.name(RecoveryMetrics.class, "ListBlobsSuccessRate"));
  }
}
