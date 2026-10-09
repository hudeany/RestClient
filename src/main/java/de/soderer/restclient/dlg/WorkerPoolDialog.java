package de.soderer.restclient.dlg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import de.soderer.restclient.worker.WorkerStats;
import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.swing.ModalDialog;
import de.soderer.utilities.worker.WorkerSimple;

/**
 * Runs a pool of parallel workers, each repeating its task a configured number
 * of times (or until canceled), and shows live statistics per worker.
 *
 * <p>
 * {@link #open()} returns true if all repetitions were done, false if the run
 * was canceled.
 * </p>
 *
 * @serial exclude
 */
public abstract class WorkerPoolDialog extends ModalDialog<Boolean> {
	private static final long serialVersionUID = 1427303826312908133L;

	private static final int COLUMN_STATUS = 4;
	private static final int MAX_VISIBLE_ROWS = 15;
	private static final Color SUCCESS_COLOR = new Color(0, 128, 0);
	private static final Color ERROR_COLOR = Color.RED;
	private static final String CSV_HEADER = "WorkerID;Success count;Error count;Latest duration;Latest status;Minimum duration;Average duration;Maximum duration";

	private final String text;

	private JTable table;
	private WorkerStatsTableModel tableModel;
	private JProgressBar progressBar;
	private JTextArea descriptionLabel;
	private JButton actionButton;
	private JButton downloadButton;
	private Timer rampUpTimer;

	/** Only accessed on the Swing event dispatch thread (sorted in place for display) */
	private final List<WorkerStats> workerStatsList = new ArrayList<>();
	private final AtomicInteger progress = new AtomicInteger(0);
	private volatile boolean cancelled = false;
	private volatile boolean finished = false;

	/** Coalesces the table refreshes requested by the workers into one pending event */
	private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);

	private ExecutorService executor;

	private int workerCount = 1;
	private int tasksPerWorker = 1;
	private int totalTasks;
	private Duration sleepTime = null;
	private LocalDateTime poolStart = null;
	private Duration rampUpTime = null;

	private int sortColumn = 0;
	private boolean ascending = true;

	/** Workers currently executing their task, so a cancellation can abort them (written by the pool threads) */
	private final Set<WorkerSimple<?>> runningWorkers = ConcurrentHashMap.newKeySet();

	/**
	 * Creates the dialog. The settings are made via the setters, the workers are started by {@link #open()}.
	 *
	 * @param parent parent window
	 * @param title window title
	 * @param text description shown above the progress bar (may contain a "RampUp...:" line, which is replaced by a countdown)
	 */
	public WorkerPoolDialog(final Window parent, final String title, final String text) {
		super(parent, title);

		this.text = text;
	}

	/**
	 * Sets the number of workers running in parallel.
	 *
	 * @param workerCount number of parallel workers
	 */
	public void setParallelWorkerAmount(final int workerCount) {
		this.workerCount = workerCount;
	}

	/**
	 * Sets the number of task runs per worker.
	 *
	 * @param tasksPerWorker number of repetitions per worker, or -1 to repeat until canceled
	 */
	public void setRepetitionsPerWorker(final int tasksPerWorker) {
		this.tasksPerWorker = tasksPerWorker;
	}

	/**
	 * Sets the pause between two task runs of a worker.
	 *
	 * @param sleepTime pause duration, or null for no pause
	 */
	public void setSleepTime(final Duration sleepTime) {
		this.sleepTime = sleepTime;
	}

	/**
	 * Sets the ramp-up time: task runs started within this time after the start of the pool are shown
	 * as latest result, but not counted in the statistics.
	 *
	 * @param rampUpTime ramp-up duration, or null for none
	 */
	public void setRampUpTime(final Duration rampUpTime) {
		this.rampUpTime = rampUpTime;
	}

	/**
	 * Creates the components and starts the workers, since both depend on the
	 * settings made after construction.
	 *
	 * @return true if all repetitions were done, false if the run was canceled
	 */
	@Override
	public Boolean open() {
		createComponents();
		pack();
		setLocationRelativeTo(getOwner());
		initWorkers();
		return super.open();
	}

	private void createComponents() {
		final int margin = 5;

		final JPanel panel = new JPanel(new BorderLayout(margin, margin));
		panel.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));

		final JPanel topPanel = new JPanel(new BorderLayout(margin, margin));

		descriptionLabel = new JTextArea(text);
		descriptionLabel.setEditable(false);
		descriptionLabel.setFocusable(false);
		descriptionLabel.setOpaque(false);
		descriptionLabel.setFont(UIManager.getFont("Label.font"));
		topPanel.add(descriptionLabel, BorderLayout.NORTH);

		totalTasks = tasksPerWorker >= 0 ? workerCount * tasksPerWorker : -1;
		progressBar = new JProgressBar(0, Math.max(totalTasks, 1));
		// Repeating until canceled has no end, so there is no meaningful percentage
		progressBar.setIndeterminate(totalTasks < 0);
		topPanel.add(progressBar, BorderLayout.SOUTH);

		panel.add(topPanel, BorderLayout.NORTH);

		tableModel = new WorkerStatsTableModel();
		table = new JTable(tableModel);
		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);

		final DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
			private static final long serialVersionUID = -1826497624913770520L;

			@Override
			public Component getTableCellRendererComponent(final JTable renderTable, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
				final Component component = super.getTableCellRendererComponent(renderTable, value, isSelected, hasFocus, row, column);
				if (!isSelected) {
					final Boolean latestStatusWasSuccess = row < workerStatsList.size() ? workerStatsList.get(row).getLatestStatusWasSuccess() : null;
					if (latestStatusWasSuccess == null) {
						component.setForeground(renderTable.getForeground());
					} else {
						component.setForeground(latestStatusWasSuccess ? SUCCESS_COLOR : ERROR_COLOR);
					}
				}
				return component;
			}
		};
		table.getColumnModel().getColumn(COLUMN_STATUS).setCellRenderer(statusRenderer);

		// Columns as wide as their header texts, like SWT's TableColumn.pack() on an empty table
		final JTableHeader header = table.getTableHeader();
		int tableWidth = 0;
		for (int i = 0; i < table.getColumnCount(); i++) {
			final TableColumn column = table.getColumnModel().getColumn(i);
			final Component headerComponent = header.getDefaultRenderer().getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, i);
			final int width = headerComponent.getPreferredSize().width + 16;
			column.setPreferredWidth(width);
			tableWidth += width;
		}
		header.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent event) {
				final int viewColumn = header.columnAtPoint(event.getPoint());
				if (viewColumn >= 0) {
					final int columnIndex = table.convertColumnIndexToModel(viewColumn);
					if (sortColumn == columnIndex) {
						ascending = !ascending;
					} else {
						sortColumn = columnIndex;
						ascending = true;
					}
					refreshTable();
				}
			}
		});

		final int visibleRows = Math.min(workerCount, MAX_VISIBLE_ROWS);
		table.setPreferredScrollableViewportSize(new Dimension(tableWidth, visibleRows * table.getRowHeight()));
		panel.add(new JScrollPane(table), BorderLayout.CENTER);

		final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

		downloadButton = new JButton(LangResources.get("saveResults"));
		downloadButton.setEnabled(false);
		downloadButton.setPreferredSize(new Dimension(Math.max(160, downloadButton.getPreferredSize().width), downloadButton.getPreferredSize().height));
		downloadButton.addActionListener(event -> exportResults());
		buttonPanel.add(downloadButton);

		actionButton = new JButton(LangResources.get("cancel"));
		actionButton.setPreferredSize(new Dimension(Math.max(120, actionButton.getPreferredSize().width), actionButton.getPreferredSize().height));
		actionButton.addActionListener(event -> {
			if (!finished) {
				cancelExecution();
			} else {
				closeDialog();
			}
		});
		buttonPanel.add(actionButton);

		panel.add(buttonPanel, BorderLayout.SOUTH);

		setContentPane(panel);

		// Closing the window while workers are still running cancels them, instead of leaving them running unseen
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent event) {
				closeDialog();
			}
		});
	}

	private void closeDialog() {
		if (!finished) {
			cancelExecution();
		}
		if (rampUpTimer != null) {
			rampUpTimer.stop();
		}
		returnValue = !cancelled;
		dispose();
	}

	private void initWorkers() {
		// Daemon threads: a worker thread still hanging (e.g. in a connect without timeout) must never keep
		// the JVM alive after the application window was closed
		final AtomicInteger threadNumber = new AtomicInteger(0);
		executor = Executors.newFixedThreadPool(workerCount, runnable -> {
			final Thread thread = new Thread(runnable, "WorkerPool-" + threadNumber.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});

		for (int i = 0; i < workerCount; i++) {
			workerStatsList.add(new WorkerStats(i + 1));
		}
		tableModel.fireTableDataChanged();

		poolStart = LocalDateTime.now();

		startRampUpCountdown();

		// Captured, so the workers never touch the (sortable) display list
		for (final WorkerStats workerStats : new ArrayList<>(workerStatsList)) {
			executor.submit(() -> runWorker(workerStats));
		}

		// No more tasks: the pool threads end once the submitted tasks are done, instead of idling forever
		executor.shutdown();
	}

	private void runWorker(final WorkerStats workerStats) {
		try {
			runWorkerLoop(workerStats);
		} finally {
			// Always signal the end, even if a worker run failed unexpectedly
			SwingUtilities.invokeLater(this::checkFinished);
		}
	}

	private void runWorkerLoop(final WorkerStats workerStats) {
		for (int j = 0; (tasksPerWorker == -1 || j < tasksPerWorker) && !cancelled; j++) {
			final LocalDateTime start = LocalDateTime.now();
			final boolean countInStatistics = rampUpTime == null || !poolStart.plus(rampUpTime).isAfter(start);
			WorkerSimple<?> worker = null;
			boolean success;
			try {
				// Created inside the try block: a failing creation (e.g. an invalid random parameter placeholder)
				// must count as an error run. Otherwise the exception ended this pool thread silently (swallowed
				// by the executor's Future), the progress never reached the total and the dialog never finished.
				worker = createWorker();

				// Registered, so cancelExecution() can abort the running request (a thread interrupt does not
				// reach a socket blocked in a read). Checked again afterwards, because cancelExecution() may have
				// run between the loop condition and the registration and would then have missed this worker.
				runningWorkers.add(worker);
				if (cancelled) {
					worker.cancel();
					break;
				}

				success = checkForSuccess(worker.work());
			} catch (@SuppressWarnings("unused") final Exception e) {
				success = false;
			} finally {
				if (worker != null) {
					runningWorkers.remove(worker);
				}
			}

			if (cancelled) {
				// A run aborted by the cancellation is neither a success nor a real error of the tested service
				break;
			} else if (success) {
				workerStats.addSuccess(Duration.between(start, LocalDateTime.now()), countInStatistics);
			} else {
				workerStats.addError(Duration.between(start, LocalDateTime.now()), countInStatistics);
			}

			progress.incrementAndGet();
			scheduleRefresh();

			if ((tasksPerWorker == -1 || j < tasksPerWorker - 1) && !cancelled && sleepTime != null) {
				try {
					Thread.sleep(sleepTime.toMillis());
				} catch (@SuppressWarnings("unused") final InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}

	/**
	 * Requests a refresh of the table and progress bar. Many fast workers would
	 * otherwise flood the event queue with one event per finished task.
	 */
	private void scheduleRefresh() {
		if (refreshScheduled.compareAndSet(false, true)) {
			SwingUtilities.invokeLater(() -> {
				refreshScheduled.set(false);
				if (isDisplayable()) {
					tableModel.fireTableRowsUpdated(0, Math.max(0, workerStatsList.size() - 1));
					if (totalTasks >= 0) {
						progressBar.setValue(progress.get());
					}
					checkFinished();
				}
			});
		}
	}

	private void startRampUpCountdown() {
		if (rampUpTime == null || rampUpTime.isZero() || rampUpTime.isNegative()) {
			return;
		}

		final long totalSeconds = rampUpTime.getSeconds();
		// Matches the whole ramp-up line of the localized text: "RampUp: 10s" (de) as well as "RampUp time: 10s" (en),
		// and also durations containing blanks (e.g. "1m 30s"), which "\S+" would only have replaced partially
		final Pattern rampUpPattern = Pattern.compile("RampUp[^:\\n]*:[^\\n]*");

		rampUpTimer = new Timer(1000, null);
		rampUpTimer.setInitialDelay(0);
		rampUpTimer.addActionListener(event -> {
			final Duration remaining = rampUpTime.minus(Duration.between(poolStart, LocalDateTime.now()));
			if (remaining.isNegative() || remaining.isZero()) {
				descriptionLabel.setText(text);
				rampUpTimer.stop();
			} else {
				// Round up so the label still reads "1 sec" during the last partial second
				final long remainingSeconds = remaining.toMillis() / 1000 + (remaining.toMillis() % 1000 > 0 ? 1 : 0);
				final String replacement = LangResources.get("rampUp") + ": " + LangResources.get("remainingTime", remainingSeconds, totalSeconds);
				descriptionLabel.setText(rampUpPattern.matcher(text).replaceFirst(Matcher.quoteReplacement(replacement)));
			}
		});
		rampUpTimer.start();
	}

	/**
	 * Sorts the display list by the current sort column.
	 *
	 * <p>
	 * The sort keys are read once before sorting, because the workers keep
	 * changing the statistics concurrently. Comparing live values could change
	 * the order between two comparisons, which makes List.sort() fail with
	 * "Comparison method violates its general contract".
	 * </p>
	 */
	private void refreshTable() {
		final Map<WorkerStats, Comparable<?>> sortKeys = new IdentityHashMap<>();
		for (final WorkerStats workerStats : workerStatsList) {
			sortKeys.put(workerStats, getSortKey(workerStats, sortColumn));
		}

		final Comparator<WorkerStats> comparator = (w1, w2) -> compareNullable(sortKeys.get(w1), sortKeys.get(w2));
		workerStatsList.sort(ascending ? comparator : comparator.reversed());

		tableModel.fireTableDataChanged();
	}

	private static Comparable<?> getSortKey(final WorkerStats workerStats, final int column) {
		switch (column) {
			case 0:
				return workerStats.getWorkerId();
			case 1:
				return workerStats.getSuccessCount();
			case 2:
				return workerStats.getErrorCount();
			case 3:
				return workerStats.getLatestDuration();
			case 4:
				return workerStats.getLatestStatusWasSuccess();
			case 5:
				return workerStats.getMinimumDuration();
			case 6:
				return workerStats.getAverageDuration();
			case 7:
				return workerStats.getMaximumDuration();
			default:
				return null;
		}
	}

	/**
	 * Null-safe comparison for columns backed by WorkerStats fields that stay null
	 * until a worker completes its first task. Workers without a value yet are
	 * sorted first in ascending order.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static int compareNullable(final Comparable a, final Comparable b) {
		if (a == null && b == null) {
			return 0;
		} else if (a == null) {
			return -1;
		} else if (b == null) {
			return 1;
		} else {
			return a.compareTo(b);
		}
	}

	private void cancelExecution() {
		cancelled = true;
		// Aborts the requests still running (for HTTP workers: HttpRequest.cancel() disconnects the connection).
		// executor.shutdownNow() alone only interrupts the threads, which does not end a blocking socket read.
		for (final WorkerSimple<?> worker : runningWorkers) {
			try {
				worker.cancel();
			} catch (@SuppressWarnings("unused") final Exception e) {
				// Cancel the remaining workers anyway
			}
		}
		if (executor != null) {
			executor.shutdownNow();
		}
		checkFinished();
	}

	private void checkFinished() {
		if (!finished) {
			final boolean allDone = cancelled || (totalTasks >= 0 && progress.get() >= totalTasks);
			if (allDone) {
				finished = true;
				progressBar.setIndeterminate(false);
				actionButton.setText(LangResources.get("close"));
				downloadButton.setEnabled(true);
				tableModel.fireTableRowsUpdated(0, Math.max(0, workerStatsList.size() - 1));
			}
		}
	}

	private void exportResults() {
		final JFileChooser fileChooser = new JFileChooser();
		final FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("CSV (*.csv)", "csv");
		fileChooser.addChoosableFileFilter(csvFilter);
		fileChooser.setFileFilter(csvFilter);
		fileChooser.setSelectedFile(new File("worker_ergebnisse.csv"));
		if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		final File exportFile = fileChooser.getSelectedFile();
		if (exportFile.exists() && JOptionPane.showConfirmDialog(this, LangResources.get("overwriteExistingFile", exportFile.getAbsolutePath()), getTitle(), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
			return;
		}

		try (PrintWriter writer = new PrintWriter(exportFile, StandardCharsets.UTF_8)) {
			writer.print(getResultsCSV());
		} catch (final Exception e) {
			JOptionPane.showMessageDialog(this, LangResources.get("errorMessage", e.getMessage()), LangResources.get("error"), JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Returns the statistics of all workers as CSV text (semicolon separated, with header line).
	 *
	 * @return the CSV text
	 */
	public String getResultsCSV() {
		final StringBuilder result = new StringBuilder();
		result.append(CSV_HEADER).append("\n");
		for (final WorkerStats workerStats : workerStatsList) {
			// "\n" like the header line ("%n" would mix in "\r\n" on Windows)
			result.append(String.format("%d;%d;%d;%s;%s;%s;%s;%s\n",
					workerStats.getWorkerId(),
					workerStats.getSuccessCount(),
					workerStats.getErrorCount(),
					formatDuration(workerStats.getLatestDuration()),
					(workerStats.getLatestStatusWasSuccess() == null ? "" : (workerStats.getLatestStatusWasSuccess() ? "success" : "error")),
					formatDuration(workerStats.getMinimumDuration()),
					formatDuration(workerStats.getAverageDuration()),
					formatDuration(workerStats.getMaximumDuration())));
		}
		return result.toString();
	}

	private static String formatDuration(final Duration duration) {
		return duration == null ? "" : DateUtilities.getShortHumanReadableTimespan(duration, true, false);
	}

	private class WorkerStatsTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 4463226218460823706L;

		private final String[] columnTitles = { "WorkerID",
				LangResources.get("successCount"),
				LangResources.get("errorCount"),
				LangResources.get("latestDuration"),
				LangResources.get("latestStatus"),
				LangResources.get("minDuration"),
				"Ø " + LangResources.get("duration"),
				LangResources.get("maxDuration") };

		@Override
		public int getRowCount() {
			return workerStatsList.size();
		}

		@Override
		public int getColumnCount() {
			return columnTitles.length;
		}

		@Override
		public String getColumnName(final int column) {
			return columnTitles[column];
		}

		@Override
		public boolean isCellEditable(final int row, final int column) {
			return false;
		}

		@Override
		public Object getValueAt(final int row, final int column) {
			final WorkerStats workerStats = workerStatsList.get(row);
			switch (column) {
				case 0:
					return String.valueOf(workerStats.getWorkerId());
				case 1:
					return String.valueOf(workerStats.getSuccessCount());
				case 2:
					return String.valueOf(workerStats.getErrorCount());
				case 3:
					return formatDuration(workerStats.getLatestDuration());
				case 4:
					return workerStats.getLatestStatusWasSuccess() == null ? "" : (workerStats.getLatestStatusWasSuccess() ? LangResources.get("success") : LangResources.get("error"));
				case 5:
					return formatDuration(workerStats.getMinimumDuration());
				case 6:
					return formatDuration(workerStats.getAverageDuration());
				case 7:
					return formatDuration(workerStats.getMaximumDuration());
				default:
					return "";
			}
		}
	}

	/**
	 * Creates the worker for one task run. Called once per run from the pool threads.
	 *
	 * @return a new worker
	 */
	protected abstract WorkerSimple<?> createWorker();

	/**
	 * Decides whether a task run was successful.
	 *
	 * @param workerResult result returned by the worker
	 * @return true if the run counts as success
	 */
	protected abstract boolean checkForSuccess(Object workerResult);
}
