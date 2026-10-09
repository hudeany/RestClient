package de.soderer.restclient.helper;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import de.soderer.json.JsonArray;
import de.soderer.json.JsonNode;
import de.soderer.json.JsonWriter;
import de.soderer.json.path.JsonPath;
import de.soderer.json.path.JsonPathArrayElement;
import de.soderer.json.path.JsonPathElement;
import de.soderer.json.path.JsonPathPropertyElement;
import de.soderer.json.path.JsonPathRoot;
import de.soderer.network.HttpContentType;
import de.soderer.utilities.Utilities;
import de.soderer.yaml.YamlWriter;
import de.soderer.yaml.data.YamlMapping;
import de.soderer.yaml.data.YamlNode;
import de.soderer.yaml.data.YamlScalar;
import de.soderer.yaml.data.YamlSequence;

/**
 * Shared logic behind the "Response content data path" feature: evaluating a JsonPath (including
 * its wildcard/filter extensions) against a parsed JSON tree, the same path syntax against a
 * parsed YAML tree, and an XPath expression against an XML response body. Used by both
 * {@link de.soderer.restclient.dlg.ResponseComponent} (GUI) and {@link de.soderer.restclient.RestClient}
 * (CLI's {@code --response-data-path} parameter), kept separate from both so these two very
 * different environments don't each carry their own copy of this logic.
 */
public class ResponseDataPathEvaluator {
	private ResponseDataPathEvaluator() {
		// Static utility class
	}

	/**
	 * Checks whether a Content-Type header value is one of the given content types, ignoring case
	 * and any parameters like "; charset=UTF-8".
	 *
	 * @param contentType value of the Content-Type header, may be null
	 * @param candidateTypes content types to check for
	 * @return true if the content type is one of the candidate types
	 */
	public static boolean isContentType(final String contentType, final HttpContentType... candidateTypes) {
		if (contentType == null) {
			return false;
		}
		// Media types are case-insensitive (RFC 9110), e.g. "Application/JSON; charset=UTF-8" is valid
		final String normalizedContentType = contentType.trim().toLowerCase(Locale.ROOT);
		for (final HttpContentType candidateType : candidateTypes) {
			final String representation = candidateType.getStringRepresentation().toLowerCase(Locale.ROOT);
			if (normalizedContentType.equals(representation) || normalizedContentType.startsWith(representation + ";")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Pretty-prints {@code jsonRootNode} as-is if {@code dataPath} is blank. Otherwise evaluates
	 * the path via {@link JsonNode#getDataListByJsonPath(JsonPath)} (which also supports the
	 * wildcard/filter extensions, e.g. {@code $.*[?(@.version=='1.0')]}): a single match is
	 * printed unwrapped (same as before wildcards/filters existed), while zero or several matches
	 * are printed as a JSON array of the matching values, since there is no single node left to
	 * print on its own.
	 *
	 * @param jsonRootNode parsed JSON content
	 * @param dataPath JsonPath to evaluate, or blank for the whole content
	 * @return the formatted JSON text
	 * @throws Exception if the path is invalid or does not exist in the content
	 */
	public static String evaluateJsonPath(final JsonNode jsonRootNode, final String dataPath) throws Exception {
		if (Utilities.isBlank(dataPath)) {
			return JsonWriter.getJsonItemString(jsonRootNode);
		}

		final List<JsonNode> matches = jsonRootNode.getDataListByJsonPath(new JsonPath(dataPath));
		if (matches.size() == 1) {
			return JsonWriter.getJsonItemString(matches.get(0));
		} else {
			final JsonArray matchesArray = new JsonArray();
			for (final JsonNode match : matches) {
				matchesArray.add(match);
			}
			return JsonWriter.getJsonItemString(matchesArray);
		}
	}

	/**
	 * Navigates a parsed YAML tree along a {@link JsonPath} (reused here purely as a generic
	 * dot/bracket path parser, not tied to JSON data itself). Property elements require a
	 * {@link YamlMapping} node, array elements require a {@link YamlSequence} node at the
	 * respective position in the tree.
	 *
	 * @param rootNode root node of the YAML document
	 * @param path path to navigate along
	 * @return the node at the end of the path
	 * @throws Exception if the path does not exist in the YAML data
	 */
	public static YamlNode getYamlNodeByPath(final YamlNode rootNode, final JsonPath path) throws Exception {
		YamlNode currentNode = rootNode;
		for (final JsonPathElement pathPart : path.getPathParts()) {
			if (pathPart instanceof JsonPathRoot) {
				// Nothing to do, currentNode already points to the document root
			} else if (pathPart instanceof JsonPathPropertyElement) {
				final String propertyKey = ((JsonPathPropertyElement) pathPart).getPropertyKey();
				if (!(currentNode instanceof YamlMapping) || !((YamlMapping) currentNode).containsKey(propertyKey)) {
					throw new Exception("YAML data does not contain path element '" + propertyKey + "'");
				}
				currentNode = ((YamlMapping) currentNode).get(propertyKey);
			} else if (pathPart instanceof JsonPathArrayElement) {
				final int index = ((JsonPathArrayElement) pathPart).getIndex();
				if (!(currentNode instanceof YamlSequence) || index >= ((YamlSequence) currentNode).size()) {
					throw new Exception("YAML data does not contain path element [" + index + "]");
				}
				currentNode = ((YamlSequence) currentNode).get(index);
			} else {
				throw new Exception("Unexpected path element: " + pathPart);
			}
		}
		return currentNode;
	}

	/**
	 * Converts a YAML node into display text: mappings and sequences as YAML text, scalars as their plain value.
	 *
	 * @param node the node to convert, may be null
	 * @return the display text, empty for null
	 * @throws Exception if the node cannot be written as YAML
	 */
	public static String yamlNodeToDisplayString(final YamlNode node) throws Exception {
		if (node instanceof YamlMapping) {
			return YamlWriter.toString((YamlMapping) node);
		} else if (node instanceof YamlSequence) {
			return YamlWriter.toString((YamlSequence) node);
		} else if (node instanceof YamlScalar) {
			return ((YamlScalar) node).getValueString();
		} else {
			return node != null ? node.toString() : "";
		}
	}

	/**
	 * Parses the given body as XML and evaluates the given XPath expression against it.
	 * Disables DOCTYPE declarations to prevent XXE attacks via a malicious response.
	 * Node-set results are serialized as XML fragments (or raw text for text/attribute nodes),
	 * joined by newlines for multiple matches; non-node-set expressions (e.g. count(...), a
	 * boolean or a number) fall back to a plain string evaluation.
	 *
	 * @param xmlBody XML text of the response body
	 * @param xPathExpression XPath expression to evaluate
	 * @return the matching XML fragments or the string result of the expression
	 * @throws Exception if the body is no valid XML or the expression is invalid
	 */
	public static String evaluateXPath(final String xmlBody, final String xPathExpression) throws Exception {
		final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		documentBuilderFactory.setNamespaceAware(true);
		documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

		// Parsed from characters, not from UTF-8 bytes: the body is already decoded text, and an XML declaration
		// like encoding="ISO-8859-1" would otherwise make the parser decode the UTF-8 bytes a second time wrongly
		final Document document = documentBuilder.parse(new InputSource(new StringReader(xmlBody)));

		final XPath xPath = XPathFactory.newInstance().newXPath();

		NodeList nodeList;
		try {
			nodeList = (NodeList) xPath.evaluate(xPathExpression, document, XPathConstants.NODESET);
		} catch (@SuppressWarnings("unused") final XPathExpressionException e) {
			// Expression does not evaluate to a node-set (e.g. count(...), a boolean or a number)
			nodeList = null;
		}

		if (nodeList == null) {
			final String stringResult = xPath.evaluate(xPathExpression, document);
			return stringResult != null ? stringResult : "";
		}

		final Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");

		final StringBuilder result = new StringBuilder();
		for (int i = 0; i < nodeList.getLength(); i++) {
			final Node node = nodeList.item(i);
			if (i > 0) {
				result.append("\n");
			}
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				final StringWriter writer = new StringWriter();
				transformer.transform(new DOMSource(node), new StreamResult(writer));
				result.append(writer.toString());
			} else {
				result.append(node.getNodeValue());
			}
		}
		return result.toString();
	}
}
