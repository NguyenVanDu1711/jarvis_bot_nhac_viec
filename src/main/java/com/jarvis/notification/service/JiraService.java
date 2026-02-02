package com.jarvis.notification.service;

import com.jarvis.notification.dto.JiraSearchResponse;
import com.jarvis.notification.dto.TaskDto;
import com.jarvis.notification.dto.TaskDetailDto;
import com.jarvis.notification.dto.UserMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JiraService {

    @Value("${jira.base-url}")
    private String baseUrl;

    @Value("${jira.username}")
    private String username;

    @Value("${jira.password}")
    private String password;

    private final RestTemplate restTemplate;
    private final UserMappingService userMappingService; // đọc từ file JSON

    public List<TaskDto> searchTasksByTelegramId(String telegramId) {
        String jiraUserId = userMappingService.findJiraUserIdByTelegramId(telegramId);
        if (jiraUserId == null) {
            throw new IllegalArgumentException("No Jira user found for telegramId=" + telegramId);
        }

        String jql = "assignee=" + jiraUserId + " AND status=\"In Progress\"";

        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/rest/api/2/search")
                .queryParam("jql", jql)
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<JiraSearchResponse> response =
                restTemplate.exchange(uri, HttpMethod.GET, entity, JiraSearchResponse.class);

        JiraSearchResponse body = response.getBody();
        List<TaskDto> tasks = new ArrayList<>();
        if (body != null && body.getIssues() != null) {
            body.getIssues().forEach(issue -> {
                tasks.add(new TaskDto(
                        issue.getKey(),
                        issue.getFields().getSummary(),
                        issue.getFields().getDuedate(),
                        issue.getFields().getTimespent()
                ));
            });
        }
        return tasks;
    }

    public TaskDetailDto getTaskDetail(String issueKey) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/rest/api/2/issue/" + issueKey)
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);

        Map body = response.getBody();
        Map fields = (Map) body.get("fields");

        return new TaskDetailDto(
                (String) body.get("key"),
                (String) fields.get("summary"),
                (String) fields.get("description"),
                (String) fields.get("duedate"),
                fields.get("timespent") != null ? fields.get("timespent").toString() : null,
                fields.get("assignee") != null ? ((Map) fields.get("assignee")).get("displayName").toString() : null,
                fields.get("status") != null ? ((Map) fields.get("status")).get("name").toString() : null
        );
    }

    public JiraSearchResponse search(String jql) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/rest/api/2/search")
                .queryParam("jql", jql)
                .build()
                .encode()
                .toUri();
        String url = baseUrl + "rest/api/2/search?jql=" +
                     URLEncoder.encode(jql, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<JiraSearchResponse> response =
                restTemplate.exchange(
                        uri,
                        HttpMethod.GET,
                        entity,
                        JiraSearchResponse.class
                );

        return response.getBody();
    }
}
