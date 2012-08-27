package org.avaje.metric.core;

import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.ObjectName;

import org.avaje.metric.Clock;
import org.avaje.metric.CounterMetric;
import org.avaje.metric.LoadMetric;
import org.avaje.metric.Metric;
import org.avaje.metric.MetricName;
import org.avaje.metric.MetricNameCache;
import org.avaje.metric.TimedMetric;
import org.avaje.metric.ValueMetric;
import org.avaje.metric.jvm.GarbageCollectionRateCollection;

public class DefaultMetricManager {

  private final String monitor = new String();

  private final ConcurrentHashMap<String, Metric> concMetricMap = new ConcurrentHashMap<String, Metric>();

  private final Timer timer = new Timer("MetricManager", true);

  private final JmxMetricRegister jmxRegistry = new JmxMetricRegister();

  private final MetricFactory timedMetricFactory = new TimedMetricFactory();
  private final MetricFactory counterMetricFactory = new CounterMetricFactory();
  private final MetricFactory loadMetricFactory = new LoadMetricFactory();
  private final MetricFactory valueMetricFactory = new ValueMetricFactory();

  private final LoadMetric[] gcLoadMetrics;

  private final ConcurrentHashMap<String, MetricNameCache> nameCache = new ConcurrentHashMap<String, MetricNameCache>();

  public DefaultMetricManager() {
    timer.scheduleAtFixedRate(new UpdateStatisticsTask(), 5 * 1000, 2 * 1000);

    GarbageCollectionRateCollection gc = new GarbageCollectionRateCollection(timer);
    gcLoadMetrics = gc.getGarbageCollectorsLoadMetrics();
    registerGcMetrics();
  }

  private class UpdateStatisticsTask extends TimerTask {

    @Override
    public void run() {
      updateStatistics();
    }
  }

  public MetricNameCache getMetricNameCache(Class<?> klass) {
    return getMetricNameCache(new MetricName(klass, null));
  }
  
  public MetricNameCache getMetricNameCache(MetricName baseName) {
   
    String key = baseName.getMBeanName();
    MetricNameCache metricNameCache = nameCache.get(key);
    if (metricNameCache == null) {
      metricNameCache = new MetricNameCache(baseName);
      MetricNameCache oldNameCache = nameCache.putIfAbsent(key, metricNameCache);
      if (oldNameCache != null) {
        return oldNameCache;
      }
    }
    return metricNameCache;
  }

  public void updateStatistics() {
    Collection<Metric> allMetrics = getAllMetrics();
    for (Metric metric : allMetrics) {
      metric.updateStatistics();
    }
  }

  public TimedMetric getTimedMetric(MetricName name, Clock clock) {
    return (TimedMetric) getMetric(name, clock, timedMetricFactory);
  }

  public CounterMetric getCounterMetric(MetricName name) {
    return (CounterMetric) getMetric(name, null, counterMetricFactory);
  }
  
  public LoadMetric getLoadMetric(MetricName name) {
    return (LoadMetric) getMetric(name, null, loadMetricFactory);
  }

  public ValueMetric getValueMetric(MetricName name) {
    return (ValueMetric) getMetric(name, null, valueMetricFactory);
  }

  private void registerGcMetrics() {
    for (LoadMetric m : gcLoadMetrics) {
      String cacheKey = m.getName().getMBeanName();
      concMetricMap.put(cacheKey, m);
    }
  }

  private Metric getMetric(MetricName name, Clock clock, MetricFactory factory) {

    String cacheKey = name.getMBeanName();
    // try lock free get first
    Metric metric = concMetricMap.get(cacheKey);
    if (metric == null) {
      synchronized (monitor) {
        // use synchronised block
        metric = concMetricMap.get(cacheKey);
        if (metric == null) {
          metric = factory.createMetric(name, clock);
          concMetricMap.put(cacheKey, metric);
        }
      }
    }
    return metric;
  }

  public void clear() {
    synchronized (monitor) {
      Collection<Metric> values = concMetricMap.values();
      for (Metric metric : values) {
        jmxRegistry.unregister(metric.getName().getMBeanObjectName());
        if (metric instanceof TimedMetric) {
          ObjectName errorMBeanName = ((TimedMetric) metric).getErrorMBeanName();
          jmxRegistry.unregister(errorMBeanName);
        }

      }
      concMetricMap.clear();
    }
  }

  public Collection<Metric> getAllMetrics() {
    synchronized (monitor) {
      return concMetricMap.values();
    }
  }



}
