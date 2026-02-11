package com.jarvis.notification.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "telegram_jira_user")
public class TelegramJiraUserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String telegramUserId;
    private String jiraUserId;
    private String fullName;
    private String telegramUsername;
}
