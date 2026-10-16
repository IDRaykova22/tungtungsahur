package org.codingburgas.demetra.services.intelligence;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Methods of the "intelligent" analysis: they turn a series of registration counts into a trend, a forecast and
 * flagged anomalies. Deliberately small, explainable statistics (linear regression and z-scores), so every
 * conclusion on the dashboard can be traced back to a formula and needs no external service.
 */
@Component
public class RegistrationAnalytics {

    /** Spikes below this count are noise for a small site, whatever their z-score. */
    public static final int MIN_ANOMALY_COUNT = 3;
    /** Standard deviations above the mean that count as unusual. */
    public static final double ANOMALY_Z = 2.0;
    /**
     * Counts vary by about one even when nothing special happens, so the standard deviation is never taken
     * below 1. Without this, a single registration after quiet days would look like a huge spike.
     */
    private static final double MIN_STD = 1.0;

    public enum Direction { RISING, STABLE, FALLING }

    public enum Confidence { HIGH, MEDIUM, LOW }

    /** Ordinary least squares over x = 0 … n-1. A flat or empty series has slope 0 and R² 0. */
    public Trend linearTrend(double[] y) {
        int n = y.length;
        if (n == 0) {
            return new Trend(0, 0, 0, 0);
        }
        double meanX = (n - 1) / 2.0;
        double meanY = mean(y);
        double sxy = 0;
        double sxx = 0;
        for (int i = 0; i < n; i++) {
            sxy += (i - meanX) * (y[i] - meanY);
            sxx += (i - meanX) * (i - meanX);
        }
        double slope = sxx == 0 ? 0 : sxy / sxx;
        double intercept = meanY - slope * meanX;
        double ssRes = 0;
        double ssTot = 0;
        for (int i = 0; i < n; i++) {
            double residual = y[i] - (intercept + slope * i);
            ssRes += residual * residual;
            ssTot += (y[i] - meanY) * (y[i] - meanY);
        }
        double r2 = ssTot == 0 ? 0 : Math.max(0, 1 - ssRes / ssTot);
        return new Trend(slope, intercept, r2, meanY);
    }

    /** Extends the trend line {@code steps} points past the end of the series; counts cannot be negative. */
    public double[] forecast(double[] y, int steps) {
        Trend trend = linearTrend(y);
        double[] result = new double[Math.max(steps, 0)];
        for (int k = 0; k < result.length; k++) {
            result[k] = Math.max(0, trend.valueAt(y.length + k));
        }
        return result;
    }

    /**
     * Chooses the forecasting model by how trustworthy the trend is: a reliable line is extended, otherwise the
     * average level is repeated. This keeps a single spike from being projected as steady growth.
     */
    public double[] adaptiveForecast(double[] y, int steps) {
        if (confidence(linearTrend(y), y) != Confidence.LOW) {
            return forecast(y, steps);
        }
        double[] flat = new double[Math.max(steps, 0)];
        Arrays.fill(flat, mean(y));
        return flat;
    }

    /** Indexes of points at least {@link #ANOMALY_Z} deviations above the series mean (and not tiny). */
    public List<Integer> detectAnomalies(double[] y) {
        List<Integer> anomalies = new ArrayList<>();
        if (y.length < 4) {
            return anomalies;
        }
        double mean = mean(y);
        double std = Math.max(standardDeviation(y, mean), MIN_STD);
        for (int i = 0; i < y.length; i++) {
            if (y[i] >= MIN_ANOMALY_COUNT && (y[i] - mean) / std >= ANOMALY_Z) {
                anomalies.add(i);
            }
        }
        return anomalies;
    }

    /** How unusual {@code value} is compared with a reference history, e.g. today against the previous days. */
    public double zScore(double value, double[] reference) {
        if (reference.length == 0) {
            return 0;
        }
        double mean = mean(reference);
        return (value - mean) / Math.max(standardDeviation(reference, mean), MIN_STD);
    }

    public boolean isUnusuallyHigh(double value, double[] reference) {
        return value >= MIN_ANOMALY_COUNT && zScore(value, reference) >= ANOMALY_Z;
    }

    /**
     * Direction of the trend. The total change along the line is compared with the average level: under 20 %
     * of it, or a line that explains less than a quarter of the variation (R² &lt; 0.25), is called stable.
     */
    public Direction classify(Trend trend, int length) {
        if (length < 2 || trend.mean() == 0) {
            return Direction.STABLE;
        }
        double relativeChange = trend.slope() * (length - 1) / Math.max(trend.mean(), 1);
        if (Math.abs(relativeChange) < 0.2 || trend.r2() < 0.25) {
            return Direction.STABLE;
        }
        return relativeChange > 0 ? Direction.RISING : Direction.FALLING;
    }

    /** Forecast reliability from the fit quality, and never high with fewer than three non-empty points. */
    public Confidence confidence(Trend trend, double[] y) {
        long nonEmpty = Arrays.stream(y).filter(v -> v > 0).count();
        if (nonEmpty < 3) {
            return Confidence.LOW;
        }
        if (trend.r2() >= 0.6) {
            return Confidence.HIGH;
        }
        return trend.r2() >= 0.3 ? Confidence.MEDIUM : Confidence.LOW;
    }

    static double mean(double[] y) {
        return y.length == 0 ? 0 : Arrays.stream(y).sum() / y.length;
    }

    private static double standardDeviation(double[] y, double mean) {
        double sum = 0;
        for (double v : y) {
            sum += (v - mean) * (v - mean);
        }
        return Math.sqrt(sum / y.length);
    }
}
