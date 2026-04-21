package com.videoanalytics.bot;

import com.videoanalytics.exception.DuplicateVideoException;
import com.videoanalytics.exception.VideoLimitException;
import com.videoanalytics.model.Video;
import com.videoanalytics.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AddUrlHelper {
    private static final Logger log = LoggerFactory.getLogger(AddUrlHelper.class);

    static final String SUPPORTED_FORMATS =
            "Поддерживаемые форматы:\n" +
            "• https://youtube.com/watch?v=ID\n" +
            "• https://youtu.be/ID\n" +
            "• https://youtube.com/shorts/ID\n" +
            "• https://rutube.ru/video/ID/\n" +
            "• https://rutube.ru/shorts/ID/";

    static void promptForUrl(VideoBot bot, long chatId, long userId,
                             ConcurrentHashMap<Long, UserState> userStates) {
        userStates.put(userId, UserState.AWAITING_URL);
        InlineKeyboardButton cancel = InlineKeyboardButton.builder()
                .text("❌ Отмена").callbackData("cancel_add").build();
        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(cancel)).build();
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Пришлите ссылку на видео.\nНапример: https://youtu.be/xxx")
                    .replyMarkup(kb)
                    .build());
        } catch (TelegramApiException e) {
            log.error("promptForUrl failed", e);
        }
    }

    static void processUrl(VideoBot bot, VideoService videoService,
                           long chatId, long userId, String url,
                           ConcurrentHashMap<Long, UserState> userStates,
                           ConcurrentHashMap<Long, CopyOnWriteArrayList<Integer>> activeCardMessageIds) {
        userStates.put(userId, UserState.IDLE);
        int[] sentMsgId = {0};
        try {
            SendMessage waiting = SendMessage.builder().chatId(chatId).text("⏳ Получаю данные...").build();
            var sent = bot.execute(waiting);
            sentMsgId[0] = sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("send waiting failed", e);
        }

        Thread.ofVirtual().start(() -> {
            try {
                Video video = videoService.addVideo(url, userId);
                String cardText = CardRenderer.renderText(video);
                InlineKeyboardButton addMore = InlineKeyboardButton.builder()
                        .text("➕ Добавить ещё").callbackData("add_more").build();
                InlineKeyboardButton menu = InlineKeyboardButton.builder()
                        .text("🏠 В меню").callbackData("menu").build();
                InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(addMore, menu)).build();

                if (sentMsgId[0] != 0) {
                    try {
                        bot.execute(EditMessageText.builder()
                                .chatId(chatId)
                                .messageId(sentMsgId[0])
                                .text(cardText)
                                .replyMarkup(kb)
                                .build());
                        bot.trackCardMessage(chatId, sentMsgId[0]);
                    } catch (TelegramApiException e) {
                        log.error("edit card failed", e);
                    }
                }
            } catch (VideoLimitException e) {
                editOrSend(bot, chatId, sentMsgId[0],
                        "⛔ " + e.getMessage() + ". Удалите ненужные видео, чтобы добавить новое.",
                        menuOnlyKeyboard());
            } catch (DuplicateVideoException e) {
                editOrSend(bot, chatId, sentMsgId[0],
                        "Это видео уже добавлено.",
                        menuOnlyKeyboard());
            } catch (IllegalArgumentException e) {
                editOrSend(bot, chatId, sentMsgId[0],
                        "Не удалось распознать ссылку.\n\n" + SUPPORTED_FORMATS,
                        retryOrMenuKeyboard());
            } catch (Exception e) {
                log.error("addVideo failed", e);
                editOrSend(bot, chatId, sentMsgId[0],
                        "Ошибка при добавлении видео.",
                        menuOnlyKeyboard());
            }
        });
    }

    private static void editOrSend(VideoBot bot, long chatId, int msgId, String text, InlineKeyboardMarkup kb) {
        if (msgId != 0) {
            try {
                bot.execute(EditMessageText.builder()
                        .chatId(chatId).messageId(msgId).text(text).replyMarkup(kb).build());
                return;
            } catch (TelegramApiException ignored) {}
        }
        try {
            bot.execute(SendMessage.builder().chatId(chatId).text(text).replyMarkup(kb).build());
        } catch (TelegramApiException e) {
            log.error("editOrSend fallback failed", e);
        }
    }

    private static InlineKeyboardMarkup menuOnlyKeyboard() {
        InlineKeyboardButton menu = InlineKeyboardButton.builder()
                .text("🏠 В меню").callbackData("menu").build();
        return InlineKeyboardMarkup.builder().keyboardRow(List.of(menu)).build();
    }

    static InlineKeyboardMarkup retryOrMenuKeyboard() {
        InlineKeyboardButton retry = InlineKeyboardButton.builder()
                .text("🔄 Попробовать снова").callbackData("retry_add").build();
        InlineKeyboardButton menu = InlineKeyboardButton.builder()
                .text("🏠 В меню").callbackData("menu").build();
        return InlineKeyboardMarkup.builder().keyboardRow(List.of(retry, menu)).build();
    }
}
