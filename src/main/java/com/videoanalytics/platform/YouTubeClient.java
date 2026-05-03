package com.videoanalytics.platform;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;

import java.math.BigInteger;
import java.util.Collections;

public class YouTubeClient implements PlatformClient {
    private final YouTube youtube;
    private final String apiKey;

    public YouTubeClient(String apiKey) throws Exception {
        this.apiKey = apiKey;
        this.youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {
                    request.setConnectTimeout(10_000);
                    request.setReadTimeout(10_000);
                })
                .setApplicationName("tg-videoanalytics")
                .build();
    }

    @Override
    public VideoData fetch(String videoId) throws PlatformException {
        try {
            VideoListResponse response = youtube.videos()
                    .list(Collections.singletonList("snippet,statistics"))
                    .setId(Collections.singletonList(videoId))
                    .setKey(apiKey)
                    .execute();
            if (response.getItems() == null || response.getItems().isEmpty()) {
                throw new PlatformException("Видео недоступно");
            }
            com.google.api.services.youtube.model.Video item = response.getItems().get(0);
            String title = item.getSnippet().getTitle();
            BigInteger views = item.getStatistics().getViewCount();
            long viewCount = views != null ? views.longValue() : 0L;
            return new VideoData(viewCount, title);
        } catch (PlatformException e) {
            throw e;
        } catch (GoogleJsonResponseException e) {
            boolean isQuota = e.getDetails() != null &&
                    e.getDetails().getErrors().stream().anyMatch(err ->
                            "quotaExceeded".equals(err.getReason()) ||
                            "dailyLimitExceeded".equals(err.getReason()));
            if (isQuota) {
                throw new PlatformException("Квота YouTube API исчерпана, попробуйте завтра");
            }
            throw new PlatformException("Ошибка YouTube API: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PlatformException("Ошибка YouTube API: " + e.getMessage(), e);
        }
    }
}
