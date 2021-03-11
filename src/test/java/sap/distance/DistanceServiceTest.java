package sap.distance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sap.distance.provider.DistanceProvider;
import sap.distance.service.DistanceService;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic test(s) for {@link DistanceService}.
 * It takes some time to complete all tests - see description for individual tests.
 */
public class DistanceServiceTest {

    private static final int TEST_DATA_SIZE = 1000;
    // timing settings
    private final int updateCheckIntervalSeconds = 10; // allows to test update from provider
    private final int testDurationSeconds = 5; // should be less than check interval so no update occurs
    // service to be tested
    private DistanceService distanceService;
    // expected data: from -> to -> distance
    private Map<LatLong, Map<LatLong, Integer>> expectedData = generateTestData(100);
    // computed distances: from -> to -> distance
    private Map<LatLong, Map<LatLong, Integer>> computedData = new ConcurrentHashMap<>();
    // service execution counter: from -> to -> count
    private Map<LatLong, Map<LatLong, Integer>> executionCounter = new ConcurrentHashMap<>();
    // distance provider that uses test data
    private final DistanceProvider distanceProvider = new DistanceProvider() {

        @Override
        public int fetchDistanceInMeters(LatLong from, LatLong to) {
            sleep(ThreadLocalRandom.current().nextInt(500)); // random delay
            executionCounter.computeIfAbsent(from, k -> new ConcurrentHashMap<>()).merge(to, 1, Integer::sum);
            return expectedData.get(from).get(to);
        }

        @Override
        public Date lastRoadNetworkUpdate() {
            return new Date();
        }
    };

    @BeforeEach
    public void beforeEachTest() {
        expectedData = generateTestData(TEST_DATA_SIZE);
        computedData = new ConcurrentHashMap<>();
        executionCounter = new ConcurrentHashMap<>();
        distanceService = new DistanceService(distanceProvider, updateCheckIntervalSeconds);
    }

    /**
     * This test uses several threads to calculate distances for test coordinates. It then validates results and
     * number of external service calls.
     *
     * It takes {@link #testDurationSeconds} to complete this test.
     */
    @Test
    public void testServiceExecutionWithoutUpdate() {
        testService(true);
    }

    /**
     * This test uses several threads to calculate distances for test coordinates. It then validates results and
     * number of external service calls.
     *
     * Second part waits for update to happen and repeats test/validation.
     *
     * It takes {@link #testDurationSeconds} + {@link #updateCheckIntervalSeconds} to complete this test.
     */
    @Test
    public void testServiceExecutionWithUpdate() {
        testService(true);

        // wait for update
        sleep(TimeUnit.SECONDS.toMillis(updateCheckIntervalSeconds - testDurationSeconds));

        testService(false);
    }

    private void testService(boolean isWithoutUpdate) {
        AtomicBoolean isTerminated = new AtomicBoolean(false); // used to stop asynchronous tasks

        // create some tasks
        CompletableFuture<?>[] tasks = new CompletableFuture[Runtime.getRuntime().availableProcessors()];
        for (int i = 0; i < tasks.length; i++) {
            tasks[i] = CompletableFuture.runAsync(() -> getAndStore(isTerminated));
        }

        // let tasks run for given time
        CompletableFuture.allOf(tasks).completeOnTimeout(null, testDurationSeconds, TimeUnit.SECONDS).join();
        isTerminated.set(true);

        // validate results
        if (isWithoutUpdate) {
            // exactly one external call is expected for each unique calculation
            validate(true, 1);
        } else {
            // after update some calculations should be fetched again
            validate(false, 2);
        }
    }

    /**
     * Validates computed values and calls to external provider.
     */
    private void validate(boolean checkAllCounters, int expectedExecutionCounter) {
        // validate if results were calculated
        assertFalse(computedData.isEmpty(), "results are missing");
        // validate from coordinates
        assertTrue(computedData.keySet().stream().allMatch(expectedData::containsKey), "invalid from coordinates");
        // validate to coordinates
        assertTrue(computedData.keySet().stream() //
            .allMatch(from -> expectedData.get(from).keySet().containsAll(computedData.get(from).keySet())),
            "invalid to coordinates");
        // validate distance values
        assertTrue(computedData.keySet().stream() //
                .allMatch(from -> computedData.get(from).keySet().stream()
                    .allMatch(to -> expectedData.get(from).get(to).equals(computedData.get(from).get(to)))),
            "invalid distance");
        // validate service execution counter
        if (checkAllCounters) {
            assertTrue(executionCounter.values().stream().flatMap(v -> v.values().stream())
                .allMatch(count -> count == expectedExecutionCounter), "invalid execution counter");
        } else {
            assertTrue(executionCounter.values().stream().flatMap(v -> v.values().stream())
                .anyMatch(count -> count == expectedExecutionCounter), "invalid execution counter");
        }
    }

    /**
     * Selects randomly from/to coordinates from test/computed data and uses service to get the distance.
     * Retrieved values are stored for further validation.
     */
    private void getAndStore(AtomicBoolean isTerminated) {
        while (!isTerminated.get()) {
            getAndStore(expectedData, computedData);
            getAndStore(computedData, computedData);
        }
    }

    /**
     * Selects randomly from/to coordinates from src, gets distance and store result in dst.
     */
    private void getAndStore(Map<LatLong, Map<LatLong, Integer>> src, Map<LatLong, Map<LatLong, Integer>> dst) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int fromIdx = rnd.nextInt(src.keySet().size());
        LatLong from = src.keySet().stream().skip(fromIdx).findFirst().orElse(null);
        int toIdx = rnd.nextInt(src.get(from).keySet().size());
        LatLong to = src.get(from).keySet().stream().skip(toIdx).findFirst().orElse(null);
        int distance = distanceService.distanceInMeters(from, to);
        dst.computeIfAbsent(from, k -> new ConcurrentHashMap<>()).put(to, distance);
    }

    /**
     * Generates at most size * size random entries/distances.
     */
    private Map<LatLong, Map<LatLong, Integer>> generateTestData(int size) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Map<LatLong, Map<LatLong, Integer>> map = new ConcurrentHashMap<>();
        int fromSize = size;
        while (fromSize-- > 0) {
            LatLong from = coordinates(rnd.nextInt());
            map.putIfAbsent(from, new ConcurrentHashMap<>());
            int toSize = size;
            while (toSize-- > 0) {
                map.get(from).put(coordinates(rnd.nextInt()), rnd.nextInt());
            }
        }
        return map;
    }

    private LatLong coordinates(int value) {
        return new LatLong(value, value);
    }

    private void sleep(long millis) {
        try {
            // delay computation randomly
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
