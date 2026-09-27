package de.soderer.restclient.dlg;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import de.soderer.json.JsonArray;
import de.soderer.json.JsonNode;
import de.soderer.json.JsonObject;
import de.soderer.json.JsonReader;
import de.soderer.json.JsonWriter;
import de.soderer.json.exception.DuplicateKeyException;
import de.soderer.network.HttpMethod;
import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.HttpUtilities;
import de.soderer.network.NetworkUtilities;
import de.soderer.network.TlsCheckConfiguration;
import de.soderer.network.TlsCheckConfiguration.TlsCheckConfigurationType;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.restclient.RestClient;
import de.soderer.restclient.image.ImageManager;
import de.soderer.restclient.worker.ExecuteHttpRequestWorker;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Result;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.swing.ApplicationConfigurationDialog;
import de.soderer.utilities.swing.ErrorDialog;
import de.soderer.utilities.swing.ProgressDialog;
import de.soderer.utilities.swing.QuestionDialog;
import de.soderer.utilities.swing.ShowDataDialog;
import de.soderer.utilities.swing.SwingColor;
import de.soderer.utilities.swing.UpdateableGuiApplication;
import de.soderer.utilities.worker.WorkerSimple;
import de.soderer.yaml.YamlReader;
import de.soderer.yaml.YamlWriter;
import de.soderer.yaml.data.YamlDocument;
import de.soderer.yaml.data.YamlMapping;
import de.soderer.yaml.data.YamlMultilineScalarChompingType;
import de.soderer.yaml.data.YamlMultilineScalarType;
import de.soderer.yaml.data.YamlScalar;
import de.soderer.yaml.data.YamlScalarType;
import de.soderer.yaml.data.YamlSequence;

public class RestClientDialog extends UpdateableGuiApplication {
	private static final long serialVersionUID = 6013829307145576321L;

	private static final int ACTION_BUTTON_HEIGHT = 48;

	private RequestComponent requestPart;
	private ResponseComponent responsePart;

	private JButton exportRequestResponseButton;

	private final ConfigurationProperties applicationConfiguration;

	public RestClientDialog(final ConfigurationProperties applicationConfiguration) throws Exception {
		super(RestClient.APPLICATION_NAME, RestClient.VERSION, RestClient.KEYSTORE_FILE);

		this.applicationConfiguration = applicationConfiguration;

		setIconImage(ImageManager.getImage("RestClient.png").getImage());
		setTitle(LangResources.get("window_title"));

		final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createLeftPart(), createRightPart());
		splitPane.setResizeWeight(0.5);
		splitPane.setContinuousLayout(true);
		setContentPane(splitPane);

		setSize(1200, 900);
		setMinimumSize(new Dimension(500, 450));
		// Centered with the final size (the SWT variant centered before setting the size)
		setLocationRelativeTo(null);

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent event) {
				closeApplication();
			}

			@Override
			public void windowOpened(final WindowEvent event) {
				// A proportional divider location only works once the split pane has its real size
				splitPane.setDividerLocation(0.5);

				// Deferred, so the main window is completely shown before a possible update dialog appears
				SwingUtilities.invokeLater(() -> runDailyUpdateCheck());
			}
		});

		checkButtonStatus();
	}

	private void runDailyUpdateCheck() {
		if (Utilities.isNotBlank(RestClient.VERSIONINFO_DOWNLOAD_URL) && dailyUpdateCheckIsPending()) {
			setDailyUpdateCheckStatus(true);
			try {
				if (ApplicationUpdateUtilities.checkForNewVersionAvailable(RestClient.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), RestClient.APPLICATION_NAME, RestClient.VERSION) != null) {
					ApplicationUpdateUtilities.executeUpdate(this, RestClient.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), RestClient.APPLICATION_NAME, RestClient.VERSION, RestClient.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true, false);
				}
			} catch (final Exception e) {
				showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e.getMessage()));
			}
		}
	}

	private JPanel createLeftPart() throws Exception {
		final JPanel leftPart = new JPanel(new BorderLayout(0, 3));
		leftPart.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		final JPanel titleRow = new JPanel(new GridBagLayout());
		final GridBagConstraints titleConstraints = new GridBagConstraints();
		titleConstraints.insets = new Insets(0, 0, 0, 3);

		final JLabel applicationLabel = new JLabel(LangResources.get("title"));
		applicationLabel.setFont(new Font("Arial", Font.BOLD, 16));
		titleConstraints.weightx = 1;
		titleConstraints.anchor = GridBagConstraints.LINE_START;
		titleRow.add(applicationLabel, titleConstraints);

		titleConstraints.weightx = 0;
		final JButton configButton = new JButton(ImageManager.getImage("wrench.png"));
		configButton.setToolTipText(LangResources.get("configuration"));
		configButton.addActionListener(event -> openConfiguration());
		titleRow.add(configButton, titleConstraints);

		titleConstraints.insets = new Insets(0, 0, 0, 0);
		final JButton helpButton = new JButton(ImageManager.getImage("question.png"));
		helpButton.setToolTipText(LangResources.get("help"));
		helpButton.addActionListener(event -> new HelpDialog(this, RestClient.APPLICATION_NAME + " (" + RestClient.VERSION.toString() + ") " + LangResources.get("help"), applicationConfiguration).open());
		titleRow.add(helpButton, titleConstraints);

		leftPart.add(titleRow, BorderLayout.NORTH);

		requestPart = new RequestComponent();
		requestPart.setBorder(BorderFactory.createEtchedBorder());
		requestPart.setProxyUrlPresets(applicationConfiguration.getList(RestClient.CONFIG_KEY_PROXY_URL_PRESETS));
		requestPart.addSaveButtonListener(this::saveRequestPreset);
		requestPart.addDeleteButtonListener(this::deleteRequestPreset);
		requestPart.addPresetSelectionListener(this::loadSelectedRequestPreset);
		requestPart.addPresetsReorderedListener(this::saveRequestPresetOrder);
		leftPart.add(requestPart, BorderLayout.CENTER);

		loadPresets();

		return leftPart;
	}

	private JPanel createRightPart() {
		final JPanel rightPart = new JPanel(new BorderLayout(0, 3));
		rightPart.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		responsePart = new ResponseComponent();
		responsePart.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		responsePart.clearResponse();
		rightPart.add(responsePart, BorderLayout.CENTER);

		final JPanel buttonRegion = new JPanel(new GridBagLayout());
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(3, 0, 0, 0);

		// First row: "Send request" takes the free width, export and import sit at the right edge
		final JPanel sendRequestRow = new JPanel(new BorderLayout(5, 0));
		final JButton sendRequestButton = new JButton(LangResources.get("sendRequest"));
		sendRequestButton.setPreferredSize(new Dimension(sendRequestButton.getPreferredSize().width, ACTION_BUTTON_HEIGHT));
		sendRequestButton.addActionListener(event -> executeRequest());
		sendRequestRow.add(sendRequestButton, BorderLayout.CENTER);

		final JPanel exportImportContainer = new JPanel(new GridLayout(1, 2, 5, 0));
		exportRequestResponseButton = new JButton(LangResources.get("export"));
		exportRequestResponseButton.setToolTipText(LangResources.get("exportRequestResponseTooltip"));
		exportRequestResponseButton.addActionListener(event -> openExportFormatMenu());
		exportImportContainer.add(exportRequestResponseButton);

		final JButton importRequestResponseButton = new JButton(LangResources.get("import"));
		importRequestResponseButton.setToolTipText(LangResources.get("importRequestResponseTooltip"));
		importRequestResponseButton.addActionListener(event -> importRequestResponse());
		exportImportContainer.add(importRequestResponseButton);

		// Square-ish buttons of the same height as "Send request", but wide enough for their texts
		final int exportImportWidth = Math.max(ACTION_BUTTON_HEIGHT, Math.max(exportRequestResponseButton.getPreferredSize().width, importRequestResponseButton.getPreferredSize().width));
		exportRequestResponseButton.setPreferredSize(new Dimension(exportImportWidth, ACTION_BUTTON_HEIGHT));
		importRequestResponseButton.setPreferredSize(new Dimension(exportImportWidth, ACTION_BUTTON_HEIGHT));
		sendRequestRow.add(exportImportContainer, BorderLayout.EAST);

		constraints.gridy = 0;
		buttonRegion.add(sendRequestRow, constraints);

		final JButton multipleRequestButton = new JButton(LangResources.get("multipleRequest"));
		multipleRequestButton.addActionListener(event -> executeMultipleRequest());
		constraints.gridy = 1;
		buttonRegion.add(multipleRequestButton, constraints);

		final JPanel clearCloseRow = new JPanel(new GridLayout(1, 2, 5, 0));
		final JButton clearRequestDataButton = new JButton(LangResources.get("clearRequestData"));
		clearRequestDataButton.addActionListener(event -> setRequestPreset(null));
		clearCloseRow.add(clearRequestDataButton);

		final JButton closeButton = new JButton(LangResources.get("close"));
		closeButton.addActionListener(event -> closeApplication());
		clearCloseRow.add(closeButton);

		constraints.gridy = 2;
		buttonRegion.add(clearCloseRow, constraints);

		rightPart.add(buttonRegion, BorderLayout.SOUTH);

		return rightPart;
	}

	private void openConfiguration() {
		try {
			byte[] iconData;
			try (InputStream inputStream = ImageManager.class.getResourceAsStream("/images/icons/RestClient.ico")) {
				iconData = IoUtilities.toByteArray(inputStream);
			}

			final ApplicationConfigurationDialog dialog = new ApplicationConfigurationDialog(this, RestClient.APPLICATION_NAME, RestClient.APPLICATION_STARTUPCLASS_NAME, RestClient.VERSION, RestClient.VERSION_BUILDTIME, applicationConfiguration, iconData, ImageManager.getImage("RestClient.png").getImage(), RestClient.VERSIONINFO_DOWNLOAD_URL, RestClient.TRUSTED_UPDATE_CA_CERTIFICATES, null);
			if (dialog.open() == Result.OK) {
				applicationConfiguration.save();
				requestPart.setProxyUrlPresets(applicationConfiguration.getList(RestClient.CONFIG_KEY_PROXY_URL_PRESETS));
			}
		} catch (final Exception ex) {
			new ErrorDialog(this, RestClient.APPLICATION_NAME, RestClient.VERSION.toString(), RestClient.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
		}
	}

	private static JsonObject readRequestPresets() throws Exception {
		if (RestClient.REQUEST_PRESETS_FILE.exists()) {
			try (JsonReader reader = new JsonReader(new FileInputStream(RestClient.REQUEST_PRESETS_FILE))) {
				return (JsonObject) reader.read();
			}
		} else {
			return new JsonObject();
		}
	}

	private static void writeRequestPresets(final JsonObject requestPresetsJsonObject) throws Exception {
		try (JsonWriter writer = new JsonWriter(new FileOutputStream(RestClient.REQUEST_PRESETS_FILE))) {
			writer.add(requestPresetsJsonObject);
		}
	}

	private void saveRequestPreset() {
		try {
			final JsonObject requestPresetsJsonObject = readRequestPresets();

			final String presetName = requestPart.getPresetName();

			if (!requestPresetsJsonObject.containsKey(presetName)
					|| askYesCancel(LangResources.get("replaceExistingRequestPreset", presetName))) {
				final JsonObject requestPresetJsonObject = createRequestPresetJsonObject();

				if (requestPresetsJsonObject.containsKey(presetName)) {
					requestPresetsJsonObject.replace(presetName, requestPresetJsonObject);
				} else {
					requestPresetsJsonObject.add(presetName, requestPresetJsonObject);
				}

				writeRequestPresets(requestPresetsJsonObject);
				showMessage(RestClient.APPLICATION_NAME, LangResources.get("savedRequestPreset", presetName));
				requestPart.setPresetNames(new ArrayList<>(requestPresetsJsonObject.keySet()));
				requestPart.setPresetName(presetName);
			}
		} catch (final Exception e) {
			showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
		}
	}

	private void deleteRequestPreset() {
		try {
			final String presetName = requestPart.getPresetName();
			if (askYesCancel(LangResources.get("reallyDeleteRequestPreset", presetName))) {
				final JsonObject requestPresetsJsonObject = readRequestPresets();

				requestPresetsJsonObject.remove(presetName);

				writeRequestPresets(requestPresetsJsonObject);
				showMessage(RestClient.APPLICATION_NAME, LangResources.get("deletedRequestPreset", presetName));
				requestPart.setPresetNames(new ArrayList<>(requestPresetsJsonObject.keySet()));

				checkButtonStatus();
			}
		} catch (final Exception e) {
			showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
		}
	}

	private void loadSelectedRequestPreset() {
		try {
			if (RestClient.REQUEST_PRESETS_FILE.exists()) {
				final JsonObject requestPresetsJsonObject = readRequestPresets();
				final JsonNode requestPresetJsonNode = requestPresetsJsonObject.get(requestPart.getPresetName());
				// A typed name that is no saved preset (custom values are allowed) must not clear the request data
				if (requestPresetJsonNode instanceof JsonObject) {
					setRequestPreset((JsonObject) requestPresetJsonNode);
				}
			}
		} catch (final Exception e) {
			showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
		}
	}

	private void saveRequestPresetOrder(final List<String> newPresetOrder) {
		try {
			if (RestClient.REQUEST_PRESETS_FILE.exists()) {
				final JsonObject requestPresetsJsonObject = readRequestPresets();

				final JsonObject reorderedRequestPresetsJsonObject = new JsonObject();
				for (final String presetName : newPresetOrder) {
					if (requestPresetsJsonObject.containsKey(presetName)) {
						reorderedRequestPresetsJsonObject.add(presetName, requestPresetsJsonObject.get(presetName));
					}
				}

				writeRequestPresets(reorderedRequestPresetsJsonObject);
			}
		} catch (final Exception e) {
			showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
		}
	}

	private boolean askYesCancel(final String question) {
		final Integer answer = new QuestionDialog(this, RestClient.APPLICATION_NAME, question, LangResources.get("yes"), LangResources.get("cancel")).open();
		return answer != null && answer == 0;
	}

	public void checkButtonStatus() {
		// do nothing
	}

	/**
	 * Saves the configuration and closes the application window.
	 */
	public void closeApplication() {
		applicationConfiguration.save();
		dispose();
	}

	@Override
	protected void setDailyUpdateCheckStatus(final boolean checkboxStatus) {
		applicationConfiguration.set(ConfigurationProperties.CONFIG_KEY_DAILY_UPDATE_CHECK, checkboxStatus);
		applicationConfiguration.set(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK, LocalDateTime.now().plusDays(1));
		applicationConfiguration.save();
	}

	@Override
	protected Boolean isDailyUpdateCheckActivated() {
		return applicationConfiguration.getBoolean(ConfigurationProperties.CONFIG_KEY_DAILY_UPDATE_CHECK);
	}

	protected boolean dailyUpdateCheckIsPending() {
		return applicationConfiguration.getBoolean(ConfigurationProperties.CONFIG_KEY_DAILY_UPDATE_CHECK)
				&& (applicationConfiguration.getDate(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK) == null || applicationConfiguration.getDate(ConfigurationProperties.CONFIG_KEY_NEXT_DAILY_UPDATE_CHECK).isBefore(LocalDateTime.now()))
				&& NetworkUtilities.checkForNetworkConnection();
	}

	public void showData(final String title, final String text) {
		new ShowDataDialog(this, title, text).withResizable(true).open();
	}

	public void showMessage(final String title, final String text) {
		new QuestionDialog(this, title, text, LangResources.get("ok")).open();
	}

	public void showErrorMessage(final String title, final String text) {
		new QuestionDialog(this, title, text, LangResources.get("ok")).setBackgroundColor(SwingColor.LightRed).open();
	}

	private void loadPresets() throws Exception {
		if (RestClient.REQUEST_PRESETS_FILE.exists()) {
			try (JsonReader reader = new JsonReader(new FileInputStream(RestClient.REQUEST_PRESETS_FILE))) {
				final JsonObject requestPresetsJsonObject = (JsonObject) reader.read();
				requestPart.setPresetNames(new ArrayList<>(requestPresetsJsonObject.keySet()));

				if (requestPresetsJsonObject.size() == 1) {
					final String presetName = requestPresetsJsonObject.keySet().iterator().next();
					setRequestPreset((JsonObject) requestPresetsJsonObject.get(presetName));
					requestPart.setPresetName(presetName);
				}
			}
		}
	}

	private void setRequestPreset(final JsonObject jsonObject) {
		if (jsonObject == null) {
			// Clear HtmlFormParameters first to prevent refill of HTTP header Content-Type
			requestPart.setHtmlFormParameters(new LinkedHashMap<>());

			requestPart.setProxyUrl("");
			requestPart.setMaxRedirects(0);
			requestPart.setHttpMethod("GET");
			requestPart.setServiceUrl("");
			requestPart.setTlsCheckConfiguration(new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true));
			requestPart.setServiceMethod("");
			requestPart.setHttpHeaders(new LinkedHashMap<>());
			requestPart.setUrlParameters(new LinkedHashMap<>());
			requestPart.setRequestBody("");

			requestPart.setIdpUrl("");
			requestPart.setIdpRealm("");
			requestPart.setIdpUsername("");
			requestPart.setIdpPassword(new char[0]);

			responsePart.setDownloadTarget("");
			responsePart.setResponseDataPath("");
		} else {
			requestPart.setProxyUrl((String) jsonObject.getSimpleValue("proxyUrl"));
			// Older presets saved before this field existed simply won't have it -> default to 0 (off)
			final Object maxRedirectsObject = jsonObject.getSimpleValue("maxRedirects");
			requestPart.setMaxRedirects(maxRedirectsObject == null ? 0 : ((Number) maxRedirectsObject).intValue());
			requestPart.setHttpMethod((String) jsonObject.getSimpleValue("httpMethod"));
			requestPart.setServiceUrl((String) jsonObject.getSimpleValue("serviceUrl"));

			final Object tlsCheckConfigurationObject = jsonObject.getSimpleValue("tlsCheck");
			if (tlsCheckConfigurationObject == null) {
				requestPart.setTlsCheckConfiguration(new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true));
			} else if (tlsCheckConfigurationObject instanceof Boolean) {
				if ((Boolean) tlsCheckConfigurationObject) {
					requestPart.setTlsCheckConfiguration(new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true));
				} else {
					requestPart.setTlsCheckConfiguration(new TlsCheckConfiguration(TlsCheckConfigurationType.NoCheck, false));
				}
			} else {
				final JsonObject tlsCheckConfigurationJsonObject = (JsonObject) tlsCheckConfigurationObject;
				TlsCheckConfiguration tlsCheckConfiguration;
				try {
					final TlsCheckConfigurationType tlsCheckConfigurationType = TlsCheckConfigurationType.getTlsCheckConfigurationByName((String) tlsCheckConfigurationJsonObject.getSimpleValue("type"));

					final String filePath = (String) tlsCheckConfigurationJsonObject.getSimpleValue("file");
					final String trustorePassword = (String) tlsCheckConfigurationJsonObject.getSimpleValue("trustorePassword");
					final boolean checkCn;
					if (tlsCheckConfigurationJsonObject.containsKey("checkCn")) {
						checkCn = (Boolean) tlsCheckConfigurationJsonObject.getSimpleValue("checkCn");
					} else {
						checkCn = tlsCheckConfigurationType != TlsCheckConfigurationType.NoCheck;
					}

					tlsCheckConfiguration = new TlsCheckConfiguration(
							tlsCheckConfigurationType,
							(filePath == null ? null : new File(filePath)),
							(trustorePassword == null ? null : trustorePassword.toCharArray()),
							checkCn
							);
				} catch (@SuppressWarnings("unused") final Exception e) {
					tlsCheckConfiguration = new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true);
				}

				requestPart.setTlsCheckConfiguration(tlsCheckConfiguration);
			}

			requestPart.setServiceMethod((String) jsonObject.getSimpleValue("serviceMethod"));

			final Map<String, String> httpHeaders = new LinkedHashMap<>();
			if (jsonObject.containsKey("httpRequestHeaders")) {
				for (final JsonNode httpHeaderJsonNode : ((JsonArray) jsonObject.get("httpRequestHeaders")).items()) {
					final JsonObject httpHeaderJsonObject = (JsonObject) httpHeaderJsonNode;
					httpHeaders.put((String) httpHeaderJsonObject.getSimpleValue("name"), (String) httpHeaderJsonObject.getSimpleValue("value"));
				}
			}
			requestPart.setHttpHeaders(httpHeaders);

			final Map<String, String> urlParameters = new LinkedHashMap<>();
			if (jsonObject.containsKey("urlParameters")) {
				for (final JsonNode urlParameterJsonNode : ((JsonArray) jsonObject.get("urlParameters")).items()) {
					final JsonObject urlParameterJsonObject = (JsonObject) urlParameterJsonNode;
					urlParameters.put((String) urlParameterJsonObject.getSimpleValue("name"), (String) urlParameterJsonObject.getSimpleValue("value"));
				}
			}
			requestPart.setUrlParameters(urlParameters);

			final Map<String, String> htmlFormParameters = new LinkedHashMap<>();
			if (jsonObject.containsKey("htmlFormParameters")) {
				for (final JsonNode htmlFormParameterJsonNode : ((JsonArray) jsonObject.get("htmlFormParameters")).items()) {
					final JsonObject htmlFormParameterJsonObject = (JsonObject) htmlFormParameterJsonNode;
					htmlFormParameters.put((String) htmlFormParameterJsonObject.getSimpleValue("name"), (String) htmlFormParameterJsonObject.getSimpleValue("value"));
				}
			}
			requestPart.setHtmlFormParameters(htmlFormParameters);

			requestPart.setRequestBody((String) jsonObject.getSimpleValue("requestBody"));

			responsePart.setDownloadTarget((String) jsonObject.getSimpleValue("downloadTarget"));
			responsePart.setResponseDataPath((String) jsonObject.getSimpleValue("responseDataPath"));

			requestPart.setIdpUrl((String) jsonObject.getSimpleValue("idpUrl"));
			requestPart.setIdpRealm((String) jsonObject.getSimpleValue("idpRealm"));
			requestPart.setIdpUsername((String) jsonObject.getSimpleValue("idpUsername"));
			final String loadedIdpPassword = (String) jsonObject.getSimpleValue("idpPassword");
			requestPart.setIdpPassword(loadedIdpPassword == null ? null : loadedIdpPassword.toCharArray());

			if (requestPart.getIdpPassword() != null && requestPart.getIdpPassword().length > 0) {
				requestPart.setStoreIdpCredentials(true);
			}
		}

		checkButtonStatus();
	}

	private JsonObject createRequestPresetJsonObject() throws DuplicateKeyException {
		final JsonObject requestPresetJsonObject = new JsonObject();

		requestPresetJsonObject.add("proxyUrl", requestPart.getProxyUrl());
		requestPresetJsonObject.add("maxRedirects", requestPart.getMaxRedirects());
		requestPresetJsonObject.add("httpMethod", requestPart.getHttpMethod());
		requestPresetJsonObject.add("serviceUrl", requestPart.getServiceUrl());

		final TlsCheckConfiguration tlsCheckConfiguration = requestPart.getTlsCheckConfiguration();
		if (tlsCheckConfiguration != null) {
			final JsonObject tlsCheckConfigurationJsonObject = new JsonObject();
			tlsCheckConfigurationJsonObject.add("type", tlsCheckConfiguration.getType().name());
			if (tlsCheckConfiguration.getTrustoreOrPemFile() != null) {
				tlsCheckConfigurationJsonObject.add("file", Utilities.replaceUsersHome(tlsCheckConfiguration.getTrustoreOrPemFile().getAbsolutePath()));
			}
			if (tlsCheckConfiguration.getTrustorePassword() != null) {
				// Persisted in plain text in the presets file, same as before this review - the user
				// has confirmed presets must keep working unattended after a restart, which rules out
				// leaving the password out entirely. If this ever needs to be revisited, an opt-in
				// checkbox analogous to isStoreIdpCredentials() would be the way to make it optional.
				tlsCheckConfigurationJsonObject.add("trustorePassword", new String(tlsCheckConfiguration.getTrustorePassword()));
			}
			tlsCheckConfigurationJsonObject.add("checkCn", tlsCheckConfiguration.getCheckCn());
			requestPresetJsonObject.add("tlsCheck", tlsCheckConfigurationJsonObject);
		}

		requestPresetJsonObject.add("serviceMethod", requestPart.getServiceMethod());

		final JsonArray httpRequestHeadersJsonArray = new JsonArray();
		for (final Entry<String, String> httpRequestHeadersEntry : requestPart.getHttpHeaders().entrySet()) {
			final JsonObject httpRequestHeaderJsonObject = new JsonObject();
			httpRequestHeaderJsonObject.add("name", httpRequestHeadersEntry.getKey());
			httpRequestHeaderJsonObject.add("value", httpRequestHeadersEntry.getValue());
			httpRequestHeadersJsonArray.add(httpRequestHeaderJsonObject);
		}
		requestPresetJsonObject.add("httpRequestHeaders", httpRequestHeadersJsonArray);

		final JsonArray urlParametersJsonArray = new JsonArray();
		for (final Entry<String, String> urlParametersEntry : requestPart.getUrlParameters().entrySet()) {
			final JsonObject urlParameterJsonObject = new JsonObject();
			urlParameterJsonObject.add("name", urlParametersEntry.getKey());
			urlParameterJsonObject.add("value", urlParametersEntry.getValue());
			urlParametersJsonArray.add(urlParameterJsonObject);
		}
		requestPresetJsonObject.add("urlParameters", urlParametersJsonArray);

		final JsonArray htmlFromParametersJsonArray = new JsonArray();
		for (final Entry<String, String> htmlFormParametersEntry : requestPart.getHtmlFormParameters().entrySet()) {
			final JsonObject htmlFormParameterJsonObject = new JsonObject();
			htmlFormParameterJsonObject.add("name", htmlFormParametersEntry.getKey());
			htmlFormParameterJsonObject.add("value", htmlFormParametersEntry.getValue());
			htmlFromParametersJsonArray.add(htmlFormParameterJsonObject);
		}
		requestPresetJsonObject.add("htmlFormParameters", htmlFromParametersJsonArray);

		requestPresetJsonObject.add("requestBody", requestPart.getRequestBody());

		requestPresetJsonObject.add("downloadTarget", responsePart.getDownloadTarget());
		requestPresetJsonObject.add("responseDataPath", responsePart.getResponseDataPath());

		if (Utilities.isNotBlank(requestPart.getIdpUrl())) {
			requestPresetJsonObject.add("idpUrl", requestPart.getIdpUrl());
			requestPresetJsonObject.add("idpRealm", requestPart.getIdpRealm());
			if (requestPart.isStoreIdpCredentials()) {
				requestPresetJsonObject.add("idpUsername", requestPart.getIdpUsername());
				final char[] idpPasswordChars = requestPart.getIdpPassword();
				requestPresetJsonObject.add("idpPassword", idpPasswordChars == null ? null : new String(idpPasswordChars));
			}
		}

		return requestPresetJsonObject;
	}

	/**
	 * Opens a small popup menu below {@link #exportRequestResponseButton} letting the user choose
	 * the export format (YAML, containing request + response, or a cURL command, request only).
	 */
	private void openExportFormatMenu() {
		final JPopupMenu exportFormatMenu = new JPopupMenu();

		final JMenuItem yamlMenuItem = new JMenuItem(LangResources.get("exportFormatYaml"));
		yamlMenuItem.addActionListener(event -> exportRequestResponseToYamlFile());
		exportFormatMenu.add(yamlMenuItem);

		final JMenuItem curlMenuItem = new JMenuItem(LangResources.get("exportFormatCurl"));
		curlMenuItem.addActionListener(event -> exportRequestToCurlFile());
		exportFormatMenu.add(curlMenuItem);

		exportFormatMenu.show(exportRequestResponseButton, 0, exportRequestResponseButton.getHeight());
	}

	/**
	 * Shows a file chooser for saving an export file, proposing a file name
	 * derived from the current preset name.
	 *
	 * @return the chosen file, with the default extension added if the user typed
	 *         a name without any extension, or null if the user canceled or
	 *         declined to overwrite an existing file
	 */
	private File chooseExportFile(final String defaultExtension, final FileNameExtensionFilter fileFilter) {
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle(LangResources.get("export"));
		fileChooser.addChoosableFileFilter(fileFilter);
		fileChooser.setFileFilter(fileFilter);
		final String presetName = requestPart.getPresetName();
		final String defaultFileName = "RestClient_export" + (Utilities.isNotBlank(presetName) ? "_" + presetName.replaceAll("[\\\\/:*?\"<>|]", "_") : "") + "." + defaultExtension;
		fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), defaultFileName));
		if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return null;
		}

		File exportFile = fileChooser.getSelectedFile();
		if (!exportFile.getName().contains(".")) {
			exportFile = new File(exportFile.getParentFile(), exportFile.getName() + "." + defaultExtension);
		}

		// JFileChooser never asks before overwriting, so an existing file is checked here explicitly
		if (exportFile.exists() && !askYesCancel(LangResources.get("overwriteExistingFile", exportFile.getAbsolutePath()))) {
			return null;
		}

		return exportFile;
	}

	/**
	 * Opens a file chooser for import, then auto-detects whether the chosen file is a YAML export
	 * (request + optional response) or a cURL command line (request only) and parses it accordingly -
	 * unlike export, import needs no format selection since the file content already reveals its format.
	 */
	private void importRequestResponse() {
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle(LangResources.get("import"));
		final FileNameExtensionFilter importFilter = new FileNameExtensionFilter("YAML / cURL (*.yaml, *.yml, *.sh, *.txt)", "yaml", "yml", "sh", "txt");
		fileChooser.addChoosableFileFilter(importFilter);
		fileChooser.setFileFilter(importFilter);
		if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		final File importFile = fileChooser.getSelectedFile();
		try {
			final String fileContent = Files.readString(importFile.toPath(), StandardCharsets.UTF_8);
			if (isCurlCommand(fileContent)) {
				applyCurlCommand(fileContent);
				responsePart.clearResponse();
				responsePart.showResponse();
			} else {
				importRequestResponseFromYaml(importFile);
			}
			checkButtonStatus();
		} catch (final Exception e) {
			showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
		}
	}

	/**
	 * Detects whether {@code fileContent} is a cURL command line rather than a YAML export: true if,
	 * after skipping leading blank lines and YAML "#" comment lines, the first remaining line's first
	 * token is exactly "curl" (as in "curl ..." or a multiline command starting "curl \").
	 */
	private static boolean isCurlCommand(final String fileContent) {
		for (final String line : fileContent.split("\r?\n")) {
			final String trimmedLine = line.trim();
			if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
				continue;
			}
			return trimmedLine.regionMatches(true, 0, "curl", 0, 4)
					&& (trimmedLine.length() == 4 || Character.isWhitespace(trimmedLine.charAt(4)));
		}
		return false;
	}

	/**
	 * Exports the currently displayed request as a cURL command line to a user-chosen file.
	 * Unlike the YAML export, this covers the request only: a cURL command has no representation for
	 * response data (httpCode, headers, body, timing), so it is intentionally left out and the user is
	 * informed of this after a successful export.
	 */
	private void exportRequestToCurlFile() {
		final File exportFile = chooseExportFile("sh", new FileNameExtensionFilter("Shell script (*.sh, *.txt)", "sh", "txt"));
		if (exportFile == null) {
			return;
		}

		try {
			final String curlCommand = buildCurlCommand();
			Files.writeString(exportFile.toPath(), curlCommand, StandardCharsets.UTF_8);

			showMessage(RestClient.APPLICATION_NAME, LangResources.get("exportedRequestResponse", exportFile.getAbsolutePath()) + "\n\n" + LangResources.get("curlExportResponseNotIncludedHint"));
		} catch (final Exception e) {
			showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
		}
	}

	/**
	 * Builds a cURL command line representing the currently displayed request. Best-effort: some
	 * request settings have no cURL equivalent and are silently left out (see inline comments below).
	 */
	private String buildCurlCommand() throws Exception {
		final StringBuilder fullUrl = new StringBuilder(Utilities.isNotBlank(requestPart.getServiceUrl()) ? requestPart.getServiceUrl() : "");
		if (Utilities.isNotBlank(requestPart.getServiceMethod())) {
			fullUrl.append("/").append(requestPart.getServiceMethod());
		}
		if (!requestPart.getUrlParameters().isEmpty()) {
			fullUrl.append("?");
			boolean firstUrlParameter = true;
			for (final Entry<String, String> urlParametersEntry : requestPart.getUrlParameters().entrySet()) {
				if (!firstUrlParameter) {
					fullUrl.append("&");
				}
				firstUrlParameter = false;
				fullUrl.append(URLEncoder.encode(urlParametersEntry.getKey(), StandardCharsets.UTF_8));
				fullUrl.append("=");
				fullUrl.append(URLEncoder.encode(urlParametersEntry.getValue() != null ? urlParametersEntry.getValue() : "", StandardCharsets.UTF_8));
			}
		}

		final StringBuilder curlCommand = new StringBuilder("curl");

		if (Utilities.isNotBlank(requestPart.getHttpMethod()) && !"GET".equalsIgnoreCase(requestPart.getHttpMethod())) {
			curlCommand.append(" -X ").append(shellQuote(requestPart.getHttpMethod()));
		}

		curlCommand.append(" ").append(shellQuote(fullUrl.toString()));

		for (final Entry<String, String> httpHeaderEntry : requestPart.getHttpHeaders().entrySet()) {
			curlCommand.append(" \\\n  -H ").append(shellQuote(httpHeaderEntry.getKey() + ": " + httpHeaderEntry.getValue()));
		}

		if (!requestPart.getHtmlFormParameters().isEmpty()) {
			final StringBuilder formBody = new StringBuilder();
			for (final Entry<String, String> formParameterEntry : requestPart.getHtmlFormParameters().entrySet()) {
				if (formBody.length() > 0) {
					formBody.append("&");
				}
				formBody.append(URLEncoder.encode(formParameterEntry.getKey(), StandardCharsets.UTF_8));
				formBody.append("=");
				formBody.append(URLEncoder.encode(formParameterEntry.getValue() != null ? formParameterEntry.getValue() : "", StandardCharsets.UTF_8));
			}
			curlCommand.append(" \\\n  --data ").append(shellQuote(formBody.toString()));
		} else if (Utilities.isNotBlank(requestPart.getRequestBody())) {
			curlCommand.append(" \\\n  --data ").append(shellQuote(requestPart.getRequestBody()));
		}

		if (requestPart.getMaxRedirects() > 0) {
			curlCommand.append(" \\\n  -L --max-redirs ").append(requestPart.getMaxRedirects());
		}

		if (Utilities.isNotBlank(requestPart.getProxyUrl()) && !"DIRECT".equalsIgnoreCase(requestPart.getProxyUrl()) && !"WPAD".equalsIgnoreCase(requestPart.getProxyUrl())) {
			// WPAD proxy autodetection has no cURL equivalent and is left out here
			curlCommand.append(" \\\n  -x ").append(shellQuote(requestPart.getProxyUrl()));
		}

		final TlsCheckConfiguration tlsCheckConfiguration = requestPart.getTlsCheckConfiguration();
		if (tlsCheckConfiguration != null && tlsCheckConfiguration.getType() == TlsCheckConfigurationType.NoCheck) {
			curlCommand.append(" \\\n  -k");
		}
		// A custom truststore/PEM file (TlsCheckConfigurationType other than SystemTrustStore/NoCheck)
		// has no single well-known cURL flag across all such configuration types and is left out here.

		// IdP/OAuth login (idpUrl/idpRealm/idpUsername/idpPassword) has no direct cURL equivalent
		// (it is a separate token-acquisition request performed by RestClient itself) and is left out.

		return curlCommand.toString();
	}

	/**
	 * Quotes a single argument for use in a POSIX shell command line, using single quotes (the only
	 * quoting style that needs no escaping for special characters other than the quote character itself).
	 */
	private static String shellQuote(final String value) {
		return "'" + (value == null ? "" : value.replace("'", "'\\''")) + "'";
	}

	/**
	 * Parses a cURL command line (as copied from a browser's "Copy as cURL" or written by hand) and
	 * fills {@link #requestPart} with it. Best-effort parser covering the commonly used options
	 * (-X/--request, -H/--header, -d/--data/--data-raw/--data-binary, -u/--user, -x/--proxy,
	 * -k/--insecure, -L/--location, --max-redirs, --cacert, --url); unrecognized options are ignored.
	 * A --cacert file is mapped to {@link TlsCheckConfigurationType#SingleCertificateFile} (a single,
	 * unencrypted PEM certificate file, as opposed to a password-protected truststore).
	 */
	private void applyCurlCommand(final String curlCommand) throws Exception {
		// Join line continuations ("... \" followed by a newline), as used when a cURL command
		// copied from a browser is spread across multiple lines for readability
		final String joinedCommand = curlCommand.replace("\\\r\n", " ").replace("\\\n", " ");

		final List<String> tokens = tokenizeCommandLine(joinedCommand);

		String httpMethod = null;
		String url = null;
		String requestBody = null;
		boolean insecure = false;
		int maxRedirects = 0;
		String proxyUrl = null;
		String cacertFile = null;
		final Map<String, String> httpHeaders = new LinkedHashMap<>();

		for (int i = 0; i < tokens.size(); i++) {
			final String token = tokens.get(i);
			if ("curl".equalsIgnoreCase(token) && i == 0) {
				continue;
			} else if (("-X".equals(token) || "--request".equals(token)) && i + 1 < tokens.size()) {
				httpMethod = tokens.get(++i);
			} else if (("-H".equals(token) || "--header".equals(token)) && i + 1 < tokens.size()) {
				final String headerLine = tokens.get(++i);
				final int colonPosition = headerLine.indexOf(':');
				if (colonPosition > 0) {
					httpHeaders.put(headerLine.substring(0, colonPosition).trim(), headerLine.substring(colonPosition + 1).trim());
				}
			} else if (("-d".equals(token) || "--data".equals(token) || "--data-raw".equals(token) || "--data-binary".equals(token) || "--data-ascii".equals(token)) && i + 1 < tokens.size()) {
				requestBody = tokens.get(++i);
				if (httpMethod == null) {
					httpMethod = "POST";
				}
			} else if (("-u".equals(token) || "--user".equals(token)) && i + 1 < tokens.size()) {
				final String userAndPassword = tokens.get(++i);
				httpHeaders.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(userAndPassword.getBytes(StandardCharsets.UTF_8)));
			} else if (("-x".equals(token) || "--proxy".equals(token)) && i + 1 < tokens.size()) {
				proxyUrl = tokens.get(++i);
			} else if ("-k".equals(token) || "--insecure".equals(token)) {
				insecure = true;
			} else if ("-L".equals(token) || "--location".equals(token)) {
				if (maxRedirects == 0) {
					maxRedirects = 10;
				}
			} else if ("--max-redirs".equals(token) && i + 1 < tokens.size()) {
				maxRedirects = Integer.parseInt(tokens.get(++i));
			} else if ("--cacert".equals(token) && i + 1 < tokens.size()) {
				cacertFile = tokens.get(++i);
			} else if ("--url".equals(token) && i + 1 < tokens.size()) {
				url = tokens.get(++i);
			} else if (url == null && !token.startsWith("-")) {
				url = token;
			}
		}

		if (url == null) {
			throw new Exception(LangResources.get("curlImportNoUrlFound"));
		}

		String serviceUrl = url;
		final Map<String, String> urlParameters = new LinkedHashMap<>();
		final int queryStringPosition = url.indexOf('?');
		if (queryStringPosition >= 0) {
			serviceUrl = url.substring(0, queryStringPosition);
			for (final String parameterPart : url.substring(queryStringPosition + 1).split("&")) {
				if (Utilities.isNotBlank(parameterPart)) {
					final int equalsPosition = parameterPart.indexOf('=');
					if (equalsPosition >= 0) {
						urlParameters.put(
								URLDecoder.decode(parameterPart.substring(0, equalsPosition), StandardCharsets.UTF_8),
								URLDecoder.decode(parameterPart.substring(equalsPosition + 1), StandardCharsets.UTF_8));
					} else {
						urlParameters.put(URLDecoder.decode(parameterPart, StandardCharsets.UTF_8), "");
					}
				}
			}
		}

		requestPart.setPresetName(null);
		requestPart.setProxyUrl(proxyUrl == null ? "" : proxyUrl);
		requestPart.setMaxRedirects(maxRedirects);
		requestPart.setHttpMethod(httpMethod == null ? "GET" : httpMethod);
		requestPart.setServiceUrl(serviceUrl);
		requestPart.setServiceMethod(null);
		requestPart.setHttpHeaders(httpHeaders);
		requestPart.setUrlParameters(urlParameters);
		requestPart.setHtmlFormParameters(new LinkedHashMap<>());
		requestPart.setRequestBody(requestBody);
		requestPart.setIdpUrl(null);
		requestPart.setIdpRealm(null);
		requestPart.setIdpUsername(null);
		requestPart.setIdpPassword(null);

		if (insecure) {
			requestPart.setTlsCheckConfiguration(new TlsCheckConfiguration(TlsCheckConfigurationType.NoCheck, false));
		} else if (cacertFile != null) {
			// A --cacert file is a single, unencrypted PEM certificate file (no truststore password)
			requestPart.setTlsCheckConfiguration(new TlsCheckConfiguration(TlsCheckConfigurationType.SingleCertificate, new File(cacertFile), null, true));
		} else {
			requestPart.setTlsCheckConfiguration(new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true));
		}
	}

	/**
	 * Splits a shell command line into tokens, honoring single- and double-quoted sections (with
	 * backslash-escaping inside double quotes and outside quotes, as a real shell would). Sufficient
	 * for the cURL command lines this feature needs to parse; not a full shell grammar implementation.
	 */
	private static List<String> tokenizeCommandLine(final String commandLine) {
		final List<String> tokens = new ArrayList<>();
		final StringBuilder currentToken = new StringBuilder();
		boolean inSingleQuotes = false;
		boolean inDoubleQuotes = false;
		boolean tokenStarted = false;

		for (int i = 0; i < commandLine.length(); i++) {
			final char currentChar = commandLine.charAt(i);
			if (inSingleQuotes) {
				if (currentChar == '\'') {
					inSingleQuotes = false;
				} else {
					currentToken.append(currentChar);
				}
			} else if (inDoubleQuotes) {
				if (currentChar == '\\' && i + 1 < commandLine.length() && (commandLine.charAt(i + 1) == '"' || commandLine.charAt(i + 1) == '\\')) {
					currentToken.append(commandLine.charAt(++i));
				} else if (currentChar == '"') {
					inDoubleQuotes = false;
				} else {
					currentToken.append(currentChar);
				}
			} else if (currentChar == '\'') {
				inSingleQuotes = true;
				tokenStarted = true;
			} else if (currentChar == '"') {
				inDoubleQuotes = true;
				tokenStarted = true;
			} else if (currentChar == '\\' && i + 1 < commandLine.length()) {
				currentToken.append(commandLine.charAt(++i));
				tokenStarted = true;
			} else if (Character.isWhitespace(currentChar)) {
				if (tokenStarted) {
					tokens.add(currentToken.toString());
					currentToken.setLength(0);
					tokenStarted = false;
				}
			} else {
				currentToken.append(currentChar);
				tokenStarted = true;
			}
		}
		if (tokenStarted) {
			tokens.add(currentToken.toString());
		}
		return tokens;
	}

	/**
	 * Writes the currently displayed request and response data (as shown in {@link #requestPart} and
	 * {@link #responsePart}) into a single YAML file chosen by the user via a save file dialog.
	 */
	private void exportRequestResponseToYamlFile() {
		final File exportFile = chooseExportFile("yaml", new FileNameExtensionFilter("YAML (*.yaml, *.yml)", "yaml", "yml"));
		if (exportFile == null) {
			return;
		}

		try {
			final YamlMapping rootMapping = new YamlMapping();
			rootMapping.add("request", createRequestYamlMapping());
			if (hasResponseData()) {
				rootMapping.add("response", createResponseYamlMapping());
			}

			final YamlDocument yamlDocument = new YamlDocument().withRoot(rootMapping);

			try (YamlWriter writer = new YamlWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
				writer.writeDocument(yamlDocument);
			}

			showMessage(RestClient.APPLICATION_NAME, LangResources.get("exportedRequestResponse", exportFile.getAbsolutePath()));
		} catch (final Exception e) {
			showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
		}
	}

	/**
	 * Reads request and response data from a YAML file (already chosen by the caller) and fills
	 * {@link #requestPart} and {@link #responsePart} with it, as if the request had just been sent.
	 */
	private void importRequestResponseFromYaml(final File importFile) throws Exception {
		final YamlDocument yamlDocument;
		try (YamlReader reader = new YamlReader(new FileInputStream(importFile), StandardCharsets.UTF_8)) {
			yamlDocument = reader.readDocument();
		}

		final YamlMapping rootMapping = (YamlMapping) yamlDocument.getRoot();

		if (rootMapping.containsKey("request")) {
			applyRequestYamlMapping((YamlMapping) rootMapping.get("request"));
		}

		responsePart.clearResponse();
		if (rootMapping.containsKey("response")) {
			applyResponseYamlMapping((YamlMapping) rootMapping.get("response"));
		}
		responsePart.showResponse();
	}

	/**
	 * Whether {@link #responsePart} currently shows any actual response data (as opposed to its
	 * cleared/initial state), used to decide whether the "response" section is written on export.
	 */
	private boolean hasResponseData() {
		return responsePart.getHttpCode() != null
				|| Utilities.isNotBlank(responsePart.getResponseBody())
				|| !responsePart.getResponseHeaders().isEmpty();
	}

	/**
	 * The default {@link TlsCheckConfiguration} that {@link #setRequestPreset(JsonObject)} and
	 * {@link #applyRequestYamlMapping(YamlMapping)} fall back to when nothing else is specified
	 * (system truststore, CN check on, no custom file/password). Used to skip writing the "tlsCheck"
	 * section on export when it wouldn't add any information beyond that default.
	 */
	private static boolean isDefaultTlsCheckConfiguration(final TlsCheckConfiguration tlsCheckConfiguration) {
		return tlsCheckConfiguration.getType() == TlsCheckConfigurationType.SystemTrustStore
				&& tlsCheckConfiguration.getTrustoreOrPemFile() == null
				&& tlsCheckConfiguration.getTrustorePassword() == null
				&& tlsCheckConfiguration.getCheckCn();
	}

	/**
	 * Builds a YAML representation of the currently displayed request data. Mirrors
	 * {@link #createRequestPresetJsonObject()}, kept as a separate method because the export file also
	 * contains the response data, which has no equivalent in the request presets file.
	 */
	private YamlMapping createRequestYamlMapping() throws Exception {
		final YamlMapping requestYamlMapping = new YamlMapping();

		if (Utilities.isNotBlank(requestPart.getPresetName())) {
			requestYamlMapping.add("presetName", requestPart.getPresetName());
		}

		if (Utilities.isNotBlank(requestPart.getProxyUrl()) && !"DIRECT".equalsIgnoreCase(requestPart.getProxyUrl())) {
			requestYamlMapping.add("proxyUrl", requestPart.getProxyUrl());
		}
		if (requestPart.getMaxRedirects() > 0) {
			requestYamlMapping.add("maxRedirects", requestPart.getMaxRedirects());
		}
		requestYamlMapping.add("httpMethod", requestPart.getHttpMethod());

		final YamlScalar serviceUrlKeyYamlScalar = new YamlScalar("serviceUrl");
		final YamlScalar serviceUrlYamlScalar = new YamlScalar(requestPart.getServiceUrl());
		if (Utilities.isNotBlank(requestPart.getServiceUrl())) {
			final StringBuilder fullUrl = new StringBuilder(requestPart.getServiceUrl());
			if (Utilities.isNotBlank(requestPart.getServiceMethod())) {
				fullUrl.append("/").append(requestPart.getServiceMethod());
			}
			if (!requestPart.getUrlParameters().isEmpty()) {
				fullUrl.append("?");
				boolean firstUrlParameter = true;
				for (final Entry<String, String> urlParametersEntry : requestPart.getUrlParameters().entrySet()) {
					if (!firstUrlParameter) {
						fullUrl.append("&");
					}
					firstUrlParameter = false;
					fullUrl.append(URLEncoder.encode(urlParametersEntry.getKey(), StandardCharsets.UTF_8));
					fullUrl.append("=");
					fullUrl.append(URLEncoder.encode(urlParametersEntry.getValue() != null ? urlParametersEntry.getValue() : "", StandardCharsets.UTF_8));
				}
			}
			serviceUrlKeyYamlScalar.addLeadingComment(" fullUrl: " + fullUrl);
		}
		requestYamlMapping.add(serviceUrlKeyYamlScalar, serviceUrlYamlScalar);

		requestYamlMapping.add("serviceMethod", requestPart.getServiceMethod());

		final TlsCheckConfiguration tlsCheckConfiguration = requestPart.getTlsCheckConfiguration();
		if (tlsCheckConfiguration != null && !isDefaultTlsCheckConfiguration(tlsCheckConfiguration)) {
			final YamlMapping tlsCheckYamlMapping = new YamlMapping();
			tlsCheckYamlMapping.add("type", tlsCheckConfiguration.getType().name());
			if (tlsCheckConfiguration.getTrustoreOrPemFile() != null) {
				tlsCheckYamlMapping.add("file", Utilities.replaceUsersHome(tlsCheckConfiguration.getTrustoreOrPemFile().getAbsolutePath()));
			}
			if (tlsCheckConfiguration.getTrustorePassword() != null) {
				// Same trade-off as in createRequestPresetJsonObject: stored in plain text so the
				// exported file can be re-imported without manual password re-entry.
				tlsCheckYamlMapping.add("trustorePassword", new String(tlsCheckConfiguration.getTrustorePassword()));
			}
			tlsCheckYamlMapping.add("checkCn", tlsCheckConfiguration.getCheckCn());
			requestYamlMapping.add("tlsCheck", tlsCheckYamlMapping);
		}

		if (!requestPart.getHttpHeaders().isEmpty()) {
			final YamlSequence httpRequestHeadersYamlSequence = new YamlSequence();
			for (final Entry<String, String> httpRequestHeadersEntry : requestPart.getHttpHeaders().entrySet()) {
				final YamlMapping httpRequestHeaderYamlMapping = new YamlMapping();
				httpRequestHeaderYamlMapping.add("name", httpRequestHeadersEntry.getKey());
				httpRequestHeaderYamlMapping.add("value", httpRequestHeadersEntry.getValue());
				httpRequestHeadersYamlSequence.add(httpRequestHeaderYamlMapping);
			}
			requestYamlMapping.add("httpRequestHeaders", httpRequestHeadersYamlSequence);
		}

		if (!requestPart.getUrlParameters().isEmpty()) {
			final YamlSequence urlParametersYamlSequence = new YamlSequence();
			for (final Entry<String, String> urlParametersEntry : requestPart.getUrlParameters().entrySet()) {
				final YamlMapping urlParameterYamlMapping = new YamlMapping();
				urlParameterYamlMapping.add("name", urlParametersEntry.getKey());
				urlParameterYamlMapping.add("value", urlParametersEntry.getValue());
				urlParametersYamlSequence.add(urlParameterYamlMapping);
			}
			requestYamlMapping.add("urlParameters", urlParametersYamlSequence);
		}

		if (!requestPart.getHtmlFormParameters().isEmpty()) {
			final YamlSequence htmlFormParametersYamlSequence = new YamlSequence();
			for (final Entry<String, String> htmlFormParametersEntry : requestPart.getHtmlFormParameters().entrySet()) {
				final YamlMapping htmlFormParameterYamlMapping = new YamlMapping();
				htmlFormParameterYamlMapping.add("name", htmlFormParametersEntry.getKey());
				htmlFormParameterYamlMapping.add("value", htmlFormParametersEntry.getValue());
				htmlFormParametersYamlSequence.add(htmlFormParameterYamlMapping);
			}
			requestYamlMapping.add("htmlFormParameters", htmlFormParametersYamlSequence);
		}

		if (Utilities.isNotBlank(requestPart.getRequestBody())) {
			requestYamlMapping.add("requestBody", requestPart.getRequestBody());
		}

		if (Utilities.isNotBlank(requestPart.getIdpUrl())) {
			requestYamlMapping.add("idpUrl", requestPart.getIdpUrl());
			requestYamlMapping.add("idpRealm", requestPart.getIdpRealm());
			if (requestPart.isStoreIdpCredentials()) {
				requestYamlMapping.add("idpUsername", requestPart.getIdpUsername());
				final char[] idpPasswordChars = requestPart.getIdpPassword();
				requestYamlMapping.add("idpPassword", idpPasswordChars == null ? null : new String(idpPasswordChars));
			}
		}

		return requestYamlMapping;
	}

	/**
	 * Builds a YAML representation of the currently displayed response data.
	 * @throws DuplicateKeyException
	 */
	private YamlMapping createResponseYamlMapping() throws DuplicateKeyException {
		final YamlMapping responseYamlMapping = new YamlMapping();

		if (responsePart.getHttpCode() != null) {
			responseYamlMapping.add("httpCode", responsePart.getHttpCode());
		}
		if (Utilities.isNotBlank(responsePart.getIpAddress())) {
			responseYamlMapping.add("ipAddress", responsePart.getIpAddress());
		}
		if (Utilities.isNotBlank(responsePart.getTime())) {
			responseYamlMapping.add("time", responsePart.getTime());
		}
		if (Utilities.isNotBlank(responsePart.getDownloadTarget())) {
			responseYamlMapping.add("downloadTarget", responsePart.getDownloadTarget());
		}
		if (Utilities.isNotBlank(responsePart.getResponseDataPath())) {
			responseYamlMapping.add("responseDataPath", responsePart.getResponseDataPath());
		}

		if (!responsePart.getResponseHeaders().isEmpty()) {
			final YamlSequence responseHeadersYamlSequence = new YamlSequence();
			for (final Entry<String, String> responseHeaderEntry : responsePart.getResponseHeaders().entrySet()) {
				final YamlMapping responseHeaderYamlMapping = new YamlMapping();
				responseHeaderYamlMapping.add("name", responseHeaderEntry.getKey());
				responseHeaderYamlMapping.add("value", responseHeaderEntry.getValue());
				responseHeadersYamlSequence.add(responseHeaderYamlMapping);
			}
			responseYamlMapping.add("responseHeaders", responseHeadersYamlSequence);
		}

		if (Utilities.isNotBlank(responsePart.getResponseBody())) {
			// The 1-arg YamlScalar(Object) constructor always produces YamlScalarType.STRING, and
			// getType() (not the multiline settings below) is what YamlWriter uses to decide between
			// a quoted single-line string and a "|"/">" block scalar - so MULTILINE must be requested
			// explicitly via this constructor for the block style to actually be written.
			final YamlScalar responseBodyYamlScalar = new YamlScalar(responsePart.getResponseBody(), YamlScalarType.MULTILINE);
			try {
				responseBodyYamlScalar.setMultilineType(YamlMultilineScalarType.LITERAL);
				responseBodyYamlScalar.setMultilineChompingType(YamlMultilineScalarChompingType.STRIP);
			} catch (@SuppressWarnings("unused") final Exception e) {
				// Cannot actually happen: both enum constants passed above are never null
			}
			responseYamlMapping.add("responseBody", responseBodyYamlScalar);
		}

		return responseYamlMapping;
	}

	/**
	 * Fills {@link #requestPart} from an imported YAML mapping. Mirrors {@link #setRequestPreset(JsonObject)},
	 * but never clears the fields when {@code requestYamlMapping} is {@code null} (unlike the preset variant,
	 * import simply leaves the request part untouched in that case).
	 */
	private void applyRequestYamlMapping(final YamlMapping requestYamlMapping) {
		if (requestYamlMapping == null) {
			return;
		}

		if (requestYamlMapping.containsKey("presetName")) {
			requestPart.setPresetName((String) requestYamlMapping.getSimpleValue("presetName"));
		}

		requestPart.setProxyUrl(requestYamlMapping.containsKey("proxyUrl") ? (String) requestYamlMapping.getSimpleValue("proxyUrl") : "");
		final Object maxRedirectsObject = requestYamlMapping.getSimpleValue("maxRedirects");
		requestPart.setMaxRedirects(maxRedirectsObject == null ? 0 : ((Number) maxRedirectsObject).intValue());
		requestPart.setHttpMethod((String) requestYamlMapping.getSimpleValue("httpMethod"));
		requestPart.setServiceUrl((String) requestYamlMapping.getSimpleValue("serviceUrl"));

		if (requestYamlMapping.containsKey("tlsCheck")) {
			final YamlMapping tlsCheckYamlMapping = (YamlMapping) requestYamlMapping.get("tlsCheck");
			TlsCheckConfiguration tlsCheckConfiguration;
			try {
				final TlsCheckConfigurationType tlsCheckConfigurationType = TlsCheckConfigurationType.getTlsCheckConfigurationByName((String) tlsCheckYamlMapping.getSimpleValue("type"));

				final String filePath = (String) tlsCheckYamlMapping.getSimpleValue("file");
				final String trustorePassword = (String) tlsCheckYamlMapping.getSimpleValue("trustorePassword");
				final boolean checkCn;
				if (tlsCheckYamlMapping.containsKey("checkCn")) {
					checkCn = (Boolean) tlsCheckYamlMapping.getSimpleValue("checkCn");
				} else {
					checkCn = tlsCheckConfigurationType != TlsCheckConfigurationType.NoCheck;
				}

				tlsCheckConfiguration = new TlsCheckConfiguration(
						tlsCheckConfigurationType,
						(filePath == null ? null : new File(filePath)),
						(trustorePassword == null ? null : trustorePassword.toCharArray()),
						checkCn
						);
			} catch (@SuppressWarnings("unused") final Exception e) {
				tlsCheckConfiguration = new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true);
			}
			requestPart.setTlsCheckConfiguration(tlsCheckConfiguration);
		} else {
			requestPart.setTlsCheckConfiguration(new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true));
		}

		requestPart.setServiceMethod((String) requestYamlMapping.getSimpleValue("serviceMethod"));

		final Map<String, String> httpHeaders = new LinkedHashMap<>();
		if (requestYamlMapping.containsKey("httpRequestHeaders")) {
			for (final Object httpHeaderItem : ((YamlSequence) requestYamlMapping.get("httpRequestHeaders")).items()) {
				final YamlMapping httpHeaderYamlMapping = (YamlMapping) httpHeaderItem;
				httpHeaders.put((String) httpHeaderYamlMapping.getSimpleValue("name"), (String) httpHeaderYamlMapping.getSimpleValue("value"));
			}
		}
		requestPart.setHttpHeaders(httpHeaders);

		final Map<String, String> urlParameters = new LinkedHashMap<>();
		if (requestYamlMapping.containsKey("urlParameters")) {
			for (final Object urlParameterItem : ((YamlSequence) requestYamlMapping.get("urlParameters")).items()) {
				final YamlMapping urlParameterYamlMapping = (YamlMapping) urlParameterItem;
				urlParameters.put((String) urlParameterYamlMapping.getSimpleValue("name"), (String) urlParameterYamlMapping.getSimpleValue("value"));
			}
		}
		requestPart.setUrlParameters(urlParameters);

		// Clear HtmlFormParameters first to prevent refill of HTTP header Content-Type, same as setRequestPreset
		requestPart.setHtmlFormParameters(new LinkedHashMap<>());
		final Map<String, String> htmlFormParameters = new LinkedHashMap<>();
		if (requestYamlMapping.containsKey("htmlFormParameters")) {
			for (final Object htmlFormParameterItem : ((YamlSequence) requestYamlMapping.get("htmlFormParameters")).items()) {
				final YamlMapping htmlFormParameterYamlMapping = (YamlMapping) htmlFormParameterItem;
				htmlFormParameters.put((String) htmlFormParameterYamlMapping.getSimpleValue("name"), (String) htmlFormParameterYamlMapping.getSimpleValue("value"));
			}
		}
		requestPart.setHtmlFormParameters(htmlFormParameters);

		requestPart.setRequestBody((String) requestYamlMapping.getSimpleValue("requestBody"));

		requestPart.setIdpUrl((String) requestYamlMapping.getSimpleValue("idpUrl"));
		requestPart.setIdpRealm((String) requestYamlMapping.getSimpleValue("idpRealm"));
		requestPart.setIdpUsername((String) requestYamlMapping.getSimpleValue("idpUsername"));
		final String loadedIdpPassword = (String) requestYamlMapping.getSimpleValue("idpPassword");
		requestPart.setIdpPassword(loadedIdpPassword == null ? null : loadedIdpPassword.toCharArray());
		if (requestPart.getIdpPassword() != null && requestPart.getIdpPassword().length > 0) {
			requestPart.setStoreIdpCredentials(true);
		}
	}

	/**
	 * Fills {@link #responsePart} from an imported YAML mapping.
	 */
	private void applyResponseYamlMapping(final YamlMapping responseYamlMapping) {
		if (responseYamlMapping == null) {
			return;
		}

		responsePart.setHttpCode((Integer) responseYamlMapping.getSimpleValue("httpCode"));
		responsePart.setIpAddress((String) responseYamlMapping.getSimpleValue("ipAddress"));
		responsePart.setTime((String) responseYamlMapping.getSimpleValue("time"));
		responsePart.setDownloadTarget((String) responseYamlMapping.getSimpleValue("downloadTarget"));

		final Map<String, String> responseHeaders = new LinkedHashMap<>();
		if (responseYamlMapping.containsKey("responseHeaders")) {
			for (final Object responseHeaderItem : ((YamlSequence) responseYamlMapping.get("responseHeaders")).items()) {
				final YamlMapping responseHeaderYamlMapping = (YamlMapping) responseHeaderItem;
				responseHeaders.put((String) responseHeaderYamlMapping.getSimpleValue("name"), (String) responseHeaderYamlMapping.getSimpleValue("value"));
			}
		}
		responsePart.setResponseHeaders(responseHeaders);

		// Set before responseBody, so setResponseBody() below already renders using the
		// imported path instead of the one still left over from the previous response.
		responsePart.setResponseDataPath((String) responseYamlMapping.getSimpleValue("responseDataPath"));
		responsePart.setResponseBody((String) responseYamlMapping.getSimpleValue("responseBody"));
		responsePart.setRedirectInfo(0, null, false);
		responsePart.setRandomParameters(null);
	}

	private void executeRequest() {
		try {
			responsePart.clearResponse();

			ExecuteHttpRequestWorker worker = null;
			try {
				final HttpRequest httpRequest = new HttpRequest(HttpMethod.getHttpMethodByName(requestPart.getHttpMethod()), requestPart.getServiceUrl() + (Utilities.isNotBlank(requestPart.getServiceMethod()) ? "/" + requestPart.getServiceMethod() : ""));
				httpRequest.setMaxRedirects(requestPart.getMaxRedirects());

				if (Utilities.isNotBlank(responsePart.getDownloadTarget())) {
					// Only actually used as a download destination if the response signals a
					// real file download via "Content-Disposition: attachment" - see
					// HttpUtilities.executeHttpRequest(). A normal (e.g. JSON) response is
					// still shown in the response body as usual.
					httpRequest.setDownloadTarget(new File(responsePart.getDownloadTarget()));
				}

				for (final Entry<String, String> httpRequestHeadersEntry : requestPart.getHttpHeaders().entrySet()) {
					httpRequest.addHeader(httpRequestHeadersEntry.getKey(), httpRequestHeadersEntry.getValue());
				}

				for (final Entry<String, String> urlParametersEntry : requestPart.getUrlParameters().entrySet()) {
					httpRequest.addUrlParameter(urlParametersEntry.getKey(), urlParametersEntry.getValue());
				}

				for (final Entry<String, String> htmlFormParametersEntry : requestPart.getHtmlFormParameters().entrySet()) {
					httpRequest.addPostParameter(htmlFormParametersEntry.getKey(), htmlFormParametersEntry.getValue());
				}

				if ("POST".equalsIgnoreCase(requestPart.getHttpMethod()) || "PUT".equalsIgnoreCase(requestPart.getHttpMethod())) {
					if (requestPart.getHtmlFormParameters().size() == 0) {
						if (Utilities.isNotBlank(requestPart.getRequestBody())) {
							httpRequest.setRequestBody(requestPart.getRequestBody());
						}
					}
				}

				Proxy proxy = null;
				if (Utilities.isNotBlank(requestPart.getProxyUrl())) {
					if ("DIRECT".equalsIgnoreCase(requestPart.getProxyUrl())) {
						proxy = Proxy.NO_PROXY;
					} else if ("WPAD".equalsIgnoreCase(requestPart.getProxyUrl())) {
						final ProxyConfiguration requestProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.WPAD);
						proxy = requestProxyConfiguration.getProxy(httpRequest.getUrl());
					} else {
						proxy = HttpUtilities.getProxyFromString(requestPart.getProxyUrl());
					}
				}

				final LocalDateTime start = LocalDateTime.now();

				worker = new ExecuteHttpRequestWorker(null, httpRequest, proxy, requestPart.getTlsCheckConfiguration().getTrustManager(), !requestPart.getTlsCheckConfiguration().getCheckCn());
				HttpResponse httpResponse;
				final ProgressDialog<WorkerSimple<HttpResponse>> progressDialog = new ProgressDialog<>(this, RestClient.APPLICATION_NAME, LangResources.get("sendRequest"), worker);
				final Result dialogResult = progressDialog.open();
				if (dialogResult == Result.CANCELED) {
					showErrorMessage(LangResources.get("sendRequest"), LangResources.get("canceledByUser"));
					return;
				} else {
					httpResponse = worker.get();
				}

				final LocalDateTime end = LocalDateTime.now();
				final Duration responseDuration = Duration.between(start, end);

				if (httpRequest.getPostParameters() != null && httpRequest.getPostParameters().size() > 0) {
					final String requestBody = HttpUtilities.convertToParameterString(httpRequest.getPostParameters(), null);
					requestPart.setRequestBody(requestBody);
				}

				responsePart.setIpAddress(httpResponse.getIpAddress());
				responsePart.setHttpCode(httpResponse.getHttpCode());
				responsePart.setTime(DateUtilities.getShortHumanReadableTimespan(responseDuration, true, false));
				responsePart.setResponseHeaders(httpResponse.getHeaders());
				responsePart.setResponseBody(httpResponse.getContent());
				responsePart.setRedirectInfo(httpResponse.getRedirectCount(), httpResponse.getFinalUrl(), httpResponse.isCredentialsDroppedOnRedirect());
				if (worker.getRandomParameterReplacements() != null && worker.getRandomParameterReplacements().size() > 0) {
					responsePart.setRandomParameters(worker.getRandomParameterReplacements());
				} else {
					responsePart.setRandomParameters(null);
				}
			} catch (final Exception e) {
				responsePart.setIpAddress("");
				responsePart.setHttpCode(null);
				responsePart.setTime("");
				final Map<String, String> responseHeaders = new LinkedHashMap<>();
				responsePart.setResponseHeaders(responseHeaders);
				responsePart.setResponseBody(e.getClass().getSimpleName() + ":\n" + e.getMessage());
				responsePart.setRedirectInfo(0, null, false);

				if (worker != null && worker.getRandomParameterReplacements() != null && worker.getRandomParameterReplacements().size() > 0) {
					responsePart.setRandomParameters(worker.getRandomParameterReplacements());
				} else {
					responsePart.setRandomParameters(null);
				}
			}

			responsePart.showResponse();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private void executeMultipleRequest() {
		try {
			responsePart.clearResponse();

			try {
				final MultipleWorkerConfigurationDialog configurationDialog = new MultipleWorkerConfigurationDialog(this, LangResources.get("multipleWorkerSettings"));
				final Boolean result = configurationDialog.open();
				if (result != null && result) {
					final HttpRequest httpRequest = new HttpRequest(HttpMethod.getHttpMethodByName(requestPart.getHttpMethod()), requestPart.getServiceUrl() + (Utilities.isNotBlank(requestPart.getServiceMethod()) ? "/" + requestPart.getServiceMethod() : ""));
					httpRequest.setMaxRedirects(requestPart.getMaxRedirects());

					for (final Entry<String, String> httpRequestHeadersEntry : requestPart.getHttpHeaders().entrySet()) {
						httpRequest.addHeader(httpRequestHeadersEntry.getKey(), httpRequestHeadersEntry.getValue());
					}

					for (final Entry<String, String> urlParametersEntry : requestPart.getUrlParameters().entrySet()) {
						httpRequest.addUrlParameter(urlParametersEntry.getKey(), urlParametersEntry.getValue());
					}

					for (final Entry<String, String> htmlFormParametersEntry : requestPart.getHtmlFormParameters().entrySet()) {
						httpRequest.addPostParameter(htmlFormParametersEntry.getKey(), htmlFormParametersEntry.getValue());
					}

					if ("POST".equalsIgnoreCase(requestPart.getHttpMethod()) || "PUT".equalsIgnoreCase(requestPart.getHttpMethod())) {
						if (requestPart.getHtmlFormParameters().size() == 0) {
							if (Utilities.isNotBlank(requestPart.getRequestBody())) {
								httpRequest.setRequestBody(requestPart.getRequestBody());
							}
						}
					}

					if (httpRequest.getPostParameters() != null && httpRequest.getPostParameters().size() > 0) {
						final String requestBody = HttpUtilities.convertToParameterString(httpRequest.getPostParameters(), null);
						requestPart.setRequestBody(requestBody);
					}

					Proxy proxy = null;
					if (Utilities.isNotBlank(requestPart.getProxyUrl())) {
						if ("DIRECT".equalsIgnoreCase(requestPart.getProxyUrl())) {
							proxy = Proxy.NO_PROXY;
						} else if ("WPAD".equalsIgnoreCase(requestPart.getProxyUrl())) {
							final ProxyConfiguration requestProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.WPAD, null);
							proxy = requestProxyConfiguration.getProxy(httpRequest.getUrl());
						} else {
							proxy = HttpUtilities.getProxyFromString(requestPart.getProxyUrl());
						}
					}

					responsePart.setIpAddress("");
					responsePart.setHttpCode(null);
					responsePart.setTime("");
					final Map<String, String> responseHeaders = new LinkedHashMap<>();
					responsePart.setResponseHeaders(responseHeaders);
					responsePart.setResponseBody("");

					final int tasksPerWorker = MultipleWorkerConfigurationDialog.UNLIMITED_REPETITIONS.equals(configurationDialog.getRepetitions()) ? -1 : Integer.parseInt(configurationDialog.getRepetitions());

					final String dialogText = LangResources.get("multipleRequestText",
						tasksPerWorker >= 0 ? tasksPerWorker : LangResources.get("unlimited"),
						DateUtilities.getShortHumanReadableTimespan(Duration.ofSeconds(configurationDialog.getPauseSeconds()), true, false),
						DateUtilities.getShortHumanReadableTimespan(Duration.ofSeconds(configurationDialog.getRampUpSeconds()), true, false));

					final HttpRequestWorkerPoolDialog dialog = new HttpRequestWorkerPoolDialog(this, LangResources.get("multipleRequest"), dialogText, httpRequest, proxy, requestPart.getTlsCheckConfiguration());
					dialog.setParallelWorkerAmount(configurationDialog.getWorkerCount());
					dialog.setRepetitionsPerWorker(tasksPerWorker);
					dialog.setSleepTime(Duration.ofSeconds(configurationDialog.getPauseSeconds()));
					dialog.setRampUpTime(Duration.ofSeconds(configurationDialog.getRampUpSeconds()));
					final Boolean dialogResult = dialog.open();

					if (dialogResult != null && dialogResult) {
						responsePart.setResponseBody(dialog.getResultsCSV());
					} else {
						responsePart.setResponseBody("CANCELLED\n\n" + dialog.getResultsCSV());
					}
				}
			} catch (final Exception e) {
				responsePart.setIpAddress("");
				responsePart.setHttpCode(null);
				final Map<String, String> responseHeaders = new LinkedHashMap<>();
				responsePart.setResponseHeaders(responseHeaders);
				responsePart.setResponseBody(e.getClass().getSimpleName() + ":\n" + e.getMessage());
			}

			responsePart.showResponse();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
}
