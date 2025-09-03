package com.java.boot.puffin_resume_bot;

import com.java.boot.puffin_resume_bot.tg.ResumeBot;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class BotRegistrar {
    private final ResumeBot bot;

    public BotRegistrar(ResumeBot bot) {
        this.bot = bot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() throws Exception {
        new TelegramBotsApi(DefaultBotSession.class).registerBot(bot);
    }
}
