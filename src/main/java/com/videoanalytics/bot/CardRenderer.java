package com.videoanalytics.bot;

import com.videoanalytics.model.Video;
import com.videoanalytics.model.ViewStatEntry;
import com.videoanalytics.util.Formatter;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public class CardRenderer {

    public static String renderText(Video video) {
        return renderText(video, -1);
    }

    public static String renderText(Video video, long delta) {
        return renderText(video, delta, true);
    }

    public static String renderText(Video video, long delta, boolean showStaleWarning) {
        StringBuilder sb = new StringBuilder();
        String platformIcon = video.getPlatform().name().equals("YOUTUBE") ? "▶️ YouTube" : "📺 RuTube";
        sb.append(platformIcon).append("\n");

        String title = video.getTitle() != null ? video.getTitle() : video.getVideoId();
        if (!video.isAvailable()) sb.append("⚠️ ");
        sb.append(title).append("\n");

        sb.append("👁 ").append(Formatter.formatViews(video.getViewCount()));
        if (delta > 0) sb.append(" (+").append(Formatter.formatNumber(delta)).append(" с последнего обновления)");
        sb.append("\n");

        String timeAgo = Formatter.formatTimeAgo(video.getLastUpdated());
        boolean stale = video.getLastUpdated() != null &&
                ChronoUnit.HOURS.between(video.getLastUpdated(), LocalDateTime.now()) >= 24;
        sb.append("🕐 обновлено: ").append(timeAgo);
        if (stale) sb.append(" ⏰");
        sb.append("\n");

        if (video.getCreatedAt() != null) {
            sb.append("📅 добавлено: ").append(Formatter.formatTimeAgo(video.getCreatedAt())).append("\n");
        }

        if (!video.isAvailable() && showStaleWarning) {
            sb.append("⚠️ данные могут быть устаревшими\n");
        }

        return sb.toString();
    }

    public static InlineKeyboardMarkup renderButtons(Video video) {
        InlineKeyboardButton open = InlineKeyboardButton.builder()
                .text("🔗 Открыть").url(video.getUrl()).build();
        InlineKeyboardButton delete = InlineKeyboardButton.builder()
                .text("🗑 Удалить").callbackData("delete_" + video.getId()).build();
        InlineKeyboardButton refresh = InlineKeyboardButton.builder()
                .text("🔄 Обновить").callbackData("refresh_" + video.getId()).build();
        InlineKeyboardButton history = InlineKeyboardButton.builder()
                .text("📈 История").callbackData("history_" + video.getId()).build();
        InlineKeyboardButton menu = InlineKeyboardButton.builder()
                .text("🏠 В меню").callbackData("menu").build();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(open, delete))
                .keyboardRow(List.of(refresh, history))
                .keyboardRow(List.of(menu))
                .build();
    }

    public static String renderHistoryText(Video video, List<ViewStatEntry> entries) {
        String title = video.getTitle() != null ? video.getTitle() : video.getVideoId();
        StringBuilder sb = new StringBuilder("📈 История: ").append(title).append("\n\n");

        if (entries.isEmpty()) {
            sb.append("История пуста — данные появятся после первого обновления.");
            return sb.toString();
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM HH:mm", new Locale("ru"));
        for (int i = 0; i < entries.size(); i++) {
            ViewStatEntry e = entries.get(i);
            String date = e.recordedAt() != null ? e.recordedAt().format(fmt) : "—";
            sb.append("🔹 ").append(date).append(" — ").append(Formatter.formatViews(e.viewCount()));
            if (i + 1 < entries.size()) {
                long delta = e.viewCount() - entries.get(i + 1).viewCount();
                if (delta > 0) sb.append(" (+").append(Formatter.formatNumber(delta)).append(")");
            }
            sb.append("\n");
        }

        long total = entries.get(0).viewCount() - entries.get(entries.size() - 1).viewCount();
        sb.append("\nЗамеров: ").append(entries.size());
        if (total > 0) sb.append(" · Рост за период: +").append(Formatter.formatNumber(total));
        return sb.toString();
    }

    public static InlineKeyboardMarkup renderHistoryButtons(long videoId) {
        InlineKeyboardButton back = InlineKeyboardButton.builder()
                .text("◀️ Назад").callbackData("back_card_" + videoId).build();
        return InlineKeyboardMarkup.builder().keyboardRow(List.of(back)).build();
    }

    public static InlineKeyboardMarkup deleteConfirmButtons(long videoId) {
        InlineKeyboardButton yes = InlineKeyboardButton.builder()
                .text("✅ Да").callbackData("confirm_delete_" + videoId).build();
        InlineKeyboardButton no = InlineKeyboardButton.builder()
                .text("❌ Нет").callbackData("cancel_delete_" + videoId).build();
        return InlineKeyboardMarkup.builder().keyboardRow(List.of(yes, no)).build();
    }
}
