package org.codingburgas.demetra.models;

import java.util.List;

public record DataQualityReport(List<DataQualityFinding> invalidRecords,
                                List<ExpiredSessionRecord> expiredSessions) {
}
