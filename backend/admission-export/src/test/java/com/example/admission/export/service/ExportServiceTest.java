package com.example.admission.export.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportServiceTest {

    @Test
    void historicalRankUsesActualRankWhenAvailable() {
        assertEquals("123456", ExportService.formatHistoricalRank(123456, false));
    }

    @Test
    void historicalRankUsesDashWhenOrdinaryPlanHasNoData() {
        assertEquals("-", ExportService.formatHistoricalRank(null, false));
    }

    @Test
    void historicalRankUsesNewForEveryNewPlanColumn() {
        assertEquals("新增", ExportService.formatHistoricalRank(123456, true));
        assertEquals("新增", ExportService.formatHistoricalRank(null, true));
    }
}
