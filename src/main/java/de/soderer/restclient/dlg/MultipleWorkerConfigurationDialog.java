package de.soderer.restclient.dlg;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import de.soderer.utilities.LangResources;
import de.soderer.utilities.swing.DropDown;
import de.soderer.utilities.swing.DropDown.MatchMode;
import de.soderer.utilities.swing.ModalDialog;

public class MultipleWorkerConfigurationDialog extends ModalDialog<Boolean> {
	private static final long serialVersionUID = -4906380342127559617L;

	/** Repetitions value for "repeat until canceled" */
	public static final String UNLIMITED_REPETITIONS = "∞";

	private int workerCount;
	private String repetitions;
	private int pauseSeconds;
	private int rampUpSeconds;

	public MultipleWorkerConfigurationDialog(final Window parent, final String title) {
		super(parent, title);

		createComponents();
	}

	private void createComponents() {
		final int margin = 5;

		final JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));

		final JSpinner workersSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
		addRow(panel, 0, LangResources.get("numberOfParallelWorkers") + " (1 ≤ x ≤ 100):", workersSpinner);

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
		addRow(panel, 1, LangResources.get("numberOfRepetitionsPerWorker") + ":", repetitionsDropDown);

		final JSpinner pauseSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
		addRow(panel, 2, LangResources.get("workerSleepTime") + " (" + LangResources.get("seconds") + ", 0 ≤ x):", pauseSpinner);

		final JSpinner rampUpSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
		addRow(panel, 3, LangResources.get("workerRampUpTime") + " (" + LangResources.get("seconds") + ", 0 ≤ x):", rampUpSpinner);

		final JPanel buttonPanel = new JPanel(new GridLayout(1, 2, margin, 0));

		final JButton startButton = new JButton(LangResources.get("start"));
		startButton.addActionListener(event -> {
			workerCount = (Integer) workersSpinner.getValue();
			repetitions = normalizeRepetitions(repetitionsDropDown.getText());
			pauseSeconds = (Integer) pauseSpinner.getValue();
			rampUpSeconds = (Integer) rampUpSpinner.getValue();

			returnValue = true;
			dispose();
		});
		buttonPanel.add(startButton);

		final JButton cancelButton = new JButton(LangResources.get("cancel"));
		cancelButton.addActionListener(event -> dispose());
		buttonPanel.add(cancelButton);

		// A typed repetitions value must be "∞" or a positive number, otherwise starting would fail later on
		repetitionsDropDown.addChangeListener(event -> startButton.setEnabled(normalizeRepetitions(repetitionsDropDown.getText()) != null));

		final GridBagConstraints buttonConstraints = new GridBagConstraints();
		buttonConstraints.gridx = 0;
		buttonConstraints.gridy = 4;
		buttonConstraints.gridwidth = 2;
		buttonConstraints.fill = GridBagConstraints.HORIZONTAL;
		buttonConstraints.insets = new Insets(margin, 0, 0, 0);
		panel.add(buttonPanel, buttonConstraints);

		setContentPane(panel);
		getRootPane().setDefaultButton(startButton);

		pack();
		setLocationRelativeTo(getOwner());
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

	private static void addRow(final JPanel panel, final int row, final String labelText, final JComponent field) {
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = row;
		constraints.insets = new Insets(2, 0, 2, 5);
		constraints.gridx = 0;
		constraints.anchor = GridBagConstraints.LINE_START;
		panel.add(new JLabel(labelText), constraints);

		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(2, 0, 2, 0);
		panel.add(field, constraints);
	}

	public int getWorkerCount() {
		return workerCount;
	}

	public String getRepetitions() {
		return repetitions;
	}

	public int getPauseSeconds() {
		return pauseSeconds;
	}

	public int getRampUpSeconds() {
		return rampUpSeconds;
	}
}
