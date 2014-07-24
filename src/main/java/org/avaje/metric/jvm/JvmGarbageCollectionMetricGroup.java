package org.avaje.metric.jvm;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

import org.avaje.metric.GaugeCounter;
import org.avaje.metric.GaugeCounterMetricGroup;
import org.avaje.metric.MetricName;
import org.avaje.metric.core.DefaultGaugeCounterMetric;
import org.avaje.metric.core.DefaultGaugeCounterMetricGroup;
import org.avaje.metric.core.DefaultMetricName;

/**
 * Collect statistics on the rate of garbage collection.
 */
public final class JvmGarbageCollectionMetricGroup {

  private static String[] names = { "count", "time" };

  public static GaugeCounterMetricGroup[] createGauges() {

    List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();

    DefaultGaugeCounterMetricGroup[] metricGroups = new DefaultGaugeCounterMetricGroup[garbageCollectorMXBeans.size()];

    for (int i = 0; i < garbageCollectorMXBeans.size(); i++) {
      GarbageCollectorMXBean gcMXBean = garbageCollectorMXBeans.get(i);

      // modify collector name replacing spaces with hyphens.
      String gcName = gcMXBean.getName();
      gcName = gcName.toLowerCase().replace(' ', '-');
      
      DefaultMetricName baseName = DefaultMetricName.createBaseName("jvm.gc", gcName);

      GaugeCounter[] gauges = new Collector(gcMXBean).getGauges();
      DefaultGaugeCounterMetric[] group = new DefaultGaugeCounterMetric[gauges.length];

      for (int j = 0; j < gauges.length; j++) {
        MetricName metricName = baseName.deriveWithName(names[j]);
        group[j] = DefaultGaugeCounterMetric.incrementing(metricName, gauges[j]);
      }

      metricGroups[i] = new DefaultGaugeCounterMetricGroup(baseName, group);
    }

    return metricGroups;
  }

  /**
   * A per garbage collector collector.
   */
  private static class Collector {

    final GarbageCollectorMXBean gcMXBean;
    final GaugeCounter[] gauges;

    Collector(GarbageCollectorMXBean gcMXBean) {
      this.gcMXBean = gcMXBean;
      this.gauges = new GaugeCounter[]{new Count(), new Time()};
    }

    public GaugeCounter[] getGauges() {
      return gauges;
    }

    class Count implements GaugeCounter {
      @Override
      public long getValue() {
        return gcMXBean.getCollectionCount();
      }
    }

    class Time implements GaugeCounter {
      @Override
      public long getValue() {
        return gcMXBean.getCollectionTime();
      }
    }
  }
}