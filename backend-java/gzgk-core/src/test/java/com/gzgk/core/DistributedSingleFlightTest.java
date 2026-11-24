package com.gzgk.core;

import com.gzgk.core.recommend.DistributedSingleFlight;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DistributedSingleFlightTest {

    @Test
    @SuppressWarnings("unchecked")
    void sixtyFourConcurrentVirtualThreadsCoalesceToSingleExecution() throws Exception {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        DistributedSingleFlight sf = new DistributedSingleFlight(provider);
        AtomicInteger expensiveCalls = new AtomicInteger(0);
        int concurrency = 64;
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<DistributedSingleFlight.ExecutionOutcome<String>>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return sf.execute(
                            "gz:586:8450:phys:chem_bio",
                            () -> {
                                expensiveCalls.incrementAndGet();
                                try {
                                    Thread.sleep(80);
                                } catch (InterruptedException ignored) {
                                }
                                return "PLAN_PAYLOAD_96";
                            },
                            s -> s,
                            s -> s,
                            Duration.ofMinutes(5)
                    );
                }));
            }
            ready.await();
            startGate.countDown();

            long coalescedOrCached = 0;
            for (var f : futures) {
                var out = f.get();
                assertEquals("PLAN_PAYLOAD_96", out.value());
                if (out.coalesced() || out.cacheHit()) {
                    coalescedOrCached++;
                }
            }
            assertEquals(1, expensiveCalls.get(), "Underlying expensive computation must run EXACTLY ONCE under 64 concurrent threads");
            assertEquals(concurrency - 1, coalescedOrCached, "All 63 followers should coalesce or hit L1 cache");
        }
    }
}
