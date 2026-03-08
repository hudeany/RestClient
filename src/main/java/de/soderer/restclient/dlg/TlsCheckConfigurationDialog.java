package de.soderer.restclient.dlg;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import de.soderer.network.TlsCheckConfiguration;
import de.soderer.network.TlsCheckConfiguration.TlsCheckConfigurationType;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swt.ModalDialog;
import de.soderer.utilities.swt.SwtUtilities;

public class TlsCheckConfigurationDialog extends ModalDialog<TlsCheckConfiguration> {
	private TlsCheckConfigurationType selectedType;
	private File selectedFile;
	private char[] password;

	private Button okButton;

	public TlsCheckConfigurationDialog(final Shell shell, final String title, final TlsCheckConfigurationType selectedType, final File selectedFile, final char[] password) {
		super(shell, title);

		if (selectedType == null) {
			this.selectedType = TlsCheckConfigurationType.SystemTrustStore;
		} else {
			this.selectedType = selectedType;
		}
		this.selectedFile = selectedFile;
		this.password = password;
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
		final Combo typeCombo = new Combo(mainComposite, SWT.DROP_DOWN | SWT.READ_ONLY);
		for (final TlsCheckConfigurationType type : TlsCheckConfigurationType.values()) {
			typeCombo.add(type.name());
		}
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
			fileText.setText(new String(password));
		}

		final Composite buttonComposite = new Composite(mainComposite, SWT.NONE);
		buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		buttonComposite.setLayout(SwtUtilities.createNoMarginGridLayout(2, true));

		okButton = new Button(buttonComposite, SWT.PUSH);
		okButton.setText(LangResources.get("ok"));
		okButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final Button cancelButton = new Button(buttonComposite, SWT.PUSH);
		cancelButton.setText(LangResources.get("cancel"));
		cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		updateUI(fileLabel, fileText, fileBrowseButton, passwordLabel, passwordText);

		typeCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				selectedType = TlsCheckConfigurationType.valueOf(typeCombo.getText());
				updateUI(fileLabel, fileText, fileBrowseButton, passwordLabel, passwordText);
				checkButtonStatus(fileText);
			}
		});

		fileText.addModifyListener(e -> {
			selectedFile = new File(fileText.getText());
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
				setReturnValue(new TlsCheckConfiguration(selectedType, selectedFile, password));
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

	private void updateUI(final Label fileLabel, final Text fileText, final Button fileBrowseButton, final Label passwordLabel, final Text passwordText) {
		final boolean enableFile = switch (selectedType) {
			case TrustStoreFile, AdditionalTrustStoreFile, RecordingToTrustStoreFile, SingleCertificate, RecordingSingleCertificate -> true;
			case NoCheck, SystemTrustStore -> false;
			default -> false;
		};

		final boolean fileMustExist = switch (selectedType) {
			case TrustStoreFile, AdditionalTrustStoreFile, RecordingToTrustStoreFile, SingleCertificate -> true;
			case RecordingSingleCertificate -> false;
			case NoCheck, SystemTrustStore -> false;
			default -> false;
		};

		final boolean enablePassword = switch (selectedType) {
			case TrustStoreFile, AdditionalTrustStoreFile, RecordingToTrustStoreFile -> true;
			case NoCheck, RecordingSingleCertificate, SingleCertificate, SystemTrustStore -> false;
			default -> false;
		};

		fileLabel.setEnabled(enableFile);
		fileText.setEnabled(enableFile);
		fileBrowseButton.setEnabled(enableFile);
		fileBrowseButton.setText(fileMustExist ? LangResources.get("browse") : LangResources.get("select"));

		passwordLabel.setEnabled(enablePassword);
		passwordText.setEnabled(enablePassword);
	}

	private void checkButtonStatus(final Text fileText) {
		boolean enabled = true;

		switch (selectedType) {
			case TrustStoreFile:
			case AdditionalTrustStoreFile:
			case RecordingToTrustStoreFile:
				enabled = Utilities.isNotEmpty(fileText.getText());
				break;
			case SingleCertificate:
				enabled = Utilities.isNotEmpty(fileText.getText()) && new File(fileText.getText()).exists();
				break;
			case RecordingSingleCertificate:
				enabled = Utilities.isNotEmpty(fileText.getText());
				break;
			case SystemTrustStore:
			case NoCheck:
			default:
				enabled = true;
				break;
		}

		okButton.setEnabled(enabled);
	}
}
