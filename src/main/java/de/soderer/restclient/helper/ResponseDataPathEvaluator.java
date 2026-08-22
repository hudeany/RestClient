package de.soderer.restclient.helper;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

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

import de.soderer.json.path.JsonPath;
import de.soderer.json.path.JsonPathArrayElement;
import de.soderer.json.path.JsonPathElement;
import de.soderer.json.path.JsonPathPropertyElement;
import de.soderer.json.path.JsonPathRoot;
import de.soderer.network.HttpContentType;
import de.soderer.yaml.YamlWriter;
import de.soderer.yaml.data.YamlMapping;
import de.soderer.yaml.data.YamlNode;
import de.soderer.yaml.data.YamlScalar;
import de.soderer.yaml.data.YamlSequence;

/**
 * Shared logic behind the "Response content data path" feature: evaluating a JsonPath-style
 * path against a parsed YAML tree, and evaluating an XPath expression against an XML response
 * body. Used by both {@link de.soderer.restclient.dlg.ResponseComponent} (GUI) and
 * {@link de.soderer.restclient.RestClient} (CLI's {@code --response-data-path} parameter), kept
 * separate from both so these two very different environments don't each carry their own copy
 * of this logic. The (much simpler) JSON case needs no shared helper - it is just a direct call
 * to {@code JsonNode.getDataByJsonPath(...)} in both places.
 */
public class ResponseDataPathEvaluator {
	private ResponseDataPathEvaluator() {
		// Static utility class
	}

	public static boolean isContentType(final String contentType, final HttpContentType... candidateTypes) {
		for (final HttpContentType candidateType : candidateTypes) {
			final String representation = candidateType.getStringRepresentation();
			if (contentType.equals(representation) || contentType.startsWith(representation + ";")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Navigates a parsed YAML tree along a {@link JsonPath} (reused here purely as a generic
	 * dot/bracket path parser, not tied to JSON data itself). Property elements require a
	 * {@link YamlMapping} node, array elements require a {@link YamlSequence} node at the
	 * respective position in the tree.
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
	 */
	public static String evaluateXPath(final String xmlBody, final String xPathExpression) throws Exception {
		final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		documentBuilderFactory.setNamespaceAware(true);
		documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

		final Document document;
		try (ByteArrayInputStream inputStream = new ByteArrayInputStream(xmlBody.getBytes(StandardCharsets.UTF_8))) {
			document = documentBuilder.parse(inputStream);
		}

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
