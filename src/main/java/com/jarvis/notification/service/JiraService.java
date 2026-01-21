package com.jarvis.notification.service;

import com.jarvis.notification.dto.JiraSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    public JiraSearchResponse search(String jql) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/rest/api/2/search")
                .queryParam("jql", jql)   // ❗ KHÔNG encode tay
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