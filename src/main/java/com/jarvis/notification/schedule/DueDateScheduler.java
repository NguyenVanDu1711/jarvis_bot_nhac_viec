package com.jarvis.notification.schedule;

import com.jarvis.notification.dto.JiraSearchResponse;
import com.jarvis.notification.service.JiraService;
import com.jarvis.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DueDateScheduler {

    private final JiraService jiraService;
    private final NotificationService notificationService;

//    @Scheduled(cron = "${scheduler.due-cron}")
//    @Scheduled(cron = "0 */1 * * * ?")
    public void remindDue() {
        log.info("DueDateScheduler.remindDue started");
//        String jql = "project = HUB AND duedate < startOfDay() " +
//                "AND statusCategory != Done " +
//                "AND assignee IS NOT EMPTY";
        List<String> listProjects = new ArrayList<>();
//        listProjects.add("TOTO");
        listProjects.add("TOTO");
//        listProjects.add("EDU");  AND duedate < startOfDay()

        for (String project: listProjects) {
            log.debug("DueDateScheduler processing project={}", project);
            String jql = String.format( """
                project = %s
                AND  duedate < startOfDay()
                AND statusCategory != Done
                AND assignee IS NOT EMPTY
                """, project);

            log.debug("DueDateScheduler JQL for project {}: {}", project, jql.replace("\n", " ").trim());

            JiraSearchResponse response =
                    jiraService.search(jql);

            int issueCount = response != null && response.getIssues() != null ? response.getIssues().size() : 0;
            log.info("DueDateScheduler project={} returned {} issues", project, issueCount);

            if (response != null && response.getIssues() != null) {
                response.getIssues().forEach(issue -> {
                            log.debug("DueDateScheduler notifying issue={} dueDate={}", issue.getKey(),
                                    issue.getFields() != null ? issue.getFields().getDuedate() : null);
                            notificationService.notifyDue(issue, "DUE_SOON",project);
                        }
                );
            }
        }

        log.info("DueDateScheduler.remindDue finished");

    }

   // @Scheduled(cron = "${scheduler.done-last-day-cron}")
    public void listTaskDoneLastDay() {
        log.info("DueDateScheduler.listTaskDoneLastDay started");
//        String jql = "project = HUB AND duedate < startOfDay() " +
//                "AND statusCategory != Done " +
//                "AND assignee IS NOT EMPTY";
        List<String> listProjects = new ArrayList<>();
//        listProjects.add("HUB");
        listProjects.add("TOTO");
//        listProjects.add("GAM");

        for (String project: listProjects) {
            log.debug("DueDateScheduler daily report processing project={}", project);
//            String jql = String.format( """
//                project = "GAM"
//                 AND Sprint in openSprints()
//                    AND status = "TASK DONE IN DAY"
//                """, project);

            StringBuilder jqlBuilder = new StringBuilder();
            jqlBuilder.append("project = ")
                    .append(project)
                    .append(" ")
                    .append("AND Sprint in openSprints() ")
                    .append("AND status = \"TASK DONE IN DAY\"");

            String jql = jqlBuilder.toString();
            log.debug("DueDateScheduler daily report JQL for project {}: {}", project, jql);

            JiraSearchResponse response = jiraService.search(jql);
            int issueCount = response != null && response.getIssues() != null ? response.getIssues().size() : 0;
            log.info("DueDateScheduler daily report project={} returned {} issues", project, issueCount);
//            if(response.getIssues().isEmpty()){
//                String msg = String.format("\"\\uD83D\\uDCCA *Daily Report *\\\\nHôm nay chưa có task nào hoàn thành.\"");
//                notificationService.notifyDone(msg);
//            }
            StringBuilder message = new StringBuilder("📊 *Daily Report *");
            message.append(project);
            message.append("\n");
            message.append("✅ Task hoàn thành hôm nay:\n");
            if(response.getIssues().isEmpty()){
                message.append("     - Hôm nay chưa có task nào hoàn thành.");
            }

            response.getIssues().forEach(issue ->
                    log.debug("DueDateScheduler daily report issue={} summary={}", issue.getKey(),
                            issue.getFields() != null ? issue.getFields().getSummary() : null)
            );

            response.getIssues().forEach(issue ->
                    message.append("- ")
                            .append(issue.getKey()).append(" \n")
                            .append(" | *Task Name* :")
                            .append(issue.getFields().getSummary()).append(" \n")
                            .append(" \t *Assignee* : ")
                            .append( issue.getFields().getAssignee() != null ? issue.getFields().getAssignee().getDisplayName() : "Unassign").append(" \n")
                            .append("\t * Created *").append(this.changeFormat(issue.getFields().getCreated())).append(" \n")
                            .append("\t * ⏱ Spent: *" ).append(issue.getFields().getTimespent() != null ? (issue.getFields().getTimespent()  / 3600) + " Hour" : "Chưa log!")
                            .append(" / * Estimate: * ").append( issue.getFields().getTimeoriginalestimate() != null ?  (issue.getFields().getTimeoriginalestimate()  / 3600) + " Hour" : "Chưa ước tính").append(" Hour").append(" \n")
                            .append("\t * Due date : * ").append(issue.getFields().getDuedate() != null ? issue.getFields().getDuedate() : "Chưa có!").append(" \n")
                            .append(" \n")
            );
            message.append("\n➡️ Tổng: ").append(response.getIssues().size()).append(" task");

            notificationService.notifyDone(message.toString());
        }
        log.info("DueDateScheduler.listTaskDoneLastDay finished");

    }

    private String changeFormat(String jiraTime){

        try {
            DateTimeFormatter inputFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

            DateTimeFormatter outputFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd :HH:mm:ss");

            ZonedDateTime zonedDateTime = ZonedDateTime.parse(jiraTime, inputFormatter);

            String formattedTime = zonedDateTime.format(outputFormatter);
            return  formattedTime;
        }catch (Exception e){
            return null;
        }

    }

}
