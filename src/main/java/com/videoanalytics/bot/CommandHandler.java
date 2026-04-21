package com.videoanalytics.bot;

import com.videoanalytics.service.VideoService;
import com.videoanalytics.util.Formatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);

    private final VideoBot bot;
    private final VideoService videoService;
    private final ConcurrentHashMap<Long, UserState> userStates;
    private final Set<Long> seenUsers;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Integer>> activeCardMessageIds;

    public CommandHandler(VideoBot bot, VideoService videoService,
                          ConcurrentHashMap<Long, UserState> userStates,
                          Set<Long> seenUsers,
                          ConcurrentHashMap<Long, CopyOnWriteArrayList<Integer>> activeCardMessageIds) {
        this.bot = bot;
        this.videoService = videoService;
        this.userStates = userStates;
        this.seenUsers = seenUsers;
        this.activeCardMessageIds = activeCardMessageIds;
    }

    public void handle(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        String text = message.getText();
        String command = text.split("\\s+")[0].toLowerCase().replaceAll("@.*", "");

        switch (command) {
            case "/start" -> handleStart(chatId, userId);
            case "/add" -> handleAdd(chatId, userId, text);
            case "/list" -> handleList(chatId);
            case "/stats" -> handleStats(chatId);
            case "/refresh" -> handleRefresh(chatId);
            default -> bot.sendText(chatId, "Неизвестная команда. Используйте меню.");
        }
    }

    private void handleStart(long chatId, long userId) {
        boolean firstTime = seenUsers.add(userId);
        if (firstTime) {
            bot.sendText(chatId,
                    "Привет! Я помогу отслеживать просмотры видео с YouTube и RuTube.\n\n" +
                    "Добавляйте видео по ссылке, смотрите статистику и обновляйте данные прямо в чате.");
        }
        bot.sendMenu(chatId);
        Thread.ofVirtual().start(() -> {
            try {
                videoService.refreshAll(s -> {});
            } catch (Exception e) {
                log.error("Background refresh failed", e);
            }
        });
    }

    void handleAdd(long chatId, long userId, String text) {
        String[] parts = text.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            AddUrlHelper.promptForUrl(bot, chatId, userId, userStates);
        } else {
            AddUrlHelper.processUrl(bot, videoService, chatId, userId, parts[1].trim(), userStates, activeCardMessageIds);
        }
    }

    private void handleList(long chatId) {
        bot.clearCards(chatId);
        ListHelper.sendList(bot, videoService, chatId, "ALL", "date", 0, activeCardMessageIds);
    }

    private void handleStats(long chatId) {
        Map<String, Long> stats = videoService.getStats();
        String text = buildStatsText(stats);
        InlineKeyboardButton refreshAll = InlineKeyboardButton.builder()
                .text("🔄 Обновить всё").callbackData("refresh_all_stats").build();
        InlineKeyboardButton menu = InlineKeyboardButton.builder()
                .text("🏠 В меню").callbackData("menu").build();
        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(refreshAll, menu)).build();
        try {
            bot.execute(SendMessage.builder().chatId(chatId).text(text).replyMarkup(kb).build());
        } catch (TelegramApiException e) {
            log.error("sendStats failed", e);
        }
    }

    static String buildStatsText(Map<String, Long> stats) {
        long total = stats.getOrDefault("total_videos", 0L);
        long totalViews = stats.getOrDefault("total_views", 0L);
        long ytCount = stats.getOrDefault("youtube_count", 0L);
        long ytViews = stats.getOrDefault("youtube_views", 0L);
        long rtCount = stats.getOrDefault("rutube_count", 0L);
        long rtViews = stats.getOrDefault("rutube_views", 0L);
        return "📊 Статистика\n\n" +
               "Всего: " + total + " видео · 👁 " + Formatter.formatNumber(totalViews) + " просмотров\n\n" +
               "▶️ YouTube: " + ytCount + " видео · " + Formatter.formatNumber(ytViews) + " просмотров\n" +
               "📺 RuTube: " + rtCount + " видео · " + Formatter.formatNumber(rtViews) + " просмотров";
    }

    void handleRefresh(long chatId) {
        Thread.ofVirtual().start(() -> {
            try {
                bot.clearCards(chatId);
                bot.sendText(chatId, "⏳ Обновляю...");
                var result = videoService.refreshAll(s -> {});
                bot.sendText(chatId, "✅ " + result.success() + " обновлено · ⚠️ " + result.errors() + " с ошибками");
                bot.sendMenu(chatId);
            } catch (Exception e) {
                log.error("Refresh failed", e);
                bot.sendText(chatId, "Ошибка при обновлении.");
            }
        });
    }
}
