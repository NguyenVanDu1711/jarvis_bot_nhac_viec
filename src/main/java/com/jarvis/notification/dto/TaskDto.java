package com.jarvis.notification.dto;

public record TaskDto(
        String issueKey,
        String summary,
        String dueDate,
        Integer timeSpent
) {}
