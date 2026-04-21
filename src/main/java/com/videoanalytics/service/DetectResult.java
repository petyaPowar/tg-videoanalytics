package com.videoanalytics.service;

import com.videoanalytics.model.Platform;

public record DetectResult(Platform platform, String videoId) {}
