package de.soderer.restclient.dlg;

import java.text.MessageFormat;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.swt.ModalDialog;
import de.soderer.utilities.swt.SwtUtilities;

public class PleaseWaitDialog extends ModalDialog<Integer> {
	protected String text;
	protected Label commentLabel;

	public PleaseWaitDialog(final Shell shell, final String title) {
		this(shell, title, null);
	}

	public PleaseWaitDialog(final Shell shell, final String title, final String text) {
		super(shell, title);

		this.text = text;
	}

	@Override
	protected void createComponents(final Shell shell) throws Exception {
		shell.setLayout(new GridLayout(1, true));

		final Composite composite = new Composite(shell, SWT.NONE);
		composite.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));
		composite.setLayout(SwtUtilities.createNoMarginGridLayout(1, true));

		if (Utilities.isNotBlank(text)) {
			commentLabel = new Label(composite, SWT.NONE);
			commentLabel.setText(text);
			final GridData labelGridData = new GridData(SWT.CENTER, SWT.CENTER, true, true);
			commentLabel.setLayoutData(labelGridData);
		} else {
			commentLabel = new Label(composite, SWT.NONE);
			commentLabel.setText(getI18NString("pleaseWait"));
			final GridData labelGridData = new GridData(SWT.CENTER, SWT.CENTER, true, true);
			commentLabel.setLayoutData(labelGridData);
		}

		shell.pack();

		shell.setMinimumSize(200, shell.getSize().y);
		shell.setSize(200, shell.getSize().y);
	}

	@Override
	public Integer open() {
		try {
			createComponents(getParent());
		} catch (final Exception e) {
			throw new RuntimeException("Cannot create dialog components", e);
		}

		setPosition(getParent());

		getParent().open();

		return 0;
	}

	private static String getI18NString(final String resourceKey, final Object... arguments) {
		if (LangResources.existsKey(resourceKey)) {
			return LangResources.get(resourceKey, arguments);
		} else if ("de".equalsIgnoreCase(Locale.getDefault().getLanguage())) {
			String pattern;
			switch(resourceKey) {
				case "pleaseWait": pattern = "Bitte warten"; break;
				default: pattern = "MessageKey unbekannt: " + resourceKey + (arguments != null && arguments.length > 0 ? " Argumente: " + Utilities.join(arguments, ", ") : "");
			}
			return MessageFormat.format(pattern, arguments);
		} else {
			String pattern;
			switch(resourceKey) {
				case "pleaseWait": pattern = "Please wait"; break;
				default: pattern = "MessageKey unknown: " + resourceKey + (arguments != null && arguments.length > 0 ? " arguments: " + Utilities.join(arguments, ", ") : "");
			}
			return MessageFormat.format(pattern, arguments);
		}
	}
}
