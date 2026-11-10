import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

interface ScheduleStrategy {
    Comparator<Task> priority();
}

final class DeadlineFirst implements ScheduleStrategy {
    public Comparator<Task> priority() {
        return Comparator.comparing(Task::deadline).thenComparingInt(Task::minutes);
    }
}

final class ShortestFirst implements ScheduleStrategy {
    public Comparator<Task> priority() {
        return Comparator.comparingInt(Task::minutes).thenComparing(Task::deadline);
    }
}

enum Method {
    DEADLINE("Deadline first", new DeadlineFirst()),
    SHORTEST("Shortest task first", new ShortestFirst());

    private final String label;
    private final ScheduleStrategy strategy;

    Method(String label, ScheduleStrategy strategy) {
        this.label = label;
        this.strategy = strategy;
    }

    Schedule schedule(Plan plan) {
        List<Session> sessions = new ArrayList<>();
        List<Task> deferred = new ArrayList<>();
        int used = 0;
        for (Task task : plan.tasks().stream().sorted(strategy.priority()).toList()) {
            if (task.minutes() <= plan.budget() - used) {
                sessions.add(new Session(task, used));
                used += task.minutes();
            } else {
                deferred.add(task);
            }
        }
        return new Schedule(plan.name(), label, plan.budget(), sessions, deferred);
    }

    @Override public String toString() { return label; }
}
