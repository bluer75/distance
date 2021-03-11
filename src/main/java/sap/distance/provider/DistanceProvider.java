package sap.distance.provider;

import sap.distance.LatLong;

import java.util.Date;

/**
 * Definition of methods supported by external provider.
 */
public interface DistanceProvider {

    int fetchDistanceInMeters(LatLong from, LatLong to);

    Date lastRoadNetworkUpdate();
}