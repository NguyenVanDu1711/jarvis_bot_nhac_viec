package com.jarvis.notification.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class Fields {
    private String summary;
    private String duedate;
    private String deadline;
    private Assignee assignee;
    private Integer timeoriginalestimate;
    private Integer timespent;
    private String created;
    private Map<String, Object> customFields = new HashMap<>();

    @JsonAnySetter
    public void addCustomField(String key, Object value) {
        customFields.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> any() {
        return customFields;
    }

    public String getCustomFieldAsString(String key) {
        Object value = customFields.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    public String dumpCustomFields() {
        return customFields.toString();
    }
}
