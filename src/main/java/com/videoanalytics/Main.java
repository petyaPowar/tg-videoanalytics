package com.videoanalytics;

import com.videoanalytics.bot.VideoBot;
import com.videoanalytics.config.AppConfig;
import com.videoanalytics.db.DatabaseManager;
import com.videoanalytics.db.VideoRepository;
import com.videoanalytics.service.VideoService;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig();
        config.validate();

        DatabaseManager db = new DatabaseManager(config);
        db.applySchema();

        VideoRepository repository = new VideoRepository(db.getDataSource());
        VideoService videoService = new VideoService(repository, config);

        VideoBot bot = new VideoBot(config, videoService);
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);

        System.out.println("Bot started: @" + config.getBotUsername());
        new CountDownLatch(1).await();
    }
}
