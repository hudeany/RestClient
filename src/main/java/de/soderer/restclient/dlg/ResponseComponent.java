package de.soderer.restclient.dlg;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
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
		for (final Control c : headerContainer.getChildren()) {
			c.dispose();
		}

		if (headers != null) {
			for (final Map.Entry<String, String> entry : headers.entrySet()) {
				addHeaderRow(entry.getKey(), entry.getValue());
			}
		}
		refreshScrolledArea(headerContainer, headerScrolled);
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

		createKeyValueSection(LangResources.get("httpResponseHeader"));

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
		headerContainer.setLayout(new GridLayout(2, false));
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

	private void createKeyValueSection(final String title) {
		final Composite sectionHeader = new Composite(this, SWT.NONE);
		sectionHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		sectionHeader.setLayout(new GridLayout(2, false));

		final Label label = new Label(this, SWT.NONE);
		label.setText(title);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

		final ScrolledComposite scrolled = new ScrolledComposite(this, SWT.V_SCROLL | SWT.BORDER);
		final GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.heightHint = 75;
		scrolled.setLayoutData(gd);
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		final Composite container = new Composite(scrolled, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		scrolled.setContent(container);

		headerContainer = container;
		headerScrolled = scrolled;
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
		nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		nameText.setMessage(LangResources.get("nameHint"));

		final Text valueText = new Text(row, SWT.BORDER);
		valueText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		valueText.setMessage(LangResources.get("valueHint"));

		final Button removeButton = new Button(row, SWT.PUSH);
		removeButton.setText("-");
		removeButton.addListener(SWT.Selection, e -> {
			row.dispose();
			refreshScrolledArea(parent, scrolled);
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
	}
}