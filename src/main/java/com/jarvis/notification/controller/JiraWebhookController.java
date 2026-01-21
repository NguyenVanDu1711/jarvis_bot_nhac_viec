package com.jarvis.notification.controller;

import com.jarvis.notification.dto.JiraWebhookPayload;
import com.jarvis.notification.service.JiraWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jira")
@RequiredArgsConstructor
public class JiraWebhookController {

    private final JiraWebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(@RequestBody JiraWebhookPayload payload) {
        webhookService.process(payload);
        return ResponseEntity.ok().build();
    }
}