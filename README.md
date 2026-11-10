# Study Sprint

Study Sprint is a Java Swing desktop app for students who need to fit study tasks into a limited session. It ranks tasks by deadline or by duration, shows what fits, and lists work to defer. Tasks are scheduled whole, and each strategy uses a greedy ordering.

## Requirements and launch

Use JDK 17 or newer and a graphical desktop. There are no third-party dependencies. Open a terminal at the project root.

**Windows PowerShell**

```powershell
javac -encoding UTF-8 --release 17 -d build (Get-ChildItem -Filter *.java).Name
java -cp build StudySprint
```

**macOS or Linux**

```sh
javac -encoding UTF-8 --release 17 -d build *.java
java -cp build StudySprint
```

The app saves to `study-sprint.properties` in the current working directory. Optionally pass a writable file path after `StudySprint`. The file is created when you click **Save plan**. The app reopens the saved plan, strategy, and report setting at startup. An exported schedule is a separate UTF-8 text file.

## Demonstrate the application

1. Launch the app with no saved file, or click **Load sample**. The sample plan has 90 minutes and three tasks of 70, 35, and 55 minutes.
2. Rename the plan, set the time budget, and click **Apply settings**. Add a task with a name, a date in YYYY-MM-DD format, and a duration. Select a task to edit it or remove it.
3. Restore the sample and generate with **Deadline first**. The earliest 70-minute task fits. Two tasks are deferred and 20 minutes remain.
4. Switch to **Shortest task first** and generate again. The 35- and 55-minute tasks fit exactly, and the 70-minute task is deferred.
5. Turn the workload summary off and regenerate to see the plain report. Turn it back on and regenerate.
6. Use **Export TXT** and inspect the saved file. Edit a task or apply a different setting and verify that the preview is cleared and export is disabled until you regenerate.
7. Click **Save plan**, close the app, and start it again. Confirm that the saved draft reappears. A generated schedule can be reproduced from the saved settings.

## Test

After compiling, run:

```sh
java "-Djava.awt.headless=true" -cp build StudySprintTests
```

The runner creates its own temporary files and checks 11 scenarios, including real Swing controls on the event dispatch thread. You can pass a writable screenshot prefix as the last argument to render the two verified GUI states as PNGs. If Windows restricts moves in its default temp directory, add `"-Djava.io.tmpdir=$PWD"` before `-cp` in PowerShell.

## Source map

| File | Responsibilities |
| --- | --- |
| `StudySprint.java` | Swing view, form parsing, and native export dialog |
| `StudyService.java` | Application facade and observer notifications |
| `Plan.java` | Plan, task, session, schedule, and Builder |
| `Scheduling.java` | Scheduling context and two Strategy implementations |
| `PlanStorage.java` | Storage port, Properties Adapter, and disk I/O |
| `ScheduleReport.java` | Plain report and optional workload Decorator |
| `StudySprintTests.java` | Repeatable tests and optional UI capture |

## Defense and coursework record

For a live change, add a longest-task-first strategy. Predict the order under the 90-minute sample, implement the comparator, register it in `Method`, and add an assertion covering the result. The GUI obtains its options from `Method.values()`. For another practice change, add a report decorator that includes the earliest deferred deadline, or add an observer that records the latest three events.

The code history records the order in which this reference was developed. The brief requires actual student ownership of at least two patterns per person, three weekly progress reviews, and an individual defense. Record actual contributors, dates, and commits in the final report. OpenAI Codex generated the reference source, tests, documentation, and diagrams. Confirm course approval before using this reference in assessed work.

## Publish the supplied history

Clone the companion Git bundle, then add an empty public repository and push:

```sh
git clone -b main Study-Sprint.bundle study-sprint
cd study-sprint
git remote set-url origin https://github.com/YOUR-USERNAME/YOUR-REPOSITORY.git
git push -u origin main
```

The separate source ZIP contains only tracked project files at its root. The PDF report is a separate deliverable.
