package com.videoanalytics.db;

import com.videoanalytics.model.Platform;
import com.videoanalytics.model.Video;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class VideoRepository {
    private final DataSource dataSource;

    public VideoRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long save(Video video) {
        String sql = "INSERT INTO videos (url, platform, video_id, title, view_count, last_updated, last_error, is_available, added_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, video.getUrl());
            ps.setString(2, video.getPlatform().name());
            ps.setString(3, video.getVideoId());
            ps.setString(4, video.getTitle());
            ps.setLong(5, video.getViewCount());
            ps.setTimestamp(6, video.getLastUpdated() != null ? Timestamp.valueOf(video.getLastUpdated()) : null);
            ps.setString(7, video.getLastError());
            ps.setBoolean(8, video.isAvailable());
            ps.setLong(9, video.getAddedBy());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("No generated key returned");
    }

    public void update(Video video) {
        String sql = "UPDATE videos SET view_count=?, title=?, last_updated=?, last_error=?, is_available=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, video.getViewCount());
            ps.setString(2, video.getTitle());
            ps.setTimestamp(3, video.getLastUpdated() != null ? Timestamp.valueOf(video.getLastUpdated()) : null);
            ps.setString(4, video.getLastError());
            ps.setBoolean(5, video.isAvailable());
            ps.setLong(6, video.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Video> findById(long id) {
        String sql = "SELECT * FROM videos WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    public Optional<Video> findByUrl(String url) {
        String sql = "SELECT * FROM videos WHERE url=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, url);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    public Optional<Video> findByUrlIgnoreCase(String url) {
        String sql = "SELECT * FROM videos WHERE LOWER(url)=LOWER(?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, url);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private static final String SQL_ALL_DATE   = "SELECT * FROM videos ORDER BY created_at DESC LIMIT ? OFFSET ?";
    private static final String SQL_ALL_VIEWS  = "SELECT * FROM videos ORDER BY view_count DESC LIMIT ? OFFSET ?";
    private static final String SQL_YT_DATE    = "SELECT * FROM videos WHERE platform='YOUTUBE' ORDER BY created_at DESC LIMIT ? OFFSET ?";
    private static final String SQL_YT_VIEWS   = "SELECT * FROM videos WHERE platform='YOUTUBE' ORDER BY view_count DESC LIMIT ? OFFSET ?";
    private static final String SQL_RT_DATE    = "SELECT * FROM videos WHERE platform='RUTUBE' ORDER BY created_at DESC LIMIT ? OFFSET ?";
    private static final String SQL_RT_VIEWS   = "SELECT * FROM videos WHERE platform='RUTUBE' ORDER BY view_count DESC LIMIT ? OFFSET ?";

    private static final String CNT_ALL = "SELECT COUNT(*) FROM videos";
    private static final String CNT_YT  = "SELECT COUNT(*) FROM videos WHERE platform='YOUTUBE'";
    private static final String CNT_RT  = "SELECT COUNT(*) FROM videos WHERE platform='RUTUBE'";

    public List<Video> findAll(String platform, String sortBy, int offset, int limit) {
        String sql = switch (platform) {
            case "YT" -> "views".equals(sortBy) ? SQL_YT_VIEWS : SQL_YT_DATE;
            case "RT" -> "views".equals(sortBy) ? SQL_RT_VIEWS : SQL_RT_DATE;
            default   -> "views".equals(sortBy) ? SQL_ALL_VIEWS : SQL_ALL_DATE;
        };
        List<Video> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public int count(String platform) {
        String sql = switch (platform) {
            case "YT" -> CNT_YT;
            case "RT" -> CNT_RT;
            default   -> CNT_ALL;
        };
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    public void saveViewStat(long videoId, long viewCount) {
        String sql = "INSERT INTO view_stats (video_id, view_count) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, videoId);
            ps.setLong(2, viewCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteById(long id) {
        String sql = "DELETE FROM videos WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Video> findAll() {
        String sql = "SELECT * FROM videos ORDER BY created_at DESC";
        List<Video> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public Map<String, Long> getStats() {
        String sql = "SELECT * FROM stats_summary";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Map<String, Long> stats = new LinkedHashMap<>();
                stats.put("total_videos", rs.getLong("total_videos"));
                stats.put("total_views", rs.getLong("total_views"));
                stats.put("unavailable_count", rs.getLong("unavailable_count"));
                stats.put("youtube_count", rs.getLong("youtube_count"));
                stats.put("youtube_views", rs.getLong("youtube_views"));
                stats.put("rutube_count", rs.getLong("rutube_count"));
                stats.put("rutube_views", rs.getLong("rutube_views"));
                return stats;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Collections.emptyMap();
    }

    private Video map(ResultSet rs) throws SQLException {
        Video v = new Video();
        v.setId(rs.getLong("id"));
        v.setUrl(rs.getString("url"));
        v.setPlatform(Platform.valueOf(rs.getString("platform")));
        v.setVideoId(rs.getString("video_id"));
        v.setTitle(rs.getString("title"));
        v.setViewCount(rs.getLong("view_count"));
        Timestamp lu = rs.getTimestamp("last_updated");
        v.setLastUpdated(lu != null ? lu.toLocalDateTime() : null);
        v.setLastError(rs.getString("last_error"));
        v.setAvailable(rs.getBoolean("is_available"));
        v.setAddedBy(rs.getLong("added_by"));
        Timestamp ca = rs.getTimestamp("created_at");
        v.setCreatedAt(ca != null ? ca.toLocalDateTime() : null);
        return v;
    }
}
