package de.soderer.restclient.worker;

import java.net.Proxy;

import javax.net.ssl.X509TrustManager;

import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.HttpUtilities;
import de.soderer.network.TrustManagerUtilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

public class ExecuteHttpRequestWorker extends WorkerSimple<HttpResponse> {
	private final HttpRequest httpRequest;
	private final Proxy proxy;
	private final boolean tlsCheck;

	public ExecuteHttpRequestWorker(final WorkerParentSimple parent, final HttpRequest httpRequest, final Proxy proxy, final boolean tlsCheck) {
		super(parent);

		this.httpRequest = httpRequest;
		this.proxy = proxy;
		this.tlsCheck = tlsCheck;
	}

	@Override
	public HttpResponse work() throws Exception {
		if (parent != null) {
			parent.changeTitle("HTTP Request");
		}

		HttpResponse httpResponse = null;

		try {
			itemsToDo = 1;
			itemsDone = 0;

			final X509TrustManager trustManager;
			if (!tlsCheck) {
				trustManager = TrustManagerUtilities.createTrustAllTrustManager();
			} else {
				trustManager = null;
			}

			httpResponse = HttpUtilities.executeHttpRequest(httpRequest, proxy, trustManager);
			itemsDone++;

			signalProgress(true);
		} catch (final Exception e) {
			throw new Exception("Error: " + e.getMessage(), e);
		}

		signalProgress(true);

		if (cancel) {
			return null;
		} else {
			return httpResponse;
		}
	}

	@Override
	public String getResultText() {
		return null;
	}
}
