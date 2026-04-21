package com.videoanalytics.bot;

import com.videoanalytics.model.Video;
import com.videoanalytics.service.VideoService;
import com.videoanalytics.util.Formatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ListHelper {
    private static final Logger log = LoggerFactory.getLogger(ListHelper.class);
    private static final int PAGE_SIZE = 5;

    static void sendList(VideoBot bot, VideoService videoService, long chatId,
                         String filter, String sort, int offset,
                         ConcurrentHashMap<Long, CopyOnWriteArrayList<Integer>> activeCardMessageIds) {
        int totalFiltered = videoService.count(filter);
        int totalAll = videoService.countAll();

        String filterLabel = switch (filter) {
            case "YT" -> "YouTube";
            case "RT" -> "RuTube";
            default -> "Все";
        };
        String sortLabel = "views".equals(sort) ? "По просмотрам" : "По дате";

        long totalViews = videoService.getStats().getOrDefault("total_views", 0L);

        String header = "Всего: " + totalAll + " видео · 👁 " + Formatter.formatNumber(totalViews) + " просмотров";

        InlineKeyboardButton btnAll = InlineKeyboardButton.builder()
                .text("Все".equals(filterLabel) ? "✅ Все" : "Все")
                .callbackData("list_0_ALL_" + sort).build();
        InlineKeyboardButton btnYt = InlineKeyboardButton.builder()
                .text("YouTube".equals(filterLabel) ? "✅ ▶️ YouTube" : "▶️ YouTube")
                .callbackData("list_0_YT_" + sort).build();
        InlineKeyboardButton btnRt = InlineKeyboardButton.builder()
                .text("RuTube".equals(filterLabel) ? "✅ 📺 RuTube" : "📺 RuTube")
                .callbackData("list_0_RT_" + sort).build();
        InlineKeyboardButton btnDate = InlineKeyboardButton.builder()
                .text("По дате".equals(sortLabel) ? "✅ По дате" : "По дате")
                .callbackData("list_0_" + filter + "_date").build();
        InlineKeyboardButton btnViews = InlineKeyboardButton.builder()
                .text("По просмотрам".equals(sortLabel) ? "✅ По просмотрам" : "По просмотрам")
                .callbackData("list_0_" + filter + "_views").build();

        InlineKeyboardMarkup controlsKb = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(btnAll, btnYt, btnRt))
                .keyboardRow(List.of(btnDate, btnViews))
                .build();

        try {
            var headerMsg = bot.execute(SendMessage.builder().chatId(chatId)
                    .text(header).replyMarkup(controlsKb).build());
            bot.trackCardMessage(chatId, headerMsg.getMessageId());
        } catch (TelegramApiException e) {
            log.error("send list header failed", e);
            return;
        }

        if (totalFiltered == 0) {
            InlineKeyboardButton add = InlineKeyboardButton.builder()
                    .text("➕ Добавить видео").callbackData("add_more").build();
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder().keyboardRow(List.of(add)).build();
            try {
                var m = bot.execute(SendMessage.builder().chatId(chatId)
                        .text("Список пуст. Добавьте первое видео!").replyMarkup(kb).build());
                bot.trackCardMessage(chatId, m.getMessageId());
            } catch (TelegramApiException e) {
                log.error("send empty list failed", e);
            }
            return;
        }

        List<Video> videos = videoService.findAll(filter, sort, offset, PAGE_SIZE);
        for (Video v : videos) {
            try {
                var m = bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(CardRenderer.renderText(v))
                        .replyMarkup(CardRenderer.renderButtons(v))
                        .build());
                bot.trackCardMessage(chatId, m.getMessageId());
            } catch (TelegramApiException e) {
                log.error("send card failed", e);
            }
        }

        String statusText = "Фильтр: " + filterLabel + " · Сортировка: " + sortLabel;
        List<InlineKeyboardButton> row = new ArrayList<>();
        if (offset + PAGE_SIZE < totalFiltered) {
            row.add(InlineKeyboardButton.builder()
                    .text("Показать ещё")
                    .callbackData("list_" + (offset + PAGE_SIZE) + "_" + filter + "_" + sort)
                    .build());
        }
        row.add(InlineKeyboardButton.builder().text("🏠 В меню").callbackData("menu").build());
        InlineKeyboardMarkup statusKb = InlineKeyboardMarkup.builder().keyboardRow(row).build();
        try {
            var m = bot.execute(SendMessage.builder().chatId(chatId)
                    .text(statusText).replyMarkup(statusKb).build());
            bot.trackCardMessage(chatId, m.getMessageId());
        } catch (TelegramApiException e) {
            log.error("send list status failed", e);
        }
    }
}
