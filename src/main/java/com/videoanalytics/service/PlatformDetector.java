package com.videoanalytics.service;

import com.videoanalytics.model.Platform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlatformDetector {
    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern YT_WATCH  = Pattern.compile("(?:www\\.)?youtube\\.com/watch\\?(?:.*[?&])?v=([A-Za-z0-9_-]+)", CI);
    private static final Pattern YT_SHORT  = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]+)", CI);
    private static final Pattern YT_SHORTS = Pattern.compile("(?:www\\.)?youtube\\.com/shorts/([A-Za-z0-9_-]+)", CI);
    private static final Pattern RT_VIDEO  = Pattern.compile("rutube\\.ru/video/([A-Za-z0-9_-]+)/?", CI);
    private static final Pattern RT_SHORTS = Pattern.compile("rutube\\.ru/shorts/([A-Za-z0-9_-]+)/?", CI);

    public DetectResult detect(String rawUrl) {
        String url = rawUrl.trim();
        Matcher m;
        m = YT_WATCH.matcher(url);
        if (m.find()) return new DetectResult(Platform.YOUTUBE, m.group(1));
        m = YT_SHORT.matcher(url);
        if (m.find()) return new DetectResult(Platform.YOUTUBE, m.group(1).split("[?#]")[0]);
        m = YT_SHORTS.matcher(url);
        if (m.find()) return new DetectResult(Platform.YOUTUBE, m.group(1).split("[?#]")[0]);
        m = RT_VIDEO.matcher(url);
        if (m.find()) return new DetectResult(Platform.RUTUBE, m.group(1));
        m = RT_SHORTS.matcher(url);
        if (m.find()) return new DetectResult(Platform.RUTUBE, m.group(1));
        return new DetectResult(Platform.UNKNOWN, null);
    }
}
