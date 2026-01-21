package com.jarvis.notification.service;

import com.jarvis.notification.dto.JiraWebhookPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JiraWebhookService {

    private final NotificationService notificationService;

    public void process(JiraWebhookPayload payload) {

        if (!"jira:issue_updated".equals(payload.getEvent())) return;

//        payload.getChangelog().getItems().forEach(item -> {
//            if ("assignee".equals(item.getField())) {
//                notificationService.notifyAssign(payload.getIssue());
//            }
//            if ("status".equals(item.getField())) {
//                notificationService.notifyStatus(payload.getIssue(), item);
//            }
//        });
    }
}