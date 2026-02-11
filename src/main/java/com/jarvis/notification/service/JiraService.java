package com.jarvis.notification.service;

import com.jarvis.notification.dto.JiraSearchResponse;
import com.jarvis.notification.dto.TaskDto;
import com.jarvis.notification.dto.TaskDetailDto;
import com.jarvis.notification.entity.TelegramJiraUserEntity;
import com.jarvis.notification.repository.TelegramJiraUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JiraService {

    @Value("${jira.base-url}")
    private String baseUrl;

    @Value("${jira.username}")
    private String jiraUsername;

    @Value("${jira.password}")
    private String password;

    private final RestTemplate restTemplate;
    private final TelegramJiraUserRepository telegramJiraUserRepository;

    public List<TaskDto> searchTasksByTelegramUsername(String username) {
        TelegramJiraUserEntity userEntity = telegramJiraUserRepository.findByTelegramUsername(username).orElse(null);
        if (userEntity == null) {
            throw new IllegalArgumentException("No Jira user found for telegram username=" + username);
        }

        String jql = "assignee = \"" + userEntity.getJiraUserId() + "\" AND status = \"IN PROGRESS\"";

        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/rest/api/2/search")
                .queryParam("jql", jql)
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(jiraUsername, password);
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
                        issue.getFields().getDuedate() != null ? issue.getFields().getDuedate() : "Chưa set deadline",
                        (issue.getFields().getTimespent() != null ? issue.getFields().getTimespent() / 60  / 60 : "Chưa log ") + "h"
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
        headers.setBasicAuth(jiraUsername, password);
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
        headers.setBasicAuth(jiraUsername, password);

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

    private String changeFormat(String jiraTime){

        try {
            DateTimeFormatter inputFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

            DateTimeFormatter outputFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            ZonedDateTime zonedDateTime = ZonedDateTime.parse(jiraTime, inputFormatter);

            String formattedTime = zonedDateTime.format(outputFormatter);
            return  formattedTime;
        }catch (Exception e){
            return null;
        }

    }
}
