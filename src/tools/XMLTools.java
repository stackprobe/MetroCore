package tools;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class XMLTools {
	private XMLTools() {
	}

	public static class Node {
		public String name;
		public String value;
		public List<Node> children = new ArrayList<Node>();
		public boolean attributeFlag;

		private Map<String, List<Node>> _name2node = null;

		private void ensureLoadName2Node() {
			if (_name2node == null) {
				_name2node = new HashMap<String, List<Node>>();

				for (Node node : children) {
					if (!_name2node.containsKey(node.name)) {
						_name2node.put(node.name, new ArrayList<Node>());
					}
					_name2node.get(node.name).add(node);
				}
			}
		}

		public Node getNode(String name) {
			return getNodes(name).get(0);
		}

		public List<Node> getNodes(String name) {
			ensureLoadName2Node();
			return _name2node.get(name);
		}
	}

	public static Node loadFromFile(String xmlFile) throws IOException, XMLStreamException {
		Node node = new Node();
		Deque<Node> ancestors = new ArrayDeque<Node>();

		XMLInputFactory factory = XMLInputFactory.newInstance();

		try (java.io.InputStream is = Files.newInputStream(Paths.get(xmlFile))) {
			XMLStreamReader reader = factory.createXMLStreamReader(is);

			while (reader.hasNext()) {
				int eventType = reader.next();

				switch (eventType) {
				case XMLStreamConstants.START_ELEMENT: {
					Node child = new Node();
					child.name = reader.getLocalName();

					node.children.add(child);
					ancestors.push(node);
					node = child;

					for (int i = 0; i < reader.getAttributeCount(); i++) {
						Node attr = new Node();
						attr.name = reader.getAttributeLocalName(i);
						attr.value = reader.getAttributeValue(i);
						attr.attributeFlag = true;
						node.children.add(attr);
					}
					break;
				}

				case XMLStreamConstants.CHARACTERS:
				case XMLStreamConstants.CDATA:
					if (!reader.isWhiteSpace()) {
						if (node.value == null) {
							node.value = reader.getText();
						}
						else {
							node.value += reader.getText();
						}
					}
					break;

				case XMLStreamConstants.END_ELEMENT:
					node = ancestors.pop();
					break;

				default:
					break;
				}
			}
			reader.close();
		}

		node = node.children.get(0);
		normalize(node);
		return node;
	}

	private static void normalize(Node root) {
		Queue<Node> q = new LinkedList<Node>();
		q.add(root);

		while (!q.isEmpty()) {
			Node node = q.poll();

			node.name = node.name == null ? "" : node.name;
			node.value = node.value == null ? "" : node.value;

			int colon = node.name.indexOf(':');
			if (colon != -1) {
				node.name = node.name.substring(colon + 1);
			}

			node.name = node.name.trim();
			node.value = node.value.trim();

			for (Node child : node.children) {
				q.add(child);
			}
		}
	}

	public static void writeToFile(Node root, String xmlFile) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(
				Paths.get(xmlFile),
				StandardCharsets.UTF_8)) {
			writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			writer.newLine();
			writeTo(root, writer, 0);
		}
	}

	private static void writeTo(Node node, Writer writer, int depth) throws IOException {
		String name = node.name;
		String value = node.value;

		name = name == null ? "" : name;
		value = value == null ? "" : value;

		name = encodeXML(name);
		value = encodeXML(value);

		writer.write(indent(depth));
		writer.write("<");
		writer.write(name);

		for (Node child : node.children) {
			if (child.attributeFlag) {
				writeAttributeTo(child, writer);
			}
		}

		boolean hasNonAttributeChild = false;
		for (Node child : node.children) {
			if (!child.attributeFlag) {
				hasNonAttributeChild = true;
				break;
			}
		}

		if (hasNonAttributeChild) {
			writer.write(">");
			writer.write(value);
			writer.write(System.lineSeparator());

			for (Node child : node.children) {
				if (!child.attributeFlag) {
					writeTo(child, writer, depth + 1);
				}
			}

			writer.write(indent(depth));
			writer.write("</");
			writer.write(name);
			writer.write(">");
			writer.write(System.lineSeparator());
		}
		else if (!"".equals(value)) {
			writer.write(">");
			writer.write(value);
			writer.write("</");
			writer.write(name);
			writer.write(">");
			writer.write(System.lineSeparator());
		}
		else {
			writer.write("/>");
			writer.write(System.lineSeparator());
		}
	}

	private static void writeAttributeTo(Node node, Writer writer) throws IOException {
		String name = node.name;
		String value = node.value;

		name = name == null ? "" : name;
		value = value == null ? "" : value;

		name = encodeXML(name);
		value = encodeXML(value);

		writer.write(" ");
		writer.write(name);
		writer.write("=\"");
		writer.write(value);
		writer.write("\"");
	}

	private static String indent(int depth) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			sb.append('\t');
		}
		return sb.toString();
	}

	private static String encodeXML(String str) {
		StringBuilder buff = new StringBuilder();

		for (int i = 0; i < str.length(); i++) {
			char chr = str.charAt(i);

			switch (chr) {
			case '"':
				buff.append("&quot;");
				break;

			case '\'':
				buff.append("&apos;");
				break;

			case '<':
				buff.append("&lt;");
				break;

			case '>':
				buff.append("&gt;");
				break;

			case '&':
				buff.append("&amp;");
				break;

			default:
				buff.append(chr);
				break;
			}
		}
		return buff.toString();
	}
}
