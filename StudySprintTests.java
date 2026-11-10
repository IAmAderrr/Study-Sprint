import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class StudySprintTests {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private static int passed;

    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("study-sprint-tests");
        try {
            var service = service(scratch);
            check("T01 Sample → generate → export", () -> {
                service.load();
                var schedule = service.generate();
                var path = scratch.resolve("schedule.txt");
                service.export(path);
                require(schedule.used() == 70 && Files.readString(path).contains("Scheduled: 70 / 90 min"));
            });
            check("T02 Switch scheduling strategy", () -> {
                service.update(service.draft().plan(), Method.SHORTEST, true);
                var result = service.generate();
                require(result.used() == 90 && result.sessions().size() == 2 && result.deferred().size() == 1);
            });
            check("T03 Observer invalidates preview on edit", () -> {
                var event = new AtomicReference<StudyEvent>();
                service.subscribe(event::set);
                service.update(plan("Renamed", 90, task(30)), Method.SHORTEST, false);
                require(!event.get().exportReady() && event.get().preview().isEmpty());
                rejects(IllegalStateException.class, () -> service.export(scratch.resolve("stale.txt")));
                require(Files.notExists(scratch.resolve("stale.txt")));
            });
            check("T04 Save and reload in a new service", () -> {
                service.save();
                var reloaded = service(scratch);
                reloaded.load();
                require(reloaded.draft().equals(service.draft()));
            });
            check("T05 Reject blank plan name", () ->
                    rejects(IllegalArgumentException.class, () -> plan(" ", 90, task(30))));
            check("T06 Reject zero-minute task", () ->
                    rejects(IllegalArgumentException.class, () -> task(0)));
            check("T07 Schedule exact fit and defer over-budget task", () -> {
                var schedule = Method.SHORTEST.schedule(plan("Fit", 35, task(35), task(36)));
                require(schedule.used() == 35 && schedule.deferred().equals(List.of(task(36))));
            });
            check("T08 Reject generation with no tasks", () -> {
                service.update(plan("Empty", 90), Method.DEADLINE, true);
                rejects(IllegalArgumentException.class, service::generate);
            });
            check("T09 Decorator preserves the base and adds totals", () -> {
                var result = Method.DEADLINE.schedule(plan("Plain", 90, task(30)));
                var plain = new TextScheduleReport();
                var withSummary = new WorkloadSummary(plain).render(result);
                require(!plain.render(result).contains("Workload summary") && withSummary.startsWith(plain.render(result))
                        && withSummary.contains("Remaining: 60 min"));
            });
            check("T10 Malformed save raises a recoverable error", () -> {
                Files.writeString(scratch.resolve("study-sprint.properties"), "version=9");
                var corrupt = service(scratch);
                rejects(java.io.IOException.class, corrupt::load);
                require(Files.readString(scratch.resolve("study-sprint.properties")).equals("version=9"));
            });
            check("T11 GUI updates the preview and validates bad dates", () -> {
                Files.delete(scratch.resolve("study-sprint.properties"));
                SwingUtilities.invokeAndWait(() -> {
                    var panel = new StudyPanel(service(scratch));
                    var generate = find(panel, JButton.class, "Generate schedule");
                    generate.doClick(0);
                    require(find(panel, JTextArea.class, null).getText().contains("Scheduled: 70 / 90 min"));
                    require(find(panel, JButton.class, "Export TXT").isEnabled());
                    if (args.length == 1) capture(panel, Path.of(args[0] + "-deadline.png"));
                    find(panel, JComboBox.class, null).setSelectedItem(Method.SHORTEST);
                    generate.doClick(0);
                    require(find(panel, JTextArea.class, null).getText().contains("Scheduled: 90 / 90 min"));
                    if (args.length == 1) capture(panel, Path.of(args[0] + "-shortest.png"));
                    find(panel, JButton.class, "Add task").doClick(0);
                    require(find(panel, JLabel.class, "Error: Enter a date as YYYY-MM-DD.") != null);
                });
            });
            System.out.println(passed + " / 11 scenarios passed");
        } finally {
            try (var entries = Files.list(scratch)) {
                for (Path entry : entries.toList()) Files.deleteIfExists(entry);
            }
            Files.deleteIfExists(scratch);
        }
    }

    private static StudyService service(Path directory) {
        return new StudyService(new PropertiesPlanAdapter(new PropertiesDisk(directory.resolve("study-sprint.properties"))));
    }

    private static Task task(int minutes) { return new Task("Task " + minutes, DATE, minutes); }

    private static Plan plan(String name, int budget, Task... tasks) {
        return new Plan.Builder().name(name).budget(budget).tasks(List.of(tasks)).build();
    }

    private static void check(String name, Checked action) throws Exception {
        action.run();
        passed++;
        System.out.println("PASS " + name);
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Expected condition was false");
    }

    private static void rejects(Class<? extends Exception> expected, Checked action) throws Exception {
        try {
            action.run();
        } catch (Exception error) {
            if (expected.isInstance(error)) return;
            throw error;
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private static <T extends Component> T find(Container root, Class<T> type, String text) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && (text == null
                    || child instanceof AbstractButton button && text.equals(button.getText())
                    || child instanceof JLabel label && text.equals(label.getText()))) return type.cast(child);
            if (child instanceof Container container) {
                T found = find(container, type, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void capture(JPanel panel, Path file) {
        panel.setSize(panel.getPreferredSize());
        layout(panel);
        var image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            panel.printAll(graphics);
            ImageIO.write(image, "png", file.toFile());
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        } finally {
            graphics.dispose();
        }
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layout(nested);
        }
    }

    @FunctionalInterface
    private interface Checked { void run() throws Exception; }
}
