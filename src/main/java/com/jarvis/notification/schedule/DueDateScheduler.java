package com.jarvis.notification.schedule;

import com.jarvis.notification.dto.JiraSearchResponse;
import com.jarvis.notification.service.JiraService;
import com.jarvis.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DueDateScheduler {

    private final JiraService jiraService;
    private final NotificationService notificationService;

//    @Scheduled(cron = "${scheduler.due-cron}")
//    @Scheduled(cron = "0 */1 * * * ?")
    public void remindDue() {
//        String jql = "project = HUB AND duedate < startOfDay() " +
//                "AND statusCategory != Done " +
//                "AND assignee IS NOT EMPTY";
        List<String> listProjects = new ArrayList<>();
        listProjects.add("HUB");
        listProjects.add("EDU");

        for (String project: listProjects) {
            String jql = String.format( """
                project = %s
                AND duedate < startOfDay()
                AND statusCategory != Done
                AND assignee IS NOT EMPTY
                """, project);

            JiraSearchResponse response =
                    jiraService.search(jql);

            response.getIssues().forEach(issue ->
                    notificationService.notifyDue(issue, "DUE_SOON",project)
            );
        }

    }

    @Scheduled(cron = "0 */1 * * * ?")
    public void listTaskDoneLastDay() {
//        String jql = "project = HUB AND duedate < startOfDay() " +
//                "AND statusCategory != Done " +
//                "AND assignee IS NOT EMPTY";
        List<String> listProjects = new ArrayList<>();
//        listProjects.add("HUB");
//        listProjects.add("EDU");
        listProjects.add("GAM");

        for (String project: listProjects) {
            String jql = String.format( """
                project = %s
                 AND Sprint in openSprints()
                    AND resolutiondate >= startOfDay()
                """, project);

            JiraSearchResponse response =
                    jiraService.search(jql);
            if(response.getIssues().isEmpty()){
                String msg = String.format("\"\\uD83D\\uDCCA *Daily Report *\\\\nHôm nay chưa có task nào hoàn thành.\"");
                notificationService.notifyDone(msg);
            }
            StringBuilder message = new StringBuilder("📊 *Daily Report *");
            message.append(project);
            message.append("\n");
            message.append("✅ Task hoàn thành hôm nay:\n");

            response.getIssues().forEach(issue ->


                    message.append("- ")
                            .append(issue.getKey())
                            .append(" | *Task Name* :")
                            .append(issue.getFields().getSummary())
                            .append(" \t *Assignee* : ")
                            .append( issue.getFields().getAssignee() != null ? issue.getFields().getAssignee().getDisplayName() : "UnAssignee")
                            .append("\t * Created *").append(this.changeFormat(issue.getFields().getCreated()))
                            .append("\t * ⏱ Spent: *" ).append(issue.getFields().getTimespent() != null ?  issue.getFields().getTimespent()  / 3600 : "0").append(" Hour")
                            .append(" / * Estimate: * ").append( issue.getFields().getTimeoriginalestimate() != null ?  issue.getFields().getTimeoriginalestimate()  / 3600 : "0").append(" Hour")
                            .append("\t * Due date : * ").append(issue.getFields().getDuedate())
                            .append(" \n")
            );
            message.append("\n➡️ Tổng: ").append(response.getIssues().size()).append(" task");

            notificationService.notifyDone(message.toString());
        }

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