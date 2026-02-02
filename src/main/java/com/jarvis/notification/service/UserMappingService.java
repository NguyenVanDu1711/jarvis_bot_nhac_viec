package com.jarvis.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.notification.dto.UserMapping;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserMappingService {

    private final Map<String, UserMapping> telegramToJira = new HashMap<>();

    @Getter
    private List<UserMapping> allMappings;

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            allMappings = mapper.readValue(
                    new File("user-mapping.json"),
                    new TypeReference<List<UserMapping>>() {}
            );
            for (UserMapping m : allMappings) {
                telegramToJira.put(m.telegramId(), m);
            }
            log.info("Loaded {} user mappings", allMappings.size());
        } catch (IOException e) {
            log.error("Failed to load user-mapping.json", e);
        }
    }

    public String findJiraUserIdByTelegramId(String telegramId) {
        UserMapping mapping = telegramToJira.get(telegramId);
        return mapping != null ? mapping.jiraUserId() : null;
    }

    public String findFullNameByTelegramId(String telegramId) {
        UserMapping mapping = telegramToJira.get(telegramId);
        return mapping != null ? mapping.fullName() : null;
    }
}
