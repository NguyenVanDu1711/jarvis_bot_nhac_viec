package com.jarvis.notification.repository;

import com.jarvis.notification.entity.TelegramJiraUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramJiraUserRepository extends JpaRepository<TelegramJiraUserEntity, Long> {
    Optional<TelegramJiraUserEntity> findByTelegramUsername(String telegramUsername);
}
