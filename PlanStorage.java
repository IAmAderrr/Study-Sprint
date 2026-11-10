import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;

record Draft(Plan plan, Method method, boolean includeSummary) {}

interface PlanStore {
    Optional<Draft> load() throws IOException;
    void save(Draft draft) throws IOException;
}

// The adaptee handles property files. It knows nothing about plans or scheduling.
final class PropertiesDisk {
    private final Path path;

    PropertiesDisk(Path path) { this.path = path.toAbsolutePath(); }

    Optional<Properties> read() throws IOException {
        if (Files.notExists(path)) return Optional.empty();
        Properties properties = new Properties();
        try (var stream = Files.newInputStream(path)) { properties.load(stream); }
        return Optional.of(properties);
    }

    void write(Properties properties) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), ".study-sprint-", ".tmp");
        try {
            try (var stream = Files.newOutputStream(temp)) { properties.store(stream, "Study Sprint v1"); }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}

final class PropertiesPlanAdapter implements PlanStore {
    private final PropertiesDisk disk;

    PropertiesPlanAdapter(PropertiesDisk disk) { this.disk = disk; }

    public Optional<Draft> load() throws IOException {
        try {
            return disk.read().map(this::decode);
        } catch (RuntimeException invalid) {
            throw new IOException("The saved plan is invalid. Keep a copy before replacing it.", invalid);
        }
    }

    private Draft decode(Properties properties) {
        if (!"1".equals(properties.getProperty("version"))) throw new IllegalArgumentException("Unknown version");
        int count = Integer.parseInt(properties.getProperty("count"));
        if (count < 0 || count > properties.size() / 3) throw new IllegalArgumentException("Invalid task count");
        var tasks = new ArrayList<Task>();
        for (int i = 0; i < count; i++) {
            String prefix = "task." + i + ".";
            tasks.add(new Task(properties.getProperty(prefix + "name"),
                    LocalDate.parse(properties.getProperty(prefix + "deadline")),
                    Integer.parseInt(properties.getProperty(prefix + "minutes"))));
        }
        String summary = properties.getProperty("summary");
        if (!"true".equals(summary) && !"false".equals(summary)) throw new IllegalArgumentException("Invalid summary flag");
        return new Draft(new Plan.Builder().name(properties.getProperty("name"))
                .budget(Integer.parseInt(properties.getProperty("budget"))).tasks(tasks).build(),
                Method.valueOf(properties.getProperty("method")), Boolean.parseBoolean(summary));
    }

    public void save(Draft draft) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("name", draft.plan().name());
        properties.setProperty("budget", Integer.toString(draft.plan().budget()));
        properties.setProperty("method", draft.method().name());
        properties.setProperty("summary", Boolean.toString(draft.includeSummary()));
        properties.setProperty("count", Integer.toString(draft.plan().tasks().size()));
        int index = 0;
        for (Task task : draft.plan().tasks()) {
            String prefix = "task." + index++ + ".";
            properties.setProperty(prefix + "name", task.name());
            properties.setProperty(prefix + "deadline", task.deadline().toString());
            properties.setProperty(prefix + "minutes", Integer.toString(task.minutes()));
        }
        disk.write(properties);
    }
}
