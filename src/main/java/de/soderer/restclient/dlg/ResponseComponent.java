package de.soderer.restclient.dlg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import de.soderer.json.JsonNode;
import de.soderer.json.JsonReader;
import de.soderer.json.path.JsonPath;
import de.soderer.network.HttpConstants;
import de.soderer.network.HttpContentType;
import de.soderer.restclient.helper.ResponseDataPathEvaluator;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.collection.CaseInsensitiveMap;
import de.soderer.yaml.YamlReader;
import de.soderer.yaml.data.YamlDocument;
import de.soderer.yaml.data.YamlNode;

/**
 * Response view of the main window: HTTP code, IP address, duration, redirect hint, response headers and
 * response body, plus the response content data path, the download target and the resolved random parameters.
 *
 * <p>
 * The response body is rendered depending on its Content-Type (pretty-printed JSON, optionally narrowed down
 * by the data path, see {@link ResponseDataPathEvaluator}), and re-rendered whenever the data path is edited.
 * </p>
 *
 * @serial exclude
 */
public class ResponseComponent extends JPanel {
	private static final long serialVersionUID = -3418745931227019654L;

	private static final int KEY_FIELD_WIDTH = 150;
	private static final int LIST_HEIGHT = 75;
	private static final Color REDIRECT_WARNING_COLOR = new Color(170, 0, 0);

	private JTextField ipAddressText;
	private JTextField httpCodeText;
	private JTextField timeText;
	private WrappingLabel redirectHintLabel;
	private ViewportWidthPanel headerContainer;
	private JTextArea responseBodyText;
	private HintTextField responseDataPathText;
	private HintTextField downloadTargetText;
	private JLabel randomParamsLabel;
	private ViewportWidthPanel randomParamsContainer;
	private JScrollPane randomParamsScrolled;

	/** Response headers as currently shown, in display order */
	private Map<String, String> responseHeaders = new LinkedHashMap<>();

	/** Parts hidden by {@link #clearResponse()} and shown again by {@link #showResponse()} */
	private final List<JComponent> responseDisplayComponents = new ArrayList<>();

	/**
	 * Body of the last received response, kept so {@link #refreshResponseBodyDisplay()} can
	 * re-evaluate {@link #responseDataPathText} against it whenever the user edits the path,
	 * without needing to resend the request.
	 */
	private String lastResponseBody;

	/**
	 * Creates the response view.
	 */
	public ResponseComponent() {
		super(new GridBagLayout());
		createUI();
	}

	/**
	 * Sets the HTTP status code.
	 *
	 * @param code the status code, or null to clear the field
	 */
	public void setHttpCode(final Integer code) {
		httpCodeText.setText(code != null ? code.toString() : "");
	}

	/**
	 * Sets the IP address of the server.
	 *
	 * @param ipAddress the IP address, or null to clear the field
	 */
	public void setIpAddress(final String ipAddress) {
		ipAddressText.setText(ipAddress != null ? ipAddress : "");
	}

	/**
	 * Sets the request duration text.
	 *
	 * @param duration human readable duration, or null to clear the field
	 */
	public void setTime(final String duration) {
		timeText.setText(duration != null ? duration : "");
	}

	/**
	 * Shows a hint above the response headers if the request followed one or more redirects, since that
	 * happens silently otherwise (the response shown may come from a completely different URL than requested).
	 * If credentials (Authorization header and/or cookies) were withheld while following a cross-origin
	 * redirect (see {@link de.soderer.network.HttpUtilities}), this is additionally called out in a warning color.
	 *
	 * @param redirectCount number of redirects followed, 0 hides the hint
	 * @param finalUrl URL the response actually came from
	 * @param credentialsDroppedOnRedirect true if credentials were withheld on a cross-origin redirect
	 */
	public void setRedirectInfo(final int redirectCount, final String finalUrl, final boolean credentialsDroppedOnRedirect) {
		final boolean hasRedirectInfo = redirectCount > 0;
		if (hasRedirectInfo) {
			String hintText = LangResources.get("redirectFollowedHint", redirectCount, finalUrl);
			if (credentialsDroppedOnRedirect) {
				hintText += " " + LangResources.get("redirectCredentialsDroppedHint");
			}
			redirectHintLabel.setText(hintText);
			redirectHintLabel.setForeground(credentialsDroppedOnRedirect ? REDIRECT_WARNING_COLOR : UIManager.getColor("Label.foreground"));
		} else {
			redirectHintLabel.setText("");
		}
		redirectHintLabel.setVisible(hasRedirectInfo);
		revalidate();
		repaint();
	}

	/**
	 * Sets the response body and renders it depending on the Content-Type of the current response headers
	 * and the current data path. So the headers should be set before the body.
	 *
	 * @param body the raw response body, may be null
	 */
	public void setResponseBody(final String body) {
		lastResponseBody = body;
		refreshResponseBodyDisplay();
	}

	/**
	 * Re-renders the response body text using the last received body ({@link #lastResponseBody}),
	 * the current response content type and the currently entered {@link #responseDataPathText}.
	 * Called both when a new response arrives and whenever the data path field is edited, so a
	 * path can be tried out against an already received response without resending the request.
	 *
	 * Behaviour by content type:
	 * <ul>
	 *   <li>JSON: always pretty-printed; if a data path is set, it is evaluated via
	 *       {@link ResponseDataPathEvaluator#evaluateJsonPath(JsonNode, String)} - a plain JsonPath
	 *       shows only the matching part, a path using the wildcard/filter extensions
	 *       (e.g. {@code $.*[?(@.version=='1.0')]}) shows all matches as a JSON array</li>
	 *   <li>YAML: shown unchanged unless a data path is set, in which case it is evaluated using
	 *       the same dot/bracket path syntax as JSON (see {@link ResponseDataPathEvaluator#getYamlNodeByPath(YamlNode, JsonPath)})</li>
	 *   <li>XML: shown unchanged unless a data path is set, in which case it is evaluated as XPath</li>
	 *   <li>anything else, or no content type known: shown unchanged</li>
	 * </ul>
	 */
	private void refreshResponseBodyDisplay() {
		final String body = lastResponseBody;
		final String contentType = new CaseInsensitiveMap<>(getResponseHeaders()).get(HttpConstants.HTTPHEADERNAME_CONTENTTYPE);
		final String dataPath = responseDataPathText.getText();

		String displayText;
		if (body != null && contentType != null && ResponseDataPathEvaluator.isContentType(contentType, HttpContentType.Json, HttpContentType.TextJson)) {
			try {
				final JsonNode jsonRootNode = JsonReader.readJsonItemString(body);
				displayText = ResponseDataPathEvaluator.evaluateJsonPath(jsonRootNode, dataPath);
			} catch (final Exception e) {
				displayText = "RestClient JsonParserError: \n" + e.getMessage() + "\n\n" + body;
			}
		} else if (body != null && Utilities.isNotBlank(dataPath) && contentType != null && ResponseDataPathEvaluator.isContentType(contentType, HttpContentType.Yaml, HttpContentType.TextYaml)) {
			try {
				final YamlDocument yamlDocument = YamlReader.readDocument(body);
				final YamlNode yamlDataNode = ResponseDataPathEvaluator.getYamlNodeByPath(yamlDocument.getRoot(), new JsonPath(dataPath));
				displayText = ResponseDataPathEvaluator.yamlNodeToDisplayString(yamlDataNode);
			} catch (final Exception e) {
				displayText = "RestClient YamlParserError: \n" + e.getMessage() + "\n\n" + body;
			}
		} else if (body != null && Utilities.isNotBlank(dataPath) && contentType != null && ResponseDataPathEvaluator.isContentType(contentType, HttpContentType.Xml, HttpContentType.TextXml)) {
			try {
				displayText = ResponseDataPathEvaluator.evaluateXPath(body, dataPath);
			} catch (final Exception e) {
				displayText = "RestClient XPathError: \n" + e.getMessage() + "\n\n" + body;
			}
		} else {
			displayText = body;
		}

		responseBodyText.setText(displayText != null ? displayText : "");
		responseBodyText.setCaretPosition(0);
	}

	/**
	 * Sets the response headers. Headers without a name are skipped.
	 *
	 * @param headers header name to value, or null for none
	 */
	public void setResponseHeaders(final Map<String, String> headers) {
		responseHeaders = new LinkedHashMap<>();
		if (headers != null) {
			for (final Map.Entry<String, String> entry : headers.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isBlank()) {
					responseHeaders.put(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
				}
			}
		}
		fillKeyValueRows(headerContainer, responseHeaders.entrySet().stream().map(entry -> new String[] { entry.getKey(), entry.getValue() }).toList());
	}

	/**
	 * Returns the HTTP status code.
	 *
	 * @return the status code, or null if none is shown
	 */
	public Integer getHttpCode() {
		return Utilities.isNotBlank(httpCodeText.getText()) ? Integer.parseInt(httpCodeText.getText()) : null;
	}

	/**
	 * Returns the IP address of the server.
	 *
	 * @return the IP address, empty if none is shown
	 */
	public String getIpAddress() {
		return ipAddressText.getText();
	}

	/**
	 * Returns the request duration text.
	 *
	 * @return the human readable duration, empty if none is shown
	 */
	public String getTime() {
		return timeText.getText();
	}

	/**
	 * Returns the raw response body as received, not the rendered text shown in the body area.
	 *
	 * @return the raw response body, empty if there is none
	 */
	public String getResponseBody() {
		// The raw body, not the displayed text: the display may be pretty-printed or narrowed down by the
		// response data path, and exporting that together with the path would apply the path twice on re-import
		return lastResponseBody != null ? lastResponseBody : "";
	}

	/**
	 * Optional path evaluated against the response content once a request succeeds, interpreted
	 * according to the response's Content-Type header: JsonPath for JSON, the same path syntax
	 * for YAML, and XPath for XML. Only the part of the content matched by the path is then
	 * shown in the response body area; if empty, the full (unmodified/pretty-printed) content is
	 * shown.
	 *
	 * @return the data path, empty if none is set
	 */
	public String getResponseDataPath() {
		return responseDataPathText.getText();
	}

	/**
	 * Sets the response content data path and re-renders the response body accordingly.
	 *
	 * @param responseDataPath the data path, or null to clear the field
	 */
	public void setResponseDataPath(final String responseDataPath) {
		responseDataPathText.setText(responseDataPath != null ? responseDataPath : "");
	}

	/**
	 * Path chosen by the user as the download target for a response body that
	 * represents a downloadable file. May point either to a directory (the
	 * response's own filename, e.g. from Content-Disposition, is then used
	 * inside it) or to a specific target file. Empty if no target was set, in
	 * which case no automatic download should be attempted.
	 *
	 * @return the download target path, empty if none is set
	 */
	public String getDownloadTarget() {
		return downloadTargetText.getText();
	}

	/**
	 * Sets the download target, see {@link #getDownloadTarget()}.
	 *
	 * @param downloadTarget directory or file path, or null to clear the field
	 */
	public void setDownloadTarget(final String downloadTarget) {
		downloadTargetText.setText(downloadTarget != null ? downloadTarget : "");
	}

	/**
	 * Returns the response headers as currently shown.
	 *
	 * @return new map of header name to value, in display order
	 */
	public Map<String, String> getResponseHeaders() {
		return new LinkedHashMap<>(responseHeaders);
	}

	private void createUI() {
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		int row = 0;

		// Code, IP address and time: labels in the first row, read-only fields in the second
		final JPanel sectionCodeAndTime = new JPanel(new GridBagLayout());
		final GridBagConstraints codeAndTimeConstraints = new GridBagConstraints();
		codeAndTimeConstraints.weightx = 1;
		codeAndTimeConstraints.fill = GridBagConstraints.HORIZONTAL;
		codeAndTimeConstraints.insets = new Insets(0, 0, 2, 5);
		codeAndTimeConstraints.gridy = 0;
		sectionCodeAndTime.add(new JLabel(LangResources.get("httpResponseCode")), codeAndTimeConstraints);
		sectionCodeAndTime.add(new JLabel(LangResources.get("httpResponseIpAddress")), codeAndTimeConstraints);
		sectionCodeAndTime.add(new JLabel(LangResources.get("httpResponseTime")), codeAndTimeConstraints);
		codeAndTimeConstraints.gridy = 1;
		httpCodeText = createReadOnlyField();
		sectionCodeAndTime.add(httpCodeText, codeAndTimeConstraints);
		ipAddressText = createReadOnlyField();
		sectionCodeAndTime.add(ipAddressText, codeAndTimeConstraints);
		timeText = createReadOnlyField();
		sectionCodeAndTime.add(timeText, codeAndTimeConstraints);
		add(sectionCodeAndTime, rowConstraints(row++, 0));
		responseDisplayComponents.add(httpCodeText);
		responseDisplayComponents.add(ipAddressText);
		responseDisplayComponents.add(timeText);

		redirectHintLabel = new WrappingLabel();
		redirectHintLabel.setVisible(false);
		add(redirectHintLabel, rowConstraints(row++, 0));

		final JLabel headerLabel = new JLabel(LangResources.get("httpResponseHeader"));
		add(headerLabel, rowConstraints(row++, 0));
		headerContainer = createKeyValueContainer();
		final JScrollPane headerScrolled = createListScrollPane(headerContainer);
		add(headerScrolled, rowConstraints(row++, 0));
		responseDisplayComponents.add(headerLabel);
		responseDisplayComponents.add(headerScrolled);

		final JLabel bodyLabel = new JLabel(LangResources.get("responseBody"));
		add(bodyLabel, rowConstraints(row++, 0));

		// JTextArea already supports Ctrl+A / Ctrl+C, which the SWT variant had to add by hand
		responseBodyText = new JTextArea();
		responseBodyText.setEditable(false);
		final JScrollPane responseBodyScrolled = new JScrollPane(responseBodyText);
		final GridBagConstraints bodyConstraints = rowConstraints(row++, 1);
		bodyConstraints.fill = GridBagConstraints.BOTH;
		add(responseBodyScrolled, bodyConstraints);
		responseDisplayComponents.add(bodyLabel);
		responseDisplayComponents.add(responseBodyScrolled);

		// Takes the body's space while the body is hidden, so the rows below stay at the bottom
		final GridBagConstraints fillerConstraints = rowConstraints(row++, 0.0001);
		fillerConstraints.fill = GridBagConstraints.BOTH;
		add(Box.createGlue(), fillerConstraints);

		final JPanel responseDataPathRow = new JPanel(new BorderLayout(5, 0));
		responseDataPathRow.add(new JLabel(LangResources.get("responseDataPath")), BorderLayout.WEST);
		responseDataPathText = new HintTextField(LangResources.get("responseDataPathHint"));
		responseDataPathText.setToolTipText(LangResources.get("responseDataPathTooltip"));
		responseDataPathText.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(final DocumentEvent event) {
				refreshResponseBodyDisplay();
			}

			@Override
			public void removeUpdate(final DocumentEvent event) {
				refreshResponseBodyDisplay();
			}

			@Override
			public void changedUpdate(final DocumentEvent event) {
				// Attribute changes only
			}
		});
		responseDataPathRow.add(responseDataPathText, BorderLayout.CENTER);
		add(responseDataPathRow, rowConstraints(row++, 0));

		final JPanel downloadTargetRow = new JPanel(new GridBagLayout());
		final GridBagConstraints downloadConstraints = new GridBagConstraints();
		downloadConstraints.insets = new Insets(0, 0, 0, 5);
		downloadTargetRow.add(new JLabel(LangResources.get("downloadTarget")), downloadConstraints);
		downloadTargetText = new HintTextField(LangResources.get("downloadTargetHint"));
		downloadTargetText.setText(Utilities.getUsersDefaultDownloadDirectory());
		downloadTargetText.setToolTipText(LangResources.get("downloadTargetTooltip"));
		// GridBagLayout shrinks to minimum sizes when space gets tight, keep the path field usable
		downloadTargetText.setMinimumSize(new Dimension(150, downloadTargetText.getPreferredSize().height));
		downloadConstraints.weightx = 1;
		downloadConstraints.fill = GridBagConstraints.HORIZONTAL;
		downloadTargetRow.add(downloadTargetText, downloadConstraints);
		downloadConstraints.weightx = 0;
		downloadConstraints.fill = GridBagConstraints.NONE;
		final JButton downloadTargetBrowseFileButton = new JButton(LangResources.get("downloadTargetBrowseFile"));
		downloadTargetBrowseFileButton.addActionListener(event -> browseDownloadTarget(false));
		downloadTargetRow.add(downloadTargetBrowseFileButton, downloadConstraints);
		downloadConstraints.insets = new Insets(0, 0, 0, 0);
		final JButton downloadTargetBrowseDirectoryButton = new JButton(LangResources.get("downloadTargetBrowseDirectory"));
		downloadTargetBrowseDirectoryButton.addActionListener(event -> browseDownloadTarget(true));
		downloadTargetRow.add(downloadTargetBrowseDirectoryButton, downloadConstraints);
		add(downloadTargetRow, rowConstraints(row++, 0));

		randomParamsLabel = new JLabel(LangResources.get("randomParameters"));
		randomParamsLabel.setVisible(false);
		add(randomParamsLabel, rowConstraints(row++, 0));

		randomParamsContainer = createKeyValueContainer();
		randomParamsScrolled = createListScrollPane(randomParamsContainer);
		randomParamsScrolled.setVisible(false);
		add(randomParamsScrolled, rowConstraints(row++, 0));
	}

	private void browseDownloadTarget(final boolean directory) {
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
		final String current = downloadTargetText.getText();
		if (Utilities.isNotBlank(current)) {
			final File currentFile = new File(current);
			final File currentDir = currentFile.isDirectory() ? currentFile : currentFile.getParentFile();
			if (currentDir != null) {
				fileChooser.setCurrentDirectory(currentDir);
			}
			if (!directory && !currentFile.isDirectory()) {
				fileChooser.setSelectedFile(currentFile);
			}
		}
		final int result = directory ? fileChooser.showOpenDialog(this) : fileChooser.showSaveDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			downloadTargetText.setText(fileChooser.getSelectedFile().getAbsolutePath());
		}
	}

	private static GridBagConstraints rowConstraints(final int row, final double weighty) {
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = row;
		constraints.weightx = 1;
		constraints.weighty = weighty;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.FIRST_LINE_START;
		constraints.insets = new Insets(2, 0, 2, 0);
		return constraints;
	}

	private static JTextField createReadOnlyField() {
		final JTextField field = new JTextField();
		field.setEditable(false);
		return field;
	}

	private static ViewportWidthPanel createKeyValueContainer() {
		return new ViewportWidthPanel(new GridBagLayout());
	}

	private static JScrollPane createListScrollPane(final ViewportWidthPanel container) {
		final JScrollPane scrollPane = new JScrollPane(container, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setPreferredSize(new Dimension(100, LIST_HEIGHT));
		scrollPane.setMinimumSize(new Dimension(100, LIST_HEIGHT));
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		return scrollPane;
	}

	/**
	 * Replaces the content of a key/value container by read-only "key | value"
	 * rows.
	 */
	private static void fillKeyValueRows(final ViewportWidthPanel container, final List<String[]> keyValuePairs) {
		container.removeAll();

		int row = 0;
		for (final String[] keyValuePair : keyValuePairs) {
			final GridBagConstraints constraints = new GridBagConstraints();
			constraints.gridy = row++;
			constraints.insets = new Insets(1, 2, 1, 2);

			final JTextField keyText = createReadOnlyField();
			keyText.setText(keyValuePair[0]);
			keyText.setCaretPosition(0);
			keyText.setPreferredSize(new Dimension(KEY_FIELD_WIDTH, keyText.getPreferredSize().height));
			constraints.gridx = 0;
			container.add(keyText, constraints);

			final JTextField valueText = createReadOnlyField();
			valueText.setText(keyValuePair[1]);
			valueText.setCaretPosition(0);
			constraints.gridx = 1;
			constraints.weightx = 1;
			constraints.fill = GridBagConstraints.HORIZONTAL;
			container.add(valueText, constraints);
		}

		// Keeps the rows at the top
		final GridBagConstraints fillerConstraints = new GridBagConstraints();
		fillerConstraints.gridy = row;
		fillerConstraints.weighty = 1;
		container.add(Box.createGlue(), fillerConstraints);

		container.revalidate();
		container.repaint();
	}

	/**
	 * Clears all response data (code, IP address, time, headers, body, redirect hint, random parameters)
	 * and hides the response parts until {@link #showResponse()} is called. The data path and download target
	 * are settings and stay unchanged.
	 */
	public void clearResponse() {
		lastResponseBody = null;
		httpCodeText.setText("");
		ipAddressText.setText("");
		timeText.setText("");
		// Headers too: otherwise a canceled request still exports the previous response's headers,
		// and their stale Content-Type would decide how the next body is rendered
		setResponseHeaders(null);
		responseBodyText.setText("");

		for (final JComponent component : responseDisplayComponents) {
			component.setVisible(false);
		}

		setRandomParameters(null);
		setRedirectInfo(0, null, false);

		revalidate();
		repaint();
	}

	/**
	 * Shows the response parts hidden by {@link #clearResponse()} again.
	 */
	public void showResponse() {
		for (final JComponent component : responseDisplayComponents) {
			component.setVisible(true);
		}

		revalidate();
		repaint();
	}

	/**
	 * Shows the values the random parameter placeholders were replaced with, or hides that area.
	 *
	 * @param params placeholder text mapped to its replacement values, or null/empty to hide the area
	 */
	public void setRandomParameters(final Map<String, List<String>> params) {
		final List<String[]> keyValuePairs = new ArrayList<>();
		if (params != null && !params.isEmpty()) {
			for (final Map.Entry<String, List<String>> entry : params.entrySet()) {
				if (entry.getValue() == null) {
					continue;
				}
				for (final String entryValue : entry.getValue()) {
					keyValuePairs.add(new String[] { entry.getKey() != null ? entry.getKey() : "", entryValue != null ? entryValue : "" });
				}
			}
		}
		fillKeyValueRows(randomParamsContainer, keyValuePairs);

		final boolean hasParams = params != null && !params.isEmpty();
		randomParamsLabel.setVisible(hasParams);
		randomParamsScrolled.setVisible(hasParams);

		revalidate();
		repaint();
	}

	/**
	 * Read-only, word wrapping text taking the width its layout gives it, the
	 * Swing counterpart of an SWT Label with SWT.WRAP. A plain wrapping JTextArea
	 * would request the width of its unwrapped text and widen the whole panel.
	 */
	private static final class WrappingLabel extends JTextArea {
		private static final long serialVersionUID = 5617640286134870133L;

		private WrappingLabel() {
			setLineWrap(true);
			setWrapStyleWord(true);
			setEditable(false);
			setOpaque(false);
			setBorder(null);
			setFont(UIManager.getFont("Label.font"));
			setForeground(UIManager.getColor("Label.foreground"));

			// The wrapped height is only known once the width is, so lay out again after a width change
			addComponentListener(new ComponentAdapter() {
				private int lastWidth = -1;

				@Override
				public void componentResized(final ComponentEvent event) {
					if (getWidth() != lastWidth) {
						lastWidth = getWidth();
						revalidate();
					}
				}
			});
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(1, super.getPreferredSize().height);
		}
	}
}
