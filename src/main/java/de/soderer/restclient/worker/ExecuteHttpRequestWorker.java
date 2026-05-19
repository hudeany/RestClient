package de.soderer.restclient.worker;

import java.net.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.TrustManager;

import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.HttpUtilities;
import de.soderer.restclient.helper.RandomParameterResolver;
import de.soderer.utilities.worker.WorkerParentSimple;
import de.soderer.utilities.worker.WorkerSimple;

public class ExecuteHttpRequestWorker extends WorkerSimple<HttpResponse> {
	private final HttpRequest httpRequest;
	private final Proxy proxy;
	private final TrustManager trustManager;
	private final boolean deactivateHostnameVerification;
	private final RandomParameterResolver randomParameterResolver = new RandomParameterResolver();

	public ExecuteHttpRequestWorker(final WorkerParentSimple parent, final HttpRequest httpRequestTemplate, final Proxy proxy, final TrustManager trustManager, final boolean deactivateHostnameVerification) throws Exception {
		super(parent);

		this.proxy = proxy;
		this.trustManager = trustManager;
		this.deactivateHostnameVerification = deactivateHostnameVerification;

		httpRequest = new HttpRequest(httpRequestTemplate.getRequestMethod(), randomParameterResolver.resolve(httpRequestTemplate.getUrl()));

		for (final Entry<String, String> entry : httpRequestTemplate.getHeaders().entrySet()) {
			httpRequest.addHeader(randomParameterResolver.resolve(entry.getKey()), randomParameterResolver.resolve(entry.getValue()));
		}

		for (final Entry<String, List<Object>> entry : httpRequestTemplate.getUrlParameters().entrySet()) {
			for (final Object value : entry.getValue()) {
				if (value != null && value instanceof String) {
					httpRequest.addUrlParameter(randomParameterResolver.resolve(entry.getKey()), randomParameterResolver.resolve((String) value));
				} else {
					httpRequest.addUrlParameter(randomParameterResolver.resolve(entry.getKey()), value);
				}
			}
		}

		for (final Entry<String, List<Object>> entry : httpRequestTemplate.getPostParameters().entrySet()) {
			for (final Object value : entry.getValue()) {
				if (value != null && value instanceof String) {
					httpRequest.addPostParameter(randomParameterResolver.resolve(entry.getKey()), randomParameterResolver.resolve((String) value));
				} else {
					httpRequest.addPostParameter(randomParameterResolver.resolve(entry.getKey()), value);
				}
			}
		}

		httpRequest.setRequestBody(randomParameterResolver.resolve(httpRequestTemplate.getRequestBody()));
	}

	public Map<String, String> getRandomParameterReplacements() {
		return randomParameterResolver.getResolvedValues();
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
			if (cancel) {
				return null;
			} else {
				throw new Exception("Error: " + e.getMessage(), e);
			}
		}

		signalProgress(true);

		if (cancel) {
			return null;
		} else {
			return httpResponse;
		}
	}

	@Override
	public boolean cancel() {
		final boolean result = super.cancel();
		httpRequest.cancel();
		return result;
	}

	@Override
	public String getResultText() {
		return null;
	}
}
