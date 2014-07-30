package org.avaje.metric.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.avaje.metric.CounterMetric;
import org.avaje.metric.Gauge;
import org.avaje.metric.GaugeCounter;
import org.avaje.metric.GaugeCounterMetric;
import org.avaje.metric.GaugeCounterMetricGroup;
import org.avaje.metric.GaugeMetric;
import org.avaje.metric.Metric;
import org.avaje.metric.MetricName;
import org.avaje.metric.MetricNameCache;
import org.avaje.metric.TimedMetric;
import org.avaje.metric.TimedMetricGroup;
import org.avaje.metric.ValueMetric;
import org.avaje.metric.core.noop.NoopCounterMetricFactory;
import org.avaje.metric.core.noop.NoopTimedMetricFactory;
import org.avaje.metric.core.noop.NoopValueMetricFactory;
import org.avaje.metric.jvm.JvmGarbageCollectionMetricGroup;
import org.avaje.metric.jvm.JvmMemoryMetricGroup;
import org.avaje.metric.jvm.JvmSystemMetricGroup;
import org.avaje.metric.jvm.JvmThreadMetricGroup;
import org.avaje.metric.spi.PluginMetricManager;

/**
 * Default implementation of the PluginMetricManager.
 */
public class DefaultMetricManager implements PluginMetricManager {

  private final Object monitor = new Object();

  /**
   * Cache of the code JVM metrics.
   */
  private final ConcurrentHashMap<String, Metric> coreJvmMetrics = new ConcurrentHashMap<String, Metric>();
  
  /**
   * Derived collection of the core jvm metrics.
   */
  private final Collection<Metric> coreJvmMetricCollection;
  
  /**
   * Cache of the created metrics (excluding JVM metrics).
   */
  private final ConcurrentHashMap<String, Metric> metricsCache = new ConcurrentHashMap<String, Metric>();

  /**
   * Factory for creating TimedMetrics.
   */
  private final MetricFactory<TimedMetric> timedMetricFactory;
  
  /**
   * Factory for creating CounterMetrics.
   */
  private final MetricFactory<CounterMetric> counterMetricFactory;
  
  /**
   * Factory for creating ValueMetrics.
   */
  private final MetricFactory<ValueMetric> valueMetricFactory;

  /**
   * Cache of the metric names.
   */
  private final ConcurrentHashMap<String, MetricNameCache> nameCache = new ConcurrentHashMap<String, MetricNameCache>();

  final boolean disableCollection;
  
  public DefaultMetricManager() {
    this("true".equalsIgnoreCase(System.getProperty("metrics.collection.disable")));
  }
  
  public DefaultMetricManager(boolean disableCollection) {
    
    this.disableCollection = disableCollection;
    this.timedMetricFactory = initTimedMetricFactory(disableCollection);
    this.valueMetricFactory = initValueMetricFactory(disableCollection);
    this.counterMetricFactory = initCounterMetricFactory(disableCollection);
    
    if (!disableCollection) {
      registerStandardJvmMetrics();
    }
    this.coreJvmMetricCollection = Collections.unmodifiableCollection(coreJvmMetrics.values()); 
  }
  
  /**
   * Return the factory used to create TimedMetric instances.
   */
  protected MetricFactory<TimedMetric> initTimedMetricFactory(boolean disableCollection) {
    return (disableCollection) ? new NoopTimedMetricFactory() : new TimedMetricFactory();
  }
  
  /**
   * Return the factory used to create CounterMetric instances.
   */
  protected MetricFactory<CounterMetric> initCounterMetricFactory(boolean disableCollection) {
    return (disableCollection) ? new NoopCounterMetricFactory() : new CounterMetricFactory();
  }

  /**
   * Return the factory used to create ValueMetric instances.
   */
  protected MetricFactory<ValueMetric> initValueMetricFactory(boolean disableCollection) {
    return (disableCollection) ? new NoopValueMetricFactory() : new ValueMetricFactory();
  }
  
  /**
   * Register the standard JVM metrics.
   */
  private void registerStandardJvmMetrics() {
    
    registerJvmMetric(JvmMemoryMetricGroup.createHeapGroup());
    registerJvmMetric(JvmMemoryMetricGroup.createNonHeapGroup());
    
    GaugeCounterMetricGroup[] gaugeMetricGroups = JvmGarbageCollectionMetricGroup.createGauges();
    for (GaugeCounterMetricGroup gaugeMetricGroup : gaugeMetricGroups) {
      registerJvmMetric(gaugeMetricGroup);
    }
    
    registerJvmMetric(JvmThreadMetricGroup.createThreadMetricGroup());
    registerJvmMetric(JvmSystemMetricGroup.getUptime());
    
    DefaultGaugeMetric osLoadAvgMetric = JvmSystemMetricGroup.getOsLoadAvgMetric();
    if (osLoadAvgMetric.getValue() >= 0) {
      // OS Load Average is supported on this system
      registerJvmMetric(osLoadAvgMetric);
    }
  }

  private void registerJvmMetric(Metric m) {
    coreJvmMetrics.put(m.getName().getSimpleName(), m);
  }

  @Override
  public MetricName nameParse(String name) {
    return DefaultMetricName.parse(name);
  }

  @Override
  public MetricName name(String group, String type, String name) {
    return new DefaultMetricName(group, type, name);
  }

  @Override
  public MetricName name(Class<?> cls, String name) {
    return new DefaultMetricName(cls, name);
  }

  @Override
  public MetricNameCache getMetricNameCache(Class<?> klass) {
    return getMetricNameCache(name(klass, null));
  }
  
  @Override
  public MetricNameCache getMetricNameCache(MetricName baseName) {
   
    String key = baseName.getSimpleName();
    MetricNameCache metricNameCache = nameCache.get(key);
    if (metricNameCache == null) {
      metricNameCache = new DefaultMetricNameCache(baseName);
      MetricNameCache oldNameCache = nameCache.putIfAbsent(key, metricNameCache);
      if (oldNameCache != null) {
        return oldNameCache;
      }
    }
    return metricNameCache;
  }
  
  @Override
  public TimedMetricGroup getTimedMetricGroup(MetricName baseName) {
    return new DefaultTimedMetricGroup(baseName);
  }
  
  @Override
  public TimedMetric getTimedMetric(String name) {
    return getTimedMetric(DefaultMetricName.parse(name));
  }
  
  @Override
  public TimedMetric getTimedMetric(MetricName name) {
    return (TimedMetric) getMetric(name, timedMetricFactory);
  }

  @Override
  public CounterMetric getCounterMetric(MetricName name) {
    return (CounterMetric) getMetric(name, counterMetricFactory);
  }
  
  @Override
  public ValueMetric getValueMetric(MetricName name) {
    return (ValueMetric) getMetric(name, valueMetricFactory);
  }

  private Metric getMetric(MetricName name, MetricFactory<?> factory) {

    String cacheKey = name.getSimpleName();
    // try lock free get first
    Metric metric = metricsCache.get(cacheKey);
    if (metric == null) {
      synchronized (monitor) {
        // use synchronised block
        metric = metricsCache.get(cacheKey);
        if (metric == null) {
          metric = factory.createMetric(name);
          metricsCache.put(cacheKey, metric);
        }
      }
    }
    return metric;
  }
  
  

  @Override
  public GaugeMetric registerGauge(MetricName name, Gauge gauge) {
    
    DefaultGaugeMetric metric = new DefaultGaugeMetric(name, gauge);
    metricsCache.put(name.getSimpleName(), metric); 
    return metric;
  }
  
  @Override
  public GaugeCounterMetric registerGauge(MetricName name, GaugeCounter gauge) {
    
    DefaultGaugeCounterMetric metric = new DefaultGaugeCounterMetric(name, gauge);
    metricsCache.put(name.getSimpleName(), metric); 
    return metric;
  }

  public void clear() {
    synchronized (monitor) {
      metricsCache.clear();
    }
  }

  @Override
  public Collection<Metric> collectNonEmptyMetrics() {
    synchronized (monitor) {
      
      Collection<Metric> values = metricsCache.values();
      List<Metric> list = new ArrayList<Metric>(values.size());
      
      for (Metric metric : values) {
        if (metric.collectStatistics()) {
          list.add(metric);
        }
      }
      
      return Collections.unmodifiableList(list);
    }
  }
  
  @Override
  public Collection<Metric> getMetrics() {
    synchronized (monitor) {
      return Collections.unmodifiableCollection(metricsCache.values());
    }
  }
  
  @Override
  public Collection<Metric> getJvmMetrics() {
    return coreJvmMetricCollection;
  }

}
