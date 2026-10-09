package de.soderer.restclient.dlg;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import de.soderer.json.JsonNode;
import de.soderer.json.JsonObject;
import de.soderer.json.JsonWriter;
import de.soderer.json.schema.JsonSchemaDependencyResolver;
import de.soderer.json.schema.JsonSchemaExampleGenerator;
import de.soderer.network.HttpConstants;
import de.soderer.network.HttpContentType;
import de.soderer.network.HttpMethod;
import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.HttpUtilities;
import de.soderer.network.TlsCheckConfiguration;
import de.soderer.network.TlsCheckConfiguration.TlsCheckConfigurationType;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.restclient.RestClient;
import de.soderer.restclient.helper.IdpHelper;
import de.soderer.restclient.worker.ExecuteHttpRequestWorker;
import de.soderer.utilities.Credentials;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Result;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swing.ComboSelectionDialog;
import de.soderer.utilities.swing.CredentialsDialog;
import de.soderer.utilities.swing.DropDown;
import de.soderer.utilities.swing.DropDown.MatchMode;
import de.soderer.utilities.swing.ProgressDialog;
import de.soderer.utilities.swing.QuestionDialog;
import de.soderer.utilities.swing.SimpleInputDialog;
import de.soderer.utilities.swing.SwingColor;
import de.soderer.utilities.worker.WorkerSimple;
import de.soderer.yaml.YamlReader;
import de.soderer.yaml.YamlToJsonConverter;

/**
 * Request editor of the main window: preset selection, proxy, redirects, HTTP method, service URL,
 * TLS check, service method, HTTP headers, URL parameters, HTML form parameters and request body.
 *
 * <p>
 * Also offers helpers to add authorization headers (basic auth, bearer token, IdP token), a Content-Type
 * or other standard headers, and to fill the request from an OpenAPI document. Selecting HTML form
 * parameters automatically adds a matching Content-Type header and disables the request body.
 * </p>
 *
 * @serial exclude
 */
public class RequestComponent extends JPanel {
	private static final long serialVersionUID = -6541962730287135460L;

	/**
	 * HTTP methods offered in the method dropdown and accepted by preset, YAML, cURL and OpenAPI imports.
	 * PATCH and CONNECT are missing because HttpURLConnection (used by HttpUtilities) rejects them with a
	 * ProtocolException; OPTIONS and TRACE are deliberately not offered in the GUI. Importing any other
	 * method fails with an "unsupported HTTP method" error, see {@link #setHttpMethod(String)}.
	 */
	private static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "DELETE", "HEAD");

	private static final int KEY_FIELD_WIDTH = 150;
	private static final int LIST_HEIGHT = 75;
	private static final int MIN_RIGHT_BUTTON_WIDTH = 100;

	private DropDown presetCombo;
	private JButton saveButton;
	private JButton deleteButton;

	private final List<String> presetNames = new ArrayList<>();
	private Runnable presetSelectionListener;
	private Consumer<List<String>> presetsReorderedListener;

	private DropDown httpMethodCombo;
	private HintTextField serviceUrlText;
	private JButton tlsCheckButton;
	private JButton openApiButton;
	private HintTextField serviceMethodText;
	private DropDown proxyUrlCombo;
	private final List<String> proxyUrlPresets = new ArrayList<>();
	private JCheckBox followRedirectsButton;
	private JSpinner maxRedirectHopsSpinner;
	private JTextArea requestBodyText;

	private String idpUrl = null;
	private String idpRealm = null;
	private String idpUsername = null;
	private char[] idpPassword = null;
	private boolean storeIdpCredentials = false;

	private KeyValueSection headerSection;
	private KeyValueSection urlParamSection;
	private KeyValueSection htmlFormParamSection;
	private JButton htmlFormAddButton;

	private ViewportWidthPanel content;
	private int contentRow = 0;

	private TlsCheckConfiguration tlsCheckConfiguration;

	/** Suppresses recursive status checks while checkRequestContentStatus() itself changes the headers */
	private boolean checkingRequestContentStatus = false;

	/**
	 * Creates the request editor with an empty GET request and the system truststore as TLS check.
	 */
	public RequestComponent() {
		super(new BorderLayout());

		tlsCheckConfiguration = new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true);

		createOuterScrollArea();

		checkRequestContentStatus();
	}

	private Window getWindow() {
		return SwingUtilities.getWindowAncestor(this);
	}

	/**
	 * Returns the selected HTTP method.
	 *
	 * @return the HTTP method in upper case, or null while the typed text is no valid method
	 */
	public String getHttpMethod() {
		// DropDown returns null while the typed text is no valid entry (allowCustomValues=false)
		return httpMethodCombo.getText();
	}

	/**
	 * Selects an HTTP method.
	 *
	 * @param method the HTTP method (case-insensitive), or null to keep the current one
	 * @throws IllegalArgumentException if the method is not supported, see {@link #isSupportedHttpMethod(String)}
	 */
	public void setHttpMethod(final String method) {
		if (method != null) {
			if (!isSupportedHttpMethod(method)) {
				// Silently keeping the previous method would send the request with a wrong method later on
				throw new IllegalArgumentException(LangResources.get("unsupportedHttpMethod", method));
			}
			for (final String httpMethod : HTTP_METHODS) {
				if (httpMethod.equalsIgnoreCase(method.trim())) {
					httpMethodCombo.setText(httpMethod);
					break;
				}
			}
		}

		checkRequestContentStatus();
	}

	/**
	 * Checks whether an HTTP method is offered in the method dropdown.
	 *
	 * @param method the HTTP method (case-insensitive), may be null
	 * @return true if the method is supported
	 */
	public static boolean isSupportedHttpMethod(final String method) {
		if (method != null) {
			for (final String httpMethod : HTTP_METHODS) {
				if (httpMethod.equalsIgnoreCase(method.trim())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Sets the TLS check configuration and updates the TLS check button text.
	 *
	 * @param tlsCheckConfiguration the TLS check configuration
	 */
	public void setTlsCheckConfiguration(final TlsCheckConfiguration tlsCheckConfiguration) {
		this.tlsCheckConfiguration = tlsCheckConfiguration;
		updateTlsCheckButtonText();
	}

	/**
	 * Returns the TLS check configuration.
	 *
	 * @return the TLS check configuration
	 */
	public TlsCheckConfiguration getTlsCheckConfiguration() {
		return tlsCheckConfiguration;
	}

	/**
	 * Returns the preset name entered or selected in the preset dropdown.
	 *
	 * @return the preset name
	 */
	public String getPresetName() {
		return presetCombo.getText();
	}

	/**
	 * Returns the service URL (base URL of the request).
	 *
	 * @return the service URL
	 */
	public String getServiceUrl() {
		return serviceUrlText.getText();
	}

	/**
	 * Returns the service method, which is appended to the service URL separated by "/".
	 * Leading slashes are removed from the field, since the separator is added anyway.
	 *
	 * @return the service method without leading slashes
	 */
	public String getServiceMethod() {
		while (serviceMethodText.getText().startsWith("/")) {
			serviceMethodText.setText(serviceMethodText.getText().substring(1));
		}
		return serviceMethodText.getText();
	}

	/**
	 * Returns the proxy setting.
	 *
	 * @return "DIRECT", "WPAD", a proxy URL like "proxy.example.com:8080", or empty for the default
	 */
	public String getProxyUrl() {
		return proxyUrlCombo.getText();
	}

	/**
	 * Returns whether redirects should be followed.
	 *
	 * @return true if redirects should be followed
	 */
	public boolean isFollowRedirects() {
		return followRedirectsButton.isSelected();
	}

	/**
	 * Returns the maximum number of redirects to follow, regardless of the "follow redirects" checkbox.
	 *
	 * @return the hop limit
	 */
	public int getMaxRedirectHops() {
		return (Integer) maxRedirectHopsSpinner.getValue();
	}

	/**
	 * Combines {@link #isFollowRedirects()} and {@link #getMaxRedirectHops()} into the single int value
	 * expected by {@link HttpRequest#setMaxRedirects(int)}.
	 *
	 * @return 0 to not follow redirects, otherwise the hop limit
	 */
	public int getMaxRedirects() {
		return isFollowRedirects() ? getMaxRedirectHops() : 0;
	}

	/**
	 * Returns the request body text.
	 *
	 * @return the request body
	 */
	public String getRequestBody() {
		return requestBodyText.getText();
	}

	/**
	 * Returns the IdP URL used for fetching an access token.
	 *
	 * @return the IdP URL, or null if none is set
	 */
	public String getIdpUrl() {
		return idpUrl;
	}

	/**
	 * Returns the IdP realm used for fetching an access token.
	 *
	 * @return the realm, or null if none is set
	 */
	public String getIdpRealm() {
		return idpRealm;
	}

	/**
	 * Returns the IdP client ID used for fetching an access token.
	 *
	 * @return the client ID, or null if none is set
	 */
	public String getIdpUsername() {
		return idpUsername;
	}

	/**
	 * Returns the IdP client secret used for fetching an access token.
	 *
	 * @return the client secret (not a copy), or null if none is set
	 */
	public char[] getIdpPassword() {
		return idpPassword;
	}

	/**
	 * Returns whether the IdP client ID and secret should be stored in the request preset.
	 *
	 * @return true if the credentials should be stored
	 */
	public boolean isStoreIdpCredentials() {
		return storeIdpCredentials;
	}

	/**
	 * Returns the HTTP headers, skipping rows without a name.
	 *
	 * @return new map of header name to value, in display order
	 */
	public Map<String, String> getHttpHeaders() {
		return headerSection.getEntries();
	}

	/**
	 * Returns the URL parameters, skipping rows without a name.
	 *
	 * @return new map of parameter name to value, in display order
	 */
	public Map<String, String> getUrlParameters() {
		return urlParamSection.getEntries();
	}

	/**
	 * Returns the HTML form parameters, skipping rows without a name.
	 *
	 * @return new map of parameter name to value, in display order
	 */
	public Map<String, String> getHtmlFormParameters() {
		return htmlFormParamSection.getEntries();
	}

	/**
	 * Sets the preset names offered in the preset dropdown.
	 *
	 * @param presets the preset names in display order, or null for none
	 */
	public void setPresetNames(final List<String> presets) {
		presetNames.clear();
		if (presets != null) {
			presetNames.addAll(presets);
		}
		presetCombo.setItems(presetNames);
	}

	/**
	 * Current preset order, e.g. to persist it after the user reordered
	 * entries via drag&amp;drop in the selection popup.
	 *
	 * @return copy of the preset names in display order
	 */
	public List<String> getPresetNames() {
		return new ArrayList<>(presetNames);
	}

	/**
	 * Sets the listener notified when a preset is selected or entered in the preset dropdown.
	 * Replaces any previously set listener.
	 *
	 * @param listener the listener, or null for none
	 */
	public void addPresetSelectionListener(final Runnable listener) {
		presetSelectionListener = listener;
	}

	/**
	 * Sets the list of preset proxy URLs offered in the proxy URL dropdown,
	 * in addition to the built-in "DIRECT" and "WPAD" special values.
	 *
	 * @param presets the proxy URLs, or null for none
	 */
	public void setProxyUrlPresets(final List<String> presets) {
		proxyUrlPresets.clear();
		if (presets != null) {
			proxyUrlPresets.addAll(presets);
		}

		final List<String> comboItems = new ArrayList<>();
		comboItems.add("DIRECT");
		comboItems.add("WPAD");
		for (final String presetProxyUrl : proxyUrlPresets) {
			if (Utilities.isNotBlank(presetProxyUrl) && !comboItems.contains(presetProxyUrl)) {
				comboItems.add(presetProxyUrl);
			}
		}
		proxyUrlCombo.setItems(comboItems);
	}

	/**
	 * Notified with the new preset order whenever the user reorders the
	 * entries via drag&amp;drop in the selection popup. Replaces any previously set listener.
	 *
	 * @param listener the listener, or null for none
	 */
	public void addPresetsReorderedListener(final Consumer<List<String>> listener) {
		presetsReorderedListener = listener;
	}

	/**
	 * Adds a listener to the preset "Save" button.
	 *
	 * @param listener the listener, null is ignored
	 */
	public void addSaveButtonListener(final Runnable listener) {
		if (listener != null) {
			saveButton.addActionListener(event -> listener.run());
		}
	}

	/**
	 * Adds a listener to the preset "Delete" button.
	 *
	 * @param listener the listener, null is ignored
	 */
	public void addDeleteButtonListener(final Runnable listener) {
		if (listener != null) {
			deleteButton.addActionListener(event -> listener.run());
		}
	}

	/**
	 * Sets the text of the preset dropdown.
	 *
	 * @param value preset name to show, or null to clear the preset name field
	 */
	public void setPresetName(final String value) {
		presetCombo.setText(value != null ? value : "");
	}

	/**
	 * Sets the service URL (base URL of the request).
	 *
	 * @param value the service URL, or null to clear the field
	 */
	public void setServiceUrl(final String value) {
		serviceUrlText.setText(value != null ? value : "");
	}

	/**
	 * Sets the service method, which is appended to the service URL separated by "/".
	 *
	 * @param value the service method, or null to clear the field
	 */
	public void setServiceMethod(final String value) {
		serviceMethodText.setText(value != null ? value : "");
	}

	/**
	 * Sets the proxy setting.
	 *
	 * @param value "DIRECT", "WPAD", a proxy URL, or null/empty for the default
	 */
	public void setProxyUrl(final String value) {
		proxyUrlCombo.setText(value != null ? value : "");
	}

	/**
	 * Sets whether redirects should be followed and enables the hop limit field accordingly.
	 *
	 * @param followRedirects true to follow redirects
	 */
	public void setFollowRedirects(final boolean followRedirects) {
		followRedirectsButton.setSelected(followRedirects);
		maxRedirectHopsSpinner.setEnabled(followRedirects);
	}

	/**
	 * Sets the maximum number of redirects to follow, limited to the range 1 to 999.
	 *
	 * @param maxRedirectHops the hop limit
	 */
	public void setMaxRedirectHops(final int maxRedirectHops) {
		maxRedirectHopsSpinner.setValue(Math.max(1, Math.min(999, maxRedirectHops)));
	}

	/**
	 * Counterpart to {@link #getMaxRedirects()}: 0 disables following, any other value enables it and
	 * sets that hop count (negative values are treated as {@link HttpRequest#DEFAULT_MAX_REDIRECTS}, since this
	 * UI does not offer an "unlimited" option).
	 *
	 * @param maxRedirects 0 to not follow redirects, otherwise the hop limit
	 */
	public void setMaxRedirects(final int maxRedirects) {
		setFollowRedirects(maxRedirects != 0);
		setMaxRedirectHops(maxRedirects > 0 ? maxRedirects : HttpRequest.DEFAULT_MAX_REDIRECTS);
	}

	/**
	 * Sets the request body text.
	 *
	 * @param value the request body, or null to clear the field
	 */
	public void setRequestBody(final String value) {
		requestBodyText.setText(value != null ? value : "");
		requestBodyText.setCaretPosition(0);
	}

	/**
	 * Sets the IdP URL used for fetching an access token.
	 *
	 * @param idpUrl the IdP URL, may be null
	 */
	public void setIdpUrl(final String idpUrl) {
		this.idpUrl = idpUrl;
	}

	/**
	 * Sets the IdP realm used for fetching an access token.
	 *
	 * @param idpRealm the realm, may be null
	 */
	public void setIdpRealm(final String idpRealm) {
		this.idpRealm = idpRealm;
	}

	/**
	 * Sets the IdP client ID used for fetching an access token.
	 *
	 * @param idpUsername the client ID, may be null
	 */
	public void setIdpUsername(final String idpUsername) {
		this.idpUsername = idpUsername;
	}

	/**
	 * Sets the IdP client secret used for fetching an access token.
	 *
	 * @param idpPassword the client secret (not copied), may be null
	 */
	public void setIdpPassword(final char[] idpPassword) {
		this.idpPassword = idpPassword;
	}

	/**
	 * Sets whether the IdP client ID and secret should be stored in the request preset.
	 *
	 * @param storeIdpCredentials true to store the credentials
	 */
	public void setStoreIdpCredentials(final boolean storeIdpCredentials) {
		this.storeIdpCredentials = storeIdpCredentials;
	}

	/**
	 * Replaces the HTTP header rows.
	 *
	 * @param headers header name to value, or null for none
	 */
	public void setHttpHeaders(final Map<String, String> headers) {
		headerSection.setEntries(headers);
		checkRequestContentStatus();
	}

	/**
	 * Replaces the URL parameter rows.
	 *
	 * @param urlParams parameter name to value, or null for none
	 */
	public void setUrlParameters(final Map<String, String> urlParams) {
		urlParamSection.setEntries(urlParams);
		checkRequestContentStatus();
	}

	/**
	 * Replaces the HTML form parameter rows. If there are any, a Content-Type header for HTML forms is
	 * added (unless one exists already) and the request body is disabled.
	 *
	 * @param htmlFormParams parameter name to value, or null for none
	 */
	public void setHtmlFormParameters(final Map<String, String> htmlFormParams) {
		htmlFormParamSection.setEntries(htmlFormParams);
		checkRequestContentStatus();
	}

	private void createOuterScrollArea() {
		content = new ViewportWidthPanel(new GridBagLayout());
		content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		createUI();

		final JScrollPane outerScrolled = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		outerScrolled.setBorder(BorderFactory.createEmptyBorder());
		outerScrolled.getVerticalScrollBar().setUnitIncrement(16);
		add(outerScrolled, BorderLayout.CENTER);
	}

	/**
	 * Adds a component as the next full-width row of the content.
	 */
	private void addContentRow(final Component component, final double weighty) {
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = contentRow++;
		constraints.weightx = 1;
		constraints.weighty = weighty;
		constraints.fill = weighty > 0 ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.FIRST_LINE_START;
		constraints.insets = new Insets(2, 0, 2, 0);
		content.add(component, constraints);
	}

	private void createUI() {
		createPresetNameSection();

		// Proxy and OpenAPI: label row above the field row, the right column shares its width with the TLS check column below
		proxyUrlCombo = new DropDown()
				.withCaseSensitive(false)
				.withMatchMode(MatchMode.STARTS_WITH)
				.withAllowCustomValues(true)
				.withMessage(LangResources.get("proxyUrlHint"));

		openApiButton = new JButton("OpenAPI");
		openApiButton.setEnabled(false);
		openApiButton.addActionListener(event -> openApi());

		addContentRow(createLabeledFieldRow(new JLabel(LangResources.get("proxyURL")), proxyUrlCombo, new JLabel(" "), openApiButton), 0);

		final JPanel redirectsRow = new JPanel(new GridBagLayout());
		final GridBagConstraints redirectsConstraints = new GridBagConstraints();
		redirectsConstraints.insets = new Insets(0, 0, 0, 7);
		followRedirectsButton = new JCheckBox(LangResources.get("followRedirects"));
		followRedirectsButton.addActionListener(event -> maxRedirectHopsSpinner.setEnabled(followRedirectsButton.isSelected()));
		redirectsRow.add(followRedirectsButton, redirectsConstraints);
		redirectsRow.add(new JLabel(LangResources.get("maxRedirectHops")), redirectsConstraints);
		maxRedirectHopsSpinner = new JSpinner(new SpinnerNumberModel(HttpRequest.DEFAULT_MAX_REDIRECTS, 1, 999, 1));
		// Disabled until "Follow redirects" is checked, since the hop count is meaningless otherwise
		maxRedirectHopsSpinner.setEnabled(false);
		redirectsRow.add(maxRedirectHopsSpinner, redirectsConstraints);
		redirectsConstraints.weightx = 1;
		redirectsRow.add(Box.createGlue(), redirectsConstraints);
		addContentRow(redirectsRow, 0);

		// Fixed list of HTTP methods: no custom values, no reordering
		httpMethodCombo = new DropDown()
				.withCaseSensitive(false)
				.withMatchMode(MatchMode.STARTS_WITH)
				.withAllowCustomValues(false)
				.withItems(HTTP_METHODS)
				.withText(HTTP_METHODS.get(0));
		httpMethodCombo.addActionListener(event -> checkRequestContentStatus());
		// Invalid (red) text makes getHttpMethod() return null, so re-evaluate on validity changes too
		httpMethodCombo.addValidationListener(valid -> checkRequestContentStatus());

		serviceUrlText = new HintTextField(LangResources.get("serviceUrlHint"));
		serviceUrlText.getDocument().addDocumentListener(new SimpleDocumentListener(() -> openApiButton.setEnabled(Utilities.isNotBlank(serviceUrlText.getText()))));

		tlsCheckButton = new JButton();
		tlsCheckButton.addActionListener(event -> {
			final TlsCheckConfigurationDialog dialog = new TlsCheckConfigurationDialog(getWindow(), RestClient.APPLICATION_NAME, tlsCheckConfiguration.getType(), tlsCheckConfiguration.getTrustoreOrPemFile(), tlsCheckConfiguration.getTrustorePassword(), tlsCheckConfiguration.getCheckCn());
			final TlsCheckConfiguration result = dialog.open();
			if (result != null) {
				setTlsCheckConfiguration(result);
				checkRequestContentStatus();
			}
		});

		final JPanel methodUrlRow = new JPanel(new GridBagLayout());
		final GridBagConstraints methodUrlConstraints = new GridBagConstraints();
		methodUrlConstraints.anchor = GridBagConstraints.LINE_START;
		methodUrlConstraints.fill = GridBagConstraints.HORIZONTAL;
		methodUrlConstraints.insets = new Insets(0, 0, 2, 7);
		methodUrlConstraints.gridy = 0;
		methodUrlConstraints.gridx = 0;
		methodUrlRow.add(new JLabel(LangResources.get("httpMethod")), methodUrlConstraints);
		methodUrlConstraints.gridx = 1;
		methodUrlRow.add(new JLabel(LangResources.get("serviceURL")), methodUrlConstraints);
		methodUrlConstraints.gridx = 2;
		methodUrlConstraints.insets = new Insets(0, 0, 2, 0);
		methodUrlRow.add(new JLabel(LangResources.get("tlsCheck")), methodUrlConstraints);
		methodUrlConstraints.gridy = 1;
		methodUrlConstraints.gridx = 0;
		methodUrlConstraints.insets = new Insets(0, 0, 0, 7);
		methodUrlRow.add(httpMethodCombo, methodUrlConstraints);
		methodUrlConstraints.gridx = 1;
		methodUrlConstraints.weightx = 1;
		methodUrlRow.add(serviceUrlText, methodUrlConstraints);
		methodUrlConstraints.gridx = 2;
		methodUrlConstraints.weightx = 0;
		methodUrlConstraints.insets = new Insets(0, 0, 0, 0);
		methodUrlRow.add(tlsCheckButton, methodUrlConstraints);
		addContentRow(methodUrlRow, 0);

		alignRightColumnButtons();

		addContentRow(new JLabel(LangResources.get("serviceMethod")), 0);
		serviceMethodText = new HintTextField(LangResources.get("serviceMethodHint"));
		addContentRow(serviceMethodText, 0);

		final JButton headerAddButton = new JButton("+");
		headerSection = new KeyValueSection();
		addContentRow(createSectionHeader(LangResources.get("httpRequestHeader"), headerAddButton), 0);
		addContentRow(headerSection.getScrollPane(), 0);
		headerSection.addRow();
		headerAddButton.addActionListener(event -> {
			headerSection.addRow();
			checkRequestContentStatus();
		});

		addContentRow(createHeaderButtonRegion(), 0);

		final JButton urlParamAddButton = new JButton("+");
		urlParamSection = new KeyValueSection();
		addContentRow(createSectionHeader(LangResources.get("urlParameter"), urlParamAddButton), 0);
		addContentRow(urlParamSection.getScrollPane(), 0);
		urlParamSection.addRow();
		urlParamAddButton.addActionListener(event -> {
			urlParamSection.addRow();
			checkRequestContentStatus();
		});

		htmlFormAddButton = new JButton("+");
		htmlFormParamSection = new KeyValueSection();
		addContentRow(createSectionHeader(LangResources.get("htmlFormParameter"), htmlFormAddButton), 0);
		addContentRow(htmlFormParamSection.getScrollPane(), 0);
		htmlFormAddButton.addActionListener(event -> {
			htmlFormParamSection.addRow();
			checkRequestContentStatus();
		});

		addContentRow(new JLabel(LangResources.get("requestBody")), 0);
		requestBodyText = new JTextArea();
		final JScrollPane requestBodyScrolled = new JScrollPane(requestBodyText);
		requestBodyScrolled.setPreferredSize(new Dimension(100, LIST_HEIGHT));
		requestBodyScrolled.setMinimumSize(new Dimension(100, LIST_HEIGHT));
		addContentRow(requestBodyScrolled, 1);
	}

	/**
	 * Gives the buttons of the right column (OpenAPI and TLS check) the same
	 * fixed width, wide enough for every possible TLS check text, so both rows
	 * line up and the width does not change when the TLS check type changes.
	 */
	private void alignRightColumnButtons() {
		int width = Math.max(MIN_RIGHT_BUTTON_WIDTH, openApiButton.getPreferredSize().width);
		for (final TlsCheckConfigurationType type : TlsCheckConfigurationType.values()) {
			tlsCheckButton.setText(getTlsCheckButtonText(type));
			width = Math.max(width, tlsCheckButton.getPreferredSize().width);
		}
		updateTlsCheckButtonText();

		openApiButton.setPreferredSize(new Dimension(width, openApiButton.getPreferredSize().height));
		tlsCheckButton.setPreferredSize(new Dimension(width, tlsCheckButton.getPreferredSize().height));
	}

	private static JPanel createLabeledFieldRow(final JLabel leftLabel, final Component leftField, final JLabel rightLabel, final Component rightField) {
		final JPanel row = new JPanel(new GridBagLayout());
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.anchor = GridBagConstraints.LINE_START;
		constraints.fill = GridBagConstraints.HORIZONTAL;

		constraints.gridy = 0;
		constraints.gridx = 0;
		constraints.insets = new Insets(0, 0, 2, 7);
		row.add(leftLabel, constraints);
		constraints.gridx = 1;
		constraints.insets = new Insets(0, 0, 2, 0);
		row.add(rightLabel, constraints);

		constraints.gridy = 1;
		constraints.gridx = 0;
		constraints.weightx = 1;
		constraints.insets = new Insets(0, 0, 0, 7);
		row.add(leftField, constraints);
		constraints.gridx = 1;
		constraints.weightx = 0;
		constraints.insets = new Insets(0, 0, 0, 0);
		row.add(rightField, constraints);
		return row;
	}

	private static JPanel createSectionHeader(final String title, final JButton addButton) {
		final JPanel sectionHeader = new JPanel(new BorderLayout());
		sectionHeader.add(new JLabel(title), BorderLayout.CENTER);
		sectionHeader.add(addButton, BorderLayout.EAST);
		return sectionHeader;
	}

	private JPanel createHeaderButtonRegion() {
		final JButton addBasicAuthButton = new JButton(LangResources.get("addBasicAuth"));
		addBasicAuthButton.addActionListener(event -> {
			final CredentialsDialog credentialsDialog = new CredentialsDialog(getWindow(), RestClient.APPLICATION_NAME, LangResources.get("enterBasicAuthCredentials"), true, true);
			final Credentials credentials = credentialsDialog.open();
			if (credentials != null) {
				final Map<String, String> httpHeadersMap = getHttpHeaders();
				httpHeadersMap.put(HttpConstants.HTTPHEADERNAME_AUTHORIZATION, HttpUtilities.createBasicAuthenticationHeaderValue(credentials.getUsername(), new String(credentials.getPassword())));
				setHttpHeaders(httpHeadersMap);
			}
		});

		final JButton addTokenAuthButton = new JButton(LangResources.get("addTokenAuth"));
		addTokenAuthButton.addActionListener(event -> {
			final String token = new SimpleInputDialog(getWindow(), RestClient.APPLICATION_NAME, LangResources.get("enterAuthToken")).open();
			if (token != null) {
				final Map<String, String> httpHeadersMap = getHttpHeaders();
				httpHeadersMap.put(HttpConstants.HTTPHEADERNAME_AUTHORIZATION, HttpConstants.AUTHORIZATIONHEADER_START_BEARER + " " + token);
				setHttpHeaders(httpHeadersMap);
			}
		});

		final JButton createTokenAuthButton = new JButton(LangResources.get("fetchIdpToken"));
		createTokenAuthButton.addActionListener(event -> fetchIdpToken());

		final JButton contentTypeButton = new JButton(LangResources.get("addContentType"));
		contentTypeButton.addActionListener(event -> {
			final List<String> contentTypes = new ArrayList<>();
			for (final HttpContentType contentTypeItem : HttpContentType.values()) {
				contentTypes.add(contentTypeItem.getStringRepresentation());
			}
			final String contentType = new ComboSelectionDialog(getWindow(), RestClient.APPLICATION_NAME, LangResources.get("addContentType"), contentTypes).open();
			if (contentType != null) {
				final Map<String, String> httpHeadersMap = getHttpHeaders();
				httpHeadersMap.put(HttpConstants.HTTPHEADERNAME_CONTENTTYPE, contentType);
				setHttpHeaders(httpHeadersMap);
			}
		});

		final JButton standardHeaderButton = new JButton(LangResources.get("addStandardHeader"));
		standardHeaderButton.addActionListener(event -> {
			final List<String> standardHeaders = List.of(
					"Accept",
					"Authorization",
					"Cache-Control",
					"Content-Encoding",
					"Content-Length",
					"Content-Type",
					"Cookie",
					"Date",
					"Pragma",
					"Proxy-Authorization",
					"Referer",
					"User-Agent",
					"Proxy-Connection");
			final String standardHeader = new ComboSelectionDialog(getWindow(), RestClient.APPLICATION_NAME, LangResources.get("addStandardHeader"), standardHeaders).open();
			if (standardHeader != null) {
				final Map<String, String> httpHeadersMap = getHttpHeaders();
				httpHeadersMap.put(standardHeader, "");
				setHttpHeaders(httpHeadersMap);
			}
		});

		// Three equally weighted columns: three buttons in the first row, two (the second spanning two columns) in the second
		final JPanel headerButtonRegion = new JPanel(new GridBagLayout());
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.insets = new Insets(2, 2, 2, 2);
		constraints.gridy = 0;
		constraints.gridx = 0;
		headerButtonRegion.add(addBasicAuthButton, constraints);
		constraints.gridx = 1;
		headerButtonRegion.add(addTokenAuthButton, constraints);
		constraints.gridx = 2;
		headerButtonRegion.add(createTokenAuthButton, constraints);
		constraints.gridy = 1;
		constraints.gridx = 0;
		headerButtonRegion.add(contentTypeButton, constraints);
		constraints.gridx = 1;
		constraints.gridwidth = 2;
		headerButtonRegion.add(standardHeaderButton, constraints);
		return headerButtonRegion;
	}

	private void fetchIdpToken() {
		try {
			final IdpCredentialsDialog inputDialog = new IdpCredentialsDialog(getWindow(), RestClient.APPLICATION_NAME, LangResources.get("enterIdpCredentials"), idpUrl, idpRealm, idpUsername, idpPassword);
			inputDialog.setRememberCredentials(storeIdpCredentials);
			final Credentials credentials = inputDialog.open();
			if (credentials != null) {
				final String tempIdpUrl = inputDialog.getIdpUrl();
				final String tempIdpRealm = inputDialog.getIdpRealm();
				final String tempIdpUsername = credentials.getUsername();
				final char[] tempIdpPasswordChars = credentials.getPassword();
				final String tempIdpPassword = new String(tempIdpPasswordChars);

				storeIdpCredentials = inputDialog.isRememberCredentials();

				ProxyConfiguration idpProxyConfiguration = null;
				if (Utilities.isNotBlank(getProxyUrl())) {
					if ("DIRECT".equalsIgnoreCase(getProxyUrl())) {
						idpProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.None);
					} else if ("WPAD".equalsIgnoreCase(getProxyUrl())) {
						idpProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.WPAD);
					} else {
						idpProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.ProxyURL, getProxyUrl());
					}
				}

				final String idpToken;
				if (tempIdpUrl.endsWith("/token")) {
					idpToken = IdpHelper.aquireAccessToken(tempIdpUrl, tempIdpUsername, tempIdpPassword, null, idpProxyConfiguration);
				} else {
					final String idpTokenEndpointURL = IdpHelper.getIdpTokenEdpointUrl(tempIdpUrl, tempIdpRealm, idpProxyConfiguration);
					idpToken = IdpHelper.aquireAccessToken(idpTokenEndpointURL, tempIdpUsername, tempIdpPassword, null, idpProxyConfiguration);
				}

				if (idpToken != null) {
					final Map<String, String> httpHeadersMap = getHttpHeaders();
					httpHeadersMap.put(HttpConstants.HTTPHEADERNAME_AUTHORIZATION, HttpConstants.AUTHORIZATIONHEADER_START_BEARER + " " + idpToken);
					setHttpHeaders(httpHeadersMap);
				}

				idpUrl = tempIdpUrl;
				idpRealm = tempIdpRealm;
				idpUsername = tempIdpUsername;
				idpPassword = tempIdpPasswordChars;
			}
		} catch (final Exception e) {
			showError(LangResources.get("fetchIdpToken"), e.getMessage());
		}
	}

	private void openApi() {
		final SimpleInputDialog dialog = new SimpleInputDialog(getWindow(), RestClient.APPLICATION_NAME, "OpenAPI URL " + LangResources.get("orLocalFilePath"));
		if (getServiceUrl() != null) {
			if (getServiceUrl().endsWith("/")) {
				dialog.setDefaultText(getServiceUrl() + "openapi");
			} else {
				dialog.setDefaultText(getServiceUrl() + "/openapi");
			}
		}
		final String result = dialog.open();
		if (result != null) {
			try {
				final File localFile = new File(result);
				final byte[] openApiContent;
				if (localFile.isFile()) {
					openApiContent = Files.readAllBytes(localFile.toPath());
				} else {
					openApiContent = fetchOpenApiContentFromUrl(result);
				}
				processOpenApiDocument(openApiContent);
			} catch (final Exception e) {
				showError("OpenAPI", e.getMessage());
			}
		}
	}

	private void showError(final String title, final String message) {
		new QuestionDialog(getWindow(), title, message, LangResources.get("ok")).setBackgroundColor(SwingColor.LightRed).open();
	}

	private void createPresetNameSection() {
		addContentRow(new JLabel(LangResources.get("preset")), 0);

		presetCombo = new DropDown()
				.withCaseSensitive(false)
				.withMatchMode(MatchMode.CONTAINS)
				.withAllowCustomValues(true)
				.withReorderable(true);
		presetCombo.addItemsReorderedListener(newOrder -> {
			presetNames.clear();
			presetNames.addAll(newOrder);
			if (presetsReorderedListener != null) {
				presetsReorderedListener.accept(getPresetNames());
			}
		});
		presetCombo.addActionListener(event -> {
			if (presetSelectionListener != null) {
				presetSelectionListener.run();
			}
		});

		saveButton = new JButton(LangResources.get("save"));
		deleteButton = new JButton(LangResources.get("delete"));

		final JPanel comboButtonRow = new JPanel(new GridBagLayout());
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 0, 5);
		constraints.weightx = 1;
		comboButtonRow.add(presetCombo, constraints);
		constraints.weightx = 0;
		comboButtonRow.add(saveButton, constraints);
		constraints.insets = new Insets(0, 0, 0, 0);
		comboButtonRow.add(deleteButton, constraints);
		addContentRow(comboButtonRow, 0);
	}

	private byte[] fetchOpenApiContentFromUrl(final String url) throws Exception {
		final HttpRequest openApiRequest = new HttpRequest(HttpMethod.GET, url);

		Proxy proxy = null;
		if (Utilities.isNotBlank(getProxyUrl())) {
			if ("DIRECT".equalsIgnoreCase(getProxyUrl())) {
				proxy = Proxy.NO_PROXY;
			} else if ("WPAD".equalsIgnoreCase(getProxyUrl())) {
				final ProxyConfiguration requestProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.WPAD);
				proxy = requestProxyConfiguration.getProxy(openApiRequest.getUrl());
			} else {
				proxy = HttpUtilities.getProxyFromString(getProxyUrl());
			}
		}

		final WorkerSimple<HttpResponse> worker = new ExecuteHttpRequestWorker(null, openApiRequest, proxy, getTlsCheckConfiguration().getTrustManager(), !getTlsCheckConfiguration().getCheckCn());
		final ProgressDialog<WorkerSimple<HttpResponse>> progressDialog = new ProgressDialog<>(getWindow(), RestClient.APPLICATION_NAME, LangResources.get("sendRequest"), worker);
		final Result dialogResult = progressDialog.open();
		if (dialogResult == Result.CANCELED) {
			return null;
		}
		final HttpResponse httpResponse = worker.get();

		if (httpResponse != null && httpResponse.getHttpCode() == 200) {
			return httpResponse.getContent().trim().getBytes(StandardCharsets.UTF_8);
		} else {
			throw new Exception("Cannot read OpenAPI data. HTTP code: " + (httpResponse == null ? "None" : httpResponse.getHttpCode()));
		}
	}

	private void processOpenApiDocument(final byte[] openApiContent) throws Exception {
		if (openApiContent == null) {
			// e.g. HTTP request was canceled by the user in the progress dialog
			return;
		}

		try (YamlReader reader = new YamlReader(new ByteArrayInputStream(openApiContent))) {
			final JsonNode rootJsonNode = YamlToJsonConverter.convert(reader.readDocument());
			if (!(rootJsonNode instanceof JsonObject)) {
				throw new Exception("OpenAPI document does not contain an object at its root");
			}
			final JsonObject rootJsonObject = (JsonObject) rootJsonNode;

			final JsonNode pathsNode = rootJsonObject.get("paths");
			if (!(pathsNode instanceof JsonObject)) {
				throw new Exception("OpenAPI document does not contain a 'paths' object");
			}
			final JsonObject pathsObject = (JsonObject) pathsNode;

			final List<String> httpMethodNames = List.of("get", "post", "put", "delete", "patch", "head", "options");
			final List<String> paths = new ArrayList<>(pathsObject.keySet());

			// Stage 1: select a path (no HTTP method involved yet)
			final String selectedPathRaw = new ComboSelectionDialog(getWindow(), "OpenAPI paths", LangResources.get("selectServiceMethod"), paths).open();
			if (selectedPathRaw != null) {
				String selectedPath = selectedPathRaw;
				while (selectedPath.startsWith("/")) {
					selectedPath = selectedPath.substring(1);
				}
				setServiceMethod(selectedPath);

				// Stage 2: select one of the HTTP methods available for that path, if any were recognized
				final List<String> availableMethods = new ArrayList<>();
				final Map<String, JsonObject> operationsByMethod = new LinkedHashMap<>();
				if (pathsObject.get(selectedPathRaw) instanceof JsonObject) {
					for (final Entry<String, JsonNode> methodEntry : ((JsonObject) pathsObject.get(selectedPathRaw)).entrySet()) {
						final String methodName = methodEntry.getKey().toLowerCase(Locale.ROOT);
						if (httpMethodNames.contains(methodName) && methodEntry.getValue() instanceof JsonObject) {
							final String methodLabel = methodName.toUpperCase(Locale.ROOT);
							availableMethods.add(methodLabel);
							operationsByMethod.put(methodLabel, (JsonObject) methodEntry.getValue());
						}
					}
				}

				if (availableMethods.size() == 1) {
					applyOpenApiMethodSelection(availableMethods.get(0), operationsByMethod, rootJsonObject);
				} else if (availableMethods.size() > 1) {
					final String selectedHttpMethod = new ComboSelectionDialog(getWindow(), "OpenAPI methods", LangResources.get("selectHttpMethod"), availableMethods).open();
					if (selectedHttpMethod != null) {
						applyOpenApiMethodSelection(selectedHttpMethod, operationsByMethod, rootJsonObject);
					}
				}
			}
		}

		checkRequestContentStatus();
	}

	private void applyOpenApiMethodSelection(final String selectedHttpMethod, final Map<String, JsonObject> operationsByMethod, final JsonObject rootJsonObject) throws Exception {
		setHttpMethod(selectedHttpMethod);

		final String exampleJson = extractJsonExampleFromOperation(operationsByMethod.get(selectedHttpMethod), rootJsonObject);
		if (exampleJson != null) {
			setRequestBody(exampleJson);
		}
	}

	private static String extractJsonExampleFromOperation(final JsonObject operation, final JsonObject rootDocument) throws Exception {
		if (operation == null) {
			return null;
		}

		final JsonNode requestBody = operation.get("requestBody");
		if (!(requestBody instanceof JsonObject)) {
			return null;
		}

		final JsonNode requestContent = ((JsonObject) requestBody).get("content");
		if (!(requestContent instanceof JsonObject)) {
			return null;
		}

		final JsonNode jsonContentNode = ((JsonObject) requestContent).get("application/json");
		if (!(jsonContentNode instanceof JsonObject)) {
			return null;
		}
		final JsonObject jsonContent = (JsonObject) jsonContentNode;

		// 1. Example directly on the media type object (content.application/json.example)
		JsonNode example = jsonContent.get("example");

		// 2. Named examples map (content.application/json.examples.<name>.value)
		if (example == null) {
			final JsonNode examples = jsonContent.get("examples");
			if (examples instanceof JsonObject) {
				for (final JsonNode namedExample : ((JsonObject) examples).values()) {
					if (namedExample instanceof JsonObject) {
						final JsonNode value = ((JsonObject) namedExample).get("value");
						if (value != null) {
							example = value;
							break;
						}
					}
				}
			}
		}

		final JsonNode schemaNode = jsonContent.get("schema");
		if (schemaNode instanceof JsonObject) {
			// 3. Example nested inside the schema object (content.application/json.schema.example)
			//    This is the convention actually used by e.g. simple string request bodies (schema: {type: string, example: "..."})
			if (example == null) {
				example = ((JsonObject) schemaNode).get("example");
			}

			// 4. No explicit example anywhere: generate a placeholder example from the schema itself
			//    (resolves "$ref" against the whole OpenAPI document, e.g. "#/components/schemas/User")
			if (example == null) {
				final JsonSchemaDependencyResolver dependencyResolver = new JsonSchemaDependencyResolver(rootDocument);
				final JsonSchemaExampleGenerator exampleGenerator = new JsonSchemaExampleGenerator(dependencyResolver);
				example = exampleGenerator.generateExample((JsonObject) schemaNode);
			}
		}

		return example == null ? null : toJsonText(example);
	}

	private static String toJsonText(final JsonNode value) throws Exception {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream();
				JsonWriter writer = new JsonWriter(output, StandardCharsets.UTF_8)) {
			writer.add(value);
			writer.flush();
			return new String(output.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private void checkRequestContentStatus() {
		if (requestBodyText == null || htmlFormParamSection == null || checkingRequestContentStatus) {
			return;
		}

		checkingRequestContentStatus = true;
		try {
			final boolean isGet = "GET".equalsIgnoreCase(getHttpMethod());

			htmlFormParamSection.setEnabled(!isGet);
			htmlFormAddButton.setEnabled(!isGet);
			final String htmlFormToolTip = isGet ? LangResources.get("deactivationHtmlFormParams") : null;
			htmlFormParamSection.getScrollPane().setToolTipText(htmlFormToolTip);
			htmlFormAddButton.setToolTipText(htmlFormToolTip);

			if (isGet) {
				requestBodyText.setEnabled(false);
			} else if (htmlFormParamSection.getRowCount() > 0) {
				requestBodyText.setEnabled(false);

				boolean contentTypeHeaderFound = false;
				final Map<String, String> httpHeaders = getHttpHeaders();
				for (final String headerName : httpHeaders.keySet()) {
					if (HttpConstants.HTTPHEADERNAME_CONTENTTYPE.equalsIgnoreCase(headerName)) {
						contentTypeHeaderFound = true;
						break;
					}
				}

				if (!contentTypeHeaderFound) {
					httpHeaders.put(HttpConstants.HTTPHEADERNAME_CONTENTTYPE, HttpContentType.HtmlForm.getStringRepresentation());
					headerSection.setEntries(httpHeaders);
				}
			} else {
				requestBodyText.setEnabled(true);
			}

			updateTlsCheckButtonText();
		} finally {
			checkingRequestContentStatus = false;
		}
	}

	private void updateTlsCheckButtonText() {
		if (tlsCheckButton != null) {
			tlsCheckButton.setText(getTlsCheckButtonText(tlsCheckConfiguration.getType()));
		}
	}

	private static String getTlsCheckButtonText(final TlsCheckConfigurationType type) {
		switch (type) {
			case AdditionalTrustStoreFile:
				return LangResources.get("AdditionalTrustStoreFile");
			case NoCheck:
				return LangResources.get("NoCheck");
			case RecordingSingleCertificate:
				return LangResources.get("RecordingSingleCertificate");
			case RecordingToTrustStoreFile:
				return LangResources.get("RecordingToTrustStoreFile");
			case SingleCertificate:
				return LangResources.get("SingleCertificate");
			case TrustStoreFile:
				return LangResources.get("TrustStoreFile");
			case SystemTrustStore:
			default:
				return LangResources.get("SystemTrustStore");
		}
	}

	/**
	 * Editable list of "name | value | -" rows in a scroll pane of fixed height,
	 * used for HTTP headers, URL parameters and HTML form parameters.
	 */
	private final class KeyValueSection {
		private final ViewportWidthPanel container = new ViewportWidthPanel(new GridBagLayout());
		private final JScrollPane scrollPane;
		private final List<KeyValueRow> rows = new ArrayList<>();
		private boolean enabled = true;

		private KeyValueSection() {
			scrollPane = new JScrollPane(container, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			scrollPane.setPreferredSize(new Dimension(100, LIST_HEIGHT));
			scrollPane.setMinimumSize(new Dimension(100, LIST_HEIGHT));
			scrollPane.getVerticalScrollBar().setUnitIncrement(16);
			relayout();
		}

		private JScrollPane getScrollPane() {
			return scrollPane;
		}

		private int getRowCount() {
			return rows.size();
		}

		private KeyValueRow addRow() {
			final KeyValueRow row = new KeyValueRow();
			row.setEnabled(enabled);
			rows.add(row);
			relayout();
			return row;
		}

		private void removeRow(final KeyValueRow row) {
			rows.remove(row);
			relayout();
			checkRequestContentStatus();
		}

		private void setEntries(final Map<String, String> entries) {
			rows.clear();
			if (entries != null) {
				for (final Map.Entry<String, String> entry : entries.entrySet()) {
					final KeyValueRow row = new KeyValueRow();
					row.nameText.setText(entry.getKey());
					row.valueText.setText(entry.getValue());
					row.setEnabled(enabled);
					rows.add(row);
				}
			}
			relayout();
		}

		/**
		 * Rows with a non-blank name, in display order
		 */
		private Map<String, String> getEntries() {
			final Map<String, String> map = new LinkedHashMap<>();
			for (final KeyValueRow row : rows) {
				if (!row.nameText.getText().isBlank()) {
					map.put(row.nameText.getText(), row.valueText.getText());
				}
			}
			return map;
		}

		private void setEnabled(final boolean enabled) {
			this.enabled = enabled;
			for (final KeyValueRow row : rows) {
				row.setEnabled(enabled);
			}
		}

		private void relayout() {
			container.removeAll();

			int rowIndex = 0;
			for (final KeyValueRow row : rows) {
				final GridBagConstraints constraints = new GridBagConstraints();
				constraints.gridy = rowIndex++;
				constraints.insets = new Insets(1, 2, 1, 2);

				constraints.gridx = 0;
				container.add(row.nameText, constraints);

				constraints.gridx = 1;
				constraints.weightx = 1;
				constraints.fill = GridBagConstraints.HORIZONTAL;
				container.add(row.valueText, constraints);

				constraints.gridx = 2;
				constraints.weightx = 0;
				constraints.fill = GridBagConstraints.NONE;
				container.add(row.removeButton, constraints);
			}

			// Keeps the rows at the top
			final GridBagConstraints fillerConstraints = new GridBagConstraints();
			fillerConstraints.gridy = rowIndex;
			fillerConstraints.weighty = 1;
			container.add(Box.createGlue(), fillerConstraints);

			container.revalidate();
			container.repaint();
		}

		private final class KeyValueRow {
			private final HintTextField nameText = new HintTextField(LangResources.get("nameHint"));
			private final HintTextField valueText = new HintTextField(LangResources.get("valueHint"));
			private final JButton removeButton = new JButton("-");

			private KeyValueRow() {
				nameText.setPreferredSize(new Dimension(KEY_FIELD_WIDTH, nameText.getPreferredSize().height));
				removeButton.addActionListener(event -> removeRow(this));
			}

			private void setEnabled(final boolean rowEnabled) {
				nameText.setEnabled(rowEnabled);
				valueText.setEnabled(rowEnabled);
				removeButton.setEnabled(rowEnabled);
			}
		}
	}

	/**
	 * DocumentListener that runs the same action for every text change.
	 */
	private static final class SimpleDocumentListener implements DocumentListener {
		private final Runnable action;

		private SimpleDocumentListener(final Runnable action) {
			this.action = action;
		}

		@Override
		public void insertUpdate(final DocumentEvent event) {
			action.run();
		}

		@Override
		public void removeUpdate(final DocumentEvent event) {
			action.run();
		}

		@Override
		public void changedUpdate(final DocumentEvent event) {
			// Attribute changes only
		}
	}
}
