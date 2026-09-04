package com.jarvis.notification.schedule;

import com.jarvis.notification.config.ProjectConfig;
import com.jarvis.notification.dto.Issue;
import com.jarvis.notification.dto.JiraSearchResponse;
import com.jarvis.notification.service.JiraService;
import com.jarvis.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class EndOfDayReportScheduler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JiraService jiraService;
    private final NotificationService notificationService;
    private final ProjectConfig projectConfig;

    @Value("${jira.report-zone-id:Asia/Ho_Chi_Minh}")
    private String zoneId;

    @Value("${jira.report-status-jql:statusCategory = Done}")
    private String reportStatusJql;

    @Value("${jira.report-testing-jql:status = \"Testing\"}")
    private String reportTestingJql;

    @Value("${jira.report-projects:TOTO}")
    private String reportProjects;

    @Value("${jira.report-output-dir:reports}")
    private String reportOutputDir;

    @Value("${jira.deadline-output-dir:reports}")
    private String deadlineOutputDir;

    // Cuối ngày 18:00
    @Scheduled(
            cron = "${scheduler.done-last-day-cron:0 0 18 * * MON-FRI}",
            zone = "${scheduler.done-last-day-zone:Asia/Ho_Chi_Minh}")
//    @Scheduled(cron = "0 */1 * * * ?")
    public void sendEndOfDayReport() {
        log.info("EndOfDayReportScheduler.sendEndOfDayReport started");

        ZoneId zone = ZoneId.of(zoneId);
        LocalDate today = LocalDate.now(zone);
        LocalDateTime generatedAt = LocalDateTime.now(zone);

        List<String> projects = projectConfig.list();
        List<DeadlineRecord> deadlineRecords = readDeadlineRecords(today);
        Map<String, List<DeadlineRecord>> recordsByProject = groupByProject(deadlineRecords);
        Set<String> lateKeys = new HashSet<>();
        for (DeadlineRecord record : deadlineRecords) {
            lateKeys.add(record.key());
        }

        int totalDoneLate = 0;
        int totalDoneOnTime = 0;
        int totalTesting = 0;
        int totalLate = deadlineRecords.size();
        int totalDueToday = 0;
        int totalNotDone = 0;

        List<ProjectSummary> summaries = new ArrayList<>();

        for (String project : projects) {
            JiraSearchResponse dueTodayResponse = jiraService.search(buildDueTodayJql(project, today));
            JiraSearchResponse doneResponse = jiraService.search(buildDoneTodayJql(project, today));
            JiraSearchResponse testingResponse = jiraService.search(buildTestingTodayJql(project, today));

            List<Issue> dueTodayIssues = safeIssues(dueTodayResponse);
            List<Issue> doneIssues = safeIssues(doneResponse);
            List<Issue> testingIssues = safeIssues(testingResponse);

            Set<String> doneKeys = extractKeys(doneIssues);
            Set<String> testingKeys = extractKeys(testingIssues);

            List<Issue> doneLateIssues = new ArrayList<>();
            List<Issue> doneOnTimeIssues = new ArrayList<>();
            for (Issue issue : doneIssues) {
                if (lateKeys.contains(issue.getKey())) {
                    doneLateIssues.add(issue);
                } else {
                    doneOnTimeIssues.add(issue);
                }
            }

            List<Issue> notDoneIssues = new ArrayList<>();
            for (Issue issue : dueTodayIssues) {
                if (!doneKeys.contains(issue.getKey()) && !testingKeys.contains(issue.getKey())) {
                    notDoneIssues.add(issue);
                }
            }

            int projectDueToday = dueTodayIssues.size();
            int projectDoneLate = doneLateIssues.size();
            int projectDoneOnTime = doneOnTimeIssues.size();
            int projectTesting = testingIssues.size();
            int projectNotDone = notDoneIssues.size();

            totalDueToday += projectDueToday;
            totalDoneLate += projectDoneLate;
            totalDoneOnTime += projectDoneOnTime;
            totalTesting += projectTesting;
            totalNotDone += projectNotDone;

            summaries.add(new ProjectSummary(
                    project,
                    projectDueToday,
                    projectDoneLate,
                    projectDoneOnTime,
                    projectTesting,
                    projectNotDone,
                    recordsByProject.getOrDefault(project, List.of()),
                    dueTodayIssues,
                    doneKeys,
                    lateKeys,
                    doneLateIssues,
                    doneOnTimeIssues,
                    testingIssues,
                    notDoneIssues
            ));

            String txtReport = buildTextReport(today, generatedAt, summaries, totalDueToday, totalDoneLate, totalDoneOnTime, totalTesting, totalLate, totalNotDone);
            writeReportFile(today, txtReport, project);
//            notificationService.notifyDone(txtReport);

            log.info("EndOfDayReportScheduler.sendEndOfDayReport finished project={} totalDueToday={} totalDoneLate={} totalDoneOnTime={} totalTesting={} totalLate={} totalNotDone={}",
                    project, totalDueToday, totalDoneLate, totalDoneOnTime, totalTesting, totalLate, totalNotDone);
        }
    }

    private String buildTextReport(LocalDate today,
                                   LocalDateTime generatedAt,
                                   List<ProjectSummary> summaries,
                                   int totalDueToday,
                                   int totalDoneLate,
                                   int totalDoneOnTime,
                                   int totalTesting,
                                   int totalLate,
                                   int totalNotDone) {
        StringBuilder sb = new StringBuilder();
        int totalCompleted = totalDoneLate + totalDoneOnTime;
        int completionRate = totalDueToday == 0 ? 100 : Math.round((totalCompleted * 100f) / totalDueToday);
        String badge = rateBadge(completionRate, totalLate, totalNotDone);

        sb.append("📊 *END OF DAY REPORT* ").append(badge).append("\n");
        sb.append("📅 *Date:* ").append(today.format(DATE_FORMATTER)).append("\n");
        sb.append("🕒 *Generated:* ").append(generatedAt.format(DATETIME_FORMATTER)).append("\n");
        sb.append("🕑 *Timezone:* ").append(zoneId).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🔎 *Overall*\n");
        sb.append("• Due today: *").append(totalDueToday).append("*\n");
        sb.append("• Done late: *").append(totalDoneLate).append("*\n");
        sb.append("• Done on time: *").append(totalDoneOnTime).append("*\n");
        sb.append("• Testing: *").append(totalTesting).append("*\n");
        sb.append("• Not done: *").append(totalNotDone).append("*\n");
        sb.append("• Late records: *").append(totalLate).append("*\n");
        sb.append("• Completion rate: *").append(completionRate).append("%*\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");

        sb.append("⚠️ *Late Tasks*\n");
        int lateTaskCount = 0;
        for (ProjectSummary summary : summaries) {
            if (!summary.lateRecords().isEmpty()) {
                sb.append("🚨 *").append(summary.project()).append("*\n");
                for (DeadlineRecord record : summary.lateRecords()) {
                    lateTaskCount++;
                    sb.append("🔴 ").append(record.assignee()).append(" chậm task ").append(record.key())
                            .append(" - ").append(record.summary()).append("\n");
                    sb.append("   ⏰ Deadline: ").append(record.deadline())
                            .append(" | ⌛ Chậm: ").append(record.overdueMinutes()).append(" phút")
                            .append("\n");
                }
                sb.append("\n");
            }
        }

        // sb.append("1. *Task đã hoàn thành nhưng chậm*\n");
        sb.append("2. *Task hoàn thành đúng thời gian*\n");
        int onTimeCount = 0;
        for (ProjectSummary summary : summaries) {
            if (!summary.doneOnTimeIssues().isEmpty()) {
                sb.append("🟢 *").append(summary.project()).append("*\n");
                for (Issue issue : summary.doneOnTimeIssues()) {
                    onTimeCount++;
                    String assignee = issue.getFields() != null && issue.getFields().getAssignee() != null
                            ? issue.getFields().getAssignee().getDisplayName()
                            : "Unassign";
                    String summaryText = issue.getFields() != null ? issue.getFields().getSummary() : "";
                    sb.append("🟢 ").append(assignee).append(" done on time ").append(issue.getKey())
                            .append(" - ").append(summaryText).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("3. *Task đang Testing*\n");
        int testingCount = 0;
        for (ProjectSummary summary : summaries) {
            if (!summary.testingIssues().isEmpty()) {
                sb.append("🟡 *").append(summary.project()).append("*\n");
                for (Issue issue : summary.testingIssues()) {
                    testingCount++;
                    String assignee = issue.getFields() != null && issue.getFields().getAssignee() != null
                            ? issue.getFields().getAssignee().getDisplayName()
                            : "Unassign";
                    String summaryText = issue.getFields() != null ? issue.getFields().getSummary() : "";
                    sb.append("🟡 ").append(assignee).append(" is testing ").append(issue.getKey())
                            .append(" - ").append(summaryText).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("4. *Not done - cần chú ý ngay*\n");
        int notDoneCount = 0;
        for (ProjectSummary summary : summaries) {
            if (!summary.notDoneIssues().isEmpty()) {
                sb.append("🚨 *").append(summary.project()).append("*\n");
                for (Issue issue : summary.notDoneIssues()) {
                    notDoneCount++;
                    String assignee = issue.getFields() != null && issue.getFields().getAssignee() != null
                            ? issue.getFields().getAssignee().getDisplayName()
                            : "Unassign";
                    String summaryText = issue.getFields() != null ? issue.getFields().getSummary() : "";
                    sb.append("🔴 ").append(assignee).append(" chưa xong task ").append(issue.getKey())
                            .append(" - ").append(summaryText).append("\n");
                    sb.append("   ⚠️ Nghiêm trọng: task đã tới hạn nhưng chưa ở Done/Testing, có thể ảnh hưởng deadline, phụ thuộc sprint và cam kết đầu ra.\n");
                }
                sb.append("\n");
            }
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🚩 *Summary*\n");
        sb.append("• Late done: *").append(lateTaskCount).append("*\n");
        sb.append("• Done on time: *").append(onTimeCount).append("*\n");
        sb.append("• Testing: *").append(testingCount).append("*\n");
        sb.append("• Not done: *").append(notDoneCount).append("*\n");
        sb.append("• Total due today: *").append(totalDueToday).append("*\n");
        sb.append("• Completion rate: *").append(completionRate).append("%*\n");
        sb.append("• Late source: `deadline-log-").append(today.format(DATE_FORMATTER)).append(".txt`\n");
        return sb.toString();
    }

    private String rateBadge(int completionRate, int totalLate, int totalNotDone) {
        if (totalLate > 0) {
            return "🔴";
        }
        if (totalNotDone > 0) {
            return "🟠";
        }
        if (completionRate >= 95) {
            return "🟢";
        }
        return "🟡";
    }

    private String buildDueTodayJql(String project, LocalDate today) {
        return String.format("""
                project = %s
                AND duedate = %s
                AND assignee IS NOT EMPTY
                """, project, today);
    }

    private String buildDoneTodayJql(String project, LocalDate today) {
        return String.format("""
                project = %s
                AND duedate = %s
                AND statusCategory = Done
                AND assignee IS NOT EMPTY
                """, project, today);
    }

    private String buildTestingTodayJql(String project, LocalDate today) {
        return String.format("""
                project = %s
                AND duedate = %s
                AND (%s)
                AND assignee IS NOT EMPTY
                """, project, today, reportTestingJql);
    }

    private void writeReportFile(LocalDate today, String content, String project) {
        try {
            Path dir = Paths.get(reportOutputDir);
            Files.createDirectories(dir);

            Path file = dir.resolve("end-of-day-" + project + "-" + today.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".txt");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            log.info("EndOfDayReportScheduler wrote report file={}", file.toAbsolutePath());
        } catch (IOException e) {
            log.error("EndOfDayReportScheduler failed to write report file", e);
        }
    }

    private List<Issue> safeIssues(JiraSearchResponse response) {
        return response != null && response.getIssues() != null ? response.getIssues() : List.of();
    }

    private Set<String> extractKeys(List<Issue> issues) {
        Set<String> keys = new HashSet<>();
        for (Issue issue : issues) {
            keys.add(issue.getKey());
        }
        return keys;
    }

    private List<String> parseProjects(String projectsValue) {
        List<String> projects = new ArrayList<>();
        if (projectsValue == null || projectsValue.isBlank()) {
            projects.add("TOTO");
            return projects;
        }

        for (String project : projectsValue.split(",")) {
            if (!project.isBlank()) {
                projects.add(project.trim());
            }
        }
        return projects;
    }

    private List<DeadlineRecord> readDeadlineRecords(LocalDate today) {
        Path file = Paths.get(deadlineOutputDir, "deadline-log-" + today.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".txt");
        if (!Files.exists(file)) {
            log.info("EndOfDayReportScheduler deadline log file not found: {}", file.toAbsolutePath());
            return List.of();
        }

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<DeadlineRecord> records = new ArrayList<>();
            for (String line : lines) {
                DeadlineRecord record = parseDeadlineLine(line);
                if (record != null) {
                    records.add(record);
                }
            }
            log.info("EndOfDayReportScheduler loaded {} deadline records from {}", records.size(), file.toAbsolutePath());
            return records;
        } catch (IOException e) {
            log.error("EndOfDayReportScheduler failed to read deadline log file", e);
            return List.of();
        }
    }

    private DeadlineRecord parseDeadlineLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        if (line.startsWith("project\tkey\t")) {
            return null;
        }

        String[] parts = line.split("\t");
        if (parts.length < 8) {
            return null;
        }

        return new DeadlineRecord(
                valueOf(parts[0]),
                valueOf(parts[1]),
                valueOf(parts[2]),
                valueOf(parts[3]),
                valueOf(parts[4]),
                valueOf(parts[5]),
                valueOf(parts[6]),
                valueOf(parts[7])
        );
    }

    private String valueOf(String segment) {
        int index = segment.indexOf('=');
        if (index < 0) {
            return "";
        }
        return segment.substring(index + 1).trim();
    }

    private Map<String, List<DeadlineRecord>> groupByProject(List<DeadlineRecord> records) {
        Map<String, List<DeadlineRecord>> grouped = new LinkedHashMap<>();
        for (DeadlineRecord record : records) {
            grouped.computeIfAbsent(record.project(), k -> new ArrayList<>()).add(record);
        }
        return grouped;
    }

    private String findDeadline(List<DeadlineRecord> records, String key) {
        for (DeadlineRecord record : records) {
            if (record.key().equals(key)) {
                return record.deadline();
            }
        }
        return "";
    }

    private String findOverdueMinutes(List<DeadlineRecord> records, String key) {
        for (DeadlineRecord record : records) {
            if (record.key().equals(key)) {
                return record.overdueMinutes();
            }
        }
        return "";
    }

    private record DeadlineRecord(
            String project,
            String key,
            String summary,
            String assignee,
            String duedate,
            String deadline,
            String overdueMinutes,
            String checkedAt
    ) {}

    private record ProjectSummary(
            String project,
            int totalDueToday,
            int doneLateCount,
            int doneOnTimeCount,
            int testingCount,
            int notDone,
            List<DeadlineRecord> lateRecords,
            List<Issue> dueTodayIssues,
            Set<String> doneKeys,
            Set<String> lateKeys,
            List<Issue> doneLateIssues,
            List<Issue> doneOnTimeIssues,
            List<Issue> testingIssues,
            List<Issue> notDoneIssues
    ) {}
}
