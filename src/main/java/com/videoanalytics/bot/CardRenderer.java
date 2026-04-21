package com.videoanalytics.bot;

import com.videoanalytics.model.Video;
import com.videoanalytics.util.Formatter;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
        InlineKeyboardButton menu = InlineKeyboardButton.builder()
                .text("🏠 В меню").callbackData("menu").build();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(open, delete))
                .keyboardRow(List.of(refresh, menu))
                .build();
    }

    public static InlineKeyboardMarkup deleteConfirmButtons(long videoId) {
        InlineKeyboardButton yes = InlineKeyboardButton.builder()
                .text("✅ Да").callbackData("confirm_delete_" + videoId).build();
        InlineKeyboardButton no = InlineKeyboardButton.builder()
                .text("❌ Нет").callbackData("cancel_delete_" + videoId).build();
        return InlineKeyboardMarkup.builder().keyboardRow(List.of(yes, no)).build();
    }
}
