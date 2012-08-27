package org.avaje.metric.core;

import org.avaje.metric.Clock;
import org.avaje.metric.CounterMetric;
import org.avaje.metric.Metric;
import org.avaje.metric.MetricName;

public class CounterMetricFactory implements MetricFactory {

  @Override
  public Metric createMetric(MetricName name, Clock clock) {

    return new CounterMetric(name);
  }

}
