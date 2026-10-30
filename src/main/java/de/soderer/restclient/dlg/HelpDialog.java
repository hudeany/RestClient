package de.soderer.restclient.dlg;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JPanel;

import de.soderer.applicationbase.ApplicationConfigurationProperties;
import de.soderer.applicationbase.ApplicationUpdateUtilities;
import de.soderer.restclient.RestClient;
import de.soderer.swing.modern.ButtonStyle;
import de.soderer.swing.modern.IconKind;
import de.soderer.swing.modern.ModalDialog;
import de.soderer.swing.modern.ModernUi;
import de.soderer.swing.modern.ShowDataDialog;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.StringUtilities;
import de.soderer.utilities.VersionInfo;

/**
 * Help dialog of the GUI with buttons for the version info, the manual and the update check.
 *
 * @serial exclude
 */
public class HelpDialog extends ModalDialog<Boolean> {
	private static final long serialVersionUID = 2894531603447213845L;

	/**
	 * Extra width for the OS-drawn title bar parts (icon, window buttons, frame),
	 * added to the title text width when making sure the title is not truncated.
	 */
	private static final int TITLE_BAR_PADDING = 120;

	private final RestClientDialog applicationDialog;
	private final ApplicationConfigurationProperties applicationConfiguration;

	/**
	 * Creates the dialog.
	 *
	 * @param applicationDialog main window, parent of this dialog and of the update process
	 * @param title window title
	 * @param applicationConfiguration application configuration, used for the proxy settings of the update check
	 */
	public HelpDialog(final RestClientDialog applicationDialog, final String title, final ApplicationConfigurationProperties applicationConfiguration) {
		super(applicationDialog, title, IconKind.HELP);

		this.applicationDialog = applicationDialog;
		this.applicationConfiguration = applicationConfiguration;

		createComponents();
	}

	private void createComponents() {
		// One column, all buttons equally wide and filling the dialog's width
		final JPanel buttonSection = new JPanel(new GridLayout(0, 1, 0, 8));
		buttonSection.setOpaque(false);

		final JButton versionInfoButton = new ModernUi.Button(LangResources.get("versionInfo"), IconKind.INFO, ButtonStyle.SECONDARY);
		versionInfoButton.addActionListener(event -> new ShowDataDialog(this, RestClient.APPLICATION_NAME + " (" + RestClient.VERSION + ") " + LangResources.get("versionInfo"), VersionInfo.getVersionInfoText()).withResizable(true).open());
		buttonSection.add(versionInfoButton);

		final JButton manualButton = new ModernUi.Button(LangResources.get("manual"), IconKind.FILE, ButtonStyle.SECONDARY);
		manualButton.addActionListener(event -> new ShowDataDialog(this, RestClient.APPLICATION_NAME + " (" + RestClient.VERSION + ") " + LangResources.get("manual"), readManualText()).withResizable(true).withSize(800, 400).open());
		buttonSection.add(manualButton);

		final JButton checkUpdateButton = new ModernUi.Button(LangResources.get("checkUpdate"), IconKind.REFRESH, ButtonStyle.SECONDARY);
		checkUpdateButton.addActionListener(event -> {
			try {
				ApplicationUpdateUtilities.executeUpdate(applicationDialog, RestClient.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), RestClient.APPLICATION_NAME, RestClient.VERSION, RestClient.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true, false);
			} catch (final Exception e) {
				applicationDialog.showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e.getMessage()));
			}
		});
		checkUpdateButton.setEnabled(!StringUtilities.isBlank(RestClient.VERSIONINFO_DOWNLOAD_URL));
		buttonSection.add(checkUpdateButton);

		getContentArea().add(buttonSection);

		final JButton closeButton = addButton(LangResources.get("close"), null, ButtonStyle.PRIMARY, event -> {
			returnValue = true;
			dispose();
		});

		getRootPane().setDefaultButton(closeButton);

		setResizable(false);

		pack();

		widenToFitTitle();

		// Centering must happen last, once the dialog has its final size
		setLocationRelativeTo(applicationDialog);
	}

	/**
	 * pack() only considers the content, never the (often much longer) window
	 * title, so without this the title may get truncated by the window manager.
	 * The GridLayout above stretches the buttons to the new width, so the former
	 * Linux/GTK resize workaround of the SWT variant is not needed.
	 */
	private void widenToFitTitle() {
		final String title = getTitle();
		if (StringUtilities.isBlank(title)) {
			return;
		}

		final int requiredWidth = getFontMetrics(getFont()).stringWidth(title) + TITLE_BAR_PADDING;
		if (getWidth() < requiredWidth) {
			setSize(new Dimension(requiredWidth, getHeight()));
		}
	}

	private static String readManualText() {
		try (InputStream resourceStream = RestClient.class.getResourceAsStream("/manual_" + Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT) + ".txt")) {
			if (resourceStream != null) {
				return IoUtilities.toString(resourceStream, StandardCharsets.UTF_8);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Fall back to the default manual
		}

		try (InputStream resourceStreamDefault = RestClient.class.getResourceAsStream("/manual.txt")) {
			if (resourceStreamDefault != null) {
				return IoUtilities.toString(resourceStreamDefault, StandardCharsets.UTF_8);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Handled below
		}

		return "Manual not available";
	}
}
