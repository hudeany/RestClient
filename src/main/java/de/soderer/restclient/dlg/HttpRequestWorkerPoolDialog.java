package de.soderer.restclient.dlg;

import java.awt.Window;
import java.net.Proxy;

import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.TlsCheckConfiguration;
import de.soderer.restclient.worker.ExecuteHttpRequestWorker;
import de.soderer.utilities.worker.WorkerSimple;

/**
 * Worker pool load test dialog sending the same HTTP request in parallel and repeatedly. Each run uses
 * its own copy of the request with freshly resolved random parameter placeholders, and counts as success
 * if the response has an HTTP 2xx status code.
 *
 * @serial exclude
 */
public class HttpRequestWorkerPoolDialog extends WorkerPoolDialog {
	private static final long serialVersionUID = -7453089130522841263L;

	/** Connect timeout used for the load test requests, if the request template has none set */
	public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 30_000;

	/** Read timeout used for the load test requests, if the request template has none set */
	public static final int DEFAULT_READ_TIMEOUT_MILLIS = 120_000;

	private final HttpRequest httpRequest;
	private final Proxy proxy;
	private final TlsCheckConfiguration tlsCheckConfiguration;

	/**
	 * Creates the dialog. The workers are started by {@link #open()}.
	 *
	 * @param parent parent window
	 * @param title window title
	 * @param text description shown above the progress bar (may contain a "RampUp...:" line, which is replaced by a countdown)
	 * @param httpRequest request template sent by every worker run; if it has no connect or read timeout set,
	 *        {@link #DEFAULT_CONNECT_TIMEOUT_MILLIS} and {@link #DEFAULT_READ_TIMEOUT_MILLIS} are set on it
	 * @param proxy proxy to use, {@link Proxy#NO_PROXY} for a direct connection, or null for the default
	 * @param tlsCheckConfiguration TLS check configuration for the requests
	 */
	public HttpRequestWorkerPoolDialog(final Window parent, final String title, final String text, final HttpRequest httpRequest, final Proxy proxy, final TlsCheckConfiguration tlsCheckConfiguration) {
		super(parent, title, text);

		// Safety net for cancellation: HttpRequest.cancel() (disconnect) reliably aborts a blocked read, but not
		// a hanging connect or DNS lookup. Without timeouts (default -1 = wait forever) such a worker thread
		// would keep running after the dialog was canceled or closed. Explicitly set timeouts stay unchanged.
		if (httpRequest.getConnectTimeoutMillis() < 0) {
			httpRequest.setConnectionTimeoutMillis(DEFAULT_CONNECT_TIMEOUT_MILLIS);
		}
		if (httpRequest.getReadTimeoutMillis() < 0) {
			httpRequest.setReadTimeoutMillis(DEFAULT_READ_TIMEOUT_MILLIS);
		}

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
