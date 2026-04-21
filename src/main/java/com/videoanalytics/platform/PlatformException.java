package com.videoanalytics.platform;

public class PlatformException extends Exception {
    public PlatformException(String message) { super(message); }
    public PlatformException(String message, Throwable cause) { super(message, cause); }
}
