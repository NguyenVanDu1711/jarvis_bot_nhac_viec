package com.jarvis.notification.dto;

public record TaskDetailDto(
        String issueKey,
        String summary,
        String description,
        String dueDate,
        String timeSpent,
        String assignee,
        String status
) {}
