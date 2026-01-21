package com.jarvis.notification.dto;

import lombok.Data;

@Data
public class Fields {
    private String summary;
    private String duedate;
    private Assignee assignee;
    private Integer timeoriginalestimate;
    private Integer timespent;
    private String created;
}