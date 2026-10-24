package com.apm.monitor.core.engine;

import com.apm.monitor.core.stats.ApmStatsCalculator;
import com.apm.monitor.core.window.WindowService;
import com.apm.monitor.model.ApmSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ApmMonitorEngineTest {
    @Test
    void pauseSavesPendingInputsExactlyOnce() throws Exception {
        AtomicReference<ApmSnapshot> last = new AtomicReference<>();
        try (ApmMonitorEngine engine = engine(() -> "Game", last)) {
            engine.start();
            input(engine, 12);
            engine.pause();
            assertFalse(last.get().monitoring());
            assertEquals(0, last.get().currentApm());
            assertEquals(720, last.get().allTimePeakApm());
            assertEquals(720, last.get().allTimeAverageApm());
            assertEquals(1, last.get().points().size());

            engine.pause();
            assertEquals(1, last.get().points().size());
            engine.start();
            tick(engine);
            assertEquals(0, last.get().currentApm());
            assertEquals(720, last.get().allTimePeakApm());
            assertEquals(360, last.get().allTimeAverageApm());
        }
    }

    @Test
    void resetDiscardsAnInputAlreadyWaitingForWindowLookup() throws Exception {
        verifyOldInputIsDiscarded(ApmMonitorEngine::reset);
    }

    @Test
    void pauseAndResumeDiscardAnInputFromBeforeThePause() throws Exception {
        verifyOldInputIsDiscarded(engine -> {
            engine.pause();
            engine.start();
        });
    }

    private void verifyOldInputIsDiscarded(java.util.function.Consumer<ApmMonitorEngine> transition) throws Exception {
        BlockingWindow windows = new BlockingWindow();
        AtomicReference<ApmSnapshot> last = new AtomicReference<>();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ApmMonitorEngine engine = engine(windows, last)) {
            engine.start();
            windows.blockNext.set(true);
            Future<?> oldInput = worker.submit(engine::onInputEvent);
            assertTrue(windows.entered.await(3, TimeUnit.SECONDS));
            transition.accept(engine);
            windows.release.countDown();
            oldInput.get(3, TimeUnit.SECONDS);
            tick(engine);
            assertEquals(0, last.get().currentApm());
            assertEquals(0, last.get().allTimePeakApm());

            engine.onInputEvent();
            tick(engine);
            assertEquals(60, last.get().currentApm());
        } finally {
            windows.release.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void resetPreventsAnOldTickFromRestoringClearedHistory() throws Exception {
        BlockingWindow windows = new BlockingWindow();
        AtomicReference<ApmSnapshot> last = new AtomicReference<>();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ApmMonitorEngine engine = engine(windows, last)) {
            engine.start();
            input(engine, 2);
            windows.blockNext.set(true);
            Future<?> oldTick = worker.submit(() -> {
                tick(engine);
                return null;
            });
            assertTrue(windows.entered.await(3, TimeUnit.SECONDS));
            engine.reset();
            ApmSnapshot resetSnapshot = last.get();
            windows.release.countDown();
            oldTick.get(3, TimeUnit.SECONDS);
            assertSame(resetSnapshot, last.get());
            assertTrue(last.get().points().isEmpty());
            engine.onInputEvent();
            tick(engine);
            assertEquals(60, last.get().allTimeAverageApm());
            assertEquals(1, last.get().points().size());
        } finally {
            windows.release.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void allTimeStatisticsSurviveHistoryEvictionAndResetTogether() throws Exception {
        AtomicReference<ApmSnapshot> last = new AtomicReference<>();
        try (ApmMonitorEngine engine = engine(() -> "Game", last)) {
            engine.start();
            input(engine, 10);
            tick(engine);
            input(engine, 2);
            tick(engine);
            tick(engine);
            assertEquals(2, last.get().points().size());
            assertEquals(60, ApmStatsCalculator.average(last.get().points()));
            assertEquals(120, ApmStatsCalculator.peak(last.get().points()));
            assertEquals(240, last.get().allTimeAverageApm());
            assertEquals(600, last.get().allTimePeakApm());
            assertTrue(last.get().historyTruncated());

            engine.reset();
            assertEquals(0, last.get().allTimeAverageApm());
            assertEquals(0, last.get().allTimePeakApm());
            assertFalse(last.get().historyTruncated());
            assertTrue(last.get().points().isEmpty());
        }
    }

    @Test
    void pausedConfigurationChangesPublishFreshSnapshots() throws Exception {
        AtomicReference<ApmSnapshot> last = new AtomicReference<>();
        try (ApmMonitorEngine engine = engine(() -> "Game", last)) {
            engine.setTargetWindowTitle(" Game ");
            assertEquals("Game", last.get().targetWindowTitle());
            engine.setFilterByWindow(true);
            assertTrue(last.get().filterByWindow());
            assertFalse(last.get().monitoring());
            engine.getCurrentForegroundWindow();
            assertEquals("Game", last.get().activeWindowTitle());
        }
    }

    @Test
    void oneCallbackFailureDoesNotPreventTheNextSample() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<ApmSnapshot> last = new AtomicReference<>();
        try (ApmMonitorEngine engine = new ApmMonitorEngine(() -> "Game", snapshot -> {
            if (callbacks.incrementAndGet() == 1) {
                throw new IllegalStateException("one failed UI delivery");
            }
            last.set(snapshot);
        }, 2)) {
            stopScheduler(engine);
            engine.start();
            engine.onInputEvent();
            tick(engine);
            assertEquals(60, last.get().currentApm());
            assertEquals(2, callbacks.get());
        }
    }

    private static ApmMonitorEngine engine(WindowService windows, AtomicReference<ApmSnapshot> last) throws Exception {
        ApmMonitorEngine engine = new ApmMonitorEngine(windows, last::set, 2);
        stopScheduler(engine);
        return engine;
    }

    private static void stopScheduler(ApmMonitorEngine engine) throws Exception {
        Field scheduler = ApmMonitorEngine.class.getDeclaredField("scheduler");
        scheduler.setAccessible(true);
        ((ScheduledExecutorService) scheduler.get(engine)).shutdownNow();
    }

    private static void tick(ApmMonitorEngine engine) throws Exception {
        Method tick = ApmMonitorEngine.class.getDeclaredMethod("onTick");
        tick.setAccessible(true);
        tick.invoke(engine);
    }

    private static void input(ApmMonitorEngine engine, int count) {
        for (int i = 0; i < count; i++) {
            engine.onInputEvent();
        }
    }

    private static final class BlockingWindow implements WindowService {
        final AtomicBoolean blockNext = new AtomicBoolean();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String getForegroundWindowTitle() {
            if (blockNext.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("window lookup was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return "Game";
        }
    }
}
