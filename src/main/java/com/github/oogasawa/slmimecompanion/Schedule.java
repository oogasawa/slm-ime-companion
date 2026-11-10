package com.github.oogasawa.slmimecompanion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * When a job runs: {@code every <n>m} or {@code every <n>h} (the first round at start), or {@code daily HH:MM}
 * (the first round at the next such time).
 */
public record Schedule(Duration firstAfter, Duration period) {

    public static Schedule parse(String text, LocalDateTime now) {
        String[] w = text.trim().split("\\s+");
        if (w.length == 2 && w[0].equals("every")) {
            String n = w[1];
            long amount = Long.parseLong(n.substring(0, n.length() - 1));
            Duration period = switch (n.charAt(n.length() - 1)) {
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                default -> throw new IllegalArgumentException("not a period: " + n);
            };
            if (period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("not a period: " + n);
            }
            return new Schedule(Duration.ZERO, period);
        }
        if (w.length == 2 && w[0].equals("daily")) {
            LocalTime at = LocalTime.parse(w[1]);
            LocalDateTime next = now.toLocalDate().atTime(at);
            if (!next.isAfter(now)) {
                next = next.plusDays(1);
            }
            return new Schedule(Duration.between(now, next), Duration.ofDays(1));
        }
        throw new IllegalArgumentException("not a schedule (every <n>m, every <n>h, daily HH:MM): " + text);
    }
}
