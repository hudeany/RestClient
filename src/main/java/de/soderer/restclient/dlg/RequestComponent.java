package de.soderer.restclient.dlg;

import java.io.ByteArrayInputStream;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

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
import de.soderer.utilities.swt.CredentialsDialog;
import de.soderer.utilities.swt.ProgressDialog;
import de.soderer.utilities.swt.QuestionDialog;
import de.soderer.utilities.swt.SelectionDialog;
import de.soderer.utilities.swt.SimpleInputDialog;
import de.soderer.utilities.swt.SwtColor;
import de.soderer.utilities.worker.WorkerSimple;
import de.soderer.yaml.YamlReader;
import de.soderer.yaml.data.YamlDocument;
import de.soderer.yaml.data.YamlMapping;
import de.soderer.yaml.data.YamlNode;

public class RequestComponent extends Composite {
	private Text presetNameText;
	private Button presetDropDownButton;
	private Composite presetFieldComposite;
	private Button saveButton;
	private Button deleteButton;

	private final List<String> presetNames = new ArrayList<>();
	private Runnable presetSelectionListener;
	private java.util.function.Consumer<List<String>> presetsReorderedListener;

	private Combo httpMethodCombo;
	private Text serviceUrlText;
	private Button tlsCheckButton;
	private Text serviceMethodText;
	private Text proxyUrlText;
	private Button followRedirectsButton;
	private Spinner maxRedirectHopsSpinner;
	private Text requestBodyText;

	private String idpUrl = null;
	private String idpRealm = null;
	private String idpUsername = null;
	private char[] idpPassword = null;
	private boolean storeIdpCredentials = false;

	private Composite headerContainer;
	private Composite urlParamContainer;
	private Composite htmlFormParamContainer;
	private Button htmlFormAddButton;

	private ScrolledComposite headerScrolled;
	private ScrolledComposite urlParamScrolled;
	private ScrolledComposite htmlFormParamScrolled;

	private Composite content;
	private ScrolledComposite outerScrolled;

	private TlsCheckConfiguration tlsCheckConfiguration;

	public RequestComponent(final Composite parent, final int style) throws Exception {
		super(parent, style);

		tlsCheckConfiguration = new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true);

		createOuterScrollArea();

		checkRequestContentStatus();
	}

	public String getHttpMethod() {
		return httpMethodCombo.getText();
	}

	public void setHttpMethod(final String method) {
		if (method != null) {
			final int index = httpMethodCombo.indexOf(method);
			if (index >= 0) httpMethodCombo.select(index);
		}

		checkRequestContentStatus();
	}

	public void setTlsCheckConfiguration(final TlsCheckConfiguration tlsCheckConfiguration) {
		this.tlsCheckConfiguration = tlsCheckConfiguration;
	}

	public TlsCheckConfiguration getTlsCheckConfiguration() {
		return tlsCheckConfiguration;
	}

	public String getPresetName() { return presetNameText.getText(); }
	public String getServiceUrl() { return serviceUrlText.getText(); }
	public String getServiceMethod() {
		while (serviceMethodText.getText().startsWith("/")) {
			serviceMethodText.setText(serviceMethodText.getText().substring(1));
		}
		return serviceMethodText.getText();
	}
	public String getProxyUrl() { return proxyUrlText.getText(); }
	public boolean isFollowRedirects() { return followRedirectsButton.getSelection(); }
	public int getMaxRedirectHops() { return maxRedirectHopsSpinner.getSelection(); }
	/** Combines {@link #isFollowRedirects()} and {@link #getMaxRedirectHops()} into the single int value expected by {@link HttpRequest#setMaxRedirects(int)} (0 = do not follow, positive = hop limit) */
	public int getMaxRedirects() { return isFollowRedirects() ? getMaxRedirectHops() : 0; }
	public String getRequestBody() { return requestBodyText.getText(); }

	public String getIdpUrl() { return idpUrl; }
	public String getIdpRealm() { return idpRealm; }
	public String getIdpUsername() { return idpUsername; }
	public char[] getIdpPassword() { return idpPassword; }
	public boolean isStoreIdpCredentials() { return storeIdpCredentials; }

	public Map<String, String> getHttpHeaders() { return extractKeyValuePairs(headerContainer); }
	public Map<String, String> getUrlParameters() { return extractKeyValuePairs(urlParamContainer); }
	public Map<String, String> getHtmlFormParameters() { return extractKeyValuePairs(htmlFormParamContainer); }

	public void setPresetNames(final List<String> presets) {
		presetNames.clear();
		if (presets != null) {
			presetNames.addAll(presets);
		}
	}

	/**
	 * Current preset order, e.g. to persist it after the user reordered
	 * entries via drag&amp;drop in the selection popup.
	 */
	public List<String> getPresetNames() {
		return new ArrayList<>(presetNames);
	}

	public void addPresetSelectionListener(final Runnable listener) {
		presetSelectionListener = listener;
	}

	/**
	 * Notified with the new preset order whenever the user reorders the
	 * entries via drag&amp;drop in the selection popup.
	 */
	public void addPresetsReorderedListener(final java.util.function.Consumer<List<String>> listener) {
		presetsReorderedListener = listener;
	}

	public void addSaveButtonListener(final Runnable listener) {
		if (listener == null) return;
		saveButton.addListener(SWT.Selection, e -> {
			listener.run();
		});
	}

	public void addDeleteButtonListener(final Runnable listener) {
		if (listener == null) return;
		deleteButton.addListener(SWT.Selection, e -> {
			listener.run();
		});
	}

	public void setPresetName(final String value) { if (value != null) presetNameText.setText(value); }
	public void setServiceUrl(final String value) { serviceUrlText.setText(value != null ? value : ""); }
	public void setServiceMethod(final String value) { serviceMethodText.setText(value != null ? value : ""); }
	public void setProxyUrl(final String value) { proxyUrlText.setText(value != null ? value : ""); }
	public void setFollowRedirects(final boolean followRedirects) {
		followRedirectsButton.setSelection(followRedirects);
		maxRedirectHopsSpinner.setEnabled(followRedirects);
	}
	public void setMaxRedirectHops(final int maxRedirectHops) { maxRedirectHopsSpinner.setSelection(maxRedirectHops); }
	/** Counterpart to {@link #getMaxRedirects()}: 0 disables following, any other value enables it and sets that hop count (negative values are treated as {@link HttpRequest#DEFAULT_MAX_REDIRECTS} since this UI does not offer an "unlimited" option) */
	public void setMaxRedirects(final int maxRedirects) {
		setFollowRedirects(maxRedirects != 0);
		setMaxRedirectHops(maxRedirects > 0 ? maxRedirects : HttpRequest.DEFAULT_MAX_REDIRECTS);
	}
	public void setRequestBody(final String value) { requestBodyText.setText(value != null ? value : ""); }

	public void setIdpUrl(final String idpUrl) { this.idpUrl = idpUrl; }
	public void setIdpRealm(final String idpRealm) { this.idpRealm = idpRealm; }
	public void setIdpUsername(final String idpUsername) { this.idpUsername = idpUsername; }
	public void setIdpPassword(final char[] idpPassword) { this.idpPassword = idpPassword; }
	public void setStoreIdpCredentials(final boolean storeIdpCredentials) { this.storeIdpCredentials = storeIdpCredentials; }

	public void setHttpHeaders(final Map<String, String> headers) {
		for (final Control c : headerContainer.getChildren()) {
			c.dispose();
		}

		if (headers != null) {
			for (final Map.Entry<String, String> entry : headers.entrySet()) {
				final Composite row = addKeyValueRow(headerContainer, headerScrolled);
				final Control[] children = row.getChildren();
				if (children.length >= 2 && children[0] instanceof final Text name && children[1] instanceof final Text value) {
					name.setText(entry.getKey());
					value.setText(entry.getValue());
				}
			}
		}
		checkRequestContentStatus();
		refreshScrolledArea(headerContainer, headerScrolled);
	}

	public void setUrlParameters(final Map<String, String> urlParams) {
		for (final Control c : urlParamContainer.getChildren()) {
			c.dispose();
		}

		if (urlParams != null) {
			for (final Map.Entry<String, String> entry : urlParams.entrySet()) {
				final Composite row = addKeyValueRow(urlParamContainer, urlParamScrolled);
				final Control[] children = row.getChildren();
				if (children.length >= 2 && children[0] instanceof final Text name && children[1] instanceof final Text value) {
					name.setText(entry.getKey());
					value.setText(entry.getValue());
				}
			}
		}
		checkRequestContentStatus();
		refreshScrolledArea(urlParamContainer, urlParamScrolled);
	}

	public void setHtmlFormParameters(final Map<String, String> htmlFormParams) {
		for (final Control c : htmlFormParamContainer.getChildren()) {
			c.dispose();
		}

		if (htmlFormParams != null) {
			for (final Map.Entry<String, String> entry : htmlFormParams.entrySet()) {
				final Composite row = addKeyValueRow(htmlFormParamContainer, htmlFormParamScrolled);
				final Control[] children = row.getChildren();
				if (children.length >= 2 && children[0] instanceof final Text name && children[1] instanceof final Text value) {
					name.setText(entry.getKey());
					value.setText(entry.getValue());
				}
			}
		}
		checkRequestContentStatus();
		refreshScrolledArea(htmlFormParamContainer, htmlFormParamScrolled);
	}

	private void createOuterScrollArea() {
		setLayout(new FillLayout());

		outerScrolled = new ScrolledComposite(this, SWT.V_SCROLL | SWT.H_SCROLL);
		outerScrolled.setExpandHorizontal(true);
		outerScrolled.setExpandVertical(true);

		content = new Composite(outerScrolled, SWT.NONE);
		content.setLayout(new GridLayout(1, false));

		outerScrolled.setContent(content);

		createUI();
		updateOuterMinSize();
	}

	private void updateOuterMinSize() {
		content.layout(true, true);
		final Point pref = content.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		final int width = outerScrolled.getClientArea().width;
		final int height = pref.y;
		outerScrolled.setMinSize(width, height);
	}

	private void createUI() {
		createPresetNameSection();

		final Composite proxyRow = new Composite(content, SWT.NONE);
		proxyRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final GridLayout proxyRowLayout = new GridLayout(2, false);
		proxyRowLayout.marginWidth = 0;
		proxyRowLayout.marginHeight = 0;
		proxyRowLayout.horizontalSpacing = 7;
		proxyRow.setLayout(proxyRowLayout);

		final Composite proxyUrlCol = new Composite(proxyRow, SWT.NONE);
		proxyUrlCol.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		final GridLayout proxyUrlLayout = new GridLayout(1, false);
		proxyUrlLayout.marginWidth = 0;
		proxyUrlLayout.marginHeight = 0;
		proxyUrlCol.setLayout(proxyUrlLayout);

		final Label proxyUrlLabel = new Label(proxyUrlCol, SWT.NONE);
		proxyUrlLabel.setText(LangResources.get("proxyURL"));

		proxyUrlText = new Text(proxyUrlCol, SWT.BORDER);
		proxyUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		proxyUrlText.setMessage(LangResources.get("proxyUrlHint"));

		final Composite openApiCol = new Composite(proxyRow, SWT.NONE);
		final GridData openApiColData = new GridData(SWT.RIGHT, SWT.FILL, false, false);
		openApiColData.minimumWidth = 300;
		openApiCol.setLayoutData(openApiColData);
		openApiCol.setLayoutData(openApiColData);
		final GridLayout openApiLayout = new GridLayout(1, false);
		openApiLayout.marginWidth = 0;
		openApiLayout.marginHeight = 0;
		openApiCol.setLayout(openApiLayout);

		final Label openApiLabel = new Label(openApiCol, SWT.NONE);
		openApiLabel.setText("");

		final Button openApiButton = new Button(openApiCol, SWT.NONE);
		final GridData openApiGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		openApiGridData.widthHint = 100;
		openApiButton.setLayoutData(openApiGridData);
		openApiButton.setText("OpenAPI");
		openApiButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				final SimpleInputDialog dialog = new SimpleInputDialog(getShell(), RestClient.APPLICATION_NAME, "OpenAPI URL");
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
						final HttpRequest openApiRequest = new HttpRequest(HttpMethod.GET, result);

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
						HttpResponse httpResponse;
						final ProgressDialog<WorkerSimple<HttpResponse>> progressDialog = new ProgressDialog<>(getShell(), RestClient.APPLICATION_NAME, LangResources.get("sendRequest"), worker);
						final Result dialogResult = progressDialog.open();
						if (dialogResult == Result.CANCELED) {
							return;
						} else {
							httpResponse = worker.get();
						}

						if (httpResponse != null && httpResponse.getHttpCode() == 200) {
							try (YamlReader reader = new YamlReader(new ByteArrayInputStream(httpResponse.getContent().trim().getBytes(StandardCharsets.UTF_8)))) {
								final YamlDocument document = reader.readDocument();
								final YamlNode rootNode = document.getRoot();
								final YamlMapping pathsYamlMapping = (YamlMapping) ((YamlMapping) rootNode).get("paths");
								final List<String> paths = new ArrayList<>();
								for (final Entry<String, Object> pathEntry : pathsYamlMapping.simpleEntrySet()) {
									paths.add(pathEntry.getKey());
								}
								String selectedMethod = new SelectionDialog(getShell(), "OpenAPI paths", LangResources.get("selectServiceMethod"), paths).open();
								if (selectedMethod != null) {
									while (selectedMethod.startsWith("/")) {
										selectedMethod = selectedMethod.substring(1);
									}
									setServiceMethod(selectedMethod);
								}
							}
						} else {
							throw new Exception("Cannot read OpenAPI data. HTTP code: " + (httpResponse == null ? "None" : httpResponse.getHttpCode()));
						}

						checkRequestContentStatus();
					} catch (final Exception e) {
						new QuestionDialog(getShell(), LangResources.get("fetchIdpToken"), e.getMessage(), LangResources.get("ok")).setBackgroundColor(SwtColor.LightRed).open();
					}
				}
			}
		});

		final Composite redirectsRow = new Composite(content, SWT.NONE);
		redirectsRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		final GridLayout redirectsRowLayout = new GridLayout(3, false);
		redirectsRowLayout.marginWidth = 0;
		redirectsRowLayout.marginHeight = 0;
		redirectsRowLayout.horizontalSpacing = 7;
		redirectsRow.setLayout(redirectsRowLayout);

		followRedirectsButton = new Button(redirectsRow, SWT.CHECK);
		followRedirectsButton.setText(LangResources.get("followRedirects"));
		followRedirectsButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		followRedirectsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				maxRedirectHopsSpinner.setEnabled(followRedirectsButton.getSelection());
			}
		});

		final Label maxRedirectHopsLabel = new Label(redirectsRow, SWT.NONE);
		maxRedirectHopsLabel.setText(LangResources.get("maxRedirectHops"));
		maxRedirectHopsLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		maxRedirectHopsSpinner = new Spinner(redirectsRow, SWT.BORDER);
		maxRedirectHopsSpinner.setMinimum(1);
		maxRedirectHopsSpinner.setMaximum(999);
		maxRedirectHopsSpinner.setSelection(HttpRequest.DEFAULT_MAX_REDIRECTS);
		final GridData maxRedirectHopsGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		maxRedirectHopsGridData.widthHint = 50;
		maxRedirectHopsSpinner.setLayoutData(maxRedirectHopsGridData);
		// Disabled until "Follow redirects" is checked, since the hop count is meaningless otherwise
		maxRedirectHopsSpinner.setEnabled(false);

		final Composite methodUrlRow = new Composite(content, SWT.NONE);
		methodUrlRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final GridLayout rowLayout = new GridLayout(3, false);
		rowLayout.marginWidth = 0;
		rowLayout.marginHeight = 0;
		rowLayout.horizontalSpacing = 7;
		methodUrlRow.setLayout(rowLayout);

		final Composite methodCol = new Composite(methodUrlRow, SWT.NONE);
		methodCol.setLayoutData(new GridData(SWT.DEFAULT, SWT.FILL, false, false));
		final GridLayout methodLayout = new GridLayout(1, false);
		methodLayout.marginWidth = 0;
		methodLayout.marginHeight = 0;
		methodCol.setLayout(methodLayout);

		final Label methodLabel = new Label(methodCol, SWT.NONE);
		methodLabel.setText(LangResources.get("httpMethod"));

		httpMethodCombo = new Combo(methodCol, SWT.DROP_DOWN | SWT.READ_ONLY);
		httpMethodCombo.setItems(new String[] {"GET", "POST", "PUT", "DELETE", "HEAD"});
		httpMethodCombo.select(0);
		httpMethodCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		httpMethodCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				checkRequestContentStatus();
			}
		});

		final Composite urlCol = new Composite(methodUrlRow, SWT.NONE);
		urlCol.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		final GridLayout urlLayout = new GridLayout(1, false);
		urlLayout.marginWidth = 0;
		urlLayout.marginHeight = 0;
		urlCol.setLayout(urlLayout);

		final Label urlLabel = new Label(urlCol, SWT.NONE);
		urlLabel.setText(LangResources.get("serviceURL"));

		serviceUrlText = new Text(urlCol, SWT.BORDER);
		serviceUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		serviceUrlText.setMessage(LangResources.get("serviceUrlHint"));

		final Composite tlsCheckCol = new Composite(methodUrlRow, SWT.NONE);
		final GridData tlsColData = new GridData(SWT.RIGHT, SWT.FILL, false, false);
		tlsColData.minimumWidth = 300;
		tlsCheckCol.setLayoutData(tlsColData);
		tlsCheckCol.setLayoutData(tlsColData);
		final GridLayout tlsCheckLayout = new GridLayout(1, false);
		tlsCheckLayout.marginWidth = 0;
		tlsCheckLayout.marginHeight = 0;
		tlsCheckCol.setLayout(tlsCheckLayout);

		final Label tlsCheckLabel = new Label(tlsCheckCol, SWT.NONE);
		tlsCheckLabel.setText(LangResources.get("tlsCheck"));

		tlsCheckButton = new Button(tlsCheckCol, SWT.NONE);
		final GridData tlsCheckGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		tlsCheckGridData.widthHint = 100;
		tlsCheckButton.setLayoutData(tlsCheckGridData);
		tlsCheckButton.setText("JVM Truststore");
		tlsCheckButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				final TlsCheckConfigurationDialog dialog = new TlsCheckConfigurationDialog(getShell(), RestClient.APPLICATION_NAME, tlsCheckConfiguration.getType(), tlsCheckConfiguration.getTrustoreOrPemFile(), tlsCheckConfiguration.getTrustorePassword(), tlsCheckConfiguration.getCheckCn());
				final TlsCheckConfiguration result = dialog.open();
				if (result != null) {
					tlsCheckConfiguration = result;
					checkRequestContentStatus();
				}
			}
		});

		serviceMethodText = createLabeledText(LangResources.get("serviceMethod"), LangResources.get("serviceMethodHint"));

		createKeyValueSectionForHeader(LangResources.get("httpRequestHeader"));

		final Composite headerButtonRegion = new Composite(content, SWT.NONE);
		headerButtonRegion.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		headerButtonRegion.setLayout(new GridLayout(3, false));

		final Button addBasicAuthButton = new Button(headerButtonRegion, SWT.PUSH);
		addBasicAuthButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		addBasicAuthButton.setText(LangResources.get("addBasicAuth"));
		addBasicAuthButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				final CredentialsDialog credentialsDialog = new CredentialsDialog(getShell(), RestClient.APPLICATION_NAME, LangResources.get("enterBasicAuthCredentials"), true, true);
				final Credentials credentials = credentialsDialog.open();
				if (credentials != null) {
					final Map<String, String> httpHeadersMap = getHttpHeaders();
					httpHeadersMap.put(HttpConstants.HTTPHEADERNAME_AUTHORIZATION, HttpUtilities.createBasicAuthenticationHeaderValue(credentials.getUsername(), new String(credentials.getPassword())));
					setHttpHeaders(httpHeadersMap);
				}
			}
		});

		final Button addTokenAuthButton = new Button(headerButtonRegion, SWT.PUSH);
		addTokenAuthButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		addTokenAuthButton.setText(LangResources.get("addTokenAuth"));
		addTokenAuthButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				final SimpleInputDialog inputDialog = new SimpleInputDialog(getShell(), RestClient.APPLICATION_NAME, LangResources.get("enterAuthToken"));
				final String token = inputDialog.open();
				if (token != null) {
					final Map<String, String> httpHeadersMap = getHttpHeaders();
					httpHeadersMap.put(HttpConstants.HTTPHEADERNAME_AUTHORIZATION, HttpConstants.AUTHORIZATIONHEADER_START_BEARER + " " + token);
					setHttpHeaders(httpHeadersMap);
				}
			}
		});

		final Button createTokenAuthButton = new Button(headerButtonRegion, SWT.PUSH);
		createTokenAuthButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		createTokenAuthButton.setText(LangResources.get("fetchIdpToken"));
		createTokenAuthButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				try {
					final IdpCredentialsDialog inputDialog = new IdpCredentialsDialog(getShell(), RestClient.APPLICATION_NAME, LangResources.get("enterIdpCredentials"), idpUrl, idpRealm, idpUsername, idpPassword);
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
							if ("DIRECT".equalsIgnoreCase(getProxyUrl()) || Utilities.isBlank(getProxyUrl())) {
								idpProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.None);
							} else if ("WPAD".equalsIgnoreCase(getProxyUrl())) {
								idpProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.WPAD);
							} else {
								idpProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.ProxyURL, getProxyUrl());
							}
						}

						String idpToken = null;
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
					new QuestionDialog(getShell(), LangResources.get("fetchIdpToken"), e.getMessage(), LangResources.get("ok")).setBackgroundColor(SwtColor.LightRed).open();
				}
			}
		});

		final Button contentTypeButton = new Button(headerButtonRegion, SWT.PUSH);
		contentTypeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		contentTypeButton.setText(LangResources.get("addContentType"));
		contentTypeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				final List<String> contentTypes = new ArrayList<>();
				for (final HttpContentType contentTypeItem : HttpContentType.values()) {
					contentTypes.add(contentTypeItem.getStringRepresentation());
				}
				final SelectionDialog selectionDialog = new SelectionDialog(getShell(), RestClient.APPLICATION_NAME, LangResources.get("addContentType"), contentTypes);
				final String contentType = selectionDialog.open();
				if (contentType != null) {
					final Map<String, String> httpHeadersMap = getHttpHeaders();
					httpHeadersMap.put(HttpConstants.HTTPHEADERNAME_CONTENTTYPE, contentType);
					setHttpHeaders(httpHeadersMap);
				}
			}
		});

		final Button standardHeaderButton = new Button(headerButtonRegion, SWT.PUSH);
		standardHeaderButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		standardHeaderButton.setText(LangResources.get("addStandardHeader"));
		standardHeaderButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				final List<String> standardHeaders = new ArrayList<>();

				standardHeaders.add("Accept");
				standardHeaders.add("Authorization");
				standardHeaders.add("Cache-Control");
				standardHeaders.add("Content-Encoding");
				standardHeaders.add("Content-Length");
				standardHeaders.add("Content-Type");
				standardHeaders.add("Cookie");
				standardHeaders.add("Date");
				standardHeaders.add("Pragma");
				standardHeaders.add("Proxy-Authorization");
				standardHeaders.add("Referer");
				standardHeaders.add("User-Agent");
				standardHeaders.add("Proxy-Connection");

				final SelectionDialog selectionDialog = new SelectionDialog(getShell(), RestClient.APPLICATION_NAME, LangResources.get("addStandardHeader"), standardHeaders);
				final String standardHeader = selectionDialog.open();
				if (standardHeader != null) {
					final Map<String, String> httpHeadersMap = getHttpHeaders();
					httpHeadersMap.put(standardHeader, "");
					setHttpHeaders(httpHeadersMap);
				}
			}
		});

		createKeyValueSectionForUrlParams(LangResources.get("urlParameter"));

		createKeyValueSectionForHtmlFormParams(LangResources.get("htmlFormParameter"));

		createRequestBodySection();
	}

	private void createPresetNameSection() {
		final Label label = new Label(content, SWT.NONE);
		label.setText(LangResources.get("preset"));

		final Composite comboButtonComposite = new Composite(content, SWT.NONE);
		comboButtonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		comboButtonComposite.setLayout(new GridLayout(3, false));

		// Text field and dropdown arrow are grouped into their own composite with
		// zero spacing, so they visually read as a single combined control.
		presetFieldComposite = new Composite(comboButtonComposite, SWT.NONE);
		presetFieldComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		final GridLayout presetFieldLayout = new GridLayout(2, false);
		presetFieldLayout.marginWidth = 0;
		presetFieldLayout.marginHeight = 0;
		presetFieldLayout.horizontalSpacing = 0;
		presetFieldComposite.setLayout(presetFieldLayout);

		presetNameText = new Text(presetFieldComposite, SWT.BORDER);
		presetNameText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		// Light gray fill on the button itself; no separate border composite,
		// so no extra background shows through around it.
		presetDropDownButton = new Button(presetFieldComposite, SWT.PUSH | SWT.FLAT);
		presetDropDownButton.setText("\u25BC");
		presetDropDownButton.setBackground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
		final GridData arrowButtonGridData = new GridData(SWT.FILL, SWT.FILL, false, true);
		arrowButtonGridData.widthHint = 20;
		presetDropDownButton.setLayoutData(arrowButtonGridData);
		presetDropDownButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				openPresetSelectionPopup();
			}
		});

		saveButton = new Button(comboButtonComposite, SWT.PUSH);
		saveButton.setText(LangResources.get("save"));

		deleteButton = new Button(comboButtonComposite, SWT.PUSH);
		deleteButton.setText(LangResources.get("delete"));
	}

	/**
	 * Opens a popup below the preset text field, listing all known presets.
	 * A single click on an entry selects it and closes the popup again.
	 * Entries can be reordered via drag&amp;drop while the popup is open.
	 */
	private void openPresetSelectionPopup() {
		if (presetNames.isEmpty()) {
			return;
		}

		final Shell popup = new Shell(getShell(), SWT.NO_TRIM | SWT.ON_TOP);

		final org.eclipse.swt.widgets.List presetList = new org.eclipse.swt.widgets.List(popup, SWT.SINGLE | SWT.V_SCROLL | SWT.BORDER);
		for (final String presetName : presetNames) {
			presetList.add(presetName);
		}

		final int itemCount = Math.min(presetNames.size(), 10);
		final int itemHeight = presetList.getItemHeight();
		final int popupHeight = itemCount * itemHeight + 4;
		final int popupWidth = presetFieldComposite.getSize().x;
		final Point popupLocation = presetFieldComposite.toDisplay(0, presetFieldComposite.getSize().y);
		popup.setBounds(popupLocation.x, popupLocation.y, popupWidth, popupHeight);
		presetList.setBounds(0, 0, popupWidth, popupHeight);

		// Thin marker bar showing exactly where a dragged entry would be inserted,
		// positioned manually on top of the list while dragging.
		final Composite insertionMarker = new Composite(popup, SWT.NONE);
		insertionMarker.setBackground(getDisplay().getSystemColor(SWT.COLOR_BLUE));
		insertionMarker.setVisible(false);

		// Drag&Drop reordering of the preset list within the popup.
		final int[] dragSourceIndex = { -1 };
		final boolean[] dragInProgress = { false };
		final int[] insertionIndex = { -1 };

		final DragSource dragSource = new DragSource(presetList, DND.DROP_MOVE);
		dragSource.setTransfer(new Transfer[] { TextTransfer.getInstance() });
		dragSource.addDragListener(new DragSourceAdapter() {
			@Override
			public void dragStart(final DragSourceEvent event) {
				final int index = presetList.getSelectionIndex();
				if (index < 0) {
					event.doit = false;
				} else {
					dragSourceIndex[0] = index;
					dragInProgress[0] = true;
				}
			}

			@Override
			public void dragSetData(final DragSourceEvent event) {
				event.data = String.valueOf(dragSourceIndex[0]);
			}

			@Override
			public void dragFinished(final DragSourceEvent event) {
				dragInProgress[0] = false;
				insertionMarker.setVisible(false);
			}
		});

		final DropTarget dropTarget = new DropTarget(presetList, DND.DROP_MOVE);
		dropTarget.setTransfer(new Transfer[] { TextTransfer.getInstance() });
		dropTarget.addDropListener(new DropTargetAdapter() {
			@Override
			public void dragOver(final DropTargetEvent event) {
				event.feedback = DND.FEEDBACK_SCROLL;

				final Point localPoint = presetList.toControl(event.x, event.y);
				insertionIndex[0] = computeInsertionIndex(localPoint.y, itemHeight, presetNames.size());

				final int markerY = Math.max(0, Math.min(popupHeight - 2, insertionIndex[0] * itemHeight - 1));
				insertionMarker.setBounds(0, markerY, popupWidth, 2);
				insertionMarker.setVisible(true);
				insertionMarker.moveAbove(presetList);
			}

			@Override
			public void dragLeave(final DropTargetEvent event) {
				insertionMarker.setVisible(false);
			}

			@Override
			public void drop(final DropTargetEvent event) {
				insertionMarker.setVisible(false);

				if (dragSourceIndex[0] < 0 || insertionIndex[0] < 0) {
					return;
				}

				if (insertionIndex[0] != dragSourceIndex[0] && insertionIndex[0] != dragSourceIndex[0] + 1) {
					final String movedPresetName = presetNames.remove(dragSourceIndex[0]);
					final int insertAt = insertionIndex[0] > dragSourceIndex[0] ? insertionIndex[0] - 1 : insertionIndex[0];
					presetNames.add(insertAt, movedPresetName);

					presetList.removeAll();
					for (final String presetName : presetNames) {
						presetList.add(presetName);
					}
					presetList.select(insertAt);

					if (presetsReorderedListener != null) {
						presetsReorderedListener.accept(getPresetNames());
					}
				}

				dragSourceIndex[0] = -1;
				insertionIndex[0] = -1;
			}
		});

		// Highlight the entry under the mouse cursor on hover (Option A: reuses the
		// real selection, so it stays consistent with drag&drop and the click handler
		// below). Suppressed while a drag is in progress so it doesn't fight the
		// drag source's own selection.
		presetList.addMouseMoveListener(e -> {
			if (dragInProgress[0]) {
				return;
			}
			int hoveredIndex = e.y / itemHeight;
			if (hoveredIndex < 0) {
				hoveredIndex = 0;
			} else if (hoveredIndex >= presetNames.size()) {
				hoveredIndex = presetNames.size() - 1;
			}
			if (presetList.getSelectionIndex() != hoveredIndex) {
				presetList.select(hoveredIndex);
			}
		});

		// A plain (non-dragging) click selects the preset and closes the popup.
		presetList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(final MouseEvent e) {
				if (dragInProgress[0]) {
					return;
				}
				final int index = presetList.getSelectionIndex();
				if (index >= 0) {
					final String selectedPresetName = presetList.getItem(index);
					popup.dispose();
					presetNameText.setText(selectedPresetName);
					if (presetSelectionListener != null) {
						presetSelectionListener.run();
					}
				}
			}
		});

		popup.addListener(SWT.Deactivate, e -> {
			if (!dragInProgress[0] && !popup.isDisposed()) {
				popup.dispose();
			}
		});

		popup.open();
		presetList.setFocus();
	}

	/**
	 * Translates a mouse y-coordinate inside the popup list into the index a
	 * dragged entry would be inserted at (0 = before the first entry, itemCount
	 * = after the last entry), based on which half of the hovered row the
	 * pointer is currently in.
	 */
	private static int computeInsertionIndex(final int localY, final int itemHeight, final int itemCount) {
		if (itemHeight <= 0 || itemCount <= 0) {
			return 0;
		}

		int hoveredIndex = localY / itemHeight;
		if (hoveredIndex < 0) {
			hoveredIndex = 0;
		}
		if (hoveredIndex >= itemCount) {
			hoveredIndex = itemCount - 1;
		}

		final int remainderInRow = localY - hoveredIndex * itemHeight;
		int insertAt = remainderInRow < itemHeight / 2 ? hoveredIndex : hoveredIndex + 1;
		if (insertAt < 0) {
			insertAt = 0;
		}
		if (insertAt > itemCount) {
			insertAt = itemCount;
		}
		return insertAt;
	}

	private Text createLabeledText(final String labelText, final String message) {
		final Label label = new Label(content, SWT.NONE);
		label.setText(labelText);

		final Text text = new Text(content, SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		text.setMessage(message);
		return text;
	}

	private void createKeyValueSectionForHeader(final String title) {
		final Composite sectionHeader = new Composite(content, SWT.NONE);
		sectionHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		sectionHeader.setLayout(new GridLayout(2, false));

		final Label label = new Label(sectionHeader, SWT.NONE);
		label.setText(title);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

		final Button addButton = new Button(sectionHeader, SWT.PUSH);
		addButton.setText("+");

		final ScrolledComposite scrolled = new ScrolledComposite(content, SWT.V_SCROLL | SWT.BORDER);
		final GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.heightHint = 75;
		scrolled.setLayoutData(gd);
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		final Composite container = new Composite(scrolled, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		scrolled.setContent(container);

		scrolled.setAlwaysShowScrollBars(true);

		addKeyValueRow(container, scrolled);

		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				addKeyValueRow(container, scrolled);
				checkRequestContentStatus();
			}
		});

		headerContainer = container;
		headerScrolled = scrolled;
	}

	private void createKeyValueSectionForUrlParams(final String title) {
		final Composite sectionHeader = new Composite(content, SWT.NONE);
		sectionHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		sectionHeader.setLayout(new GridLayout(2, false));

		final Label label = new Label(sectionHeader, SWT.NONE);
		label.setText(title);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

		final Button addButton = new Button(sectionHeader, SWT.PUSH);
		addButton.setText("+");

		final ScrolledComposite scrolled = new ScrolledComposite(content, SWT.V_SCROLL | SWT.BORDER);
		final GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.heightHint = 75;
		scrolled.setLayoutData(gd);
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		final Composite container = new Composite(scrolled, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		scrolled.setContent(container);

		scrolled.setAlwaysShowScrollBars(true);

		addKeyValueRow(container, scrolled);

		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				addKeyValueRow(container, scrolled);
				checkRequestContentStatus();
			}
		});

		urlParamContainer = container;
		urlParamScrolled = scrolled;
	}

	private void createKeyValueSectionForHtmlFormParams(final String title) {
		final Composite sectionHeader = new Composite(content, SWT.NONE);
		sectionHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		sectionHeader.setLayout(new GridLayout(2, false));

		final Label label = new Label(sectionHeader, SWT.NONE);
		label.setText(title);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

		htmlFormAddButton = new Button(sectionHeader, SWT.PUSH);
		htmlFormAddButton.setText("+");

		final ScrolledComposite scrolled = new ScrolledComposite(content, SWT.V_SCROLL | SWT.BORDER);
		final GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.heightHint = 75;
		scrolled.setLayoutData(gd);
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		final Composite container = new Composite(scrolled, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		scrolled.setContent(container);

		scrolled.setAlwaysShowScrollBars(true);

		htmlFormAddButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				addKeyValueRow(container, scrolled);
				checkRequestContentStatus();
			}
		});

		htmlFormParamContainer = container;
		htmlFormParamScrolled = scrolled;
	}

	private Composite addKeyValueRow(final Composite parent, final ScrolledComposite scrolled) {
		final Composite row = new Composite(parent, SWT.NONE);

		final GridLayout gl = new GridLayout(3, false);
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		gl.verticalSpacing = 2;
		gl.horizontalSpacing = 5;
		row.setLayout(gl);
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final Text nameText = new Text(row, SWT.BORDER);
		final GridData gridDataName = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		gridDataName.widthHint = 150;
		nameText.setLayoutData(gridDataName);
		nameText.setMessage(LangResources.get("nameHint"));

		final Text valueText = new Text(row, SWT.BORDER);
		final GridData gridDataValue = new GridData(SWT.FILL, SWT.CENTER, true, false);
		valueText.setLayoutData(gridDataValue);
		valueText.setMessage(LangResources.get("valueHint"));

		final Button removeButton = new Button(row, SWT.PUSH);
		removeButton.setText("-");
		removeButton.addListener(SWT.Selection, e -> {
			row.dispose();
			refreshScrolledArea(parent, scrolled);

			checkRequestContentStatus();
		});

		refreshScrolledArea(parent, scrolled);

		return row;
	}

	private void refreshScrolledArea(final Composite refreshScrolledAreaParent, final ScrolledComposite scrolled) {
		refreshScrolledAreaParent.layout(true, true);

		final int width = scrolled.getClientArea().width;
		final int height = Math.max(refreshScrolledAreaParent.computeSize(SWT.DEFAULT, SWT.DEFAULT).y, 80);
		scrolled.setMinSize(width, height);

		scrolled.layout(true, true);

		updateOuterMinSize();
	}

	private void createRequestBodySection() {
		final Label label = new Label(content, SWT.NONE);
		label.setText(LangResources.get("requestBody"));

		final ScrolledComposite scrolled = new ScrolledComposite(content, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		final GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.heightHint = 75;
		scrolled.setLayoutData(gd);
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		requestBodyText = new Text(scrolled, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		requestBodyText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		scrolled.setContent(requestBodyText);

		scrolled.addListener(SWT.Resize, e -> {
			final int width = scrolled.getClientArea().width;
			final Point pref = requestBodyText.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			scrolled.setMinSize(width, Math.max(pref.y, 75));
		});

		scrolled.setMinSize(scrolled.getClientArea().width, 200);
	}

	private static Map<String, String> extractKeyValuePairs(final Composite container) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (final Control c : container.getChildren()) {
			if (c instanceof final Composite row) {
				final Control[] children = row.getChildren();
				if (children.length >= 2 &&
						children[0] instanceof final Text name &&
						children[1] instanceof final Text value &&
						!name.getText().isBlank()) {
					map.put(name.getText(), value.getText());
				}
			}
		}
		return map;
	}

	private void checkRequestContentStatus() {
		if (requestBodyText != null && htmlFormParamContainer != null) {
			final boolean isGet = "GET".equalsIgnoreCase(getHttpMethod());

			setCompositeEnabled(htmlFormParamContainer, !isGet);
			if (htmlFormAddButton != null) {
				htmlFormAddButton.setEnabled(!isGet);
			}
			if (isGet) {
				htmlFormParamScrolled.setToolTipText(LangResources.get("deactivationHtmlFormParams"));
				if (htmlFormAddButton != null) {
					htmlFormAddButton.setToolTipText(LangResources.get("deactivationHtmlFormParams"));
				}
			} else {
				htmlFormParamScrolled.setToolTipText(null);
				if (htmlFormAddButton != null) {
					htmlFormAddButton.setToolTipText(null);
				}
			}

			if (isGet) {
				requestBodyText.setEnabled(false);
			} else if (htmlFormParamContainer.getChildren().length > 0) {
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
					setHttpHeaders(httpHeaders);
				}
			} else {
				requestBodyText.setEnabled(true);
			}

			switch (tlsCheckConfiguration.getType()) {
				case AdditionalTrustStoreFile:
					tlsCheckButton.setText(LangResources.get("AdditionalTrustStoreFile"));
					break;
				case NoCheck:
					tlsCheckButton.setText(LangResources.get("NoCheck"));
					break;
				case RecordingSingleCertificate:
					tlsCheckButton.setText(LangResources.get("RecordingSingleCertificate"));
					break;
				case RecordingToTrustStoreFile:
					tlsCheckButton.setText(LangResources.get("RecordingToTrustStoreFile"));
					break;
				case SingleCertificate:
					tlsCheckButton.setText(LangResources.get("SingleCertificate"));
					break;
				case TrustStoreFile:
					tlsCheckButton.setText(LangResources.get("TrustStoreFile"));
					break;
				case SystemTrustStore:
				default:
					tlsCheckButton.setText(LangResources.get("SystemTrustStore"));
					break;
			}
		}
	}

	private void setCompositeEnabled(final Composite composite, final boolean enabled) {
		composite.setEnabled(enabled);
		for (final Control child : composite.getChildren()) {
			child.setEnabled(enabled);
			if (child instanceof Composite) {
				setCompositeEnabled((Composite) child, enabled);
			}
		}
	}
}
