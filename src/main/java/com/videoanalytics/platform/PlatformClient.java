package com.videoanalytics.platform;

public interface PlatformClient {
    VideoData fetch(String videoId) throws PlatformException;
}
