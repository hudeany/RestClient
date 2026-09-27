package de.soderer.restclient.dlg;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import de.soderer.utilities.Credentials;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swing.ModalDialog;

public class IdpCredentialsDialog extends ModalDialog<Credentials> {
	private static final long serialVersionUID = 1839920463575017126L;

	private static final int FIELD_WIDTH = 200;

	private final String text;

	private JButton okButton;
	private JTextField idpUrlTextField;
	private JTextField idpRealmTextField;
	private JTextField usernameTextField;
	private JPasswordField passwordTextField;

	private String idpUrl = null;
	private String idpRealm = null;
	private String idpUsername = null;
	private char[] idpPassword = null;

	private boolean rememberCredentials = false;

	public IdpCredentialsDialog(final Window parent, final String title, final String text, final String idpUrl, final String idpRealm, final String idpUsername, final char[] idpPassword) {
		super(parent, title);

		this.text = text;
		this.idpUrl = idpUrl;
		this.idpRealm = idpRealm;
		this.idpUsername = idpUsername;
		this.idpPassword = idpPassword;
	}

	/**
	 * The components are created on open, so settings made after construction
	 * (e.g. {@link #setRememberCredentials(boolean)}) are reflected.
	 */
	@Override
	public Credentials open() {
		createComponents();
		pack();
		setLocationRelativeTo(getOwner());
		return super.open();
	}

	private void createComponents() {
		final int margin = 5;

		final JPanel panel = new JPanel(new BorderLayout(margin, margin));
		panel.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));

		if (Utilities.isNotBlank(text)) {
			final JTextArea textArea = new JTextArea(text);
			textArea.setEditable(false);
			textArea.setFocusable(false);
			textArea.setOpaque(false);
			textArea.setFont(UIManager.getFont("Label.font"));
			panel.add(textArea, BorderLayout.NORTH);
		}

		final JPanel credentialsPanel = new JPanel(new GridBagLayout());
		idpUrlTextField = addField(credentialsPanel, 0, LangResources.get("idpUrl"), new JTextField(Utilities.isNotBlank(idpUrl) ? idpUrl : ""));
		idpRealmTextField = addField(credentialsPanel, 1, LangResources.get("idpRealm"), new JTextField(Utilities.isNotBlank(idpRealm) ? idpRealm : ""));
		usernameTextField = addField(credentialsPanel, 2, LangResources.get("username"), new JTextField(Utilities.isNotBlank(idpUsername) ? idpUsername : ""));
		passwordTextField = addField(credentialsPanel, 3, LangResources.get("password"), new JPasswordField(idpPassword != null ? new String(idpPassword) : ""));

		final JCheckBox rememberCredentialsCheckBox = new JCheckBox(LangResources.get("rememberIdpCredentials"), rememberCredentials);
		rememberCredentialsCheckBox.addActionListener(event -> rememberCredentials = rememberCredentialsCheckBox.isSelected());
		final GridBagConstraints checkBoxConstraints = new GridBagConstraints();
		checkBoxConstraints.gridx = 0;
		checkBoxConstraints.gridy = 4;
		checkBoxConstraints.gridwidth = 2;
		checkBoxConstraints.anchor = GridBagConstraints.LINE_START;
		checkBoxConstraints.insets = new Insets(margin, 0, 0, 0);
		credentialsPanel.add(rememberCredentialsCheckBox, checkBoxConstraints);

		panel.add(credentialsPanel, BorderLayout.CENTER);

		final JPanel buttonPanel = new JPanel(new GridLayout(1, 2, margin, 0));
		okButton = new JButton(LangResources.get("ok"));
		okButton.addActionListener(event -> {
			returnValue = getCredentials();
			dispose();
		});
		buttonPanel.add(okButton);

		final JButton cancelButton = new JButton(LangResources.get("cancel"));
		cancelButton.addActionListener(event -> {
			returnValue = null;
			dispose();
		});
		buttonPanel.add(cancelButton);
		panel.add(buttonPanel, BorderLayout.SOUTH);

		setContentPane(panel);

		// Enter in any field confirms, but only while OK is enabled (the SWT variant also confirmed incomplete credentials)
		getRootPane().setDefaultButton(okButton);

		final DocumentListener fieldListener = new DocumentListener() {
			@Override
			public void insertUpdate(final DocumentEvent event) {
				readFields();
			}

			@Override
			public void removeUpdate(final DocumentEvent event) {
				readFields();
			}

			@Override
			public void changedUpdate(final DocumentEvent event) {
				// Attribute changes only
			}
		};
		idpUrlTextField.getDocument().addDocumentListener(fieldListener);
		idpRealmTextField.getDocument().addDocumentListener(fieldListener);
		usernameTextField.getDocument().addDocumentListener(fieldListener);
		passwordTextField.getDocument().addDocumentListener(fieldListener);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(final WindowEvent event) {
				idpUrlTextField.requestFocusInWindow();
			}
		});

		checkButtonStatus();
	}

	private static <T extends JTextField> T addField(final JPanel panel, final int row, final String labelText, final T field) {
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = row;
		constraints.insets = new Insets(2, 0, 2, 5);
		constraints.gridx = 0;
		constraints.anchor = GridBagConstraints.LINE_START;
		panel.add(new JLabel(labelText), constraints);

		field.setPreferredSize(new Dimension(FIELD_WIDTH, field.getPreferredSize().height));
		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(2, 0, 2, 0);
		panel.add(field, constraints);
		return field;
	}

	private void readFields() {
		idpUrl = idpUrlTextField.getText();
		idpRealm = idpRealmTextField.getText();
		idpUsername = usernameTextField.getText();
		idpPassword = passwordTextField.getPassword();
		checkButtonStatus();
	}

	private void checkButtonStatus() {
		okButton.setEnabled(Utilities.isNotEmpty(idpUrl) && Utilities.isNotEmpty(idpUsername) && idpPassword != null && idpPassword.length > 0);
	}

	public IdpCredentialsDialog setRememberCredentials(final boolean rememberCredentials) {
		this.rememberCredentials = rememberCredentials;
		return this;
	}

	public boolean isRememberCredentials() {
		return rememberCredentials;
	}

	public Credentials getCredentials() {
		if (idpUsername != null) {
			return new Credentials(idpUsername, idpPassword);
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
