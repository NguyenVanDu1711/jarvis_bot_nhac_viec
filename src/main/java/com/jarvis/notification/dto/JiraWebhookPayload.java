package com.jarvis.notification.dto;

import lombok.Data;

@Data
public class JiraWebhookPayload {
    private String event;
    private Issue issue;
//    private Changelog changelog;
}