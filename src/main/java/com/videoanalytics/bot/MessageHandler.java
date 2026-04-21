package com.videoanalytics.bot;

import com.videoanalytics.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);

    private final VideoBot bot;
    private final VideoService videoService;
    private final ConcurrentHashMap<Long, UserState> userStates;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Integer>> activeCardMessageIds;

    public MessageHandler(VideoBot bot, VideoService videoService,
                          ConcurrentHashMap<Long, UserState> userStates,
                          ConcurrentHashMap<Long, CopyOnWriteArrayList<Integer>> activeCardMessageIds) {
        this.bot = bot;
        this.videoService = videoService;
        this.userStates = userStates;
        this.activeCardMessageIds = activeCardMessageIds;
    }

    public void handle(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        String text = message.getText();

        UserState state = userStates.getOrDefault(userId, UserState.IDLE);

        if (state == UserState.AWAITING_URL) {
            AddUrlHelper.processUrl(bot, videoService, chatId, userId, text, userStates, activeCardMessageIds);
            return;
        }

        if ("➕ Добавить видео".equals(text)) {
            AddUrlHelper.promptForUrl(bot, chatId, userId, userStates);
        } else if (text.startsWith("📋 Мои видео")) {
            bot.clearCards(chatId);
            ListHelper.sendList(bot, videoService, chatId, "ALL", "date", 0, activeCardMessageIds);
        } else if ("📊 Статистика".equals(text)) {
            Map<String, Long> stats = videoService.getStats();
            String statsText = CommandHandler.buildStatsText(stats);
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                    .keyboardRow(List.of(
                            InlineKeyboardButton.builder()
                                    .text("🔄 Обновить всё").callbackData("refresh_all_stats").build(),
                            InlineKeyboardButton.builder()
                                    .text("🏠 В меню").callbackData("menu").build()))
                    .build();
            try {
                bot.execute(SendMessage.builder().chatId(chatId).text(statsText).replyMarkup(kb).build());
            } catch (TelegramApiException e) {
                log.error("sendStats failed", e);
                bot.sendText(chatId, statsText);
            }
        } else if ("🔄 Обновить всё".equals(text)) {
            handleRefreshAll(chatId);
        } else {
            bot.sendText(chatId, "Используйте кнопки меню или команды (/start, /add, /list, /stats, /refresh).");
        }
    }

    public void handleNonText(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        UserState state = userStates.getOrDefault(userId, UserState.IDLE);
        if (state == UserState.AWAITING_URL) {
            bot.sendText(chatId, "Пожалуйста, пришлите текстовую ссылку.");
        }
    }

    private void handleRefreshAll(long chatId) {
        Thread.ofVirtual().start(() -> {
            try {
                bot.clearCards(chatId);
                bot.sendText(chatId, "⏳ Обновляю...");
                var result = videoService.refreshAll(s -> {});
                bot.sendText(chatId, "✅ " + result.success() + " обновлено · ⚠️ " + result.errors() + " с ошибками");
                bot.sendMenu(chatId);
            } catch (Exception e) {
                log.error("refreshAll failed", e);
                bot.sendText(chatId, "Ошибка при обновлении.");
            }
        });
    }
}
