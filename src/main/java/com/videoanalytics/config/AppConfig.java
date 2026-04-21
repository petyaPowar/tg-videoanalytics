package com.videoanalytics.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AppConfig {
    private final String botToken;
    private final String botUsername;
    private final String youtubeApiKey;
    private final Set<Long> allowedUserIds;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int maxVideos;

    public AppConfig() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.botToken = get(dotenv, "BOT_TOKEN");
        this.botUsername = get(dotenv, "BOT_USERNAME");
        this.youtubeApiKey = get(dotenv, "YOUTUBE_API_KEY");
        String ids = get(dotenv, "ALLOWED_USER_IDS");
        this.allowedUserIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());
        this.dbUrl = get(dotenv, "DB_URL");
        this.dbUser = get(dotenv, "DB_USER");
        this.dbPassword = get(dotenv, "DB_PASSWORD");
        String maxV = get(dotenv, "MAX_VIDEOS");
        this.maxVideos = maxV.isBlank() ? 0 : Integer.parseInt(maxV);
    }

    private String get(Dotenv dotenv, String key) {
        String val = dotenv.get(key);
        if (val == null || val.isBlank()) {
            val = System.getenv(key);
        }
        return val != null ? val.trim() : "";
    }

    public void validate() {
        checkRequired("BOT_TOKEN", botToken);
        checkRequired("BOT_USERNAME", botUsername);
        checkRequired("YOUTUBE_API_KEY", youtubeApiKey);
        checkRequired("ALLOWED_USER_IDS", allowedUserIds.isEmpty() ? "" : "ok");
        checkRequired("DB_URL", dbUrl);
        checkRequired("DB_USER", dbUser);
        checkRequired("DB_PASSWORD", dbPassword);
    }

    private void checkRequired(String name, String value) {
        if (value == null || value.isBlank()) {
            System.err.println("ERROR: Missing required environment variable: " + name);
            System.exit(1);
        }
    }

    public String getBotToken() { return botToken; }
    public String getBotUsername() { return botUsername; }
    public String getYoutubeApiKey() { return youtubeApiKey; }
    public Set<Long> getAllowedUserIds() { return allowedUserIds; }
    public String getDbUrl() { return dbUrl; }
    public String getDbUser() { return dbUser; }
    public String getDbPassword() { return dbPassword; }
    public int getMaxVideos() { return maxVideos; }
}
