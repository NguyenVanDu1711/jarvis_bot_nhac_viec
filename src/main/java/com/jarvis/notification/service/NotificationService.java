package com.jarvis.notification.service;

import com.jarvis.notification.dto.Issue;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final TelegramService telegramService;
    @Value("${jira.base-url}")
    private String baseUrl;
//    private final NotificationLogRepository logRepository;

    public void notifyAssign(Issue issue) {

//        if (logRepository.sent(issue.getKey(), "ASSIGN")) return;

        String msg = """
👤 *New Assignment*
📌 %s
📝 %s
""".formatted(
                issue.getKey(),
                issue.getFields().getSummary()
        );

        telegramService.send(msg);
//        logRepository.save(issue.getKey(), "ASSIGN");
    }
//
//    public void notifyStatus(Issue issue, ChangeItem item) {
//
//        if (logRepository.sent(issue.getKey(), "STATUS")) return;
//
//        String msg = """
//🔄 *Status Changed*
//📌 %s
//%s → %s
//""".formatted(
//                issue.getKey(),
//                item.getFromString(),
//                item.getToString()
//        );
//
//        telegramService.send(msg);
////        logRepository.save(issue.getKey(), "STATUS");
//    }

    public void notifyDue(Issue issue, String type,String projectName, String chatId) {

//        if (logRepository.sent(issue.getKey(), type)) return;

        String msg = """
* -- %s --- *
🚨 *%s*
━━━━━━━━━━━━━━
📌 *Task:*  %s %s
📝 *Task name:*  %s
👤 *Assignee:* %s 
⏰ *Due:*  %s
""".formatted(projectName,
                type,
                issue.getKey(),
                baseUrl + "/browse/"  + issue.getKey(),
                issue.getFields().getSummary(),
                issue.getFields().getAssignee().getDisplayName(),
                issue.getFields().getDuedate()
        );

        telegramService.send(msg, chatId);
//        logRepository.save(issue.getKey(), type);
    }
    public void notifyDone(String msg, String chatId) {
        telegramService.send(msg, chatId);
    }
}