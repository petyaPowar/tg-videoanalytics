package com.videoanalytics.service;

import com.videoanalytics.config.AppConfig;
import com.videoanalytics.db.VideoRepository;
import com.videoanalytics.exception.DuplicateVideoException;
import com.videoanalytics.exception.VideoLimitException;
import com.videoanalytics.model.Platform;
import com.videoanalytics.model.Video;
import com.videoanalytics.platform.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class VideoService {
    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private final VideoRepository repository;
    private final PlatformDetector detector;
    private final Map<Platform, PlatformClient> clients;
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private final int maxVideos;

    public VideoService(VideoRepository repository, AppConfig config) throws Exception {
        this.repository = repository;
        this.detector = new PlatformDetector();
        this.clients = new EnumMap<>(Platform.class);
        this.clients.put(Platform.YOUTUBE, new YouTubeClient(config.getYoutubeApiKey()));
        this.clients.put(Platform.RUTUBE, new RuTubeClient());
        this.maxVideos = config.getMaxVideos();
    }

    public Video addVideo(String rawUrl, long userId) throws DuplicateVideoException, VideoLimitException, IllegalArgumentException {
        String url = rawUrl.trim(); // оригинальный URL для хранения и кнопки «Открыть»
        DetectResult detect = detector.detect(url); // videoId с сохранением регистра
        if (detect.platform() == Platform.UNKNOWN) {
            throw new IllegalArgumentException("Неизвестный формат URL");
        }
        if (maxVideos > 0 && repository.count("ALL") >= maxVideos) {
            throw new VideoLimitException("Достигнут лимит: " + maxVideos + " видео");
        }
        if (repository.findByUrlIgnoreCase(url).isPresent()) {
            throw new DuplicateVideoException("Видео уже добавлено");
        }

        Video video = new Video();
        video.setUrl(url);
        video.setPlatform(detect.platform());
        video.setVideoId(detect.videoId());
        video.setAddedBy(userId);
        video.setAvailable(true);

        PlatformClient client = clients.get(detect.platform());
        try {
            VideoData data = client.fetch(detect.videoId());
            video.setTitle(data.title());
            video.setViewCount(data.viewCount());
            video.setLastUpdated(LocalDateTime.now());
            video.setAvailable(true);
        } catch (PlatformException e) {
            video.setTitle(detect.videoId());
            video.setViewCount(0);
            video.setAvailable(false);
            video.setLastError(e.getMessage());
        }

        long generatedId = repository.save(video);
        video.setId(generatedId);
        return video;
    }

    public Video refreshOne(long id) {
        Optional<Video> opt = repository.findById(id);
        if (opt.isEmpty()) return null;
        Video video = opt.get();
        PlatformClient client = clients.get(video.getPlatform());
        try {
            VideoData data = client.fetch(video.getVideoId());
            video.setTitle(data.title());
            video.setViewCount(data.viewCount());
            video.setLastUpdated(LocalDateTime.now());
            video.setAvailable(true);
            video.setLastError(null);
        } catch (PlatformException e) {
            video.setAvailable(false);
            video.setLastError(e.getMessage());
        }
        repository.update(video);
        return video;
    }

    public RefreshResult refreshAll(Consumer<String> onProgress) {
        if (!isRefreshing.compareAndSet(false, true)) {
            return new RefreshResult(0, 0);
        }
        int success = 0, errors = 0;
        try {
            List<Video> videos = repository.findAll();
            int total = videos.size();
            for (int i = 0; i < total; i++) {
                Video v = videos.get(i);
                PlatformClient client = clients.get(v.getPlatform());
                try {
                    VideoData data = client.fetch(v.getVideoId());
                    v.setTitle(data.title());
                    v.setViewCount(data.viewCount());
                    v.setLastUpdated(LocalDateTime.now());
                    v.setAvailable(true);
                    v.setLastError(null);
                    success++;
                } catch (PlatformException e) {
                    v.setAvailable(false);
                    v.setLastError(e.getMessage());
                    errors++;
                }
                repository.update(v);
                onProgress.accept("Обновляю... " + (i + 1) + "/" + total);
            }
        } finally {
            isRefreshing.set(false);
        }
        return new RefreshResult(success, errors);
    }

    public int countAll() {
        return repository.count("ALL");
    }

    public Optional<Video> findById(long id) {
        return repository.findById(id);
    }

    public List<Video> findAll(String platform, String sortBy, int offset, int limit) {
        return repository.findAll(platform, sortBy, offset, limit);
    }

    public int count(String platform) {
        return repository.count(platform);
    }

    public void deleteById(long id) {
        repository.deleteById(id);
    }

    public Map<String, Long> getStats() {
        return repository.getStats();
    }
}
