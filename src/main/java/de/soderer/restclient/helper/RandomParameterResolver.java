package de.soderer.restclient.helper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

	/** Matches the optional INT range parameter {@code MIN-MAX}, where both bounds may be negative (e.g. {@code -10--1}). */
	private static final Pattern INT_RANGE_PATTERN = Pattern.compile("\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*");

	private final Map<String, String> cache = new HashMap<>();
	/** Insertion ordered, so the replacements are displayed in the order they appear in the request */
	private final Map<String, List<String>> replacementsForDisplay = new LinkedHashMap<>();
	private final SecureRandom random = new SecureRandom();

	/**
	 * Creates a resolver with an empty value cache. Use one instance per request, so the same
	 * TYPE:SLOT combination resolves to the same value within that request only.
	 */
	public RandomParameterResolver() {
		// Nothing to initialize
	}

	/**
	 * Resolves all {@code ${rnd:...}} placeholders in the given input string.
	 *
	 * @param input the raw string potentially containing placeholders
	 * @return the string with all placeholders replaced by their generated values, or the input itself if it is null or empty
	 * @throws Exception if a placeholder has an unknown type or an invalid parameter
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

			// Only generated if not yet cached (computeIfAbsent cannot be used, since generate() throws checked exceptions)
			String value = cache.get(cacheKey);
			if (value == null) {
				value = generate(type, param, input, matcher.start());
				cache.put(cacheKey, value);
			}

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
	 * Returns the replacements made so far, e.g. to show them to the user.
	 *
	 * @return read-only map of placeholder text to its replacement values, in order of first appearance
	 */
	public Map<String, List<String>> getResolvedValues() {
		// Map.copyOf() would lose the order and still share the mutable value lists with this resolver
		final Map<String, List<String>> resolvedValues = new LinkedHashMap<>();
		for (final Map.Entry<String, List<String>> entry : replacementsForDisplay.entrySet()) {
			resolvedValues.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Collections.unmodifiableMap(resolvedValues);
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
			// A plain split("-") cannot handle negative bounds like "-5-5"
			final Matcher rangeMatcher = INT_RANGE_PATTERN.matcher(param);
			if (rangeMatcher.matches()) {
				try {
					min = Integer.parseInt(rangeMatcher.group(1));
					max = Integer.parseInt(rangeMatcher.group(2));
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
		// Upper bound is exclusive, so +1 makes MAX itself reachable (long arithmetic, no overflow for Integer.MAX_VALUE)
		return String.valueOf(random.nextLong(min, (long) max + 1));
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