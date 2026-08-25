package com.jarvis.notification.service;

import com.jarvis.notification.dto.Issue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final TelegramService telegramService;
    @Value("${jira.base-url}")
    private String baseUrl;
//    private final NotificationLogRepository logRepository;

    public void notifyAssign(Issue issue) {
        log.debug("notifyAssign issue={}", issue != null ? issue.getKey() : null);

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

    public void notifyDue(Issue issue, String type,String projectName) {
        log.debug("notifyDue type={} project={} issue={}", type, projectName, issue != null ? issue.getKey() : null);

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

        telegramService.send(msg);
        log.info("Notification sent type={} project={} issue={}", type, projectName, issue.getKey());
//        logRepository.save(issue.getKey(), type);
    }

    public void notifyDeadline(Issue issue, String projectName, String deadlineValue, String deadlineFieldKey, Duration overdue) {
        log.debug("notifyDeadline project={} issue={} deadlineFieldKey={} deadlineValue={}",
                projectName, issue != null ? issue.getKey() : null, deadlineFieldKey, deadlineValue);

        String overdueText = formatOverdue(overdue);

        String msg = """
* -- %s --- *
🚨 *DEADLINE_NOW*
━━━━━━━━━━━━━━
📌 *Task:*  %s %s
📝 *Task name:*  %s
👤 *Assignee:* %s
⏰ *Deadline:* %s
⏱ *Late:* %s
""".formatted(
                projectName,
                issue.getKey(),
                baseUrl + "/browse/" + issue.getKey(),
                issue.getFields().getSummary(),
                issue.getFields().getAssignee() != null ? issue.getFields().getAssignee().getDisplayName() : "Unassign",
                deadlineValue,
                overdueText
        );

        telegramService.send(msg);
        log.info("Notification sent type=DEADLINE_NOW project={} issue={} deadlineFieldKey={}",
                projectName, issue.getKey(), deadlineFieldKey);
    }

    private String formatOverdue(Duration overdue) {
        if (overdue == null || overdue.isNegative() || overdue.isZero()) {
            return "0 minutes";
        }

        long totalMinutes = overdue.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours <= 0) {
            return minutes + " minutes";
        }

        if (minutes <= 0) {
            return hours + " hours";
        }

        return hours + " hours " + minutes + " minutes";
    }

    public void notifyDone(String msg) {
        log.debug("notifyDone messageLength={}", msg != null ? msg.length() : 0);
        telegramService.send(msg);
    }
}
