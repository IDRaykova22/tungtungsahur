package org.codingburgas.demetra.services.intelligence;

/**
 * Least-squares line {@code y = intercept + slope * x} through a time series (x = 0, 1, 2 …).
 *
 * @param slope     change per step (per hour, day or month)
 * @param intercept fitted value at the first step
 * @param r2        coefficient of determination, 0-1: how well the line explains the series
 * @param mean      average value of the series
 */
public record Trend(double slope, double intercept, double r2, double mean) {

    public double valueAt(double x) {
        return intercept + slope * x;
    }
}
