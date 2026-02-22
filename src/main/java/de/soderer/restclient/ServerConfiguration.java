package de.soderer.restclient;

import de.soderer.utilities.Utilities;

public class ServerConfiguration {
	private String displayName;
	private String idpUrl;
	private String realmID;
	private String argoWfSchedulerBaseUrl;
	private String clientID;
	private String clientSecret = null;

	public String getDisplayName() {
		if (Utilities.isNotBlank(displayName)) {
			return displayName;
		} else {
			return argoWfSchedulerBaseUrl;
		}
	}

	public ServerConfiguration setDisplayName(final String displayName) {
		this.displayName = displayName;
		return this;
	}

	public String getIdpUrl() {
		return idpUrl;
	}

	public ServerConfiguration setIdpUrl(final String idpUrl) {
		this.idpUrl = idpUrl;
		return this;
	}

	public String getRealmID() {
		return realmID;
	}

	public ServerConfiguration setRealmID(final String realmID) {
		this.realmID = realmID;
		return this;
	}

	public String getArgoWfSchedulerBaseUrl() {
		return argoWfSchedulerBaseUrl;
	}

	public ServerConfiguration setArgoWfSchedulerBaseUrl(final String argoWfSchedulerBaseUrl) {
		this.argoWfSchedulerBaseUrl = argoWfSchedulerBaseUrl;
		return this;
	}

	public String getClientID() {
		return clientID;
	}

	public ServerConfiguration setClientID(final String clientID) {
		this.clientID = clientID;
		return this;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public ServerConfiguration setClientSecret(final String clientSecret) {
		this.clientSecret = clientSecret;
		return this;
	}
}
