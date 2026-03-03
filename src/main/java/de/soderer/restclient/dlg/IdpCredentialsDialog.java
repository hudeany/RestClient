package de.soderer.restclient.dlg;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import de.soderer.utilities.Credentials;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swt.ModalDialog;
import de.soderer.utilities.swt.SwtUtilities;

public class IdpCredentialsDialog extends ModalDialog<Credentials> {
	private final String text;

	private Button okButton;

	private String idpUrl = null;
	private String idpRealm = null;
	private String username = null;
	private char[] password = null;

	private boolean rememberCredentials = false;

	public IdpCredentialsDialog(final Shell shell, final String title, final String text, final String idpUrl, final String idpRealm) {
		super(shell, title);

		this.text = text;
		this.idpUrl = idpUrl;
		this.idpRealm = idpRealm;
	}

	@Override
	protected void createComponents(final Shell shell) throws Exception {
		shell.setLayout(new GridLayout(1, false));

		if (Utilities.isNotBlank(text)) {
			final StyledText styledText = new StyledText(shell, SWT.NONE);
			styledText.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, true, 2, 1));
			styledText.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));
			styledText.setText(text);
			styledText.setBackground(shell.getBackground());
			styledText.setEditable(false);
		}

		final Composite credentialsComposite = new Composite(shell, SWT.NONE);
		credentialsComposite.setLayout(new GridLayout(2, false));

		final Label idpUrlLabel = new Label(credentialsComposite, SWT.NONE);
		idpUrlLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		idpUrlLabel.setText(LangResources.get("idpUrl"));

		final Text idpUrlTextField = new Text(credentialsComposite, SWT.BORDER);

		final GridData gridDataIdpUrl = new GridData(SWT.LEFT, SWT.CENTER, true, true, 1, 1);
		gridDataIdpUrl.widthHint = 200;
		idpUrlTextField.setLayoutData(gridDataIdpUrl);

		idpUrlTextField.setText(Utilities.isNotBlank(idpUrl) ? idpUrl : "");
		idpUrlTextField.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(final ModifyEvent event) {
				idpUrl = ((Text) event.widget).getText();
				checkButtonStatus();
			}
		});
		idpUrlTextField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(final KeyEvent event) {
				if (event.keyCode == SWT.CR || event.keyCode == SwtUtilities.BOTTOM_RIGHT_ENTER_KEY) {
					setReturnValue(getCredentials());
					getParent().close();
				}
			}
		});

		final Label idpRealmLabel = new Label(credentialsComposite, SWT.NONE);
		idpRealmLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		idpRealmLabel.setText(LangResources.get("idpRealm"));

		final Text idpRealmTextField = new Text(credentialsComposite, SWT.BORDER);

		final GridData gridDataIdpRealm = new GridData(SWT.LEFT, SWT.CENTER, true, true, 1, 1);
		gridDataIdpRealm.widthHint = 200;
		idpRealmTextField.setLayoutData(gridDataIdpRealm);

		idpRealmTextField.setText(Utilities.isNotBlank(idpRealm) ? idpRealm : "");
		idpRealmTextField.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(final ModifyEvent event) {
				idpRealm = ((Text) event.widget).getText();
				checkButtonStatus();
			}
		});
		idpRealmTextField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(final KeyEvent event) {
				if (event.keyCode == SWT.CR || event.keyCode == SwtUtilities.BOTTOM_RIGHT_ENTER_KEY) {
					setReturnValue(getCredentials());
					getParent().close();
				}
			}
		});

		final Label usernameLabel = new Label(credentialsComposite, SWT.NONE);
		usernameLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		usernameLabel.setText(LangResources.get("username"));

		final Text usernameTextField = new Text(credentialsComposite, SWT.BORDER);

		final GridData gridDataUsername = new GridData(SWT.LEFT, SWT.CENTER, true, true, 1, 1);
		gridDataUsername.widthHint = 200;
		usernameTextField.setLayoutData(gridDataUsername);

		usernameTextField.setText("");
		usernameTextField.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(final ModifyEvent event) {
				username = ((Text) event.widget).getText();
				checkButtonStatus();
			}
		});
		usernameTextField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(final KeyEvent event) {
				if (event.keyCode == SWT.CR || event.keyCode == SwtUtilities.BOTTOM_RIGHT_ENTER_KEY) {
					setReturnValue(getCredentials());
					getParent().close();
				}
			}
		});

		final Label passwordLabel = new Label(credentialsComposite, SWT.NONE);
		passwordLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		passwordLabel.setText(LangResources.get("password"));

		final Text passwordTextField = new Text(credentialsComposite, SWT.BORDER | SWT.PASSWORD);

		final GridData gridDataPassword = new GridData(SWT.LEFT, SWT.CENTER, true, true, 1, 1);
		gridDataPassword.widthHint = 200;
		passwordTextField.setLayoutData(gridDataPassword);

		passwordTextField.setText("");
		password = "".toCharArray();
		passwordTextField.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(final ModifyEvent event) {
				password = ((Text) event.widget).getTextChars();
				checkButtonStatus();
			}
		});
		passwordTextField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(final KeyEvent event) {
				if (event.keyCode == SWT.CR || event.keyCode == SwtUtilities.BOTTOM_RIGHT_ENTER_KEY) {
					setReturnValue(getCredentials());
					getParent().close();
				}
			}
		});

		final Button rememberCredentialsButton = new Button(shell, SWT.CHECK);
		rememberCredentialsButton.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));
		rememberCredentialsButton.setText(LangResources.get("rememberIdpCredentials"));
		rememberCredentialsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent event) {
				rememberCredentials = ((Button) event.widget).getSelection();
			}
		});

		final Composite mainButtonComposite = new Composite(shell, SWT.NONE);
		mainButtonComposite.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));
		mainButtonComposite.setLayout(SwtUtilities.createNoMarginGridLayout(2, true));

		okButton = new Button(mainButtonComposite, SWT.PUSH);
		okButton.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));
		okButton.setText(LangResources.get("ok"));
		okButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent event) {
				setReturnValue(getCredentials());
				getParent().close();
			}
		});

		final Button cancelButton = new Button(mainButtonComposite, SWT.PUSH);
		cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, false));
		cancelButton.setText(LangResources.get("cancel"));
		cancelButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent event) {
				setReturnValue(null);
				getParent().close();
			}
		});

		shell.pack();

		checkButtonStatus();
	}

	private void checkButtonStatus() {
		okButton.setEnabled(Utilities.isNotEmpty(idpUrl) && Utilities.isNotEmpty(username) && Utilities.isNotEmpty(password));
	}

	public boolean isRememberCredentials() {
		return rememberCredentials;
	}

	public Credentials getCredentials() {
		if (username != null) {
			return new Credentials(username, password);
		} else {
			return null;
		}
	}

	public String getIdpUrl() {
		return idpUrl;
	}

	public String getIdpRealm() {
		return idpRealm;
	}
}
