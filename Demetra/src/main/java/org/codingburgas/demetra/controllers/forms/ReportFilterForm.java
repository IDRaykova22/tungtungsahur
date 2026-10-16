package org.codingburgas.demetra.controllers.forms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Report filter: a date range (both inclusive) and the criterion the rows are grouped by. */
@Getter
@Setter
public class ReportFilterForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
    private String groupBy = "month";
}
