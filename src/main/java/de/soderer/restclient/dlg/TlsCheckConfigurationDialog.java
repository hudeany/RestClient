package de.soderer.restclient.dlg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import de.soderer.network.TlsCheckConfiguration;
import de.soderer.network.TlsCheckConfiguration.TlsCheckConfigurationType;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swt.DropDown;
import de.soderer.utilities.swt.DropDown.MatchMode;
import de.soderer.utilities.swt.ModalDialog;
import de.soderer.utilities.swt.SwtUtilities;

public class TlsCheckConfigurationDialog extends ModalDialog<TlsCheckConfiguration> {
	private TlsCheckConfigurationType selectedType;
	private File selectedFile;
	private char[] password;
	private boolean checkCn;

	private Button okButton;

	public TlsCheckConfigurationDialog(final Shell shell, final String title, final TlsCheckConfigurationType selectedType, final File selectedFile, final char[] password, final boolean checkCn) {
		super(shell, title);

		if (selectedType == null) {
			this.selectedType = TlsCheckConfigurationType.SystemTrustStore;
		} else {
			this.selectedType = selectedType;
		}
		this.selectedFile = selectedFile;
		this.password = password;
		this.checkCn = checkCn;
	}

	@Override
	protected void createComponents(final Shell shell) throws Exception {
		shell.setLayout(new GridLayout(1, false));

		final Label infoLabel = new Label(shell, SWT.WRAP);
		infoLabel.setText(LangResources.get("selectTlsCheckType"));
		infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final Composite mainComposite = new Composite(shell, SWT.NONE);
		mainComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		mainComposite.setLayout(new GridLayout(2, false));

		final Label typeLabel = new Label(mainComposite, SWT.NONE);
		typeLabel.setText(LangResources.get("tlsCheckType") + ":");
		// Fixed list of enum names: no custom values (replaces the former read-only SWT Combo)
		final List<String> typeItems = new ArrayList<>();
		for (final TlsCheckConfigurationType type : TlsCheckConfigurationType.values()) {
			typeItems.add(type.name());
		}
		final DropDown typeCombo = new DropDown(mainComposite, SWT.NONE);
		typeCombo.setCaseSensitive(false);
		typeCombo.setMatchMode(MatchMode.CONTAINS);
		typeCombo.setAllowCustomValues(false);
		typeCombo.setItems(typeItems);
		typeCombo.setText(selectedType.name());
		typeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final Label fileLabel = new Label(mainComposite, SWT.NONE);
		fileLabel.setText(LangResources.get("filePath") + ":");
		final Text fileText = new Text(mainComposite, SWT.BORDER);
		fileText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		if (selectedFile != null) {
			fileText.setText(selectedFile.getAbsolutePath());
		}

		final Button fileBrowseButton = new Button(mainComposite, SWT.PUSH);
		fileBrowseButton.setText(LangResources.get("browse"));
		fileBrowseButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

		final Label passwordLabel = new Label(mainComposite, SWT.NONE);
		passwordLabel.setText(LangResources.get("password") + ":");
		final Text passwordText = new Text(mainComposite, SWT.BORDER | SWT.PASSWORD);
		passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		if (password != null) {
			passwordText.setText(new String(password));
		}

		final Button checkCnCheckbox = new Button(mainComposite, SWT.CHECK);
		checkCnCheckbox.setText(LangResources.get("checkCn"));
		checkCnCheckbox.setSelection(checkCn);
		checkCnCheckbox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		final Composite buttonComposite = new Composite(mainComposite, SWT.NONE);
		buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		buttonComposite.setLayout(SwtUtilities.createNoMarginGridLayout(2, true));

		okButton = new Button(buttonComposite, SWT.PUSH);
		okButton.setText(LangResources.get("ok"));
		okButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final Button cancelButton = new Button(buttonComposite, SWT.PUSH);
		cancelButton.setText(LangResources.get("cancel"));
		cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		updateUI(fileLabel, fileText, fileBrowseButton, passwordLabel, passwordText, checkCnCheckbox);

		typeCombo.addListener(SWT.Selection, e -> {
			final String typeText = typeCombo.getText();
			if (typeText == null) {
				// Only valid entries are accepted, but stay defensive
				return;
			}
			selectedType = TlsCheckConfigurationType.valueOf(typeText);

			if (!selectedType.isFilePathSupported()) {
				fileText.setText("");
				selectedFile = null;
			}

			if (!selectedType.isPasswordSupported()) {
				passwordText.setText("");
				password = null;
			}

			updateUI(fileLabel, fileText, fileBrowseButton, passwordLabel, passwordText, checkCnCheckbox);
			checkButtonStatus(fileText);
		});

		// While the typed type text is invalid (red), "selectedType" still holds the last valid type,
		// so block OK to avoid confirming a type the user no longer sees
		typeCombo.addValidationListener(valid -> {
			if (valid) {
				checkButtonStatus(fileText);
			} else {
				okButton.setEnabled(false);
			}
		});

		checkCnCheckbox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				checkCn = checkCnCheckbox.getSelection();
			}
		});

		fileText.addModifyListener(e -> {
			if (Utilities.isBlank(fileText.getText())) {
				selectedFile = null;
			} else {
				selectedFile = new File(fileText.getText());
			}
			checkButtonStatus(fileText);
		});

		passwordText.addModifyListener(e -> {
			password = passwordText.getText().toCharArray();
			checkButtonStatus(fileText);
		});

		fileBrowseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				final FileDialog dialog = new FileDialog(shell,
						(selectedType == TlsCheckConfigurationType.RecordingSingleCertificate) ? SWT.SAVE : SWT.OPEN);
				final String selected = dialog.open();
				if (selected != null) {
					fileText.setText(selected);
				}
			}
		});

		okButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				setReturnValue(new TlsCheckConfiguration(selectedType, selectedFile, password, checkCn));
				shell.close();
			}
		});

		cancelButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				setReturnValue(null);
				shell.close();
			}
		});

		shell.pack();
	}

	private void updateUI(final Label fileLabel, final Text fileText, final Button fileBrowseButton, final Label passwordLabel, final Text passwordText, final Button checkCnCheckbox) {
		final boolean enableFile = selectedType.isFilePathSupported();
		final boolean fileMustExist = enableFile && selectedType != TlsCheckConfigurationType.RecordingSingleCertificate;
		final boolean enablePassword = selectedType.isPasswordSupported();

		fileLabel.setEnabled(enableFile);
		fileText.setEnabled(enableFile);
		fileBrowseButton.setEnabled(enableFile);
		fileBrowseButton.setText(fileMustExist ? LangResources.get("browse") : LangResources.get("select"));

		passwordLabel.setEnabled(enablePassword);
		passwordText.setEnabled(enablePassword);

		final boolean enableCheckCn = selectedType != TlsCheckConfigurationType.NoCheck;
		checkCnCheckbox.setEnabled(enableCheckCn);
	}

	private void checkButtonStatus(final Text fileText) {
		boolean enabled;

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
