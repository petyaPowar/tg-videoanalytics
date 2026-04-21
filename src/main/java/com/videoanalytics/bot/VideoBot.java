package com.videoanalytics.bot;

import com.videoanalytics.config.AppConfig;
import com.videoanalytics.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class VideoBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(VideoBot.class);

    private final AppConfig config;
    private final VideoService videoService;

    final ConcurrentHashMap<Long, UserState> userStates = new ConcurrentHashMap<>();
    final Set<Long> seenUsers = ConcurrentHashMap.newKeySet();
    final ConcurrentHashMap<Long, CopyOnWriteArrayList<Integer>> activeCardMessageIds = new ConcurrentHashMap<>();

    private final CommandHandler commandHandler;
    private final CallbackHandler callbackHandler;
    private final MessageHandler messageHandler;

    public VideoBot(AppConfig config, VideoService videoService) {
        super(config.getBotToken());
        this.config = config;
        this.videoService = videoService;
        this.commandHandler = new CommandHandler(this, videoService, userStates, seenUsers, activeCardMessageIds);
        this.callbackHandler = new CallbackHandler(this, videoService, userStates, activeCardMessageIds);
        this.messageHandler = new MessageHandler(this, videoService, userStates, activeCardMessageIds);
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        long chatId = 0;
        try {
            Long userId = extractUserId(update);
            if (userId == null) return;
            chatId = extractChatId(update);

            if (!config.getAllowedUserIds().contains(userId)) {
                if (chatId != 0) {
                    sendText(chatId, "⛔ Нет доступа.");
                }
                return;
            }

            if (update.hasCallbackQuery()) {
                String queryId = update.getCallbackQuery().getId();
                try { execute(new AnswerCallbackQuery(queryId)); } catch (TelegramApiException ignored) {}
                callbackHandler.handle(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                if (update.getMessage().isCommand()) {
                    userStates.put(userId, UserState.IDLE);
                    commandHandler.handle(update.getMessage());
                } else {
                    messageHandler.handle(update.getMessage());
                }
            } else if (update.hasMessage()) {
                messageHandler.handleNonText(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
            if (chatId != 0) {
                sendText(chatId, "Произошла ошибка, попробуйте снова.");
            }
        }
    }

    private Long extractUserId(Update update) {
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getFrom().getId();
        if (update.hasMessage()) return update.getMessage().getFrom().getId();
        return null;
    }

    private long extractChatId(Update update) {
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getMessage().getChatId();
        if (update.hasMessage()) return update.getMessage().getChatId();
        return 0;
    }

    public void sendText(long chatId, String text) {
        try {
            execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("sendText failed", e);
        }
    }

    public void sendMenu(long chatId) {
        int count = videoService.countAll();
        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(List.of(
                        new KeyboardButton("➕ Добавить видео"),
                        new KeyboardButton("📋 Мои видео (" + count + ")"))))
                .keyboardRow(new KeyboardRow(List.of(
                        new KeyboardButton("📊 Статистика"),
                        new KeyboardButton("🔄 Обновить всё"))))
                .resizeKeyboard(true)
                .build();
        try {
            execute(SendMessage.builder().chatId(chatId).text("Главное меню").replyMarkup(keyboard).build());
        } catch (TelegramApiException e) {
            log.error("sendMenu failed", e);
        }
    }

    public void deleteMessageSilently(long chatId, int messageId) {
        try {
            execute(new DeleteMessage(String.valueOf(chatId), messageId));
        } catch (TelegramApiException ignored) {}
    }

    public void clearCards(long chatId) {
        CopyOnWriteArrayList<Integer> ids = activeCardMessageIds.get(chatId);
        if (ids != null) {
            for (int id : ids) deleteMessageSilently(chatId, id);
            ids.clear();
        }
    }

    public void trackCardMessage(long chatId, int messageId) {
        activeCardMessageIds.computeIfAbsent(chatId, k -> new CopyOnWriteArrayList<>()).add(messageId);
    }
}
