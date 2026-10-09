package de.soderer.restclient;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import de.soderer.json.JsonArray;
import de.soderer.json.JsonNode;
import de.soderer.json.JsonObject;
import de.soderer.json.JsonReader;
import de.soderer.json.path.JsonPath;
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
import de.soderer.restclient.dlg.RestClientDialog;
import de.soderer.restclient.helper.IdpHelper;
import de.soderer.restclient.helper.ResponseDataPathEvaluator;
import de.soderer.restclient.worker.ExecuteHttpRequestWorker;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.ParameterException;
import de.soderer.utilities.UpdateableConsoleApplication;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.Version;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.collection.CaseInsensitiveMap;
import de.soderer.utilities.console.ConsoleType;
import de.soderer.utilities.console.ConsoleUtilities;
import de.soderer.utilities.swing.ErrorDialog;
import de.soderer.utilities.worker.WorkerParentDual;
import de.soderer.yaml.YamlReader;
import de.soderer.yaml.data.YamlDocument;
import de.soderer.yaml.data.YamlMapping;
import de.soderer.yaml.data.YamlNode;
import de.soderer.yaml.data.YamlSequence;

/**
 * Main class of the RestClient application.
 *
 * <p>
 * Without parameters (and in a non-headless environment) or with the parameter "gui" it opens the Swing GUI
 * ({@link RestClientDialog}). Otherwise it executes a single HTTP request from the command line and prints the
 * response body to stdout (see help.txt for all parameters). As {@link WorkerParentDual} it shows the progress
 * of console tasks like the application update on the terminal.
 * </p>
 */
public class RestClient extends UpdateableConsoleApplication implements WorkerParentDual {
	/** The Constant APPLICATION_NAME. */
	public static final String APPLICATION_NAME = "RestClient";
	/**
	 * Startup class name of the application, also used as AWT window manager class (e.g. for pinning the
	 * application to the Linux Gnome dock).
	 */
	public static final String APPLICATION_STARTUPCLASS_NAME = "de-soderer-restclient";
	/**
	 * Email address offered in error dialogs for sending error reports.
	 */
	public static final String APPLICATION_ERROR_EMAIL_ADRESS = "RestClient.Error@soderer.de";

	/**
	 * HTTP methods supported by the command line parameter "--method". PATCH and CONNECT are missing on purpose:
	 * HttpURLConnection (used by HttpUtilities) rejects them with a ProtocolException.
	 */
	public static final List<String> CLI_HTTP_METHODS = List.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "TRACE");

	/**
	 * Configuration key of the proxy URLs offered in the proxy URL dropdown, in addition to "DIRECT" and "WPAD".
	 */
	public static final String CONFIG_KEY_PROXY_URL_PRESETS = "ProxyUrlPresets";

	/**
	 * Keystore file of the application, handed to the GUI application base class.
	 */
	public static final File KEYSTORE_FILE = new File(System.getProperty("user.home") + File.separator + "." + APPLICATION_NAME + File.separator + "." + APPLICATION_NAME + ".keystore");

	/** The Constant VERSION_RESOURCE_FILE, which contains version number and versioninfo download url. */
	public static final String VERSION_RESOURCE_FILE = "/application_version.txt";

	/** The version is filled in at application start from the application_version.txt file. */
	public static Version VERSION = null;

	/** The version build time is filled in at application start from the application_version.txt file */
	public static LocalDateTime VERSION_BUILDTIME = null;

	/** The versioninfo download url is filled in at application start from the application_version.txt file. */
	public static String VERSIONINFO_DOWNLOAD_URL = null;

	/** Trusted CA certificate for updates **/
	public static String TRUSTED_UPDATE_CA_CERTIFICATES = null;

	/**
	 * Classpath resource containing the command line help text.
	 */
	public static final String HELP_RESOURCE_FILE_DEFAULT = "/help.txt";
	/**
	 * Classpath resource containing the German command line help text (currently the same file as the default).
	 */
	public static final String HELP_RESOURCE_FILE_DE = "/help.txt";

	/** The Constant CONFIGURATION_FILE. */
	public static final File CONFIGURATION_FILE = new File(System.getProperty("user.home") + File.separator + "." + APPLICATION_NAME + ".config");

	/**
	 * JSON file containing the request presets saved in the GUI, also used by the command line parameters
	 * "--preset" and "--list-presets".
	 */
	public static final File REQUEST_PRESETS_FILE = new File(System.getProperty("user.home") + File.separator + "." + RestClient.APPLICATION_NAME + File.separator + "RequestPresets.json");

	/**
	 * Initializes the default values of the application configuration, including the RestClient specific ones.
	 *
	 * @param applicationConfiguration the configuration to initialize
	 */
	public static void setupDefaultConfig(final ConfigurationProperties applicationConfiguration) {
		applicationConfiguration.setupDefaultConfig();

		if (!applicationConfiguration.containsKey(CONFIG_KEY_PROXY_URL_PRESETS)) {
			applicationConfiguration.set(CONFIG_KEY_PROXY_URL_PRESETS, new ArrayList<>());
		}
	}

	/** The usage message. */
	private static String getUsageMessage() {
		try (InputStream helpInputStream = RestClient.class.getResourceAsStream(RestClient.HELP_RESOURCE_FILE_DEFAULT)) {
			// The build time is optional in application_version.txt, so it must not break the help output
			final String buildTimeText = VERSION_BUILDTIME == null ? "" : " (" + DateUtilities.formatDate(DateUtilities.YYYY_MM_DD_HHMMSS, VERSION_BUILDTIME) + ")";
			return "RestClient (by Andreas Soderer, mail: RestClient@soderer.de)\n"
					+ "VERSION: " + VERSION.toString() + buildTimeText + "\n\n"
					+ new String(IoUtilities.toByteArray(helpInputStream), StandardCharsets.UTF_8);
		} catch (@SuppressWarnings("unused") final Exception e) {
			return "Help info is missing";
		}
	}

	/**
	 * The main method.
	 *
	 * @param arguments the arguments
	 */
	public static void main(final String[] arguments) {
		final int returnCode = _main(arguments);
		if (returnCode >= 0) {
			System.exit(returnCode);
		}
	}

	/**
	 * Implementation of {@link #main(String[])} without the System.exit() call, so it can be tested with JUnit.
	 *
	 * @param args command line arguments
	 * @return exit code: 0 for success, 1 for an error or after printing help or version information,
	 *         -1 if the GUI was opened and keeps running (System.exit() must not be called then)
	 */
	protected static int _main(final String[] args) {
		ApplicationUpdateUtilities.removeUpdateLeftovers();

		try (InputStream resourceStream = RestClient.class.getResourceAsStream(VERSION_RESOURCE_FILE)) {
			// Try to fill the version and versioninfo download url
			final List<String> versionInfoLines = Utilities.readLines(resourceStream, StandardCharsets.UTF_8);
			VERSION = new Version(versionInfoLines.get(0));
			if (versionInfoLines.size() >= 2) {
				VERSION_BUILDTIME = DateUtilities.parseLocalDateTime(DateUtilities.YYYY_MM_DD_HHMMSS, versionInfoLines.get(1));
			}
			if (versionInfoLines.size() >= 3) {
				VERSIONINFO_DOWNLOAD_URL = versionInfoLines.get(2);
			}
			if (versionInfoLines.size() >= 4) {
				TRUSTED_UPDATE_CA_CERTIFICATES = versionInfoLines.get(3);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Without the application_version.txt file we may not go on
			System.err.println("Invalid application_version.txt");
			return 1;
		}

		ConfigurationProperties applicationConfiguration;
		try {
			applicationConfiguration = new ConfigurationProperties(RestClient.APPLICATION_NAME, true);
			RestClient.setupDefaultConfig(applicationConfiguration);
			if ("de".equalsIgnoreCase(applicationConfiguration.get(ConfigurationProperties.CONFIG_KEY_LANGUAGE))) {
				Locale.setDefault(Locale.GERMAN);
			} else {
				Locale.setDefault(Locale.ENGLISH);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			System.err.println("Invalid application configuration");
			return 1;
		}

		try {
			String[] arguments = args;

			boolean openGui = false;

			if (arguments.length == 0) {
				// If started without any parameter we check for headless mode and show the GUI or help
				if (GraphicsEnvironment.isHeadless()) {
					System.out.println(getUsageMessage());
				} else {
					openGui = true;
				}
			} else if (arguments.length == 1 && isHelpKeyword(arguments[0])) {
				// The standalone keywords are only recognized as the one and only argument (resp. as the first
				// argument for "update"), so a parameter value like "--body gui" or "--header help" is never
				// mistaken for a keyword. Combined with request parameters they are rejected as invalid parameters.
				System.out.println(getUsageMessage());
				return 1;
			} else if (arguments.length == 1 && "version".equalsIgnoreCase(arguments[0])) {
				System.out.println(VERSION.toString());
				return 1;
			} else if (arguments.length <= 3 && "update".equalsIgnoreCase(arguments[0])) {
				// "update [username [password]]": the optional credentials follow directly after "update"
				final RestClient restclient = new RestClient();
				final String updateUsername = arguments.length >= 2 ? arguments[1] : null;
				final char[] updatePassword = arguments.length >= 3 ? arguments[2].toCharArray() : null;
				ApplicationUpdateUtilities.executeUpdate(restclient, RestClient.VERSIONINFO_DOWNLOAD_URL, applicationConfiguration.getProxyConfiguration(), RestClient.APPLICATION_NAME, RestClient.VERSION, RestClient.TRUSTED_UPDATE_CA_CERTIFICATES, updateUsername, updatePassword, null, false, false);
				return 1;
			} else if (arguments.length == 1 && "gui".equalsIgnoreCase(arguments[0])) {
				if (GraphicsEnvironment.isHeadless()) {
					throw new Exception("GUI can only be shown on a non-headless environment");
				}
				openGui = true;
				arguments = new String[0];
			}

			// Read the parameters
			String cliUrl = null;
			String cliMethod = null;
			final Map<String, String> cliHeaders = new LinkedHashMap<>();
			final Map<String, String> cliUrlParameters = new LinkedHashMap<>();
			final Map<String, String> cliFormParameters = new LinkedHashMap<>();
			String cliRequestBody = null;
			String cliBodyFile = null;
			String cliProxy = null;
			Integer cliMaxRedirects = null;
			String cliTlsCheckType = null;
			String cliTlsCheckFile = null;
			String cliTlsCheckPassword = null;
			Boolean cliTlsCheckCn = null;
			String cliDownloadTarget = null;
			String cliResponseDataPath = null;
			String cliIdpUrl = null;
			String cliIdpRealm = null;
			String cliIdpUsername = null;
			String cliIdpPassword = null;
			String cliPresetName = null;
			String cliRequestFile = null;
			boolean cliListPresets = false;
			String cliOutputFile = null;
			String cliBasicAuth = null;
			String cliBearerToken = null;
			boolean cliVerbose = false;
			boolean cliFail = false;

			for (int i = 0; i < arguments.length; i++) {
				final String argument = arguments[i];
				switch (argument.toLowerCase(Locale.ROOT)) {
					case "--url":
						cliUrl = requireValue(arguments, i++, argument);
						break;
					case "--method":
						cliMethod = requireValue(arguments, i++, argument);
						break;
					case "--header": {
						final String headerLine = requireValue(arguments, i++, argument);
						final int separatorIndex = headerLine.indexOf(':');
						if (separatorIndex < 0) {
							throw new ParameterException(argument, "Header must be in the form 'Name: Value'");
						}
						cliHeaders.put(headerLine.substring(0, separatorIndex).trim(), headerLine.substring(separatorIndex + 1).trim());
						break;
					}
					case "--url-param": {
						final String paramLine = requireValue(arguments, i++, argument);
						final int separatorIndex = paramLine.indexOf('=');
						if (separatorIndex < 0) {
							throw new ParameterException(argument, "URL parameter must be in the form 'name=value'");
						}
						cliUrlParameters.put(paramLine.substring(0, separatorIndex), paramLine.substring(separatorIndex + 1));
						break;
					}
					case "--form-param": {
						final String paramLine = requireValue(arguments, i++, argument);
						final int separatorIndex = paramLine.indexOf('=');
						if (separatorIndex < 0) {
							throw new ParameterException(argument, "Form parameter must be in the form 'name=value'");
						}
						cliFormParameters.put(paramLine.substring(0, separatorIndex), paramLine.substring(separatorIndex + 1));
						break;
					}
					case "--body":
						cliRequestBody = requireValue(arguments, i++, argument);
						break;
					case "--body-file":
						cliBodyFile = requireValue(arguments, i++, argument);
						break;
					case "--proxy":
						cliProxy = requireValue(arguments, i++, argument);
						break;
					case "--max-redirects":
						try {
							cliMaxRedirects = Integer.parseInt(requireValue(arguments, i++, argument));
						} catch (@SuppressWarnings("unused") final NumberFormatException e) {
							throw new ParameterException(argument, "Must be a whole number");
						}
						break;
					case "--tls-check-type":
						cliTlsCheckType = requireValue(arguments, i++, argument);
						break;
					case "--tls-check-file":
						cliTlsCheckFile = requireValue(arguments, i++, argument);
						break;
					case "--tls-check-password":
						cliTlsCheckPassword = requireValue(arguments, i++, argument);
						break;
					case "--tls-check-cn":
						cliTlsCheckCn = Utilities.interpretAsBool(requireValue(arguments, i++, argument));
						break;
					case "--download-target":
						cliDownloadTarget = requireValue(arguments, i++, argument);
						break;
					case "--response-data-path":
						cliResponseDataPath = requireValue(arguments, i++, argument);
						break;
					case "--idp-url":
						cliIdpUrl = requireValue(arguments, i++, argument);
						break;
					case "--idp-realm":
						cliIdpRealm = requireValue(arguments, i++, argument);
						break;
					case "--idp-username":
						cliIdpUsername = requireValue(arguments, i++, argument);
						break;
					case "--idp-password":
						cliIdpPassword = requireValue(arguments, i++, argument);
						break;
					case "--preset":
						cliPresetName = requireValue(arguments, i++, argument);
						break;
					case "--request-file":
						cliRequestFile = requireValue(arguments, i++, argument);
						break;
					case "--list-presets":
						cliListPresets = true;
						break;
					case "--output":
						cliOutputFile = requireValue(arguments, i++, argument);
						break;
					case "--basic-auth":
						cliBasicAuth = requireValue(arguments, i++, argument);
						break;
					case "--bearer-token":
						cliBearerToken = requireValue(arguments, i++, argument);
						break;
					case "-v":
					case "--verbose":
						cliVerbose = true;
						break;
					case "--fail":
						cliFail = true;
						break;
					default:
						throw new ParameterException(argument, "Invalid parameter");
				}
			}

			if (openGui) {
				// Allows pinning the application to the Linux Gnome dock (needs "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
				de.soderer.utilities.swing.SwingUtilities.setAwtWmClass(APPLICATION_STARTUPCLASS_NAME);
				de.soderer.utilities.swing.SwingUtilities.setSystemLookAndFeel();

				final ConfigurationProperties guiApplicationConfiguration = applicationConfiguration;
				final int[] guiReturnCode = new int[] { -1 };
				javax.swing.SwingUtilities.invokeAndWait(() -> {
					try {
						final RestClientDialog mainDialog = new RestClientDialog(guiApplicationConfiguration);
						mainDialog.setVisible(true);
					} catch (final Exception ex) {
						guiReturnCode[0] = 1;
						try {
							new ErrorDialog(null, RestClient.APPLICATION_NAME, RestClient.VERSION.toString(), RestClient.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
						} catch (final Exception dialogException) {
							System.out.println(ex.toString());
							ex.printStackTrace();
							dialogException.printStackTrace();
						}
					}
				});

				// -1: The GUI keeps running on the Swing event dispatch thread, so main() must not call System.exit()
				return guiReturnCode[0];
			} else {
				LangResources.enforceDefaultLocale();

				if (cliListPresets) {
					printPresetNames();
					return 0;
				}

				// Values start out as request defaults, then get overwritten by a loaded preset (if any),
				// then get overwritten again by whatever was explicitly given on the command line - same
				// precedence as the GUI, where a loaded preset fills the form and the user can still edit
				// individual fields afterwards.
				String httpMethod = "GET";
				String serviceUrl = null;
				final Map<String, String> httpHeaders = new LinkedHashMap<>();
				final Map<String, String> urlParameters = new LinkedHashMap<>();
				final Map<String, String> htmlFormParameters = new LinkedHashMap<>();
				String requestBody = null;
				int maxRedirects = 0;
				String proxyUrl = null;
				TlsCheckConfiguration tlsCheckConfiguration = new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true);
				String downloadTarget = null;
				String responseDataPath = null;
				String idpUrl = null;
				String idpRealm = null;
				String idpUsername = null;
				String idpPassword = null;

				if (cliPresetName != null) {
					if (!REQUEST_PRESETS_FILE.exists()) {
						throw new ParameterException("preset", "No request presets file found");
					}
					final JsonObject requestPresetsJsonObject;
					try (JsonReader reader = new JsonReader(new FileInputStream(REQUEST_PRESETS_FILE))) {
						requestPresetsJsonObject = (JsonObject) reader.read();
					}
					if (!requestPresetsJsonObject.containsKey(cliPresetName)) {
						throw new ParameterException("preset", "Unknown preset '" + cliPresetName + "'");
					}
					final JsonObject presetJsonObject = (JsonObject) requestPresetsJsonObject.get(cliPresetName);

					proxyUrl = (String) presetJsonObject.getSimpleValue("proxyUrl");
					final Object maxRedirectsObject = presetJsonObject.getSimpleValue("maxRedirects");
					maxRedirects = maxRedirectsObject == null ? 0 : ((Number) maxRedirectsObject).intValue();
					httpMethod = (String) presetJsonObject.getSimpleValue("httpMethod");
					serviceUrl = (String) presetJsonObject.getSimpleValue("serviceUrl");
					final String serviceMethod = (String) presetJsonObject.getSimpleValue("serviceMethod");
					if (Utilities.isNotBlank(serviceMethod)) {
						serviceUrl = (serviceUrl == null ? "" : serviceUrl) + "/" + serviceMethod;
					}

					if (presetJsonObject.containsKey("tlsCheck")) {
						final JsonObject tlsCheckJsonObject = (JsonObject) presetJsonObject.get("tlsCheck");
						try {
							final TlsCheckConfigurationType type = TlsCheckConfigurationType.getTlsCheckConfigurationByName((String) tlsCheckJsonObject.getSimpleValue("type"));
							final String filePath = (String) tlsCheckJsonObject.getSimpleValue("file");
							final String trustorePassword = (String) tlsCheckJsonObject.getSimpleValue("trustorePassword");
							final boolean checkCn = tlsCheckJsonObject.containsKey("checkCn") ? (Boolean) tlsCheckJsonObject.getSimpleValue("checkCn") : type != TlsCheckConfigurationType.NoCheck;
							tlsCheckConfiguration = new TlsCheckConfiguration(type, filePath == null ? null : new File(filePath), trustorePassword == null ? null : trustorePassword.toCharArray(), checkCn);
						} catch (@SuppressWarnings("unused") final Exception e) {
							tlsCheckConfiguration = new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true);
						}
					}

					if (presetJsonObject.containsKey("httpRequestHeaders")) {
						for (final JsonNode item : ((JsonArray) presetJsonObject.get("httpRequestHeaders")).items()) {
							final JsonObject headerJsonObject = (JsonObject) item;
							httpHeaders.put((String) headerJsonObject.getSimpleValue("name"), (String) headerJsonObject.getSimpleValue("value"));
						}
					}
					if (presetJsonObject.containsKey("urlParameters")) {
						for (final JsonNode item : ((JsonArray) presetJsonObject.get("urlParameters")).items()) {
							final JsonObject paramJsonObject = (JsonObject) item;
							urlParameters.put((String) paramJsonObject.getSimpleValue("name"), (String) paramJsonObject.getSimpleValue("value"));
						}
					}
					if (presetJsonObject.containsKey("htmlFormParameters")) {
						for (final JsonNode item : ((JsonArray) presetJsonObject.get("htmlFormParameters")).items()) {
							final JsonObject paramJsonObject = (JsonObject) item;
							htmlFormParameters.put((String) paramJsonObject.getSimpleValue("name"), (String) paramJsonObject.getSimpleValue("value"));
						}
					}

					requestBody = (String) presetJsonObject.getSimpleValue("requestBody");
					downloadTarget = (String) presetJsonObject.getSimpleValue("downloadTarget");
					responseDataPath = (String) presetJsonObject.getSimpleValue("responseDataPath");

					idpUrl = (String) presetJsonObject.getSimpleValue("idpUrl");
					idpRealm = (String) presetJsonObject.getSimpleValue("idpRealm");
					idpUsername = (String) presetJsonObject.getSimpleValue("idpUsername");
					idpPassword = (String) presetJsonObject.getSimpleValue("idpPassword");

					if (Utilities.isBlank(httpMethod)) {
						httpMethod = "GET";
					}
				}

				if (cliRequestFile != null) {
					if (cliPresetName != null) {
						throw new ParameterException("request-file", "Cannot use --preset and --request-file together");
					}

					final YamlDocument yamlDocument = YamlReader.readDocument(Files.readString(Path.of(cliRequestFile), StandardCharsets.UTF_8));
					final YamlMapping rootYamlMapping = (YamlMapping) yamlDocument.getRoot();

					if (rootYamlMapping.containsKey("request")) {
						final YamlMapping requestYamlMapping = (YamlMapping) rootYamlMapping.get("request");

						proxyUrl = (String) requestYamlMapping.getSimpleValue("proxyUrl");
						final Object maxRedirectsObject = requestYamlMapping.getSimpleValue("maxRedirects");
						maxRedirects = maxRedirectsObject == null ? 0 : ((Number) maxRedirectsObject).intValue();
						httpMethod = (String) requestYamlMapping.getSimpleValue("httpMethod");
						serviceUrl = (String) requestYamlMapping.getSimpleValue("serviceUrl");
						final String serviceMethod = (String) requestYamlMapping.getSimpleValue("serviceMethod");
						if (Utilities.isNotBlank(serviceMethod)) {
							serviceUrl = (serviceUrl == null ? "" : serviceUrl) + "/" + serviceMethod;
						}

						if (requestYamlMapping.containsKey("tlsCheck")) {
							final YamlMapping tlsCheckYamlMapping = (YamlMapping) requestYamlMapping.get("tlsCheck");
							try {
								final TlsCheckConfigurationType type = TlsCheckConfigurationType.getTlsCheckConfigurationByName((String) tlsCheckYamlMapping.getSimpleValue("type"));
								final String filePath = (String) tlsCheckYamlMapping.getSimpleValue("file");
								final String trustorePassword = (String) tlsCheckYamlMapping.getSimpleValue("trustorePassword");
								final boolean checkCn = tlsCheckYamlMapping.containsKey("checkCn") ? (Boolean) tlsCheckYamlMapping.getSimpleValue("checkCn") : type != TlsCheckConfigurationType.NoCheck;
								tlsCheckConfiguration = new TlsCheckConfiguration(type, filePath == null ? null : new File(filePath), trustorePassword == null ? null : trustorePassword.toCharArray(), checkCn);
							} catch (@SuppressWarnings("unused") final Exception e) {
								tlsCheckConfiguration = new TlsCheckConfiguration(TlsCheckConfigurationType.SystemTrustStore, true);
							}
						}

						if (requestYamlMapping.containsKey("httpRequestHeaders")) {
							for (final YamlNode item : ((YamlSequence) requestYamlMapping.get("httpRequestHeaders")).items()) {
								final YamlMapping headerYamlMapping = (YamlMapping) item;
								httpHeaders.put((String) headerYamlMapping.getSimpleValue("name"), (String) headerYamlMapping.getSimpleValue("value"));
							}
						}
						if (requestYamlMapping.containsKey("urlParameters")) {
							for (final YamlNode item : ((YamlSequence) requestYamlMapping.get("urlParameters")).items()) {
								final YamlMapping paramYamlMapping = (YamlMapping) item;
								urlParameters.put((String) paramYamlMapping.getSimpleValue("name"), (String) paramYamlMapping.getSimpleValue("value"));
							}
						}
						if (requestYamlMapping.containsKey("htmlFormParameters")) {
							for (final YamlNode item : ((YamlSequence) requestYamlMapping.get("htmlFormParameters")).items()) {
								final YamlMapping paramYamlMapping = (YamlMapping) item;
								htmlFormParameters.put((String) paramYamlMapping.getSimpleValue("name"), (String) paramYamlMapping.getSimpleValue("value"));
							}
						}

						requestBody = (String) requestYamlMapping.getSimpleValue("requestBody");

						idpUrl = (String) requestYamlMapping.getSimpleValue("idpUrl");
						idpRealm = (String) requestYamlMapping.getSimpleValue("idpRealm");
						idpUsername = (String) requestYamlMapping.getSimpleValue("idpUsername");
						idpPassword = (String) requestYamlMapping.getSimpleValue("idpPassword");

						if (Utilities.isBlank(httpMethod)) {
							httpMethod = "GET";
						}
					}

					// The "response" section of an export file is mostly a snapshot of a previous
					// response (httpCode/time/headers/body) and not relevant to re-executing the
					// request - only the two settings that affect how the request/response is
					// handled are picked up here, same as with a loaded preset.
					if (rootYamlMapping.containsKey("response")) {
						final YamlMapping responseYamlMapping = (YamlMapping) rootYamlMapping.get("response");
						downloadTarget = (String) responseYamlMapping.getSimpleValue("downloadTarget");
						responseDataPath = (String) responseYamlMapping.getSimpleValue("responseDataPath");
					}
				}

				// Command line values override whatever a loaded preset set (or the defaults, if no preset was given)
				if (cliUrl != null) {
					serviceUrl = cliUrl;
				}
				if (cliMethod != null) {
					httpMethod = cliMethod;
				}
				// Checked up front (also for methods from a preset or request file), since HttpURLConnection
				// would only reject an unsupported method like PATCH or CONNECT when sending the request
				if (!CLI_HTTP_METHODS.contains(httpMethod.trim().toUpperCase(Locale.ROOT))) {
					throw new ParameterException("method", "Unsupported HTTP method '" + httpMethod + "' (supported: " + String.join(", ", CLI_HTTP_METHODS) + ")");
				}
				httpHeaders.putAll(cliHeaders);
				urlParameters.putAll(cliUrlParameters);
				htmlFormParameters.putAll(cliFormParameters);
				if (cliBodyFile != null) {
					if (cliRequestBody != null) {
						throw new ParameterException("body", "Cannot use --body and --body-file together");
					}
					requestBody = Files.readString(Path.of(cliBodyFile), StandardCharsets.UTF_8);
				} else if (cliRequestBody != null) {
					requestBody = cliRequestBody;
				}
				if (cliProxy != null) {
					proxyUrl = cliProxy;
				}
				if (cliMaxRedirects != null) {
					maxRedirects = cliMaxRedirects;
				}
				if (cliTlsCheckType != null) {
					final TlsCheckConfigurationType type = TlsCheckConfigurationType.getTlsCheckConfigurationByName(cliTlsCheckType);
					final boolean checkCn = cliTlsCheckCn != null ? cliTlsCheckCn : type != TlsCheckConfigurationType.NoCheck;
					tlsCheckConfiguration = new TlsCheckConfiguration(
							type,
							cliTlsCheckFile == null ? null : new File(cliTlsCheckFile),
							cliTlsCheckPassword == null ? null : cliTlsCheckPassword.toCharArray(),
							checkCn);
				}
				if (cliDownloadTarget != null) {
					downloadTarget = cliDownloadTarget;
				}
				if (cliResponseDataPath != null) {
					responseDataPath = cliResponseDataPath;
				}
				if (cliIdpUrl != null) {
					idpUrl = cliIdpUrl;
				}
				if (cliIdpRealm != null) {
					idpRealm = cliIdpRealm;
				}
				if (cliIdpUsername != null) {
					idpUsername = cliIdpUsername;
				}
				if (cliIdpPassword != null) {
					idpPassword = cliIdpPassword;
				}
				if (cliBasicAuth != null) {
					final int separatorIndex = cliBasicAuth.indexOf(':');
					if (separatorIndex < 0) {
						throw new ParameterException("basic-auth", "Must be in the form 'username:password'");
					}
					httpHeaders.put(HttpConstants.HTTPHEADERNAME_AUTHORIZATION,
							HttpUtilities.createBasicAuthenticationHeaderValue(cliBasicAuth.substring(0, separatorIndex), cliBasicAuth.substring(separatorIndex + 1)));
				}
				if (cliBearerToken != null) {
					httpHeaders.put(HttpConstants.HTTPHEADERNAME_AUTHORIZATION, HttpConstants.AUTHORIZATIONHEADER_START_BEARER + " " + cliBearerToken);
				}

				if (Utilities.isBlank(serviceUrl)) {
					if (arguments.length == 0) {
						// Nothing was requested at all - usage was already printed above (headless, no arguments)
						return 1;
					} else {
						throw new ParameterException("url", "Missing request URL (use --url or --preset)");
					}
				}

				// IdP/OAuth login: acquires a bearer token and adds it as an Authorization header, same
				// as the "Fetch IdP token" button in the GUI (see RequestComponent) - but done automatically
				// here, since there is no interactive step in CLI mode.
				if (Utilities.isNotBlank(idpUrl)) {
					final ProxyConfiguration idpProxyConfiguration = buildIdpProxyConfiguration(proxyUrl);
					final String idpToken;
					if (idpUrl != null && idpUrl.endsWith("/token")) {
						idpToken = IdpHelper.aquireAccessToken(idpUrl, idpUsername, idpPassword, null, idpProxyConfiguration);
					} else {
						final String idpTokenEndpointUrl = IdpHelper.getIdpTokenEdpointUrl(idpUrl, idpRealm, idpProxyConfiguration);
						idpToken = IdpHelper.aquireAccessToken(idpTokenEndpointUrl, idpUsername, idpPassword, null, idpProxyConfiguration);
					}
					if (idpToken != null) {
						httpHeaders.put(HttpConstants.HTTPHEADERNAME_AUTHORIZATION, HttpConstants.AUTHORIZATIONHEADER_START_BEARER + " " + idpToken);
					}
				}

				final HttpRequest httpRequest = new HttpRequest(HttpMethod.getHttpMethodByName(httpMethod), serviceUrl);
				httpRequest.setMaxRedirects(maxRedirects);

				if (Utilities.isNotBlank(downloadTarget)) {
					httpRequest.setDownloadTarget(new File(downloadTarget));
				}

				for (final Entry<String, String> entry : httpHeaders.entrySet()) {
					httpRequest.addHeader(entry.getKey(), entry.getValue());
				}
				for (final Entry<String, String> entry : urlParameters.entrySet()) {
					httpRequest.addUrlParameter(entry.getKey(), entry.getValue());
				}
				for (final Entry<String, String> entry : htmlFormParameters.entrySet()) {
					httpRequest.addPostParameter(entry.getKey(), entry.getValue());
				}

				// Same restriction as the GUI (see RestClientDialog.executeRequest): an explicit body is
				// only used for POST/PUT, and only if there are no form parameters (which would become the
				// body instead)
				if (("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod))
						&& htmlFormParameters.isEmpty() && Utilities.isNotBlank(requestBody)) {
					httpRequest.setRequestBody(requestBody);
				}

				final Proxy proxy = resolveProxy(proxyUrl, httpRequest.getUrl());

				// Passing null here (no progress reporting), same as RestClientDialog.executeRequest()
				// does for the GUI - a single one-shot CLI request needs no progress bar, and printing
				// one would pollute stdout for scripted/piped usage anyway.
				final ExecuteHttpRequestWorker worker = new ExecuteHttpRequestWorker(null, httpRequest, proxy, tlsCheckConfiguration.getTrustManager(), !tlsCheckConfiguration.getCheckCn());
				worker.run();
				final HttpResponse httpResponse;
				try {
					httpResponse = worker.get();
				} catch (final ExecutionException e) {
					throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
				}

				if (cliVerbose) {
					System.err.println("HTTP " + httpResponse.getHttpCode());
					if (httpResponse.getHeaders() != null) {
						for (final Entry<String, String> headerEntry : httpResponse.getHeaders().entrySet()) {
							System.err.println(headerEntry.getKey() + ": " + headerEntry.getValue());
						}
					}
					System.err.println();
				}

				final String displayBody = renderResponseBody(httpResponse, responseDataPath);

				if (Utilities.isNotBlank(cliOutputFile)) {
					Files.writeString(Path.of(cliOutputFile), displayBody != null ? displayBody : "", StandardCharsets.UTF_8);
				} else {
					System.out.println(displayBody != null ? displayBody : "");
				}

				if (httpResponse.getDownloadedFilePath() != null) {
					System.out.println("File downloaded to '" + httpResponse.getDownloadedFilePath() + "'");
				}

				// Matches curl's default behaviour: exit 0 means the request itself was carried out
				// (a response was received), independent of the HTTP status code it returned -
				// unless --fail was given, in which case a 4xx/5xx status code itself is treated
				// as a failure (also matching curl's --fail).
				if (cliFail && httpResponse.getHttpCode() >= 400) {
					System.err.println("Request failed with HTTP " + httpResponse.getHttpCode());
					return 1;
				}
				return 0;
			}
		} catch (final ParameterException e) {
			System.err.println(e.getMessage());
			System.err.println();
			System.err.println(getUsageMessage());
			return 1;
		} catch (final Exception e) {
			// Some exceptions (e.g. NullPointerException) carry no message, which would print just "null"
			System.err.println(e.getMessage() != null ? e.getMessage() : e.toString());
			return 1;
		}
	}

	/**
	 * Checks whether an argument is one of the keywords requesting the help text
	 * ("help", "-help", "--help", "-h", "--h", "-?", "--?", ignoring case).
	 */
	private static boolean isHelpKeyword(final String argument) {
		return "help".equalsIgnoreCase(argument) || "-help".equalsIgnoreCase(argument) || "--help".equalsIgnoreCase(argument)
				|| "-h".equalsIgnoreCase(argument) || "--h".equalsIgnoreCase(argument)
				|| "-?".equalsIgnoreCase(argument) || "--?".equalsIgnoreCase(argument);
	}

	/**
	 * Returns the argument following {@code arguments[index]}, or throws if {@code arguments[index]}
	 * is the last argument (no value follows the flag). {@code flagName} is the flag as given on the
	 * command line (e.g. "--url"), used only for the exception message.
	 */
	private static String requireValue(final String[] arguments, final int index, final String flagName) throws ParameterException {
		if (index + 1 >= arguments.length) {
			throw new ParameterException(flagName, "Missing value for parameter");
		}
		return arguments[index + 1];
	}

	private static void printPresetNames() throws Exception {
		if (!REQUEST_PRESETS_FILE.exists()) {
			System.out.println("No presets found");
			return;
		}
		final JsonObject requestPresetsJsonObject;
		try (JsonReader reader = new JsonReader(new FileInputStream(REQUEST_PRESETS_FILE))) {
			requestPresetsJsonObject = (JsonObject) reader.read();
		}
		for (final Object presetName : requestPresetsJsonObject.keySet()) {
			System.out.println(presetName);
		}
	}

	/**
	 * Resolves the request proxy the same way {@link RestClientDialog#executeRequest()} does:
	 * "DIRECT" bypasses any proxy, "WPAD" autodetects one, anything else is parsed as a literal
	 * proxy URL (or left as no proxy if blank).
	 */
	private static Proxy resolveProxy(final String proxyUrl, final String url) throws Exception {
		if (Utilities.isBlank(proxyUrl)) {
			return null;
		} else if ("DIRECT".equalsIgnoreCase(proxyUrl)) {
			return Proxy.NO_PROXY;
		} else if ("WPAD".equalsIgnoreCase(proxyUrl)) {
			final ProxyConfiguration wpadProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.WPAD);
			return wpadProxyConfiguration.getProxy(url);
		} else {
			return HttpUtilities.getProxyFromString(proxyUrl);
		}
	}

	/**
	 * Builds the {@link ProxyConfiguration} used for the separate IdP token request, the same way
	 * {@link de.soderer.restclient.dlg.RequestComponent}'s "Fetch IdP token" button does.
	 */
	private static ProxyConfiguration buildIdpProxyConfiguration(final String proxyUrl) {
		if (Utilities.isBlank(proxyUrl) || "DIRECT".equalsIgnoreCase(proxyUrl)) {
			return new ProxyConfiguration(ProxyConfigurationType.None);
		} else if ("WPAD".equalsIgnoreCase(proxyUrl)) {
			return new ProxyConfiguration(ProxyConfigurationType.WPAD);
		} else {
			return new ProxyConfiguration(ProxyConfigurationType.ProxyURL, proxyUrl);
		}
	}

	/**
	 * Renders the response body for CLI output the same way {@link de.soderer.restclient.dlg.ResponseComponent}
	 * renders it for the GUI (see there for the full behaviour by content type): JSON is always
	 * pretty-printed and, if {@code responseDataPath} is set, narrowed down via
	 * {@link ResponseDataPathEvaluator#evaluateJsonPath(JsonNode, String)} (a plain JsonPath shows
	 * only the matching part, a wildcard/filter path shows all matches as a JSON array); YAML/XML
	 * are only touched (evaluated as the same path syntax / as XPath respectively) if a path is set,
	 * otherwise shown unchanged. Parse/path errors are reported on stderr and fall back to the raw body.
	 */
	private static String renderResponseBody(final HttpResponse httpResponse, final String responseDataPath) {
		final String body = httpResponse.getContent();
		final String contentType = new CaseInsensitiveMap<>(httpResponse.getHeaders() == null ? new LinkedHashMap<String, String>() : httpResponse.getHeaders()).get(HttpConstants.HTTPHEADERNAME_CONTENTTYPE);

		if (body != null && contentType != null && ResponseDataPathEvaluator.isContentType(contentType, HttpContentType.Json, HttpContentType.TextJson)) {
			try {
				final JsonNode jsonRootNode = JsonReader.readJsonItemString(body);
				return ResponseDataPathEvaluator.evaluateJsonPath(jsonRootNode, responseDataPath);
			} catch (final Exception e) {
				System.err.println("JsonParserError: " + e.getMessage());
				return body;
			}
		} else if (body != null && Utilities.isNotBlank(responseDataPath) && contentType != null && ResponseDataPathEvaluator.isContentType(contentType, HttpContentType.Yaml, HttpContentType.TextYaml)) {
			try {
				final YamlDocument yamlDocument = YamlReader.readDocument(body);
				final YamlNode yamlDataNode = ResponseDataPathEvaluator.getYamlNodeByPath(yamlDocument.getRoot(), new JsonPath(responseDataPath));
				return ResponseDataPathEvaluator.yamlNodeToDisplayString(yamlDataNode);
			} catch (final Exception e) {
				System.err.println("YamlParserError: " + e.getMessage());
				return body;
			}
		} else if (body != null && Utilities.isNotBlank(responseDataPath) && contentType != null && ResponseDataPathEvaluator.isContentType(contentType, HttpContentType.Xml, HttpContentType.TextXml)) {
			try {
				return ResponseDataPathEvaluator.evaluateXPath(body, responseDataPath);
			} catch (final Exception e) {
				System.err.println("XPathError: " + e.getMessage());
				return body;
			}
		} else {
			return body;
		}
	}

	/**
	 * Creates the console application instance, used as worker parent for console tasks like the update.
	 *
	 * @throws Exception if the application base class cannot be initialized
	 */
	public RestClient() throws Exception {
		super(APPLICATION_NAME, VERSION);
	}

	/**
	 * Ignored, there is no console output for a progress without a known end.
	 */
	@Override
	public void receiveUnlimitedProgressSignal() {
		// Do nothing
	}

	/**
	 * Prints the progress bar on the console.
	 *
	 * @param start start time of the task
	 * @param itemsToDo total number of items to do
	 * @param itemsDone number of items done so far
	 * @param itemsUnitSign unit sign of the items (e.g. "B" for bytes), or null for plain item counts
	 */
	@Override
	public void receiveProgressSignal(final LocalDateTime start, final long itemsToDo, final long itemsDone, final String itemsUnitSign) {
		printProgressBar(start, itemsToDo, itemsDone, itemsUnitSign);
	}

	/**
	 * Prints the result text of the finished task on the console.
	 *
	 * @param start start time of the task
	 * @param end end time of the task
	 * @param itemsDone number of items done
	 * @param itemsUnitSign unit sign of the items, or null for plain item counts
	 * @param resultText result text of the task, may be null
	 */
	@Override
	public void receiveDoneSignal(final LocalDateTime start, final LocalDateTime end, final long itemsDone, final String itemsUnitSign, final String resultText) {
		@SuppressWarnings("unused")
		int currentTerminalWidth;
		try {
			currentTerminalWidth = ConsoleUtilities.getTerminalSize().getWidth();
		} catch (@SuppressWarnings("unused") final Exception e) {
			currentTerminalWidth = 80;
		}

		if (Utilities.isNotBlank(resultText)) {
			System.out.println("Result: \n" + resultText);
		}

		System.out.println();
		System.out.println();
	}

	/**
	 * Prints "Canceled" on the console.
	 *
	 * @return always true
	 */
	@Override
	public boolean cancel() {
		System.out.println("Canceled");
		return true;
	}

	/**
	 * Prints the new title of the current task on the console.
	 *
	 * @param text the title text
	 */
	@Override
	public void changeTitle(final String text) {
		System.out.println(text);
	}

	/**
	 * Ignored, there is no console output for a sub progress without a known end.
	 */
	@Override
	public void receiveUnlimitedSubProgressSignal() {
		// Do nothing
	}

	/**
	 * Prints the description of the started item on the console.
	 *
	 * @param itemName name of the started item
	 * @param description description of the started item
	 */
	@Override
	public void receiveItemStartSignal(final String itemName, final String description) {
		System.out.println(description);
	}

	/**
	 * Prints the progress bar of the current item on the console.
	 *
	 * @param itemStart start time of the item
	 * @param subItemToDo total number of sub items to do
	 * @param subItemDone number of sub items done so far
	 * @param itemsUnitSign unit sign of the sub items (e.g. "B" for bytes), or null for plain item counts
	 */
	@Override
	public void receiveItemProgressSignal(final LocalDateTime itemStart, final long subItemToDo, final long subItemDone, final String itemsUnitSign) {
		printProgressBar(itemStart, subItemToDo, subItemDone, itemsUnitSign);
	}

	private static void printProgressBar(final LocalDateTime itemStart, final long subItemToDo, final long subItemDone, final String itemsUnitSign) {
		try {
			if (ConsoleUtilities.getConsoleType() == ConsoleType.ANSI) {
				int currentTerminalWidth;
				try {
					currentTerminalWidth = ConsoleUtilities.getTerminalSize().getWidth();
				} catch (@SuppressWarnings("unused") final Exception e) {
					currentTerminalWidth = 80;
				}

				ConsoleUtilities.saveCurrentCursorPosition();

				ConsoleUtilities.moveCursorToSavedPosition();

				System.out.print(ConsoleUtilities.getConsoleProgressString(currentTerminalWidth - 1, itemStart, subItemToDo, subItemDone, itemsUnitSign));

				ConsoleUtilities.moveCursorToSavedPosition();
			} else if (ConsoleUtilities.getConsoleType() == ConsoleType.TEST) {
				System.out.print(ConsoleUtilities.getConsoleProgressString(80 - 1, itemStart, subItemToDo, subItemDone, itemsUnitSign) + "\n");
			} else {
				System.out.print("\r" + ConsoleUtilities.getConsoleProgressString(80 - 1, itemStart, subItemToDo, subItemDone, itemsUnitSign) + "\r");
			}
		} catch (final Throwable e) {
			// Do nothing => no progress bar
			e.printStackTrace();
		}
	}

	/**
	 * Prints the final progress bar, the duration and the result text of the finished item on the console.
	 *
	 * @param itemStart start time of the item
	 * @param itemEnd end time of the item
	 * @param subItemsDone number of sub items done
	 * @param itemsUnitSign unit sign of the sub items, or null for plain item counts
	 * @param resultText result text of the item, may be null
	 */
	@Override
	public void receiveItemDoneSignal(final LocalDateTime itemStart, final LocalDateTime itemEnd, final long subItemsDone, final String itemsUnitSign, final String resultText) {
		if (subItemsDone > 0) {
			printProgressBar(itemStart, subItemsDone, subItemsDone, itemsUnitSign);
		}
		System.out.println();
		if (itemsUnitSign != null) {
			System.out.println("End (" + Utilities.getHumanReadableNumber(subItemsDone, itemsUnitSign, true, 5, true, Locale.ENGLISH) + " done in " + DateUtilities.getHumanReadableTimespanEnglish(Duration.between(itemStart, itemEnd), true) + ")");
		} else {
			System.out.println("End (" + NumberFormat.getNumberInstance(Locale.ENGLISH).format(subItemsDone) + " data items done in " + DateUtilities.getHumanReadableTimespanEnglish(Duration.between(itemStart, itemEnd), true) + ")");
		}

		if (Utilities.isNotBlank(resultText)) {
			System.out.println("Result: \n" + resultText);
		}

		System.out.println();
	}
}
