package de.soderer.restclient.dlg;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
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
import org.eclipse.swt.widgets.Text;

import de.soderer.utilities.LangResources;

public class RequestComponent extends Composite {
	private Combo presetNameCombo;
	private Button saveButton;
	private Button deleteButton;

	private Combo httpMethodCombo;
	private Text serviceUrlText;
	private Button tlsCheckBox;
	private Text serviceMethodText;
	private Text proxyUrlText;
	private Text requestBodyText;

	private Composite headerContainer;
	private Composite paramContainer;

	private ScrolledComposite headerScrolled;
	private ScrolledComposite paramScrolled;

	private Composite content;
	private ScrolledComposite outerScrolled;

	public RequestComponent(final Composite parent, final int style) {
		super(parent, style);

		createOuterScrollArea();
	}

	public String getHttpMethod() {
		return httpMethodCombo.getText();
	}

	public void setHttpMethod(final String method) {
		if (method != null) {
			final int index = httpMethodCombo.indexOf(method);
			if (index >= 0) httpMethodCombo.select(index);
		}
	}

	public String getCheckTlsCert() {
		return httpMethodCombo.getText();
	}

	public void setTlsCheck(final boolean check) {
		tlsCheckBox.setSelection(check);
	}

	public String getPresetName() { return presetNameCombo.getText(); }
	public String getServiceUrl() { return serviceUrlText.getText(); }
	public String getServiceMethod() { return serviceMethodText.getText(); }
	public String getProxyUrl() { return proxyUrlText.getText(); }
	public String getRequestBody() { return requestBodyText.getText(); }
	public boolean getTlsCheck() { return tlsCheckBox.getSelection(); }

	public Map<String, String> getHttpHeaders() { return extractKeyValuePairs(headerContainer); }
	public Map<String, String> getUrlParameters() { return extractKeyValuePairs(paramContainer); }

	public void setPresetNames(final List<String> presets) {
		presetNameCombo.removeAll();
		if (presets != null) {
			for (final String p : presets) {
				presetNameCombo.add(p);
			}
		}
	}

	public void addPresetSelectionListener(final Runnable listener) {
		if (listener == null) return;
		presetNameCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				listener.run();
			}
		});
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

	public void setPresetName(final String value) { if (value != null) presetNameCombo.setText(value); }
	public void setServiceUrl(final String value) { serviceUrlText.setText(value != null ? value : ""); }
	public void setServiceMethod(final String value) { serviceMethodText.setText(value != null ? value : ""); }
	public void setProxyUrl(final String value) { proxyUrlText.setText(value != null ? value : ""); }
	public void setRequestBody(final String value) { requestBodyText.setText(value != null ? value : ""); }

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
		refreshScrolledArea(headerContainer, headerScrolled);
	}

	public void setUrlParameters(final Map<String, String> params) {
		for (final Control c : paramContainer.getChildren()) {
			c.dispose();
		}

		if (params != null) {
			for (final Map.Entry<String, String> entry : params.entrySet()) {
				final Composite row = addKeyValueRow(paramContainer, paramScrolled);
				final Control[] children = row.getChildren();
				if (children.length >= 2 && children[0] instanceof final Text name && children[1] instanceof final Text value) {
					name.setText(entry.getKey());
					value.setText(entry.getValue());
				}
			}
		}
		refreshScrolledArea(paramContainer, paramScrolled);
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

		proxyUrlText = createLabeledText(LangResources.get("proxyURL"), LangResources.get("proxyUrlHint"));

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
		tlsCheckCol.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, false, false));
		final GridLayout tlsCheckLayout = new GridLayout(1, false);
		urlLayout.marginWidth = 0;
		urlLayout.marginHeight = 0;
		tlsCheckCol.setLayout(tlsCheckLayout);

		final Label tlsCheckLabel = new Label(tlsCheckCol, SWT.NONE);
		tlsCheckLabel.setText(LangResources.get("tlsCheck"));

		tlsCheckBox = new Button(tlsCheckCol, SWT.CHECK);
		tlsCheckBox.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		tlsCheckBox.setSelection(true);

		serviceMethodText = createLabeledText(LangResources.get("serviceMethod"), LangResources.get("serviceMethodHint"));

		createKeyValueSection(LangResources.get("httpRequestHeader"), true);
		createKeyValueSection(LangResources.get("urlParameter"), false);

		createRequestBodySection();
	}

	private void createPresetNameSection() {
		final Label label = new Label(content, SWT.NONE);
		label.setText(LangResources.get("preset"));

		final Composite comboButtonComposite = new Composite(content, SWT.NONE);
		comboButtonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		comboButtonComposite.setLayout(new GridLayout(3, false));

		presetNameCombo = new Combo(comboButtonComposite, SWT.DROP_DOWN | SWT.BORDER);
		presetNameCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		saveButton = new Button(comboButtonComposite, SWT.PUSH);
		saveButton.setText(LangResources.get("save"));

		deleteButton = new Button(comboButtonComposite, SWT.PUSH);
		deleteButton.setText(LangResources.get("delete"));
	}

	private Text createLabeledText(final String labelText, final String message) {
		final Label label = new Label(content, SWT.NONE);
		label.setText(labelText);

		final Text text = new Text(content, SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		text.setMessage(message);
		return text;
	}

	private void createKeyValueSection(final String title, final boolean isHeaderSection) {
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

		if (isHeaderSection) {
			headerContainer = container;
			headerScrolled = scrolled;
		} else {
			paramContainer = container;
			paramScrolled = scrolled;
		}

		addKeyValueRow(container, scrolled);
		addKeyValueRow(container, scrolled);

		addButton.addListener(SWT.Selection, e -> addKeyValueRow(container, scrolled));
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
		final int height = Math.max(refreshScrolledAreaParent.computeSize(SWT.DEFAULT, SWT.DEFAULT).y, 140);
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
}