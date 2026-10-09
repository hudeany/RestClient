package de.soderer.restclient;

/**
 * Exception for errors of the RestClient application.
 */
public class RestClientException extends Exception {
	private static final long serialVersionUID = -7240533232921526907L;

	/**
	 * Creates an exception with an error message.
	 *
	 * @param errorMessage the error message
	 */
	public RestClientException(final String errorMessage) {
		super(errorMessage);
	}

	/**
	 * Creates an exception with an error message and its cause.
	 *
	 * @param errorMessage the error message
	 * @param e the cause
	 */
	public RestClientException(final String errorMessage, final Exception e) {
		super(errorMessage, e);
	}
}
