package com.apm.monitor.core.stats;

import com.apm.monitor.model.ApmPoint;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApmStatsCalculatorTest {
    @ParameterizedTest
    @ValueSource(ints = {60, 300, 900, 1800})
    void recentRangeExcludesTheExpiredBoundaryAndFuturePoints(int seconds) {
        long now = 10_000;
        List<ApmPoint> points = new ArrayList<>();
        points.add(new ApmPoint(now - seconds, 600));
        for (long time = now - seconds + 1; time <= now; time++) {
            points.add(new ApmPoint(time, 60));
        }
        points.add(new ApmPoint(now + 1, 1200));
        List<ApmPoint> recent = ApmStatsCalculator.filterByRange(points, seconds, now);
        assertEquals(seconds, recent.size());
        assertEquals(60, ApmStatsCalculator.average(recent));
        assertEquals(60, ApmStatsCalculator.peak(recent));
    }
}
