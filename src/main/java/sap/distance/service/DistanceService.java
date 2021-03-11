package sap.distance.service;

import sap.distance.LatLong;
import sap.distance.provider.DistanceProvider;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that provides distance between two geographic coordinates.
 * This implementation uses caching internally to minimize number of calls to external provider. It periodically checks
 * for updates from provider however, it is therefore possible to occasionally get outdated results (it's eventually
 * consistent).
 *
 * This implementation is thread safe.
 *
 * Please note this is not production-ready solution. There are still some features missing:
 * - cache-eviction policy (LRU)
 * - validation/error handling when calling external service
 * - metrics for service monitoring
 * - further cache optimization to minimize inconsistency
 */
public class DistanceService {

    // from -> to -> distance
    private final ConcurrentMap<LatLong, ConcurrentMap<LatLong, Future<StampedResult>>> cache;
    // executor to check
    private final ScheduledExecutorService scheduler;
    // external service providing actual distances
    private DistanceProvider distanceProvider;
    // timestamp when provider updated last time the road network - may result in changes in calculated distances
    private AtomicLong lastUpdate;
    // task that checks if provider has new updates
    private final Runnable checkLastUpdate = () -> {
        lastUpdate.set(distanceProvider.lastRoadNetworkUpdate().getTime());
    };

    public DistanceService(DistanceProvider distanceProvider, int updateCheckIntervalSeconds) {
        this.cache = new ConcurrentHashMap<>();
        this.distanceProvider = Objects.requireNonNull(distanceProvider);
        this.lastUpdate = new AtomicLong(System.currentTimeMillis());
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(checkLastUpdate, updateCheckIntervalSeconds, updateCheckIntervalSeconds,
            TimeUnit.SECONDS);
    }

    /**
     * If cache contains valid result, it retrieves the distance from the cache. Otherwise the value is provided by
     * external service (can be slow) and it's stored in the cache.
     *
     * TODO: add eviction-policy and metrics
     */
    public int distanceInMeters(LatLong from, LatLong to) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        try {
            return cache.computeIfAbsent(from, key -> new ConcurrentHashMap<>()) //
                .compute(to, (k, v) -> isUpdateNeeded(v) ? fetchDistanceAsync(from, to) : v) //
                .get().result;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This returns collected metrics for this service.
     *
     * TODO: implement
     */
    public DistanceServiceMetrics getMetrics() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /**
     * Retrieves the Future object holding the result. Call to external service is performed asynchronously. Getting the
     * result from the Future can block calling thread.
     *
     * TODO: add exception handling to CompletableFuture
     */
    private Future<StampedResult> fetchDistanceAsync(LatLong from, LatLong to) {

        return CompletableFuture.supplyAsync(() -> new StampedResult(distanceProvider.fetchDistanceInMeters(from, to)));
    }

    /**
     * Checks if given result has to be fetched from provider. Returns true only if task has not been created yet or
     * existing result has expired.
     */
    private boolean isUpdateNeeded(Future<StampedResult> task) {
        try {
            return task == null || (task.isDone() && task.get().timestamp < lastUpdate.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return true;
        }
    }

    /**
     * It holds result of computation together with timestamp when the value was computed.
     */
    private static class StampedResult {
        private final long timestamp;
        private final int result;

        StampedResult(int result) {
            this.timestamp = System.currentTimeMillis();
            this.result = result;
        }
    }
}
