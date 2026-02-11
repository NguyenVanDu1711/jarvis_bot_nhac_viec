package com.jarvis.notification.service;

import com.jarvis.notification.dto.TaskDto;
import com.jarvis.notification.entity.TelegramJiraUserEntity;
import com.jarvis.notification.repository.TelegramJiraUserRepository;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TelegramBotService extends TelegramLongPollingBot {

    private final JiraService jiraService;
    private final TelegramJiraUserRepository telegramJiraUserRepository;

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
//            GetChatAdministrators getChatAdministrators = new GetChatAdministrators(update.getMessage().getChatId().toString());
//            List<ChatMember> admins = null;
//            try {
//                admins = execute(getChatAdministrators);
//            } catch (TelegramApiException e) {
//                throw new RuntimeException(e);
//            }
//            for (ChatMember admin : admins) {
//                System.out.println("Admin ID: " + admin.getUser().getId() + admin.getUser().;
//            }
            String telegramUsername = "@" + update.getMessage().getFrom().getUserName();
            TelegramJiraUserEntity userEntity = telegramJiraUserRepository
                    .findByTelegramUsername(telegramUsername)
                    .orElse(new TelegramJiraUserEntity());
            if (StringUtils.isBlank(userEntity.getTelegramUserId())) {
                userEntity.setTelegramUserId(update.getMessage().getFrom().getId().toString());
                userEntity.setFullName(update.getMessage().getFrom().getFirstName() + " " + update.getMessage().getFrom().getLastName());
                userEntity.setTelegramUsername(telegramUsername);
                telegramJiraUserRepository.save(userEntity);
            }

            String text = update.getMessage().getText();

            if (text.startsWith("/mytask")) {
                handleGetJiraTaskByTelegramUsername(update, telegramUsername);
            } else if (text.startsWith("/task")) {
                String[] parts = text.split(" ");
                if (parts.length > 1) {
                    String memberTelegramUsername = parts[1];
                    handleGetJiraTaskByTelegramUsername(update, memberTelegramUsername);
                }
            }
        }
    }

    private void handleGetJiraTaskByTelegramUsername(Update update, String telegramUsername) {
        List<TaskDto> tasks = jiraService.searchTasksByTelegramUsername(telegramUsername);
        StringBuilder sb = new StringBuilder("📝 Task của ").append(telegramUsername).append(":\n");
        if (tasks.isEmpty()) {
            sb.append(" - Không có task nào cả");
        } else {
            for (TaskDto task : tasks) {
                sb.append("[").append(task.issueKey()).append("]")
                        .append("(http://14.224.201.179:8032/browse/")
                        .append(task.issueKey()).append(")\n")
                        .append("Summary: ").append(task.summary()).append("\n")
                        .append("Due date: ").append(task.dueDate()).append("\n")
                        .append("Time spent: ").append(task.timeSpent()).append("\n\n");
            }
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
