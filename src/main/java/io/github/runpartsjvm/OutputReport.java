package io.github.runpartsjvm;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

class OutputReport {

    final AtomicReference<String> reportStringRef = new AtomicReference<>();

    final boolean report;

    final boolean verbose;

    OutputReport(String reportString, boolean report, boolean verbose) {
        reportStringRef.set(reportString);
        this.report = report;
        this.verbose = verbose;
    }

    Optional<String> getReport(boolean condition) {
        return condition ? Optional.ofNullable(reportStringRef.getAndSet(null)) : Optional.empty();
    }

    Optional<String> getOutReport() {
        return getReport(report);
    }

    Optional<String> getErrReport() {
        return getReport(report && !verbose);
    }

}
