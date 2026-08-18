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

		if (httpRequestTemplate.getRequestBody() != null) {
			httpRequest.setRequestBody(randomParameterResolver.resolve(httpRequestTemplate.getRequestBody()));
		}

		for (final Entry<String, String> entry : httpRequestTemplate.getCookieData().entrySet()) {
			httpRequest.addCookieData(randomParameterResolver.resolve(entry.getKey()), randomParameterResolver.resolve(entry.getValue()));
		}

		for (final HttpRequest.UploadFileAttachment uploadFileAttachment : httpRequestTemplate.getUploadFileAttachments()) {
			httpRequest.addUploadFileData(
					randomParameterResolver.resolve(uploadFileAttachment.getHtmlInputName()),
					randomParameterResolver.resolve(uploadFileAttachment.getFileName()),
					uploadFileAttachment.getData());
		}

		httpRequest.setMaxRedirects(httpRequestTemplate.getMaxRedirects());
		httpRequest.setConnectionTimeoutMillis(httpRequestTemplate.getConnectTimeoutMillis());
		httpRequest.setReadTimeoutMillis(httpRequestTemplate.getReadTimeoutMillis());
		httpRequest.setEncoding(httpRequestTemplate.getEncoding());

		if (httpRequestTemplate.getDownloadTarget() != null) {
			// Also relevant for the single "send request" execution, not just the worker
			// pool load test - both go through this same class (see RestClientDialog).
			httpRequest.setDownloadTarget(httpRequestTemplate.getDownloadTarget());
		}

		/*
		 * The following three fields are deliberately NOT copied onto the cloned
		 * "httpRequest" - unlike everything else copied above, they cannot be safely
		 * shared/reused, since this worker may run several times in parallel (worker
		 * pool load test):
		 * - requestBodyContentStream: an InputStream can only be consumed once, so
		 *   reusing the very same stream instance across (potentially parallel) worker
		 *   runs would either fail outright or silently send a truncated/empty body
		 *   for all but the first run (same reasoning as the redirect-body handling in
		 *   HttpUtilities.executeHttpRequest, which refuses to re-send such a body).
		 * - downloadStream/downloadFile: an OutputStream/File is not safely writable by
		 *   several worker runs at once (data corruption from concurrent writes) -
		 *   unlike "downloadTarget" above, they have no built-in collision handling,
		 *   since they are meant for a single explicit, programmatic call.
		 * Silently dropping them would leave the request looking fine while quietly
		 * sending no body / not downloading, so they fail fast here instead.
		 */
		if (httpRequestTemplate.getRequestBodyContentStream() != null) {
			throw new Exception("RequestBodyContentStream cannot be used with " + ExecuteHttpRequestWorker.class.getSimpleName()
					+ ", because it may run this request more than once (e.g. for a worker pool load test) and an InputStream can only be consumed once");
		}
		if (httpRequestTemplate.getDownloadStream() != null) {
			throw new Exception("DownloadStream cannot be used with " + ExecuteHttpRequestWorker.class.getSimpleName()
					+ ", because it may run this request more than once (e.g. for a worker pool load test) and a single OutputStream cannot be safely written to by several runs");
		}
		if (httpRequestTemplate.getDownloadFile() != null) {
			throw new Exception("DownloadFile cannot be used with " + ExecuteHttpRequestWorker.class.getSimpleName()
					+ ", because it may run this request more than once (e.g. for a worker pool load test) and a single target file cannot be safely written to by several runs - use DownloadTarget instead, which handles this via ascending name collision numbering");
		}
	}

	public Map<String, List<String>> getRandomParameterReplacements() {
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
