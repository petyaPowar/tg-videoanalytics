package com.videoanalytics.bot;

import com.videoanalytics.model.Video;
import com.videoanalytics.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CallbackHandler {
    private static final Logger log = LoggerFactory.getLogger(CallbackHandler.class);

    private final VideoBot bot;
    private final VideoService videoService;
    private final ConcurrentHashMap<Long, UserState> userStates;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Integer>> activeCardMessageIds;

    public CallbackHandler(VideoBot bot, VideoService videoService,
                           ConcurrentHashMap<Long, UserState> userStates,
                           ConcurrentHashMap<Long, CopyOnWriteArrayList<Integer>> activeCardMessageIds) {
        this.bot = bot;
        this.videoService = videoService;
        this.userStates = userStates;
        this.activeCardMessageIds = activeCardMessageIds;
    }

    public void handle(CallbackQuery query) {
        long chatId = query.getMessage().getChatId();
        long userId = query.getFrom().getId();
        String data = query.getData();
        int msgId = query.getMessage().getMessageId();

        if ("menu".equals(data)) {
            bot.sendMenu(chatId);
        } else if ("cancel_add".equals(data)) {
            userStates.put(userId, UserState.IDLE);
            bot.sendMenu(chatId);
        } else if ("add_more".equals(data)) {
            AddUrlHelper.promptForUrl(bot, chatId, userId, userStates);
        } else if ("retry_add".equals(data)) {
            AddUrlHelper.promptForUrl(bot, chatId, userId, userStates);
        } else if (data.startsWith("delete_")) {
            long videoId = Long.parseLong(data.substring("delete_".length()));
            editMessage(chatId, msgId, "Удалить это видео?",
                    CardRenderer.deleteConfirmButtons(videoId));
        } else if (data.startsWith("confirm_delete_")) {
            long videoId = Long.parseLong(data.substring("confirm_delete_".length()));
            videoService.deleteById(videoId);
            InlineKeyboardButton menu = InlineKeyboardButton.builder()
                    .text("🏠 В меню").callbackData("menu").build();
            editMessage(chatId, msgId, "✅ Удалено",
                    InlineKeyboardMarkup.builder().keyboardRow(List.of(menu)).build());
        } else if (data.startsWith("cancel_delete_")) {
            long videoId = Long.parseLong(data.substring("cancel_delete_".length()));
            Optional<Video> opt = videoService.findById(videoId);
            if (opt.isPresent()) {
                editMessage(chatId, msgId, CardRenderer.renderText(opt.get()),
                        CardRenderer.renderButtons(opt.get()));
            } else {
                editMessage(chatId, msgId, "Видео не найдено.", menuOnlyKb());
            }
        } else if (data.startsWith("refresh_") && !data.startsWith("refresh_all")) {
            long videoId = Long.parseLong(data.substring("refresh_".length()));
            handleRefreshOne(chatId, msgId, videoId);
        } else if ("refresh_all_stats".equals(data)) {
            handleRefreshAll(chatId, false);
        } else if ("refresh_all_menu".equals(data)) {
            handleRefreshAll(chatId, true);
        } else if (data.startsWith("list_")) {
            String[] parts = data.split("_", 4);
            int offset = Integer.parseInt(parts[1]);
            String filter = parts[2];
            String sort = parts[3];
            bot.clearCards(chatId);
            ListHelper.sendList(bot, videoService, chatId, filter, sort, offset, activeCardMessageIds);
        }
    }

    private void handleRefreshOne(long chatId, int msgId, long videoId) {
        Optional<Video> before = videoService.findById(videoId);
        long oldCount = before.map(Video::getViewCount).orElse(0L);
        boolean hadData = before.map(v -> v.getLastUpdated() != null).orElse(false);

        Video updated = videoService.refreshOne(videoId);
        if (updated == null) {
            editMessage(chatId, msgId, "Видео не найдено.", menuOnlyKb());
            return;
        }

        long delta = -1;
        if (hadData && oldCount > 0 && updated.getViewCount() > oldCount) {
            delta = updated.getViewCount() - oldCount;
        }

        if (!updated.isAvailable()) {
            String cardText = CardRenderer.renderText(updated, -1, false) +
                    "⚠️ Платформа временно недоступна, последние данные: " +
                    com.videoanalytics.util.Formatter.formatNumber(updated.getViewCount()) + " просмотров";
            editMessage(chatId, msgId, cardText, CardRenderer.renderButtons(updated));
        } else {
            editMessage(chatId, msgId, CardRenderer.renderText(updated, delta),
                    CardRenderer.renderButtons(updated));
        }
    }

    private void handleRefreshAll(long chatId, boolean goToMenu) {
        Thread.ofVirtual().start(() -> {
            try {
                bot.clearCards(chatId);
                int[] sentId = {0};
                try {
                    var m = bot.execute(SendMessage.builder().chatId(chatId).text("⏳ Обновляю...").build());
                    sentId[0] = m.getMessageId();
                } catch (TelegramApiException e) {
                    log.error("send updating failed", e);
                }

                var result = videoService.refreshAll(progress -> {
                    if (sentId[0] != 0) {
                        try {
                            bot.execute(EditMessageText.builder()
                                    .chatId(chatId).messageId(sentId[0])
                                    .text("⏳ " + progress)
                                    .build());
                        } catch (TelegramApiException ignored) {}
                    }
                });

                String summary = "✅ " + result.success() + " обновлено · ⚠️ " + result.errors() + " с ошибками";
                if (sentId[0] != 0) {
                    try {
                        bot.execute(EditMessageText.builder()
                                .chatId(chatId).messageId(sentId[0]).text(summary).build());
                    } catch (TelegramApiException ignored) {}
                } else {
                    bot.sendText(chatId, summary);
                }

                if (goToMenu) {
                    bot.sendMenu(chatId);
                } else {
                    Map<String, Long> stats = videoService.getStats();
                    String statsText = CommandHandler.buildStatsText(stats);
                    InlineKeyboardButton refreshBtn = InlineKeyboardButton.builder()
                            .text("🔄 Обновить всё").callbackData("refresh_all_stats").build();
                    InlineKeyboardButton menuBtn = InlineKeyboardButton.builder()
                            .text("🏠 В меню").callbackData("menu").build();
                    InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                            .keyboardRow(List.of(refreshBtn, menuBtn)).build();
                    try {
                        bot.execute(SendMessage.builder().chatId(chatId).text(statsText).replyMarkup(kb).build());
                    } catch (TelegramApiException e) {
                        log.error("send stats after refresh failed", e);
                    }
                }
            } catch (Exception e) {
                log.error("refreshAll callback failed", e);
                bot.sendText(chatId, "Ошибка при обновлении.");
            }
        });
    }

    private void editMessage(long chatId, int msgId, String text, InlineKeyboardMarkup kb) {
        try {
            bot.execute(EditMessageText.builder()
                    .chatId(chatId).messageId(msgId).text(text).replyMarkup(kb).build());
        } catch (TelegramApiException e) {
            log.error("editMessage failed", e);
        }
    }

    private InlineKeyboardMarkup menuOnlyKb() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(InlineKeyboardButton.builder()
                        .text("🏠 В меню").callbackData("menu").build()))
                .build();
    }
}
