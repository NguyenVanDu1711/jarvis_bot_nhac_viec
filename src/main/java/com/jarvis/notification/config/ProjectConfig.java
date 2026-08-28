package com.jarvis.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "project")
public record ProjectConfig(List<String> list) {
}
