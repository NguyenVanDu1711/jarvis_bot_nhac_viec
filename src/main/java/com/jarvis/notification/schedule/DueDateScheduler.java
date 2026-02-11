package com.jarvis.notification.schedule;

import com.jarvis.notification.dto.JiraSearchResponse;
import com.jarvis.notification.service.JiraService;
import com.jarvis.notification.service.NotificationService;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DueDateScheduler {

    private final JiraService jiraService;
    private final NotificationService notificationService;

    @Value("${telegram.chat-id}")
    private String chatId;

//    @Scheduled(cron = "${scheduler.due-cron}")
//    @Scheduled(cron = "0 */1 * * * ?")
    public void remindDue() {
//        String jql = "project = HUB AND duedate < startOfDay() " +
//                "AND statusCategory != Done " +
//                "AND assignee IS NOT EMPTY";
        Map<String, String> projectMap = new HashMap<>();
//        listProjects.add("HUB");
        projectMap.put("EDU", "-1003643996267");
        projectMap.put("GAM", "-4852912535");
        projectMap.put("JJW", "-4507267566");
        projectMap.put("FTTHBIL", "");

        for (Map.Entry<String, String> entry: projectMap.entrySet()) {
            if (StringUtils.isBlank(entry.getValue())) continue;
            String jql = String.format( """
                project = %s
                AND duedate < startOfDay()
                AND statusCategory != Done
                AND assignee IS NOT EMPTY
                """, entry.getKey());

            JiraSearchResponse response =
                    jiraService.search(jql);

            response.getIssues().forEach(issue ->
                    notificationService.notifyDue(issue, "DUE_SOON", entry.getKey(), entry.getValue())
            );
        }

    }

    @Scheduled(cron = "${scheduler.done-last-day-cron}")
    public void listTaskDoneLastDay() {
        Map<String, String> projectMap = new HashMap<>();
//        listProjects.add("HUB");
        projectMap.put("EDU", "-1003643996267");
        projectMap.put("GAM", "-4852912535");
        projectMap.put("JJW", "-4507267566");
        projectMap.put("VCCIP", "-5060611918");

        for (Map.Entry<String, String> entry: projectMap.entrySet()) {
            if (StringUtils.isBlank(entry.getValue())) continue;
            StringBuilder jqlBuilder = new StringBuilder();
            jqlBuilder.append("project = ")
                    .append(entry.getKey())
                    .append(" ")
                    .append("AND Sprint in openSprints() ")
                    .append("AND status = \"TASK DONE IN DAY\"");

            String jql = jqlBuilder.toString();

            JiraSearchResponse response = jiraService.search(jql);
            StringBuilder message = new StringBuilder("📊 *Daily Report *");
            message.append(entry.getKey());
            message.append("\n");
            message.append("✅ Task hoàn thành hôm nay:\n");
            if(response.getIssues().isEmpty()){
                message.append("     - Hôm nay chưa có task nào hoàn thành.");
            }

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

            notificationService.notifyDone(message.toString(), entry.getValue());
        }

    }

    @Scheduled(cron = "${scheduler.in-progress-cron}")
//    @Scheduled(cron = "0 */1 * * * ?")
    public void listTaskInProgress() {
        Map<String, String> projectMap = new HashMap<>();
        projectMap.put("EDU", "-1003643996267");
        projectMap.put("GAM", "-4852912535");
        projectMap.put("JJW", "-4507267566");
        projectMap.put("FTTHBIL", "");
        projectMap.put("VCCIP", "-5060611918");

        for (Map.Entry<String, String> entry: projectMap.entrySet()) {
            if (StringUtils.isBlank(entry.getValue())) continue;

            // JQL để lấy các task đang thực hiện
            StringBuilder jqlBuilder = new StringBuilder();
            jqlBuilder.append("project = ")
                    .append(entry.getKey())
                    .append(" ")
                    .append("AND Sprint in openSprints() ")
                    .append("AND statusCategory = \"In Progress\"");

            String jql = jqlBuilder.toString();

            JiraSearchResponse response = jiraService.search(jql);

            StringBuilder message = new StringBuilder("🚧 *In Progress Report* ");
            message.append(entry.getKey());
            message.append("\n");
            message.append("🔨 Task đang thực hiện:\n");

            if(response.getIssues().isEmpty()){
                message.append("     - Hiện chưa có task nào đang thực hiện.");
            }

            response.getIssues().forEach(issue ->
                    message.append("- ")
                            .append(issue.getKey()).append(" \n")
                            .append(" | *Task Name* :")
                            .append(issue.getFields().getSummary()).append(" \n")
                            .append(" \t *Assignee* : ")
                            .append(issue.getFields().getAssignee() != null ? issue.getFields().getAssignee().getDisplayName() : "Unassign").append(" \n")
                            .append("\t * Created *").append(this.changeFormat(issue.getFields().getCreated())).append(" \n")
                            .append("\t * ⏱ Spent: *" ).append(issue.getFields().getTimespent() != null ? (issue.getFields().getTimespent()  / 3600) + " Hour" : "Chưa log!")
                            .append(" / * Estimate: * ").append( issue.getFields().getTimeoriginalestimate() != null ?  (issue.getFields().getTimeoriginalestimate()  / 3600) + " Hour" : "Chưa ước tính").append(" Hour").append(" \n")
                            .append("\t * Due date : * ").append(issue.getFields().getDuedate() != null ? issue.getFields().getDuedate() : "Chưa có!").append(" \n")
                            .append(" \n")
            );
            message.append("\n➡️ Tổng: ").append(response.getIssues().size()).append(" task sẽ thực hiện vào ngày ").append(LocalDate.now());

            notificationService.notifyDone(message.toString(), entry.getValue());
        }
    }



    private String changeFormat(String jiraTime) {
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