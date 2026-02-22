package de.soderer.restclient;

public class RestClientException extends Exception {
	private static final long serialVersionUID = -7240533232921526907L;

	public RestClientException(final String errorMessage) {
		super(errorMessage);
	}

	public RestClientException(final String errorMessage, final Exception e) {
		super(errorMessage, e);
	}
}
