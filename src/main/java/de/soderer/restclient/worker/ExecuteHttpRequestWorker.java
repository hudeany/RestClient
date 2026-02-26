package de.soderer.restclient.worker;

import java.net.Proxy;

import javax.net.ssl.X509TrustManager;

import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.HttpUtilities;
import de.soderer.network.TrustManagerUtilities;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

public class ExecuteHttpRequestWorker extends WorkerSimple<HttpResponse> {
	private final HttpRequest httpRequest;
	private final String proxyURL;
	private final boolean tlsCheck;

	public ExecuteHttpRequestWorker(final WorkerParentSimple parent, final HttpRequest httpRequest, final String proxyURL, final boolean tlsCheck) {
		super(parent);

		this.httpRequest = httpRequest;
		this.proxyURL = proxyURL;
		this.tlsCheck = tlsCheck;
	}

	@Override
	public HttpResponse work() throws Exception {
		parent.changeTitle("HTTP Request");

		HttpResponse httpResponse = null;

		try {
			itemsToDo = 2;
			itemsDone = 0;

			signalUnlimitedProgress();

			Proxy proxy = null;
			if (Utilities.isNotBlank(proxyURL)) {
				if ("DIRECT".equalsIgnoreCase(proxyURL)) {
					proxy = Proxy.NO_PROXY;
				} else if ("WPAD".equalsIgnoreCase(proxyURL)) {
					final ProxyConfiguration requestProxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.WPAD, null);
					proxy = requestProxyConfiguration.getProxy(httpRequest.getUrl());
				} else {
					proxy = HttpUtilities.getProxyFromString(proxyURL);
				}
			}

			itemsDone++;
			signalProgress(true);

			if (!tlsCheck) {
				final X509TrustManager trustManager = TrustManagerUtilities.createTrustAllTrustManager();
				httpResponse = HttpUtilities.executeHttpRequest(httpRequest, proxy, trustManager);
			} else {
				httpResponse = HttpUtilities.executeHttpRequest(httpRequest, proxy);
			}
		} catch (final Exception e) {
			throw new Exception("Error: " + e.getMessage(), e);
		}

		itemsDone++;
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
