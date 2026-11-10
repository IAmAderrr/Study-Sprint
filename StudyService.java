import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

record StudyEvent(String message, String preview, boolean exportReady) {}

@FunctionalInterface
interface StudyObserver {
    void onStudyChanged(StudyEvent event);
}

final class StudyService {
    private final PlanStore store;
    private final List<StudyObserver> observers = new ArrayList<>();
    private Draft draft;
    private Schedule schedule;
    private String preview = "";

    StudyService(PlanStore store) {
        this.store = store;
        draft = sample();
    }

    void subscribe(StudyObserver observer) { observers.add(observer); }
    Draft draft() { return draft; }

    void load() throws IOException {
        var saved = store.load();
        draft = saved.orElseGet(StudyService::sample);
        invalidate(saved.isPresent() ? "Saved plan loaded." : "Sample plan ready.");
    }

    void update(Plan plan, Method method, boolean summary) {
        Draft next = new Draft(plan, method, summary);
        if (next.equals(draft)) return;
        draft = next;
        invalidate("Plan changed. Generate a new schedule.");
    }

    void restoreSample() {
        draft = sample();
        invalidate("Sample plan restored. Save to keep it.");
    }

    void save() throws IOException {
        store.save(draft);
        notifyObservers("Plan saved.");
    }

    Schedule generate() {
        if (draft.plan().tasks().isEmpty()) throw new IllegalArgumentException("Add at least one task before generating.");
        schedule = draft.method().schedule(draft.plan());
        ScheduleReport report = new TextScheduleReport();
        if (draft.includeSummary()) report = new WorkloadSummary(report);
        preview = report.render(schedule);
        notifyObservers("Schedule generated.");
        return schedule;
    }

    void export(Path path) throws IOException {
        if (schedule == null) throw new IllegalStateException("Generate a current schedule before exporting.");
        ReportFile.write(path, preview);
        notifyObservers("Exported " + path.getFileName() + ".");
    }

    private void invalidate(String message) {
        schedule = null;
        preview = "";
        notifyObservers(message);
    }

    private void notifyObservers(String message) {
        var event = new StudyEvent(message, preview, schedule != null);
        for (StudyObserver observer : List.copyOf(observers)) observer.onStudyChanged(event);
    }

    static Draft sample() {
        return new Draft(new Plan.Builder().name("Exam preparation").budget(90).tasks(List.of(
                new Task("Algorithms practice", LocalDate.of(2026, 11, 20), 70),
                new Task("Review SQL notes", LocalDate.of(2026, 11, 22), 35),
                new Task("Practice Java patterns", LocalDate.of(2026, 11, 24), 55))).build(),
                Method.DEADLINE, true);
    }
}
