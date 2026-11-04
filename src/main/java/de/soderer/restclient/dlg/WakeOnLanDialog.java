package de.soderer.restclient.dlg;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

import de.soderer.network.NetworkUtilities;
import de.soderer.swing.DropDown.MatchMode;
import de.soderer.swing.modern.Button;
import de.soderer.swing.modern.ButtonStyle;
import de.soderer.swing.modern.Card;
import de.soderer.swing.modern.DropDown;
import de.soderer.swing.modern.IconKind;
import de.soderer.swing.modern.ModernUi;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.StringUtilities;

/**
 * Dialog for the target of a Wake-on-LAN magic packet: MAC address, broadcast address and UDP port.
 *
 * <p>
 * Previously used targets are offered in the MAC address drop down; selecting one fills in its broadcast
 * address and port. Targets are stored as entries "MAC|broadcast address|port", see
 * {@link #createTargetEntry(String, String, int)}.
 * </p>
 *
 * @serial exclude
 */
public class WakeOnLanDialog extends JDialog {
	private static final long serialVersionUID = -3207356188297516462L;

	private static final String TARGET_ENTRY_SEPARATOR = "|";

	private final DropDown macAddressCombo;
	private final HintTextField broadcastAddressText;
	private final HintTextField portText;
	private final JLabel errorLabel;

	/** Previously used targets by normalized (upper case) MAC address: [broadcast address, port] */
	private final Map<String, String[]> targetsByMacAddress = new LinkedHashMap<>();

	private boolean confirmed = false;
	private String macAddress;
	private String broadcastAddress;
	private int port;

	/**
	 * Creates the dialog.
	 *
	 * @param owner parent window
	 * @param title window title
	 * @param targetEntries previously used targets, most recent first, as created by {@link #createTargetEntry(String, String, int)}; may be null
	 */
	public WakeOnLanDialog(final Window owner, final String title, final List<String> targetEntries) {
		super(owner, title, ModalityType.APPLICATION_MODAL);

		if (targetEntries != null) {
			for (final String targetEntry : targetEntries) {
				final String[] parts = targetEntry.split("\\" + TARGET_ENTRY_SEPARATOR, -1);
				if (parts.length == 3 && StringUtilities.isNotBlank(parts[0])) {
					targetsByMacAddress.putIfAbsent(parts[0].trim().toUpperCase(Locale.ROOT), new String[] { parts[1], parts[2] });
				}
			}
		}

		macAddressCombo = new DropDown()
				.withCaseSensitive(false)
				.withMatchMode(MatchMode.CONTAINS)
				.withAllowCustomValues(true)
				.withMessage(LangResources.get("macAddressHint"));
		macAddressCombo.setItems(new ArrayList<>(targetsByMacAddress.keySet()));
		macAddressCombo.addActionListener(event -> applyKnownTarget());

		broadcastAddressText = new HintTextField(LangResources.get("broadcastAddressHint"));
		ModernUi.styleTextField(broadcastAddressText, false);
		broadcastAddressText.addActionListener(event -> confirm());

		portText = new HintTextField(Integer.toString(NetworkUtilities.WAKE_ON_LAN_DEFAULT_PORT));
		ModernUi.styleTextField(portText, false);
		portText.setText(Integer.toString(NetworkUtilities.WAKE_ON_LAN_DEFAULT_PORT));
		portText.setColumns(6);
		portText.addActionListener(event -> confirm());

		errorLabel = new JLabel(" ");
		errorLabel.setForeground(ModernUi.DANGER);

		final JPanel formPanel = new JPanel(new GridBagLayout());
		formPanel.setOpaque(false);
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.WEST;

		addRow(formPanel, constraints, ModernUi.createMutedLabel(LangResources.get("macAddress")), 0);
		addRow(formPanel, constraints, macAddressCombo, 2);
		addRow(formPanel, constraints, ModernUi.createMutedLabel(LangResources.get("broadcastAddress")), 10);
		addRow(formPanel, constraints, broadcastAddressText, 2);
		addRow(formPanel, constraints, ModernUi.createMutedLabel(LangResources.get("port")), 10);
		constraints.fill = GridBagConstraints.NONE;
		addRow(formPanel, constraints, portText, 2);
		constraints.fill = GridBagConstraints.HORIZONTAL;
		addRow(formPanel, constraints, errorLabel, 8);

		final Card formCard = new Card(null, null, 12);
		formCard.setContent(formPanel);

		final Button sendButton = new Button(LangResources.get("wakeOnLanSend"), (IconKind) null, ButtonStyle.PRIMARY);
		sendButton.addActionListener(event -> confirm());
		final Button cancelButton = new Button(LangResources.get("cancel"), (IconKind) null, ButtonStyle.SECONDARY);
		cancelButton.addActionListener(event -> dispose());

		final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttonPanel.setOpaque(false);
		buttonPanel.add(cancelButton);
		buttonPanel.add(sendButton);

		final JPanel contentPane = new JPanel(new BorderLayout(0, 8));
		contentPane.setBackground(ModernUi.BACKGROUND);
		contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		contentPane.add(formCard, BorderLayout.CENTER);
		contentPane.add(buttonPanel, BorderLayout.SOUTH);
		setContentPane(contentPane);

		// Escape closes the dialog like the cancel button
		contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
		contentPane.getActionMap().put("cancel", new javax.swing.AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final java.awt.event.ActionEvent event) {
				dispose();
			}
		});

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		// Preselect the most recently used target
		if (!targetsByMacAddress.isEmpty()) {
			macAddressCombo.setText(targetsByMacAddress.keySet().iterator().next());
			applyKnownTarget();
		}
	}

	private static void addRow(final JPanel panel, final GridBagConstraints constraints, final JComponent component, final int topInset) {
		constraints.gridy = panel.getComponentCount();
		constraints.insets = new Insets(topInset, 0, 0, 0);
		panel.add(component, constraints);
	}

	/**
	 * Fills in broadcast address and port, if the entered MAC address is a previously used target.
	 */
	private void applyKnownTarget() {
		final String enteredMacAddress = macAddressCombo.getText();
		if (enteredMacAddress != null) {
			final String[] knownTarget = targetsByMacAddress.get(enteredMacAddress.trim().toUpperCase(Locale.ROOT));
			if (knownTarget != null) {
				broadcastAddressText.setText(knownTarget[0]);
				portText.setText(knownTarget[1]);
			}
		}
	}

	/**
	 * Validates the input and closes the dialog, or shows the validation error in the dialog.
	 */
	private void confirm() {
		final String enteredMacAddress = macAddressCombo.getText() == null ? "" : macAddressCombo.getText().trim();
		try {
			NetworkUtilities.getMacAddressBytes(enteredMacAddress);
		} catch (@SuppressWarnings("unused") final IllegalArgumentException e) {
			showError(LangResources.get("invalidMacAddress", enteredMacAddress));
			return;
		}

		final int enteredPort;
		try {
			enteredPort = StringUtilities.isBlank(portText.getText()) ? NetworkUtilities.WAKE_ON_LAN_DEFAULT_PORT : Integer.parseInt(portText.getText().trim());
			if (enteredPort < 1 || enteredPort > 65535) {
				throw new NumberFormatException();
			}
		} catch (@SuppressWarnings("unused") final NumberFormatException e) {
			showError(LangResources.get("invalidPort", portText.getText()));
			return;
		}

		macAddress = enteredMacAddress.toUpperCase(Locale.ROOT);
		broadcastAddress = broadcastAddressText.getText() == null ? "" : broadcastAddressText.getText().trim();
		port = enteredPort;
		confirmed = true;
		dispose();
	}

	private void showError(final String text) {
		errorLabel.setText(text);
		pack();
	}

	/**
	 * Shows the dialog and blocks until it is closed.
	 *
	 * @return true if the user confirmed with valid input, false if the dialog was canceled
	 */
	public boolean open() {
		pack();
		setMinimumSize(getSize());
		setLocationRelativeTo(getOwner());
		setVisible(true);
		return confirmed;
	}

	/**
	 * Returns the entered MAC address in upper case, as typed (separators are kept).
	 *
	 * @return the MAC address, null if the dialog was canceled
	 */
	public String getMacAddress() {
		return macAddress;
	}

	/**
	 * Returns the entered broadcast address.
	 *
	 * @return the broadcast address, empty for "all local networks", null if the dialog was canceled
	 */
	public String getBroadcastAddress() {
		return broadcastAddress;
	}

	/**
	 * Returns the entered UDP port.
	 *
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Creates a target entry for the list of previously used targets passed to the constructor.
	 *
	 * @param macAddress MAC address
	 * @param broadcastAddress broadcast address, may be empty
	 * @param port UDP port
	 * @return the entry "MAC|broadcast address|port"
	 */
	public static String createTargetEntry(final String macAddress, final String broadcastAddress, final int port) {
		return macAddress + TARGET_ENTRY_SEPARATOR + (broadcastAddress == null ? "" : broadcastAddress) + TARGET_ENTRY_SEPARATOR + port;
	}

	/**
	 * Returns the MAC address of a target entry.
	 *
	 * @param targetEntry entry as created by {@link #createTargetEntry(String, String, int)}
	 * @return the MAC address part
	 */
	public static String getMacAddressOfTargetEntry(final String targetEntry) {
		final int separatorIndex = targetEntry.indexOf(TARGET_ENTRY_SEPARATOR);
		return separatorIndex >= 0 ? targetEntry.substring(0, separatorIndex) : targetEntry;
	}
}
