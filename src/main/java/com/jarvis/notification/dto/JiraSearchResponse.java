package com.jarvis.notification.dto;

import lombok.Data;

import java.util.List;

@Data
public class JiraSearchResponse {
    private List<Issue> issues;
}

