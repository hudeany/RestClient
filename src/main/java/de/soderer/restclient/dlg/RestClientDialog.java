package de.soderer.restclient.dlg;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Monitor;

import de.soderer.json.JsonArray;
import de.soderer.json.JsonNode;
import de.soderer.json.JsonObject;
import de.soderer.json.JsonReader;
import de.soderer.json.JsonWriter;
import de.soderer.json.exception.DuplicateKeyException;
import de.soderer.network.HttpMethod;
import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.NetworkUtilities;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.restclient.RestClient;
import de.soderer.restclient.image.ImageManager;
import de.soderer.restclient.worker.ExecuteHttpRequestWorker;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Result;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.swt.ApplicationConfigurationDialog;
import de.soderer.utilities.swt.ErrorDialog;
import de.soderer.utilities.swt.ProgressDialog;
import de.soderer.utilities.swt.QuestionDialog;
import de.soderer.utilities.swt.ShowDataDialog;
import de.soderer.utilities.swt.SwtColor;
import de.soderer.utilities.swt.SwtUtilities;
import de.soderer.utilities.swt.UpdateableGuiApplication;
import de.soderer.utilities.worker.WorkerSimple;

/**
 * TODO:
 * - Button Create Html Form Body
 * - Application Icon
 */
public class RestClientDialog extends UpdateableGuiApplication {
	private final ProxyConfiguration proxyConfiguration;

	private RequestComponent requestPart;
	private ResponseComponent responsePart;

	private Button clearRequestDataButton;
	private Button sendRequestButton;
	private Button closeButton;

	private final ConfigurationProperties applicationConfiguration;

	public RestClientDialog(final Display display, final ConfigurationProperties applicationConfiguration) throws Exception {
		super(display, RestClient.APPLICATION_NAME, RestClient.VERSION, RestClient.KEYSTORE_FILE);

		this.applicationConfiguration = applicationConfiguration;

		final Monitor[] monitorArray = display.getMonitors();
		if (monitorArray != null) {
			getShell().setLocation((monitorArray[0].getClientArea().width - getSize().x) / 2, (monitorArray[0].getClientArea().height - getSize().y) / 2);
		}

		final ProxyConfigurationType proxyConfigurationType = ProxyConfigurationType.getFromString(applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE));
		final String proxyUrl = applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_URL);
		proxyConfiguration = new ProxyConfiguration(proxyConfigurationType, proxyUrl);

		if (Utilities.isNotBlank(RestClient.VERSIONINFO_DOWNLOAD_URL) && dailyUpdateCheckIsPending()) {
			setDailyUpdateCheckStatus(true);
			try {
				if (ApplicationUpdateUtilities.checkForNewVersionAvailable(RestClient.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, RestClient.APPLICATION_NAME, RestClient.VERSION) != null) {
					ApplicationUpdateUtilities.executeUpdate(this, RestClient.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, RestClient.APPLICATION_NAME, RestClient.VERSION, RestClient.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true, false);
				}
			} catch (final Exception e) {
				showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e.getMessage()));
			}
		}

		// Mandatory initialization
		@SuppressWarnings("unused")
		final ImageManager imageManager = new ImageManager(getShell());

		final SashForm sashForm = new SashForm(this, SWT.SMOOTH | SWT.HORIZONTAL);
		setImage(ImageManager.getImage("RestClient.png"));
		setText(LangResources.get("window_title"));
		setLayout(new FillLayout());

		createLeftPart(sashForm);

		createRightPart(sashForm);

		setSize(1000, 700);
		setMinimumSize(450, 450);

		addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(final Event event) {
				close();
			}
		});

		checkButtonStatus();
	}

	private void createLeftPart(final SashForm parent) throws Exception {
		final Composite leftPart = new Composite(parent, SWT.BORDER);
		leftPart.setLayout(SwtUtilities.createSmallMarginGridLayout(3, false));
		leftPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));

		final Label applicationLabel = new Label(leftPart, SWT.NONE);
		applicationLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		applicationLabel.setText(LangResources.get("title"));
		applicationLabel.setFont(new Font(getDisplay(), "Arial", 12, SWT.BOLD));

		final Button configButton = new Button(leftPart, SWT.PUSH);
		configButton.setImage(ImageManager.getImage("wrench.png"));
		configButton.setToolTipText(LangResources.get("configuration"));
		configButton.addSelectionListener(new ConfigButtonSelectionListener());

		final Button helpButton = new Button(leftPart, SWT.PUSH);
		helpButton.setImage(ImageManager.getImage("question.png"));
		helpButton.setToolTipText(LangResources.get("help"));
		helpButton.addSelectionListener(new HelpButtonSelectionListener(this));

		requestPart = new RequestComponent(leftPart, SWT.BORDER);
		requestPart.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		requestPart.addSaveButtonListener(new Runnable() {
			@Override
			public void run() {
				try {
					final JsonObject requestPresetsJsonObject;
					if (RestClient.REQUEST_PRESETS_FILE.exists()) {
						try (JsonReader reader = new JsonReader(new FileInputStream(RestClient.REQUEST_PRESETS_FILE))) {
							requestPresetsJsonObject = (JsonObject) reader.read();
						}
					} else {
						requestPresetsJsonObject = new JsonObject();
					}

					final String presetName = requestPart.getPresetName();
					if (!requestPresetsJsonObject.containsKey(presetName)
							|| new QuestionDialog(getShell(), RestClient.APPLICATION_NAME, LangResources.get("replaceExistingRequestPreset", presetName), LangResources.get("yes"), LangResources.get("cancel")).open() == 0) {
						try (JsonWriter writer = new JsonWriter(new FileOutputStream(RestClient.REQUEST_PRESETS_FILE))) {
							final JsonObject requestPresetJsonObject = createRequestPresetJsonObject();

							if (requestPresetsJsonObject.containsKey(presetName)) {
								requestPresetsJsonObject.replace(presetName, requestPresetJsonObject);
							} else {
								requestPresetsJsonObject.add(presetName, requestPresetJsonObject);
							}
							writer.add(requestPresetsJsonObject);
						}
						showMessage(RestClient.APPLICATION_NAME, LangResources.get("savedRequestPreset", presetName));
						requestPart.setPresetNames(new ArrayList<>(requestPresetsJsonObject.keySet()));
						requestPart.setPresetName(presetName);
					}
				} catch (final Exception e) {
					showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
				}
			}
		});
		requestPart.addDeleteButtonListener(new Runnable() {
			@Override
			public void run() {
				try {
					final String presetName = requestPart.getPresetName();
					if (new QuestionDialog(getShell(), RestClient.APPLICATION_NAME, LangResources.get("reallyDeleteRequestPreset", presetName), LangResources.get("yes"), LangResources.get("cancel")).open() == 0) {
						final JsonObject requestPresetsJsonObject;
						if (RestClient.REQUEST_PRESETS_FILE.exists()) {
							try (JsonReader reader = new JsonReader(new FileInputStream(RestClient.REQUEST_PRESETS_FILE))) {
								requestPresetsJsonObject = (JsonObject) reader.read();
							}
						} else {
							requestPresetsJsonObject = new JsonObject();
						}

						requestPresetsJsonObject.remove(presetName);

						try (JsonWriter writer = new JsonWriter(new FileOutputStream(RestClient.REQUEST_PRESETS_FILE))) {
							writer.add(requestPresetsJsonObject);
						}
						showMessage(RestClient.APPLICATION_NAME, LangResources.get("deletedRequestPreset", presetName));
						requestPart.setPresetNames(new ArrayList<>(requestPresetsJsonObject.keySet()));
					}
				} catch (final Exception e) {
					showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
				}
			}
		});
		requestPart.addPresetSelectionListener(new Runnable() {
			@Override
			public void run() {
				try {
					final JsonObject requestPresetsJsonObject;

					if (RestClient.REQUEST_PRESETS_FILE.exists()) {
						try (JsonReader reader = new JsonReader(new FileInputStream(RestClient.REQUEST_PRESETS_FILE))) {
							requestPresetsJsonObject = (JsonObject) reader.read();
							setRequestPreset((JsonObject) requestPresetsJsonObject.get(requestPart.getPresetName()));
						}
					}
				} catch (final Exception e) {
					showErrorMessage(RestClient.APPLICATION_NAME, e.getMessage());
				}
			}
		});

		loadPresets();
	}

	private void createRightPart(final SashForm parent) throws Exception {
		final Composite rightPart = new Composite(parent, SWT.BORDER);
		rightPart.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));

		responsePart = new ResponseComponent(rightPart, SWT.BORDER);
		responsePart.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		responsePart.clearResponse();

		final Composite buttonRegion = new Composite(rightPart, SWT.NONE);
		buttonRegion.setLayout(SwtUtilities.createSmallMarginGridLayout(2, true));
		buttonRegion.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

		sendRequestButton = new Button(buttonRegion, SWT.PUSH);
		final GridData gdDouble = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		gdDouble.heightHint = 48;
		sendRequestButton.setLayoutData(gdDouble);
		sendRequestButton.setText(LangResources.get("sendRequest"));
		sendRequestButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				executeRequest();
			}
		});

		clearRequestDataButton = new Button(buttonRegion, SWT.PUSH);
		clearRequestDataButton.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		clearRequestDataButton.setText(LangResources.get("clearRequestData"));
		clearRequestDataButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				setRequestPreset(null);
			}
		});

		closeButton = new Button(buttonRegion, SWT.PUSH);
		closeButton.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		closeButton.setText(LangResources.get("close"));
		closeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				close();
			}
		});

		checkButtonStatus();
	}

	private class ConfigButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			try {
				byte[] iconData;
				try (InputStream inputStream = ImageManager.class.getResourceAsStream("/images/icons/RestClient.ico")) {
					iconData = IoUtilities.toByteArray(inputStream);
				}

				final ApplicationConfigurationDialog dialog = new ApplicationConfigurationDialog(getShell(), applicationConfiguration, RestClient.APPLICATION_NAME, RestClient.APPLICATION_STARTUPCLASS_NAME, iconData, ImageManager.getImage("RestClient.png"));
				if (dialog.open()) {
					applicationConfiguration.save();
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), RestClient.APPLICATION_NAME, RestClient.VERSION.toString(), RestClient.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
		}
	}

	private class HelpButtonSelectionListener extends SelectionAdapter {
		private final RestClientDialog applicationDialog;

		public HelpButtonSelectionListener(final RestClientDialog applicationDialog) {
			this.applicationDialog = applicationDialog;
		}

		@Override
		public void widgetSelected(final SelectionEvent e) {
			new HelpDialog(applicationDialog, RestClient.APPLICATION_NAME + " (" + RestClient.VERSION.toString() + ") " + LangResources.get("help"), applicationConfiguration).open();
		}
	}

	public void checkButtonStatus() {
		// do nothing
	}

	@Override
	public void close() {
		applicationConfiguration.save();
		dispose();
	}

	@Override
	protected void setDailyUpdateCheckStatus(final boolean checkboxStatus) {
		applicationConfiguration.set(RestClient.CONFIG_DAILY_UPDATE_CHECK, checkboxStatus);
		applicationConfiguration.set(RestClient.CONFIG_NEXT_DAILY_UPDATE_CHECK, LocalDateTime.now().plusDays(1));
		applicationConfiguration.save();
	}

	@Override
	protected Boolean isDailyUpdateCheckActivated() {
		return applicationConfiguration.getBoolean(RestClient.CONFIG_DAILY_UPDATE_CHECK);
	}

	protected boolean dailyUpdateCheckIsPending() {
		return applicationConfiguration.getBoolean(RestClient.CONFIG_DAILY_UPDATE_CHECK)
				&& (applicationConfiguration.getDate(RestClient.CONFIG_NEXT_DAILY_UPDATE_CHECK) == null || applicationConfiguration.getDate(RestClient.CONFIG_NEXT_DAILY_UPDATE_CHECK).isBefore(LocalDateTime.now()))
				&& NetworkUtilities.checkForNetworkConnection();
	}

	public void showData(final String title, final String text) {
		new ShowDataDialog(getShell(), title, text, true).open();
	}

	public void showMessage(final String title, final String text) {
		new QuestionDialog(getShell(), title, text, LangResources.get("ok")).open();
	}

	public void showErrorMessage(final String title, final String text) {
		new QuestionDialog(getShell(), title, text, LangResources.get("ok")).setBackgroundColor(SwtColor.LightRed).open();
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
		if (jsonObject != null) {
			requestPart.setProxyUrl((String) jsonObject.getSimpleValue("proxyUrl"));
			requestPart.setHttpMethod((String) jsonObject.getSimpleValue("httpMethod"));
			requestPart.setServiceUrl((String) jsonObject.getSimpleValue("serviceUrl"));
			requestPart.setTlsCheck((Boolean) jsonObject.getSimpleValue("tlsCheck"));
			requestPart.setServiceMethod((String) jsonObject.getSimpleValue("serviceMethod"));

			final Map<String, String> httpHeaders = new LinkedHashMap<>();
			for (final JsonNode httpHeaderJsonNode : ((JsonArray) jsonObject.get("httpRequestHeaders")).items()) {
				final JsonObject httpHeaderJsonObject = (JsonObject) httpHeaderJsonNode;
				httpHeaders.put((String) httpHeaderJsonObject.getSimpleValue("name"), (String) httpHeaderJsonObject.getSimpleValue("value"));
			}
			requestPart.setHttpHeaders(httpHeaders);

			final Map<String, String> urlParameters = new LinkedHashMap<>();
			for (final JsonNode urlParameterJsonNode : ((JsonArray) jsonObject.get("urlParameters")).items()) {
				final JsonObject urlParameterJsonObject = (JsonObject) urlParameterJsonNode;
				urlParameters.put((String) urlParameterJsonObject.getSimpleValue("name"), (String) urlParameterJsonObject.getSimpleValue("value"));
			}
			requestPart.setUrlParameters(urlParameters);

			requestPart.setRequestBody((String) jsonObject.getSimpleValue("requestBody"));
		} else {
			requestPart.setProxyUrl("");
			requestPart.setHttpMethod("");
			requestPart.setServiceUrl("");
			requestPart.setTlsCheck(true);
			requestPart.setServiceMethod("");

			final Map<String, String> httpHeaders = new LinkedHashMap<>();
			requestPart.setHttpHeaders(httpHeaders);

			final Map<String, String> urlParameters = new LinkedHashMap<>();
			requestPart.setUrlParameters(urlParameters);

			requestPart.setRequestBody("");
		}
	}

	private JsonObject createRequestPresetJsonObject() throws DuplicateKeyException {
		final JsonObject requestPresetJsonObject = new JsonObject();

		requestPresetJsonObject.add("proxyUrl", requestPart.getProxyUrl());
		requestPresetJsonObject.add("httpMethod", requestPart.getHttpMethod());
		requestPresetJsonObject.add("serviceUrl", requestPart.getServiceUrl());
		requestPresetJsonObject.add("tlsCheck", requestPart.getTlsCheck());
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

		requestPresetJsonObject.add("requestBody", requestPart.getRequestBody());

		return requestPresetJsonObject;
	}

	private void executeRequest() {
		try {
			responsePart.clearResponse();

			try {
				final HttpRequest httpRequest = new HttpRequest(HttpMethod.getHttpMethodByName(requestPart.getHttpMethod()), requestPart.getServiceUrl() + (Utilities.isNotBlank(requestPart.getServiceMethod()) ? "/" + requestPart.getServiceMethod() : ""));
				if (Utilities.isNotBlank(requestPart.getRequestBody())) {
					httpRequest.setRequestBody(requestPart.getRequestBody());
				}

				for (final Entry<String, String> httpRequestHeadersEntry : requestPart.getHttpHeaders().entrySet()) {
					httpRequest.addHeader(httpRequestHeadersEntry.getKey(), httpRequestHeadersEntry.getValue());
				}

				for (final Entry<String, String> urlParametersEntry : requestPart.getUrlParameters().entrySet()) {
					httpRequest.addUrlParameter(urlParametersEntry.getKey(), urlParametersEntry.getValue());
				}

				final WorkerSimple<HttpResponse> worker = new ExecuteHttpRequestWorker(null, httpRequest, requestPart.getProxyUrl(), requestPart.getTlsCheck());
				HttpResponse httpResponse;
				final ProgressDialog<WorkerSimple<HttpResponse>> progressDialog = new ProgressDialog<>(getShell(), RestClient.APPLICATION_NAME, LangResources.get("sendRequest"), worker);
				final Result dialogResult = progressDialog.open();
				if (dialogResult == Result.CANCELED) {
					showErrorMessage(LangResources.get("sendRequest"), LangResources.get("canceledByUser"));
					return;
				} else {
					httpResponse = worker.get();
				}
				responsePart.setHttpCode(Integer.toString(httpResponse.getHttpCode()));
				responsePart.setResponseHeaders(httpResponse.getHeaders());
				responsePart.setResponseBody(httpResponse.getContent());
			} catch (final Exception e) {
				responsePart.setHttpCode("");
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
