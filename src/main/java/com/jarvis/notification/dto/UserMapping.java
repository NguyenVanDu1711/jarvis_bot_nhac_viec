package com.jarvis.notification.dto;

public record UserMapping(
        String telegramId,
        String jiraUserId,
        String fullName
) {}
