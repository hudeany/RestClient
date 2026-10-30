package de.soderer.restclient.dlg;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import de.soderer.swing.DropDown.MatchMode;
import de.soderer.swing.modern.ButtonStyle;
import de.soderer.swing.modern.DropDown;
import de.soderer.swing.modern.IconKind;
import de.soderer.swing.modern.ModalDialog;
import de.soderer.swing.modern.ModernUi;
import de.soderer.utilities.LangResources;

/**
 * Dialog for the settings of the worker pool load test: number of parallel workers, repetitions per
 * worker, pause between runs and ramp-up time. {@link #open()} returns true if the user clicked "Start".
 *
 * @serial exclude
 */
public class MultipleWorkerConfigurationDialog extends ModalDialog<Boolean> {
	private static final long serialVersionUID = -4906380342127559617L;

	/** Repetitions value for "repeat until canceled" */
	public static final String UNLIMITED_REPETITIONS = "∞";

	/** Width of the spinner text fields in columns */
	private static final int SPINNER_COLUMNS = 6;

	private int workerCount;
	private String repetitions;
	private int pauseSeconds;
	private int rampUpSeconds;

	/**
	 * Creates the dialog.
	 *
	 * @param parent parent window
	 * @param title window title
	 */
	public MultipleWorkerConfigurationDialog(final Window parent, final String title) {
		super(parent, title);

		setHeaderIcon(IconKind.GEAR);
		setResizable(false);

		createComponents();
	}

	private void createComponents() {
		final JPanel formPanel = new JPanel(new GridBagLayout());
		formPanel.setOpaque(false);

		final JSpinner workersSpinner = createSpinner(new SpinnerNumberModel(1, 1, 100, 1));
		addRow(formPanel, 0, LangResources.get("numberOfParallelWorkers") + " (1 ≤ x ≤ 100)", workersSpinner);

		// Editable like the former SWT.DROP_DOWN Combo: presets 1..50, but any other value may be typed in
		final List<String> repetitionItems = new ArrayList<>();
		repetitionItems.add(UNLIMITED_REPETITIONS);
		for (int i = 1; i <= 50; i++) {
			repetitionItems.add(String.valueOf(i));
		}
		final DropDown repetitionsDropDown = new DropDown()
				.withMatchMode(MatchMode.STARTS_WITH)
				.withAllowCustomValues(true)
				.withItems(repetitionItems)
				.withText(UNLIMITED_REPETITIONS);
		addRow(formPanel, 1, LangResources.get("numberOfRepetitionsPerWorker"), repetitionsDropDown);

		final JSpinner pauseSpinner = createSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
		addRow(formPanel, 2, LangResources.get("workerSleepTime") + " (" + LangResources.get("seconds") + ", 0 ≤ x)", pauseSpinner);

		final JSpinner rampUpSpinner = createSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
		addRow(formPanel, 3, LangResources.get("workerRampUpTime") + " (" + LangResources.get("seconds") + ", 0 ≤ x)", rampUpSpinner);

		getContentArea().add(formPanel, BorderLayout.CENTER);

		final JButton startButton = addButton(LangResources.get("start"), IconKind.PLAY, ButtonStyle.PRIMARY, event -> {
			workerCount = (Integer) workersSpinner.getValue();
			repetitions = normalizeRepetitions(repetitionsDropDown.getText());
			pauseSeconds = (Integer) pauseSpinner.getValue();
			rampUpSeconds = (Integer) rampUpSpinner.getValue();

			returnValue = true;
			dispose();
		});

		addButton(LangResources.get("cancel"), null, ButtonStyle.SECONDARY, event -> dispose());

		// A typed repetitions value must be "∞" or a positive number, otherwise starting would fail later on
		repetitionsDropDown.addChangeListener(event -> startButton.setEnabled(normalizeRepetitions(repetitionsDropDown.getText()) != null));

		getRootPane().setDefaultButton(startButton);

		pack();
		setLocationRelativeTo(getOwner());
	}

	/**
	 * Creates a spinner with a fixed text width, so all spinners are aligned and as wide as the drop down.
	 */
	private static JSpinner createSpinner(final SpinnerNumberModel model) {
		final JSpinner spinner = new JSpinner(model);
		((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(SPINNER_COLUMNS);
		return spinner;
	}

	/**
	 * @return "∞", a positive number without surrounding blanks, or null if the
	 *         value is none of these
	 */
	private static String normalizeRepetitions(final String repetitionsText) {
		final String trimmed = repetitionsText == null ? "" : repetitionsText.trim();
		if (UNLIMITED_REPETITIONS.equals(trimmed)) {
			return trimmed;
		}
		try {
			return Integer.parseInt(trimmed) > 0 ? trimmed : null;
		} catch (@SuppressWarnings("unused") final NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Adds a form row: muted label left, stretched input component right.
	 */
	private static void addRow(final JPanel formPanel, final int row, final String labelText, final JComponent field) {
		final GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.gridy = row;
		labelConstraints.anchor = GridBagConstraints.LINE_START;
		labelConstraints.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 14);
		formPanel.add(ModernUi.createMutedLabel(labelText), labelConstraints);

		final GridBagConstraints fieldConstraints = new GridBagConstraints();
		fieldConstraints.gridx = 1;
		fieldConstraints.gridy = row;
		fieldConstraints.weightx = 1;
		fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
		fieldConstraints.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 0);
		formPanel.add(field, fieldConstraints);
	}

	/**
	 * Returns the chosen number of parallel workers.
	 *
	 * @return the number of workers
	 */
	public int getWorkerCount() {
		return workerCount;
	}

	/**
	 * Returns the chosen number of repetitions per worker.
	 *
	 * @return a positive number as text, or {@link #UNLIMITED_REPETITIONS} to repeat until canceled
	 */
	public String getRepetitions() {
		return repetitions;
	}

	/**
	 * Returns the chosen pause between two runs of a worker.
	 *
	 * @return the pause in seconds
	 */
	public int getPauseSeconds() {
		return pauseSeconds;
	}

	/**
	 * Returns the chosen ramp-up time.
	 *
	 * @return the ramp-up time in seconds
	 */
	public int getRampUpSeconds() {
		return rampUpSeconds;
	}
}
