package sap.distance.service;

import sap.distance.LatLong;

import java.util.Collection;

/**
 * Basic metrics for {@link DistanceService}.
 * Consider using JMX instead.
 */
public interface DistanceServiceMetrics {

    int getCacheSize();

    int getCacheMissesCount();

    long getMaxExecutionTime();

    long getAvgExecutionTime();

    Collection<LatLong> getTopLocations();

    int getExecutionCount();
}
