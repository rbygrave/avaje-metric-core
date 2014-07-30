package org.avaje.metric.core;

import org.avaje.metric.TimedMetric;
import org.junit.Assert;
import org.junit.Test;

public class DisableCollectionTest {

  @Test
  public void test() {
    
    DefaultMetricManager mgr = new DefaultMetricManager(true);
    
    TimedMetric timedMetric = mgr.getTimedMetric("check.disabled.timed1");
    Assert.assertEquals(0, timedMetric.getSuccessStatistics(false).getCount());
    Assert.assertEquals(0, timedMetric.getErrorStatistics(false).getCount());
    
    timedMetric.addEventDuration(false, 1000000);
    timedMetric.addEventDuration(true, 1000000);
    timedMetric.addEventSince(true, 1000000);
    timedMetric.operationEnd(1000000, 100);
    timedMetric.operationEnd(1000000, 191);
    
    Assert.assertEquals(0, timedMetric.getSuccessStatistics(false).getCount());
    Assert.assertEquals(0, timedMetric.getErrorStatistics(false).getCount());
    
  }
  
  public static void main(String[] args) {
    
    System.setProperty("metrics.collection.disable", "true");

    DefaultMetricManager mgr = new DefaultMetricManager();
    Assert.assertTrue(mgr.disableCollection);
    
    TimedMetric timedMetric = mgr.getTimedMetric("check.disabled.timed1");
    Assert.assertEquals(0, timedMetric.getSuccessStatistics(false).getCount());
    Assert.assertEquals(0, timedMetric.getErrorStatistics(false).getCount());
    
    timedMetric.addEventDuration(true, 1000000);
    timedMetric.addEventDuration(false, 1000000);
    Assert.assertEquals(0, timedMetric.getSuccessStatistics(false).getCount());
    Assert.assertEquals(0, timedMetric.getErrorStatistics(false).getCount());

  }
}
