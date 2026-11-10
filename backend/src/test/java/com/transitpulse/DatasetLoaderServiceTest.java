package com.transitpulse;

import com.transitpulse.service.DatasetLoaderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** CSV parsing and timestamp handling, including genuinely absent values. */
class DatasetLoaderServiceTest {

    @Test
    @DisplayName("simple CSV lines are split on commas")
    void parsesSimpleLine() {
        List<String> fields = DatasetLoaderService.parseLine("a,b,c");
        assertThat(fields).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("quoted fields containing commas stay intact")
    void parsesQuotedCommas() {
        List<String> fields = DatasetLoaderService.parseLine(
                "R01,\"Harbour Line, express\",BUS");
        assertThat(fields).containsExactly("R01", "Harbour Line, express", "BUS");
    }

    @Test
    @DisplayName("escaped double quotes are unescaped")
    void parsesEscapedQuotes() {
        List<String> fields = DatasetLoaderService.parseLine("a,\"say \"\"hi\"\"\",c");
        assertThat(fields).containsExactly("a", "say \"hi\"", "c");
    }

    @Test
    @DisplayName("empty trailing fields are preserved")
    void preservesEmptyFields() {
        List<String> fields = DatasetLoaderService.parseLine("a,,c,");
        assertThat(fields).containsExactly("a", "", "c", "");
    }

    @Test
    @DisplayName("standard timestamps parse")
    void parsesTimestamps() {
        assertThat(DatasetLoaderService.timestampOf("2026-03-16 09:15:00"))
                .isEqualTo(LocalDateTime.of(2026, 3, 16, 9, 15, 0));
        assertThat(DatasetLoaderService.timestampOf("2026-03-16T09:15:00"))
                .isEqualTo(LocalDateTime.of(2026, 3, 16, 9, 15, 0));
    }

    @Test
    @DisplayName("a blank timestamp stays null rather than becoming a default time")
    void blankTimestampIsNull() {
        assertThat(DatasetLoaderService.timestampOf("")).isNull();
        assertThat(DatasetLoaderService.timestampOf("   ")).isNull();
        assertThat(DatasetLoaderService.timestampOf(null)).isNull();
    }

    @Test
    @DisplayName("an unparseable timestamp yields null, not an exception")
    void unparseableTimestampIsNull() {
        assertThat(DatasetLoaderService.timestampOf("not a date")).isNull();
    }
}
