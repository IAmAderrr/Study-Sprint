import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

interface ScheduleReport {
    String render(Schedule schedule);
}

final class TextScheduleReport implements ScheduleReport {
    public String render(Schedule schedule) {
        var text = new StringBuilder(schedule.planName()).append("\n")
                .append(schedule.method()).append("\n\n");
        if (schedule.sessions().isEmpty()) text.append("No tasks fit the available time.\n");
        for (Session session : schedule.sessions()) {
            text.append("%3d–%-3d min  %s%n".formatted(session.startMinute(),
                    session.endMinute(), session.task().name()));
        }
        if (!schedule.deferred().isEmpty()) {
            text.append("\nDeferred\n");
            for (Task task : schedule.deferred()) {
                text.append("- ").append(task.name()).append(" (").append(task.minutes()).append(" min)\n");
            }
        }
        return text.toString();
    }
}

final class WorkloadSummary implements ScheduleReport {
    private final ScheduleReport base;

    WorkloadSummary(ScheduleReport base) { this.base = base; }

    public String render(Schedule schedule) {
        return base.render(schedule) + "\nWorkload summary\n"
                + "Scheduled: %d / %d min%nRemaining: %d min%nScheduled tasks: %d%nDeferred tasks: %d%n"
                .formatted(schedule.used(), schedule.budget(), schedule.budget() - schedule.used(),
                        schedule.sessions().size(), schedule.deferred().size());
    }
}

final class ReportFile {
    private ReportFile() {}

    static void write(Path path, String report) throws IOException {
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }
}
