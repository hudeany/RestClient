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

public class IdpHelper {
	public static String getIdpTokenEdpointUrl(final String idpUrlConfigurationUrl, final String realmID, final ProxyConfiguration proxyConfiguration) throws Exception {
		try {
			final HttpRequest request = new HttpRequest(HttpMethod.GET, idpUrlConfigurationUrl + "/realms/" + realmID + "/.well-known/openid-configuration");

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration == null ? null : proxyConfiguration.getProxy(request.getUrl()));
			if (response.getHttpCode() == 200) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid AccessToken JSON data", e);
				}
				return (String) ((JsonObject) contentJson).getSimpleValue("token_endpoint");
			} else {
				System.out.println(response);
				throw new Exception("aquireAccessToken failed");
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost: '" + e.getMessage() + "'", e);
		}
	}

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
				System.out.println(response);
				throw new Exception("aquireAccessToken failed: " + response.getContent());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost: '" + e.getMessage() + "'", e);
		}
	}
}
