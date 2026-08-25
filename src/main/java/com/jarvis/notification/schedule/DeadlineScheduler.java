package com.jarvis.notification.schedule;

import com.jarvis.notification.dto.Fields;
import com.jarvis.notification.dto.Issue;
import com.jarvis.notification.dto.JiraSearchResponse;
import com.jarvis.notification.service.JiraService;
import com.jarvis.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadlineScheduler {

    private static final DateTimeFormatter DEADLINE_OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yy HH:mm");

    private static final DateTimeFormatter DEADLINE_INPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final JiraService jiraService;
    private final NotificationService notificationService;

    @Value("${jira.deadline-field-key:customfield_10301}")
    private String deadlineFieldKey;

    @Value("${jira.deadline-zone-id:Asia/Ho_Chi_Minh}")
    private String zoneId;

    @Value("${jira.deadline-status-jql:(status = \"To Do\" OR status = \"In Progress\")}")
    private String deadlineStatusJql;

    @Value("${jira.deadline-output-dir:reports}")
    private String deadlineOutputDir;

    // Chạy mỗi 30 phút
    @Scheduled(cron = "0 */30 * * * ?")
    public void remindDeadline() {
        log.info("DeadlineScheduler.remindDeadline started");

        ZoneId zone = ZoneId.of(zoneId);
        OffsetDateTime now = OffsetDateTime.now(zone);
        LocalTime currentTime = now.toLocalTime();
        log.debug("DeadlineScheduler currentNow={}", now);

        if (!isWithinWorkingHours(currentTime)) {
            log.info("DeadlineScheduler skipped because currentTime={} is outside working hours", currentTime);
            return;
        }

        List<String> listProjects = new ArrayList<>();
        listProjects.add("TOTO");

        for (String project : listProjects) {
            log.debug("DeadlineScheduler processing project={}", project);

            String jql = String.format("""
                project = %s
                AND duedate = startOfDay()
                AND %s
                AND assignee IS NOT EMPTY
                """, project, deadlineStatusJql);

            log.debug("DeadlineScheduler JQL for project {}: {}", project, jql.replace("\n", " ").trim());

            JiraSearchResponse response = jiraService.search(jql);
            int issueCount = response != null && response.getIssues() != null ? response.getIssues().size() : 0;
            log.info("DeadlineScheduler project={} returned {} issues from Jira", project, issueCount);

            if (response == null || response.getIssues() == null) {
                continue;
            }

            for (Issue issue : response.getIssues()) {
                Fields fields = issue.getFields();
                String rawDeadline = resolveDeadline(fields);

                log.debug("DeadlineScheduler issue={} customFields={}",
                        issue.getKey(), fields != null ? fields.dumpCustomFields() : "{}");
                log.debug("DeadlineScheduler issue={} rawDeadline={}", issue.getKey(), rawDeadline);

                if (rawDeadline == null || rawDeadline.isBlank()) {
                    log.debug("DeadlineScheduler skip issue={} because deadline is empty", issue.getKey());
                    continue;
                }

                OffsetDateTime deadlineTime = parseDeadline(rawDeadline);
                if (deadlineTime == null) {
                    log.warn("DeadlineScheduler skip issue={} because deadline format is invalid: {}",
                            issue.getKey(), rawDeadline);
                    continue;
                }

                boolean shouldSend = deadlineTime.isBefore(now);
                Duration overdue = shouldSend ? Duration.between(deadlineTime, now) : Duration.ZERO;

                log.debug("DeadlineScheduler issue={} parsedDeadline={} shouldSend={} overdueMinutes={}",
                        issue.getKey(), deadlineTime, shouldSend, overdue.toMinutes());

                if (shouldSend) {
                    appendDeadlineRecord(project, issue, deadlineTime, overdue);
                    notificationService.notifyDeadline(
                            issue,
                            project,
                            formatDeadline(deadlineTime),
                            deadlineFieldKey,
                            overdue
                    );
                }
            }
        }

        log.info("DeadlineScheduler.remindDeadline finished");
    }

    private void appendDeadlineRecord(String project, Issue issue, OffsetDateTime deadlineTime, Duration overdue) {
        try {
            ZoneId zone = ZoneId.of(zoneId);
            LocalDate reportDate = LocalDate.now(zone);
            Path dir = Paths.get(deadlineOutputDir);
            Files.createDirectories(dir);

            Path file = dir.resolve("deadline-log-" + reportDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".txt");
            String issueKey = issue.getKey();
            String deadlineValue = formatDeadline(deadlineTime);

            if (isAlreadyRecorded(file, issueKey, deadlineValue)) {
                log.debug("DeadlineScheduler skip writing duplicate record issue={} deadline={}", issueKey, deadlineValue);
                return;
            }

            ensureHeader(file);

            String line = String.join("\t",
                    "project=" + project,
                    "key=" + issueKey,
                    "summary=" + safe(issue.getFields() != null ? issue.getFields().getSummary() : null),
                    "assignee=" + safe(issue.getFields() != null && issue.getFields().getAssignee() != null
                            ? issue.getFields().getAssignee().getDisplayName()
                            : "Unassign"),
                    "duedate=" + safe(issue.getFields() != null ? issue.getFields().getDuedate() : null),
                    "deadline=" + deadlineValue,
                    "overdueMinutes=" + overdue.toMinutes(),
                    "checkedAt=" + OffsetDateTime.now(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ) + System.lineSeparator();

            Files.writeString(file, line, StandardCharsets.UTF_8,
                    Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
            log.debug("DeadlineScheduler wrote deadline record file={} issue={}", file.toAbsolutePath(), issueKey);
        } catch (IOException e) {
            log.error("DeadlineScheduler failed to write deadline record for issue={}", issue != null ? issue.getKey() : null, e);
        }
    }

    private void ensureHeader(Path file) throws IOException {
        if (Files.exists(file) && Files.size(file) > 0) {
            return;
        }

        String header = String.join("\t",
                "project",
                "key",
                "summary",
                "assignee",
                "duedate",
                "deadline",
                "overdueMinutes",
                "checkedAt"
        ) + System.lineSeparator();

        Files.writeString(file, header, StandardCharsets.UTF_8,
                Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
    }

    private boolean isAlreadyRecorded(Path file, String issueKey, String deadlineValue) {
        if (!Files.exists(file)) {
            return false;
        }

        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("project\tkey\t"))
                    .anyMatch(line -> line.contains("\tkey=" + issueKey + "\t") && line.contains("\tdeadline=" + deadlineValue + "\t"));
        } catch (IOException e) {
            log.warn("DeadlineScheduler cannot check duplicate record file={}", file.toAbsolutePath(), e);
            return false;
        }
    }

    private boolean isWithinWorkingHours(LocalTime currentTime) {
        LocalTime morningStart = LocalTime.of(9, 0);
        LocalTime lunchStart = LocalTime.of(11, 30);
        LocalTime lunchEnd = LocalTime.of(13, 30);
        LocalTime endOfDay = LocalTime.of(18, 0);

        return !currentTime.isBefore(morningStart)
                && currentTime.isBefore(lunchStart)
                || !currentTime.isBefore(lunchEnd)
                && currentTime.isBefore(endOfDay);
    }

    private String resolveDeadline(Fields fields) {
        if (fields == null) {
            return null;
        }

        String direct = fields.getDeadline();
        if (direct != null && !direct.isBlank()) {
            return direct;
        }

        String customValue = fields.getCustomFieldAsString(deadlineFieldKey);
        if (customValue != null && !customValue.isBlank()) {
            return customValue;
        }

        return null;
    }

    private OffsetDateTime parseDeadline(String rawDeadline) {
        try {
            return OffsetDateTime.parse(rawDeadline, DEADLINE_INPUT_FORMATTER);
        } catch (DateTimeParseException ex) {
            log.warn("DeadlineScheduler cannot parse deadline value={}", rawDeadline);
            return null;
        }
    }

    private String formatDeadline(OffsetDateTime deadlineTime) {
        return deadlineTime.atZoneSameInstant(ZoneId.of(zoneId)).format(DEADLINE_OUTPUT_FORMATTER);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\n", " ").trim();
    }
}
