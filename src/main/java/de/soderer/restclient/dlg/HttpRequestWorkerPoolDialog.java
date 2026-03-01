package de.soderer.restclient.dlg;

import java.net.Proxy;

import org.eclipse.swt.widgets.Shell;

import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.restclient.worker.ExecuteHttpRequestWorker;
import de.soderer.utilities.worker.WorkerSimple;

public class HttpRequestWorkerPoolDialog extends WorkerPoolDialog {
	private final HttpRequest httpRequest;
	private final Proxy proxy;
	private final boolean checkTlsCertificate;

	public HttpRequestWorkerPoolDialog(final Shell parent, final String title, final String text, final HttpRequest httpRequest, final Proxy proxy, final boolean checkTlsCertificate) {
		super(parent, title, text);

		this.httpRequest = httpRequest;
		this.proxy = proxy;
		this.checkTlsCertificate = checkTlsCertificate;
	}

	@Override
	protected WorkerSimple<?> createWorker() {
		final ExecuteHttpRequestWorker worker = new ExecuteHttpRequestWorker(null, httpRequest, proxy, checkTlsCertificate);
		return worker;
	}

	@Override
	protected boolean checkForSuccess(final Object httpResponse) {
		if (httpResponse != null && httpResponse instanceof HttpResponse) {
			return 200 <= ((HttpResponse) httpResponse).getHttpCode() && ((HttpResponse) httpResponse).getHttpCode() < 300;
		} else {
			return false;
		}
	}
}
