package de.soderer.restclient.worker;

import java.net.Proxy;

import javax.net.ssl.TrustManager;

import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.HttpUtilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

public class ExecuteHttpRequestWorker extends WorkerSimple<HttpResponse> {
	private final HttpRequest httpRequest;
	private final Proxy proxy;
	private final TrustManager trustManager;
private final boolean deactivateHostnameVerification;

	public ExecuteHttpRequestWorker(final WorkerParentSimple parent, final HttpRequest httpRequest, final Proxy proxy, final TrustManager trustManager, final boolean deactivateHostnameVerification) {
		super(parent);

		this.httpRequest = httpRequest;
		this.proxy = proxy;
		this.trustManager = trustManager;
		this.deactivateHostnameVerification = deactivateHostnameVerification;
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

			httpResponse = HttpUtilities.executeHttpRequest(httpRequest, proxy, trustManager, deactivateHostnameVerification);
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
