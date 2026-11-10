import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public final class StudySprint {
    private StudySprint() {}

    public static void main(String[] args) {
        Path path = Path.of(args.length == 0 ? "study-sprint.properties" : args[0]);
        SwingUtilities.invokeLater(() -> {
            var frame = new JFrame("Study Sprint");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setContentPane(new StudyPanel(new StudyService(new PropertiesPlanAdapter(new PropertiesDisk(path)))));
            frame.pack();
            frame.setMinimumSize(frame.getSize());
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

final class StudyPanel extends JPanel {
    private static final Color INK = new Color(25, 38, 53);
    private static final Color ACCENT = new Color(26, 102, 99);
    private final StudyService service;
    private final JTextField planName = new JTextField(18);
    private final JTextField budget = new JTextField(5);
    private final JComboBox<Method> method = new JComboBox<>(Method.values());
    private final JCheckBox summary = new JCheckBox("Include workload summary");
    private final DefaultListModel<Task> tasks = new DefaultListModel<>();
    private final JList<Task> taskList = new JList<>(tasks);
    private final JTextField taskName = new JTextField(16);
    private final JTextField deadline = new JTextField(12);
    private final JTextField duration = new JTextField(5);
    private final JTextArea preview = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final JButton export = new JButton("Export TXT");

    StudyPanel(StudyService service) {
        super(new BorderLayout(12, 12));
        this.service = service;
        setPreferredSize(new Dimension(960, 620));
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        setBackground(new Color(239, 246, 246));
        var heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        var title = new JLabel("Study Sprint");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 30f));
        title.setForeground(INK);
        heading.add(title, BorderLayout.NORTH);
        heading.add(new JLabel("Build a study plan, compare schedules, and export a session list."), BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        var columns = new JPanel(new GridLayout(1, 2, 12, 0));
        columns.setOpaque(false);
        columns.add(editor());
        columns.add(results());
        add(columns, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
        status.setOpaque(true);
        status.setBackground(new Color(224, 239, 238));
        status.setForeground(ACCENT);
        add(status, BorderLayout.SOUTH);

        service.subscribe(event -> {
            status.setText(event.message());
            preview.setText(event.preview().isEmpty() ? "Generate a schedule to see your sessions here." : event.preview());
            preview.setCaretPosition(0);
            export.setEnabled(event.exportReady());
        });
        export.setEnabled(false);
        preview.setText("Generate a schedule to see your sessions here.");
        act(service::load);
        readDraft();
    }

    private JPanel editor() {
        var panel = card("01  Plan and tasks");
        var settings = new JPanel();
        settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
        settings.add(row(label("Plan name", planName), planName));
        settings.add(row(label("Budget (min)", budget), budget, method));
        settings.add(row(summary, button("Apply settings", () -> sync(taskItems()))));
        panel.add(settings, BorderLayout.NORTH);

        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskList.setFixedCellHeight(29);
        taskList.getAccessibleContext().setAccessibleName("Plan tasks");
        taskList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && taskList.getSelectedValue() != null) {
                Task task = taskList.getSelectedValue();
                taskName.setText(task.name());
                deadline.setText(task.deadline().toString());
                duration.setText(Integer.toString(task.minutes()));
            }
        });
        panel.add(new JScrollPane(taskList), BorderLayout.CENTER);

        var edit = new JPanel();
        edit.setLayout(new BoxLayout(edit, BoxLayout.Y_AXIS));
        edit.add(row(label("Task", taskName), taskName));
        edit.add(row(label("Due (YYYY-MM-DD)", deadline), deadline, label("Minutes", duration), duration));
        edit.add(row(button("Add task", () -> editTask(false)), button("Update task", () -> editTask(true)),
                button("Remove task", this::removeTask)));
        edit.add(row(button("Load sample", () -> { service.restoreSample(); readDraft(); }),
                button("Save plan", () -> { sync(taskItems()); service.save(); })));
        panel.add(edit, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel results() {
        var panel = card("02  Schedule preview");
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        preview.setMargin(new java.awt.Insets(12, 12, 12, 12));
        preview.getAccessibleContext().setAccessibleName("Schedule preview");
        panel.add(new JScrollPane(preview), BorderLayout.CENTER);
        export.addActionListener(event -> act(this::export));
        panel.add(row(button("Generate schedule", () -> { sync(taskItems()); service.generate(); }), export),
                BorderLayout.SOUTH);
        return panel;
    }

    private void readDraft() {
        Draft draft = service.draft();
        planName.setText(draft.plan().name());
        budget.setText(Integer.toString(draft.plan().budget()));
        method.setSelectedItem(draft.method());
        summary.setSelected(draft.includeSummary());
        showTasks(draft.plan().tasks());
        clearTaskEditor();
    }

    private void editTask(boolean replace) {
        int selected = replace ? selectedIndex() : -1;
        LocalDate due;
        try { due = LocalDate.parse(deadline.getText().strip()); }
        catch (DateTimeParseException invalid) { throw new IllegalArgumentException("Enter a date as YYYY-MM-DD."); }
        var task = new Task(taskName.getText(), due, number(duration, "Duration"));
        var next = taskItems();
        if (replace) next.set(selected, task);
        else next.add(task);
        sync(next);
        showTasks(next);
        clearTaskEditor();
    }

    private void removeTask() {
        var next = taskItems();
        next.remove(selectedIndex());
        sync(next);
        showTasks(next);
        clearTaskEditor();
    }

    private int selectedIndex() {
        int index = taskList.getSelectedIndex();
        if (index < 0) throw new IllegalArgumentException("Select a task first.");
        return index;
    }

    private void clearTaskEditor() {
        taskList.clearSelection();
        taskName.setText("");
        deadline.setText("");
        duration.setText("");
    }

    private void showTasks(List<Task> values) {
        tasks.clear();
        tasks.addAll(values);
    }

    private ArrayList<Task> taskItems() {
        var copy = new ArrayList<Task>(tasks.size());
        for (int i = 0; i < tasks.size(); i++) copy.add(tasks.get(i));
        return copy;
    }

    private void sync(List<Task> values) {
        Plan plan = new Plan.Builder().name(planName.getText()).budget(number(budget, "Budget"))
                .tasks(values).build();
        service.update(plan, (Method) method.getSelectedItem(), summary.isSelected());
    }

    private void export() throws IOException {
        sync(taskItems());
        if (!export.isEnabled()) throw new IllegalStateException("Generate a current schedule before exporting.");
        var picker = new JFileChooser();
        picker.setDialogTitle("Save schedule as text");
        picker.setSelectedFile(new java.io.File("study-schedule.txt"));
        if (picker.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = picker.getSelectedFile().toPath();
        if (java.nio.file.Files.exists(path) && JOptionPane.showConfirmDialog(this,
                "Replace " + path.getFileName() + "?", "Confirm replacement",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        service.export(path);
    }

    private static int number(JTextField field, String label) {
        try { return Integer.parseInt(field.getText().strip()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException(label + " must be a whole number."); }
    }

    private JButton button(String caption, Action action) {
        var button = new JButton(caption);
        button.addActionListener(event -> act(action));
        return button;
    }

    private void act(Action action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException | IOException error) {
            status.setText("Error: " + error.getMessage());
        }
    }

    private static JPanel card(String heading) {
        var card = new JPanel(new BorderLayout(4, 7));
        card.setBorder(BorderFactory.createTitledBorder(heading));
        return card;
    }

    private static JPanel row(java.awt.Component... children) {
        var row = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 3));
        for (var child : children) row.add(child);
        return row;
    }

    private static JLabel label(String caption, JComponent target) {
        var label = new JLabel(caption);
        label.setLabelFor(target);
        return label;
    }

    @FunctionalInterface
    private interface Action { void run() throws IOException; }
}
