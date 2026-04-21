package com.videoanalytics.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RuTubeClient implements PlatformClient {
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public RuTubeClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public VideoData fetch(String videoId) throws PlatformException {
        String url = "https://rutube.ru/api/video/" + videoId + "/";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (compatible; bot/1.0)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new PlatformException("HTTP " + response.statusCode());
            }
            JsonNode node = mapper.readTree(response.body());
            long hits = node.path("hits").asLong(0);
            String title = node.path("title").asText("");
            return new VideoData(hits, title);
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformException("Ошибка RuTube API: " + e.getMessage(), e);
        }
    }
}
