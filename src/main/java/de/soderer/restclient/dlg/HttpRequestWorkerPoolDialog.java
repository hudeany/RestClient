package de.soderer.restclient.dlg;

import java.awt.Window;
import java.net.Proxy;

import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.TlsCheckConfiguration;
import de.soderer.restclient.worker.ExecuteHttpRequestWorker;
import de.soderer.utilities.worker.WorkerSimple;

public class HttpRequestWorkerPoolDialog extends WorkerPoolDialog {
	private static final long serialVersionUID = -7453089130522841263L;

	private final HttpRequest httpRequest;
	private final Proxy proxy;
	private final TlsCheckConfiguration tlsCheckConfiguration;

	public HttpRequestWorkerPoolDialog(final Window parent, final String title, final String text, final HttpRequest httpRequest, final Proxy proxy, final TlsCheckConfiguration tlsCheckConfiguration) {
		super(parent, title, text);

		this.httpRequest = httpRequest;
		this.proxy = proxy;
		this.tlsCheckConfiguration = tlsCheckConfiguration;
	}

	@Override
	protected WorkerSimple<?> createWorker() {
		try {
			return new ExecuteHttpRequestWorker(null, httpRequest, proxy, tlsCheckConfiguration.getTrustManager(), !tlsCheckConfiguration.getCheckCn());
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected boolean checkForSuccess(final Object httpResponse) {
		if (httpResponse instanceof HttpResponse) {
			return 200 <= ((HttpResponse) httpResponse).getHttpCode() && ((HttpResponse) httpResponse).getHttpCode() < 300;
		} else {
			return false;
		}
	}
}
