package de.soderer.restclient.dlg;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import de.soderer.utilities.LangResources;

public class ResponseComponent extends Composite {
	private Text httpCodeText;
	private Composite headerContainer;
	private ScrolledComposite headerScrolled;
	private Text responseBodyText;

	public ResponseComponent(final Composite parent, final int style) {
		super(parent, style);
		createUI();
	}

	public void setHttpCode(final String code) {
		httpCodeText.setText(code != null ? code : "");
	}

	public void setResponseBody(final String body) {
		responseBodyText.setText(body != null ? body : "");
	}

	public void setResponseHeaders(final Map<String, String> headers) {
		for (final Control c : headerContainer.getChildren()) c.dispose();
		if (headers != null) {
			for (final Map.Entry<String, String> entry : headers.entrySet()) {
				addHeaderRow(entry.getKey(), entry.getValue());
			}
		}
		headerContainer.layout(true, true);
		headerScrolled.setMinSize(headerContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	private void addHeaderRow(final String name, final String value) {
		final Text nameText = new Text(headerContainer, SWT.BORDER | SWT.READ_ONLY);
		nameText.setText(name != null ? name : "");
		nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final Text valueText = new Text(headerContainer, SWT.BORDER | SWT.READ_ONLY);
		valueText.setText(value != null ? value : "");
		valueText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		updateHeaderMinSize();
	}

	public String getHttpCode() {
		return httpCodeText.getText();
	}

	public String getResponseBody() {
		return responseBodyText.getText();
	}

	public Map<String, String> getResponseHeaders() {
		final Map<String, String> map = new LinkedHashMap<>();
		final Control[] children = headerContainer.getChildren();
		for (int i = 0; i < children.length - 1; i += 2) {
			if (children[i] instanceof final Text nameText && children[i + 1] instanceof final Text valueText) {
				final String name = nameText.getText();
				final String value = valueText.getText();
				if (!name.isBlank()) {
					map.put(name, value);
				}
			}
		}
		return map;
	}

	private void createUI() {
		setLayout(new GridLayout(1, false));

		final Label codeLabel = new Label(this, SWT.NONE);
		codeLabel.setText(LangResources.get("httpResponseCode"));

		httpCodeText = new Text(this, SWT.BORDER | SWT.READ_ONLY | SWT.SINGLE);
		httpCodeText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final Label headerLabel = new Label(this, SWT.NONE);
		headerLabel.setText(LangResources.get("httpResponseHeader"));

		headerScrolled = new ScrolledComposite(this, SWT.V_SCROLL | SWT.BORDER);
		headerScrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		headerScrolled.setExpandHorizontal(true);
		headerScrolled.setExpandVertical(true);

		headerContainer = new Composite(headerScrolled, SWT.NONE);
		headerContainer.setLayout(new GridLayout(2, false));
		headerScrolled.setContent(headerContainer);

		final Label bodyLabel = new Label(this, SWT.NONE);
		bodyLabel.setText(LangResources.get("responseBody"));

		responseBodyText = new Text(this,
				SWT.MULTI
				| SWT.BORDER
				| SWT.READ_ONLY
				| SWT.V_SCROLL
				| SWT.H_SCROLL);

		responseBodyText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	}

	private void updateHeaderMinSize() {
		headerContainer.layout(true, true);
		final Point size = headerContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		headerScrolled.setMinSize(size);
	}

	public void clearResponse() {
		httpCodeText.setText("");
		responseBodyText.setText("");

		httpCodeText.setVisible(false);

		headerContainer.setVisible(false);
		headerScrolled.setVisible(false);

		responseBodyText.setVisible(false);

		for (final Control c : getChildren()) {
			if (c instanceof Label) {
				c.setVisible(false);
			}
		}

		layout(true, true);
	}

	public void showResponse() {
		httpCodeText.setVisible(true);

		headerContainer.setVisible(true);
		headerScrolled.setVisible(true);

		responseBodyText.setVisible(true);

		for (final Control c : getChildren()) {
			if (c instanceof Label) {
				c.setVisible(true);
			}
		}

		layout(true, true);
	}
}