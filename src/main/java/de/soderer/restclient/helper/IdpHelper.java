package de.soderer.restclient.helper;

import java.net.UnknownHostException;

import de.soderer.json.JsonNode;
import de.soderer.json.JsonObject;
import de.soderer.json.JsonReader;
import de.soderer.network.HttpConstants;
import de.soderer.network.HttpContentType;
import de.soderer.network.HttpMethod;
import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.HttpUtilities;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.utilities.Utilities;

/**
 * Helper for acquiring OAuth2 access tokens from an identity provider (IdP), e.g. Keycloak,
 * via the "client credentials" grant. Used by the GUI's "Fetch IdP token" button and by the
 * CLI parameters {@code --idp-url}, {@code --idp-realm}, {@code --idp-username} and {@code --idp-password}.
 */
public class IdpHelper {
	private IdpHelper() {
		// Static utility class
	}

	/**
	 * Discovers the token endpoint URL of an IdP realm via its OpenID Connect discovery document
	 * ({@code <idpUrl>/realms/<realm>/.well-known/openid-configuration}).
	 *
	 * @param idpUrlConfigurationUrl
	 *            base URL of the IdP, e.g. "https://idp.example.com" (a trailing slash is ignored)
	 * @param realmID
	 *            name of the realm
	 * @param proxyConfiguration
	 *            proxy configuration for the discovery request, or null for no special proxy handling
	 * @return the value of "token_endpoint" from the discovery document
	 * @throws Exception
	 *             if the IdP host is unknown, the discovery request fails or its response contains no token endpoint
	 */
	public static String getIdpTokenEdpointUrl(final String idpUrlConfigurationUrl, final String realmID, final ProxyConfiguration proxyConfiguration) throws Exception {
		if (Utilities.isBlank(idpUrlConfigurationUrl)) {
			throw new Exception("Missing IdP URL");
		} else if (Utilities.isBlank(realmID)) {
			throw new Exception("Missing IdP realm (needed unless the IdP URL is the token endpoint itself, ending with \"/token\")");
		}

		// A trailing slash in the base URL would otherwise result in a double slash ("...//realms/...")
		String baseUrl = idpUrlConfigurationUrl.trim();
		while (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}

		try {
			final HttpRequest request = new HttpRequest(HttpMethod.GET, baseUrl + "/realms/" + realmID.trim() + "/.well-known/openid-configuration");

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration == null ? null : proxyConfiguration.getProxy(request.getUrl()));
			if (response.getHttpCode() == 200) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid OpenID configuration JSON data", e);
				}
				if (!(contentJson instanceof JsonObject) || Utilities.isBlank((String) ((JsonObject) contentJson).getSimpleValue("token_endpoint"))) {
					throw new Exception("OpenID configuration contains no \"token_endpoint\"");
				}
				return (String) ((JsonObject) contentJson).getSimpleValue("token_endpoint");
			} else {
				// No System.out output here: in CLI mode stdout carries the actual response output
				throw new Exception("Discovery of IdP token endpoint failed with HTTP code " + response.getHttpCode() + ": " + response.getContent());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost: '" + e.getMessage() + "'", e);
		}
	}

	/**
	 * Acquires an access token from an IdP token endpoint via the OAuth2 "client credentials" grant.
	 *
	 * @param idpTokenEndpointUrl
	 *            URL of the token endpoint
	 * @param clientID
	 *            client ID (entered as "username" in the GUI)
	 * @param clientSecret
	 *            client secret (entered as "password" in the GUI)
	 * @param scope
	 *            optional scope, or null to request none
	 * @param proxyConfiguration
	 *            proxy configuration for the token request, or null for no special proxy handling
	 * @return the value of "access_token" from the token response
	 * @throws Exception
	 *             if the IdP host is unknown, the token request fails or its response is no valid JSON
	 */
	public static String aquireAccessToken(final String idpTokenEndpointUrl, final String clientID, final String clientSecret, final String scope, final ProxyConfiguration proxyConfiguration) throws Exception {
		try {
			final HttpRequest request = new HttpRequest(HttpMethod.POST, idpTokenEndpointUrl);
			request.addHeader(HttpConstants.HTTPHEADERNAME_CONTENTTYPE, HttpContentType.HtmlForm.getStringRepresentation());
			request.addPostParameter("grant_type", "client_credentials");
			request.addPostParameter("client_id", clientID);
			request.addPostParameter("client_secret", clientSecret);
			if (scope != null) {
				request.addPostParameter("scope", scope);
			}

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration == null ? null : proxyConfiguration.getProxy(request.getUrl()));
			if (response.getHttpCode() == 200) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid AccessToken JSON data", e);
				}
				return (String) ((JsonObject) contentJson).getSimpleValue("access_token");
			} else {
				// No System.out output here: in CLI mode stdout carries the actual response output
				throw new Exception("aquireAccessToken failed with HTTP code " + response.getHttpCode() + ": " + response.getContent());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost: '" + e.getMessage() + "'", e);
		}
	}
}
