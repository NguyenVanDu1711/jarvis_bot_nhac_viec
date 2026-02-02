package com.jarvis.notification.service;

import com.jarvis.notification.dto.TaskDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${telegram.bot-username}")
    private String botUsername;

    @Value("${telegram.bot-token}")
    private String botToken;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            Long telegramId = update.getMessage().getFrom().getId();

            if (text.equals("/tasks")) {
                // Gọi API Java của bạn
                String url = "http://localhost:8180/tasks?telegramId=" + telegramId;
                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<TaskDto[]> response = restTemplate.getForEntity(url, TaskDto[].class);

                TaskDto[] tasks = response.getBody();
                StringBuilder sb = new StringBuilder("📝 Task của bạn:\n");
                for (TaskDto task : tasks) {
                    sb.append("[").append(task.issueKey()).append("]")
                      .append("(http://14.224.201.179:8032/browse/")
                      .append(task.issueKey()).append(")\n")
                      .append("Summary: ").append(task.summary()).append("\n")
                      .append("Due date: ").append(task.dueDate()).append("\n")
                      .append("Time spent: ").append(task.timeSpent()).append("\n\n");
                }

                SendMessage message = new SendMessage();
                message.setChatId(update.getMessage().getChatId().toString());
                message.setText(sb.toString());
                message.enableMarkdown(true);
                try {
                    execute(message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
