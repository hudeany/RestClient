package de.soderer.restclient.dlg;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import de.soderer.network.TlsCheckConfiguration;
import de.soderer.network.TlsCheckConfiguration.TlsCheckConfigurationType;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swing.DropDown;
import de.soderer.utilities.swing.DropDown.MatchMode;
import de.soderer.utilities.swing.ModalDialog;

/**
 * Dialog for the TLS server certificate check of a request: check type, truststore or certificate file,
 * truststore password and the CN (hostname) check. {@link #open()} returns the new configuration, or null if canceled.
 *
 * @serial exclude
 */
public class TlsCheckConfigurationDialog extends ModalDialog<TlsCheckConfiguration> {
	private static final long serialVersionUID = 8158372040158337392L;

	private TlsCheckConfigurationType selectedType;
	private File selectedFile;
	private char[] password;
	private boolean checkCn;

	private DropDown typeDropDown;
	private JLabel fileLabel;
	private JTextField fileText;
	private JButton fileBrowseButton;
	private JLabel passwordLabel;
	private JPasswordField passwordText;
	private JCheckBox checkCnCheckbox;
	private JButton okButton;

	/**
	 * Creates the dialog with the given initial values.
	 *
	 * @param parent parent window
	 * @param title window title
	 * @param selectedType initial check type, or null for the system truststore
	 * @param selectedFile initial truststore or certificate file, may be null
	 * @param password initial truststore password, may be null
	 * @param checkCn initial state of the CN check (always off for {@link TlsCheckConfigurationType#NoCheck})
	 */
	public TlsCheckConfigurationDialog(final Window parent, final String title, final TlsCheckConfigurationType selectedType, final File selectedFile, final char[] password, final boolean checkCn) {
		super(parent, title);

		this.selectedType = selectedType == null ? TlsCheckConfigurationType.SystemTrustStore : selectedType;
		this.selectedFile = selectedFile;
		this.password = password;
		// NoCheck never checks the CN (the checkbox is disabled for it)
		this.checkCn = checkCn && this.selectedType != TlsCheckConfigurationType.NoCheck;

		createComponents();
	}

	private void createComponents() {
		final int margin = 5;

		final JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));

		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(2, 0, 2, 5);
		constraints.anchor = GridBagConstraints.LINE_START;

		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.gridwidth = 3;
		constraints.insets = new Insets(0, 0, margin, 0);
		panel.add(new JLabel(LangResources.get("selectTlsCheckType")), constraints);
		constraints.gridwidth = 1;
		constraints.insets = new Insets(2, 0, 2, 5);

		// Fixed list of enum names: no custom values
		final List<String> typeItems = new ArrayList<>();
		for (final TlsCheckConfigurationType type : TlsCheckConfigurationType.values()) {
			typeItems.add(type.name());
		}
		typeDropDown = new DropDown()
				.withCaseSensitive(false)
				.withMatchMode(MatchMode.CONTAINS)
				.withAllowCustomValues(false)
				.withItems(typeItems)
				.withText(selectedType.name());
		addRow(panel, 1, new JLabel(LangResources.get("tlsCheckType") + ":"), typeDropDown, null);

		fileLabel = new JLabel(LangResources.get("filePath") + ":");
		fileText = new JTextField(30);
		if (selectedFile != null) {
			fileText.setText(selectedFile.getAbsolutePath());
		}
		fileBrowseButton = new JButton(LangResources.get("browse"));
		addRow(panel, 2, fileLabel, fileText, fileBrowseButton);

		passwordLabel = new JLabel(LangResources.get("password") + ":");
		passwordText = new JPasswordField(password != null ? new String(password) : "");
		addRow(panel, 3, passwordLabel, passwordText, null);

		checkCnCheckbox = new JCheckBox(LangResources.get("checkCn"), checkCn);
		constraints.gridx = 0;
		constraints.gridy = 4;
		constraints.gridwidth = 3;
		panel.add(checkCnCheckbox, constraints);

		final JPanel buttonPanel = new JPanel(new GridLayout(1, 2, margin, 0));
		okButton = new JButton(LangResources.get("ok"));
		buttonPanel.add(okButton);
		final JButton cancelButton = new JButton(LangResources.get("cancel"));
		buttonPanel.add(cancelButton);

		constraints.gridy = 5;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(margin, 0, 0, 0);
		panel.add(buttonPanel, constraints);

		typeDropDown.addActionListener(event -> {
			final String typeText = typeDropDown.getText();
			if (typeText == null) {
				// Only valid entries are accepted, but stay defensive
				return;
			}
			final TlsCheckConfigurationType previousType = selectedType;
			selectedType = TlsCheckConfigurationType.valueOf(typeText);

			// The CN checkbox is disabled for NoCheck, so its value must not silently stay active there
			// (same default as for loaded presets: CN check off for NoCheck, on for all other types)
			if (selectedType == TlsCheckConfigurationType.NoCheck) {
				checkCn = false;
				checkCnCheckbox.setSelected(false);
			} else if (previousType == TlsCheckConfigurationType.NoCheck) {
				checkCn = true;
				checkCnCheckbox.setSelected(true);
			}

			if (!selectedType.isFilePathSupported()) {
				fileText.setText("");
				selectedFile = null;
			}

			if (!selectedType.isPasswordSupported()) {
				passwordText.setText("");
				password = null;
			}

			updateFieldStates();
			checkButtonStatus();
		});

		// While the typed type text is invalid (red), "selectedType" still holds the last valid type,
		// so block OK to avoid confirming a type the user no longer sees
		typeDropDown.addValidationListener(valid -> {
			if (valid) {
				checkButtonStatus();
			} else {
				okButton.setEnabled(false);
			}
		});

		checkCnCheckbox.addActionListener(event -> checkCn = checkCnCheckbox.isSelected());

		fileText.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(final DocumentEvent event) {
				fileTextChanged();
			}

			@Override
			public void removeUpdate(final DocumentEvent event) {
				fileTextChanged();
			}

			@Override
			public void changedUpdate(final DocumentEvent event) {
				// Attribute changes only
			}
		});

		passwordText.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(final DocumentEvent event) {
				password = passwordText.getPassword();
			}

			@Override
			public void removeUpdate(final DocumentEvent event) {
				password = passwordText.getPassword();
			}

			@Override
			public void changedUpdate(final DocumentEvent event) {
				// Attribute changes only
			}
		});

		fileBrowseButton.addActionListener(event -> {
			final JFileChooser fileChooser = new JFileChooser();
			if (Utilities.isNotBlank(fileText.getText())) {
				fileChooser.setSelectedFile(new File(fileText.getText()));
			}
			final boolean saveMode = selectedType == TlsCheckConfigurationType.RecordingSingleCertificate;
			final int result = saveMode ? fileChooser.showSaveDialog(this) : fileChooser.showOpenDialog(this);
			if (result == JFileChooser.APPROVE_OPTION) {
				fileText.setText(fileChooser.getSelectedFile().getAbsolutePath());
			}
		});

		okButton.addActionListener(event -> {
			returnValue = new TlsCheckConfiguration(selectedType, selectedFile, password, checkCn);
			dispose();
		});

		cancelButton.addActionListener(event -> {
			returnValue = null;
			dispose();
		});

		setContentPane(panel);
		getRootPane().setDefaultButton(okButton);

		updateFieldStates();
		checkButtonStatus();

		pack();
		setLocationRelativeTo(getOwner());
	}

	private static void addRow(final JPanel panel, final int row, final JLabel label, final java.awt.Component field, final JButton trailingButton) {
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = row;
		constraints.insets = new Insets(2, 0, 2, 5);
		constraints.gridx = 0;
		constraints.anchor = GridBagConstraints.LINE_START;
		panel.add(label, constraints);

		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.gridwidth = trailingButton == null ? 2 : 1;
		constraints.insets = new Insets(2, 0, 2, trailingButton == null ? 0 : 5);
		panel.add(field, constraints);

		if (trailingButton != null) {
			constraints.gridx = 2;
			constraints.weightx = 0;
			constraints.gridwidth = 1;
			constraints.insets = new Insets(2, 0, 2, 0);
			panel.add(trailingButton, constraints);
		}
	}

	private void fileTextChanged() {
		if (Utilities.isBlank(fileText.getText())) {
			selectedFile = null;
		} else {
			selectedFile = new File(fileText.getText());
		}
		checkButtonStatus();
	}

	private void updateFieldStates() {
		final boolean enableFile = selectedType.isFilePathSupported();
		final boolean fileMustExist = enableFile && selectedType != TlsCheckConfigurationType.RecordingSingleCertificate;
		final boolean enablePassword = selectedType.isPasswordSupported();

		fileLabel.setEnabled(enableFile);
		fileText.setEnabled(enableFile);
		fileBrowseButton.setEnabled(enableFile);
		fileBrowseButton.setText(fileMustExist ? LangResources.get("browse") : LangResources.get("select"));

		passwordLabel.setEnabled(enablePassword);
		passwordText.setEnabled(enablePassword);

		checkCnCheckbox.setEnabled(selectedType != TlsCheckConfigurationType.NoCheck);
	}

	private void checkButtonStatus() {
		final boolean enabled;
		if (!selectedType.isFilePathSupported()) {
			enabled = true;
		} else if (selectedType == TlsCheckConfigurationType.SingleCertificate) {
			enabled = Utilities.isNotEmpty(fileText.getText()) && new File(fileText.getText()).exists();
		} else {
			enabled = Utilities.isNotEmpty(fileText.getText());
		}

		okButton.setEnabled(enabled);
	}
}
