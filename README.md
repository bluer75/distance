Solution to the following problem:

Optimization happens in a spatial world. 
The driving time is one of the most important pieces of information when planning the workday job. A common feature is to be able to get the distance between two points. In this challenge, you are expected to improve a simple existing service.

```java

import com.cloudspatialprovider.api; // this provider is fictional
/**
 *  Service that provides distance from two geographic locations
 *  Calls an external cloud service
 */
class DistanceService {
    public DistanceService() {
    }
    
    public int distanceInMeters(LatLong from, LatLong to) {
        return com.cloudspatialprovider.api.FetchDistanceInMeters(from, to);
    } 
}
```

However, we have the following challenge with the code above:
The calls to cloudspatialprovider.com are expensive, taking half second on average. And we have to do a lot of them. We want to make the calls to distanceInMeters faster. Luckily, there are not many different points, since the technician visit to the same equipments will be evaluated multiple times. The same lat/long point is repeated multiple times. The distance between two points is not expected to change often, about once per week when cloudspatialprovider adds new roads. When this happens, potentially all the distances are invalid. There is a method public Date lastRoadNetworkUpdate() that returns the time when the roads were updated.

How can we change the method above to make it more performant?
