package de.soderer.restclient.helper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.soderer.utilities.Utilities;

/**
 * Resolves random parameter placeholders in REST client requests.
 *
 * <p>
 * Syntax: {@code ${rnd:TYPE:SLOT}} or {@code ${rnd:TYPE:SLOT:PARAM}}
 *
 * <p>
 * Supported types:
 * <ul>
 * <li>{@code UUID} – random UUID (e.g. {@code 550e8400-e29b-41d4-a716-446655440000})</li>
 * <li>{@code INT} – random integer, optional range {@code MIN-MAX}</li>
 * <li>{@code STR} – random alphanumeric string, optional length</li>
 * <li>{@code HEX} – random hex string, optional length</li>
 * <li>{@code BOOL} – random boolean ({@code true} or {@code false})</li>
 * <li>{@code TS} – current Unix timestamp in milliseconds</li>
 * <li>{@code ISO} – current timestamp in ISO-8601 format</li>
 * </ul>
 *
 * <p>
 * All occurrences of the same TYPE:SLOT combination within one resolver
 * instance produce the same value, enabling correlation across headers, URL,
 * and body.
 */
public class RandomParameterResolver {
	/** Matches {@code ${rnd:TYPE:SLOT}} and {@code ${rnd:TYPE:SLOT:PARAM}}. */
	private static final Pattern PATTERN = Pattern.compile("\\$\\{rnd:([A-Z]+)(?::(\\d+)(?::([^}]*))?)?\\}");

	private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final String HEX_CHARS = "0123456789abcdef";
	private static final int DEFAULT_STR_LENGTH = 12;
	private static final int DEFAULT_HEX_LENGTH = 16;
	private static final int DEFAULT_INT_MIN = 0;
	private static final int DEFAULT_INT_MAX = Integer.MAX_VALUE;

	private final Map<String, String> cache = new HashMap<>();
	private final Map<String, List<String>> replacementsForDisplay = new HashMap<>();
	private final SecureRandom random = new SecureRandom();

	/**
	 * Resolves all {@code ${rnd:...}} placeholders in the given input string.
	 *
	 * @param input the raw string potentially containing placeholders
	 * @return the string with all placeholders replaced by their generated values
	 * @throws Exception
	 * @throws RandomParameterException if a placeholder is malformed or an unknown type is used
	 */
	public String resolve(final String input) throws Exception {
		if (input == null || input.isEmpty()) {
			return input;
		}
		final Matcher matcher = PATTERN.matcher(input);
		final StringBuilder sb = new StringBuilder();
		final Map<String, Integer> typeCounter = new HashMap<>();
		final String inputNamespace = Integer.toHexString(input.hashCode());
		while (matcher.find()) {
			final String type = matcher.group(1);
			final String slot = matcher.group(2); // optional: may be null
			final String param = matcher.group(3); // optional: may be null

			final String cacheKey;
			if (Utilities.isNotBlank(slot)) {
				cacheKey = type + ":" + slot;
			} else {
				final int index = typeCounter.getOrDefault(type, 0);
				typeCounter.put(type, index + 1);
				cacheKey = inputNamespace + "|" + type + "#" + index;
			}

			final String generatedValue = generate(type, param, input, matcher.start());
			final String value = cache.computeIfAbsent(cacheKey, k -> generatedValue);

			matcher.appendReplacement(sb, Matcher.quoteReplacement(value));

			final String foundText = matcher.group();
			final List<String> replacementsList = replacementsForDisplay.computeIfAbsent(foundText, k -> new ArrayList<>());
			if (replacementsList.isEmpty() || Utilities.isBlank(slot)) {
				replacementsList.add(value);
			}
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Resets the internal value cache. Call this between requests to ensure fresh
	 * values per request.
	 */
	public void reset() {
		cache.clear();
		replacementsForDisplay.clear();
	}

	/**
	 * Returns a read-only view of the currently cached slot => value mappings. Useful
	 * for logging or debugging.
	 */
	public Map<String, List<String>> getResolvedValues() {
		return Map.copyOf(replacementsForDisplay);
	}

	private String generate(final String type, final String param, final String input, final int position) throws Exception {
		return switch (type) {
			case "UUID" -> generateUUID();
			case "INT" -> generateInt(param);
			case "STR" -> generateStr(param);
			case "HEX" -> generateHex(param);
			case "BOOL" -> generateBool();
			case "TS" -> generateTimestamp();
			case "ISO" -> generateIso();
			default -> throw new Exception("Unknown random parameter type '" + type + "' at position " + position + " in: " + truncate(input));
		};
	}

	private static String generateUUID() {
		return UUID.randomUUID().toString();
	}

	private String generateInt(final String param) throws Exception {
		int min = DEFAULT_INT_MIN;
		int max = DEFAULT_INT_MAX;
		if (param != null && !param.isBlank()) {
			final String[] parts = param.split("-", 2);
			if (parts.length == 2) {
				try {
					min = Integer.parseInt(parts[0].trim());
					max = Integer.parseInt(parts[1].trim());
				} catch (@SuppressWarnings("unused") final NumberFormatException e) {
					throw new Exception("Invalid INT range parameter '" + param + "'. Expected format: MIN-MAX");
				}
				if (min > max) {
					throw new Exception("Invalid INT range: MIN (" + min + ") must be <= MAX (" + max + ")");
				}
			} else {
				throw new Exception("Invalid INT parameter '" + param + "'. Expected format: MIN-MAX");
			}
		}
		return String.valueOf(min + (long) (random.nextDouble() * ((long) max - min + 1)));
	}

	private String generateStr(final String param) throws Exception {
		int length = DEFAULT_STR_LENGTH;
		if (param != null && !param.isBlank()) {
			try {
				length = Integer.parseInt(param.trim());
			} catch (@SuppressWarnings("unused") final NumberFormatException e) {
				throw new Exception(
						"Invalid STR length parameter '" + param + "'. Expected a positive integer.");
			}
			if (length <= 0) {
				throw new Exception("STR length must be > 0, got: " + length);
			}
		}
		final StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
		}
		return sb.toString();
	}

	private String generateHex(final String param) throws Exception {
		int length = DEFAULT_HEX_LENGTH;
		if (param != null && !param.isBlank()) {
			try {
				length = Integer.parseInt(param.trim());
			} catch (@SuppressWarnings("unused") final NumberFormatException e) {
				throw new Exception("Invalid HEX length parameter '" + param + "'. Expected a positive integer.");
			}
			if (length <= 0) {
				throw new Exception("HEX length must be > 0, got: " + length);
			}
		}
		final StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(HEX_CHARS.charAt(random.nextInt(HEX_CHARS.length())));
		}
		return sb.toString();
	}

	private String generateBool() {
		return String.valueOf(random.nextBoolean());
	}

	private static String generateTimestamp() {
		return String.valueOf(Instant.now().toEpochMilli());
	}

	private static String generateIso() {
		return Instant.now().toString();
	}

	private static String truncate(final String s) {
		return s.length() > 80 ? s.substring(0, 80) + "…" : s;
	}
}