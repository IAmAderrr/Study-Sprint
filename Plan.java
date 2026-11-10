import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

record Task(String name, LocalDate deadline, int minutes) {
    Task {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Enter a task name.");
        if (minutes < 1 || minutes > 1440) throw new IllegalArgumentException("Task duration must be 1–1440 minutes.");
        Objects.requireNonNull(deadline, "Deadline required");
        name = name.strip();
    }
    @Override public String toString() { return name + "  ·  " + minutes + " min  ·  due " + deadline; }
}

record Plan(String name, int budget, List<Task> tasks) {
    Plan {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Enter a plan name.");
        if (budget < 1 || budget > 1440) throw new IllegalArgumentException("Time budget must be 1–1440 minutes.");
        name = name.strip();
        tasks = List.copyOf(tasks);
    }

    static final class Builder {
        private String name = "";
        private int budget = 90;
        private List<Task> tasks = List.of();

        Builder name(String value) { name = value; return this; }
        Builder budget(int value) { budget = value; return this; }
        Builder tasks(List<Task> value) { tasks = List.copyOf(value); return this; }
        Plan build() { return new Plan(name, budget, tasks); }
    }
}

record Session(Task task, int startMinute) {
    int endMinute() { return startMinute + task.minutes(); }
}

record Schedule(String planName, String method, int budget, List<Session> sessions, List<Task> deferred) {
    Schedule {
        sessions = List.copyOf(sessions);
        deferred = List.copyOf(deferred);
    }

    int used() { return sessions.isEmpty() ? 0 : sessions.get(sessions.size() - 1).endMinute(); }
}
