package com.videoanalytics.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class Formatter {

    public static String formatTimeAgo(LocalDateTime time) {
        if (time == null) return "ещё не обновлялось";
        long minutes = ChronoUnit.MINUTES.between(time, LocalDateTime.now());
        if (minutes < 1) return "только что";
        if (minutes < 60) return minutes + " " + pluralMinutes(minutes) + " назад";
        long hours = ChronoUnit.HOURS.between(time, LocalDateTime.now());
        if (hours < 24) return hours + " " + pluralHours(hours) + " назад";
        long days = ChronoUnit.DAYS.between(time, LocalDateTime.now());
        return days + " " + pluralDays(days) + " назад";
    }

    public static String formatNumber(long number) {
        if (number < 1000) return String.valueOf(number);
        StringBuilder sb = new StringBuilder(String.valueOf(number));
        int insertAt = sb.length() - 3;
        while (insertAt > 0) {
            sb.insert(insertAt, '\u00A0');
            insertAt -= 3;
        }
        return sb.toString();
    }

    public static String formatViews(long n) {
        return formatNumber(n) + " " + plural(n, "просмотр", "просмотра", "просмотров");
    }

    private static String pluralMinutes(long n) {
        return plural(n, "минуту", "минуты", "минут");
    }

    private static String pluralHours(long n) {
        return plural(n, "час", "часа", "часов");
    }

    private static String pluralDays(long n) {
        return plural(n, "день", "дня", "дней");
    }

    private static String plural(long n, String form1, String form2, String form5) {
        long mod100 = n % 100;
        long mod10 = n % 10;
        if (mod100 >= 11 && mod100 <= 19) return form5;
        if (mod10 == 1) return form1;
        if (mod10 >= 2 && mod10 <= 4) return form2;
        return form5;
    }
}
