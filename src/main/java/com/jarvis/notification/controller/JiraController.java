package com.jarvis.notification.controller;

import com.jarvis.notification.dto.TaskDto;
import com.jarvis.notification.dto.TaskDetailDto;
import com.jarvis.notification.service.JiraService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class JiraController {

    private final JiraService jiraService;

    /**
     * Lấy danh sách task đang IN PROGRESS của user theo telegramId
     * Ví dụ: GET /tasks?telegramId=12345
     */
    @GetMapping
    public List<TaskDto> getTasks(@RequestParam String telegramId) {
        return jiraService.searchTasksByTelegramId(telegramId);
    }

    /**
     * Lấy chi tiết một task theo issueKey
     * Ví dụ: GET /tasks/detail?issueKey=PROJ-123
     */
    @GetMapping("/detail")
    public TaskDetailDto getTaskDetail(@RequestParam String issueKey) {
        return jiraService.getTaskDetail(issueKey);
    }
}
