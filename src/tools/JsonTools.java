package tools;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class JsonTools {
	private JsonTools() {
	}

	public static class Node {
		public static class Pair {
			public String name;
			public Node value;

			public Pair() {
			}

			public Pair(String name, Node value) {
				this.name = name;
				this.value = value;
			}
		}

		public List<Node> array;
		public List<Pair> map;
		public String stringValue;
		public String wordValue;

		public Node get(int index) {
			return this.array.get(index);
		}

		public Node get(String name) {
			for (Pair pair : this.map) {
				if (pair.name.equals(name)) {
					return pair.value;
				}
			}
			throw new IllegalArgumentException("Key not found: " + name);
		}
	}

	public static Node loadFromFile(String file) throws IOException {
		return loadFromFile(file, getFileEncoding(file));
	}

	public static Node loadFromFile(String file, Charset charset) throws IOException {
		byte[] data = Files.readAllBytes(Paths.get(file));
		return load(new String(data, charset));
	}

	public static Node load(String text) {
		return new Reader(text).getNode();
	}

	private static class Reader {
		private final String text;
		private int index = 0;

		public Reader(String text) {
			this.text = text;
		}

		private char next() {
			return this.text.charAt(this.index++);
		}

		private char nextNS() {
			char chr;
			do {
				chr = this.next();
			}
			while (chr <= ' ');
			return chr;
		}

		public Node getNode() {
			char chr = this.nextNS();
			Node node = new Node();

			if (chr == '[') {
				node.array = new ArrayList<Node>();

				if ((chr = this.nextNS()) != ']') {
					for (;;) {
						this.index--;
						node.array.add(this.getNode());
						chr = this.nextNS();

						if (chr == ']') {
							break;
						}
						if (chr != ',') {
							throw new RuntimeException("JSON format error: Array ','");
						}

						chr = this.nextNS();

						if (chr == ']') {
							writeLog("JSON format warning: ',' before ']'");
							break;
						}
					}
				}
			}
			else if (chr == '{') {
				node.map = new ArrayList<Node.Pair>();

				if ((chr = this.nextNS()) != '}') {
					for (;;) {
						this.index--;
						Node nameNode = this.getNode();
						String name = nameNode.stringValue != null ? nameNode.stringValue : nameNode.wordValue;

						if (name == null) {
							throw new RuntimeException("JSON format error: Map name");
						}

						if (this.nextNS() != ':') {
							throw new RuntimeException("JSON format error: Map ':'");
						}

						node.map.add(new Node.Pair(name, this.getNode()));

						chr = this.nextNS();

						if (chr == '}') {
							break;
						}
						if (chr != ',') {
							throw new RuntimeException("JSON format error: Map ','");
						}

						chr = this.nextNS();

						if (chr == '}') {
							writeLog("JSON format warning: ',' before '}'");
							break;
						}
					}
				}
			}
			else if (chr == '"' || chr == '\'') {
				StringBuilder buff = new StringBuilder();
				char encl = chr;

				if (encl == '\'') {
					writeLog("JSON format warning: String enclosed in single quotes");
				}

				for (;;) {
					chr = this.next();

					if (chr == encl) {
						break;
					}

					if (chr == '\\') {
						chr = this.next();

						if (chr == 'b') {
							chr = '\b';
						}
						else if (chr == 'f') {
							chr = '\f';
						}
						else if (chr == 'n') {
							chr = '\n';
						}
						else if (chr == 'r') {
							chr = '\r';
						}
						else if (chr == 't') {
							chr = '\t';
						}
						else if (chr == 'u') {
							char c1 = this.next();
							char c2 = this.next();
							char c3 = this.next();
							char c4 = this.next();
							chr = (char) Integer.parseInt(new String(new char[] { c1, c2, c3, c4 }), 16);
						}
					}
					buff.append(chr);
				}
				node.stringValue = buff.toString();
			}
			else {
				StringBuilder buff = new StringBuilder();

				this.index--;

				while (this.index < this.text.length()) {
					chr = this.next();

					if (chr == '}' || chr == ']' || chr == ',' || chr == ':') {
						this.index--;
						break;
					}
					buff.append(chr);
				}
				node.wordValue = buff.toString().trim();
			}
			return node;
		}
	}

	private static Charset CHARSET_UTF_32BE = Charset.forName("UTF-32BE");
	private static Charset CHARSET_UTF_32LE = Charset.forName("UTF-32LE");

	private static Charset getFileEncoding(String file) throws IOException {
		byte[] buff = new byte[4];

		try (InputStream is = new BufferedInputStream(Files.newInputStream(Paths.get(file)))) {
			int readSize = is.read(buff, 0, 4);

			if (readSize >= 4
					&& buff[0] == 0x00
					&& buff[1] == 0x00
					&& (buff[2] & 0xff) == 0xfe
					&& (buff[3] & 0xff) == 0xff) {
				return CHARSET_UTF_32BE;
			}

			if (readSize >= 4
					&& (buff[0] & 0xff) == 0xff
					&& (buff[1] & 0xff) == 0xfe
					&& buff[2] == 0x00
					&& buff[3] == 0x00) {
				return CHARSET_UTF_32LE;
			}

			if (readSize >= 2
					&& (buff[0] & 0xff) == 0xfe
					&& (buff[1] & 0xff) == 0xff) {
				return StandardCharsets.UTF_16BE;
			}

			if (readSize >= 2
					&& (buff[0] & 0xff) == 0xff
					&& (buff[1] & 0xff) == 0xfe) {
				return StandardCharsets.UTF_16LE;
			}

			return StandardCharsets.UTF_8;
		}
	}

	public static void writeToFile(Node node, String file) throws IOException {
		writeToFile(node, file, StandardCharsets.UTF_8);
	}

	public static void writeToFile(Node node, String file, Charset charset) throws IOException {
		Files.write(Paths.get(file), getString(node).getBytes(charset));
	}

	public static String getString(Node node) {
		StringBuilder buff = new StringBuilder();
		new WriterImpl(buff).writeRoot(node);
		return buff.toString();
	}

	private static class WriterImpl {
		private final StringBuilder buff;
		private int depth = 0;

		public WriterImpl(StringBuilder buff) {
			this.buff = buff;
		}

		public void writeRoot(Node node) {
			this.write(node);
			this.writeNewLine();
		}

		public void write(Node node) {
			if (node.array != null) {
				this.write('[');
				this.writeNewLine();
				this.depth++;

				for (int i = 0; i < node.array.size(); i++) {
					this.writeIndent();
					this.write(node.array.get(i));

					if (i < node.array.size() - 1) {
						this.write(',');
					}
					this.writeNewLine();
				}
				this.depth--;
				this.writeIndent();
				this.write(']');
			}
			else if (node.map != null) {
				this.write('{');
				this.writeNewLine();
				this.depth++;

				for (int i = 0; i < node.map.size(); i++) {
					Node.Pair pair = node.map.get(i);

					this.writeIndent();

					Node nameNode = new Node();
					nameNode.stringValue = pair.name;
					this.write(nameNode);

					this.write(':');
					this.writeSpace();
					this.write(pair.value);

					if (i < node.map.size() - 1) {
						this.write(',');
					}
					this.writeNewLine();
				}
				this.depth--;
				this.writeIndent();
				this.write('}');
			}
			else if (node.stringValue != null) {
				this.write('"');

				for (int i = 0; i < node.stringValue.length(); i++) {
					char chr = node.stringValue.charAt(i);

					if (chr == '"') {
						this.write("\\\"");
					}
					else if (chr == '\\') {
						this.write("\\\\");
					}
					/*
					else if (chr == '/') {
						//this.Write("\\/");
						this.write("\\u002F");
					}
					*/
					else if (chr == '\b') {
						this.write("\\b");
					}
					else if (chr == '\f') {
						this.write("\\f");
					}
					else if (chr == '\n') {
						this.write("\\n");
					}
					else if (chr == '\r') {
						this.write("\\r");
					}
					else if (chr == '\t') {
						this.write("\\t");
					}
					else {
						this.write(chr);
					}
				}
				this.write('"');
			}
			else if (node.wordValue != null) {
				this.write(node.wordValue);
			}
			else {
				throw new RuntimeException("JSON node error");
			}
		}

		private void writeIndent() {
			for (int i = 0; i < this.depth; i++) {
				this.write('\t');
			}
		}

		private void writeNewLine() {
			this.write("\r\n");
		}

		private void writeSpace() {
			this.write(' ');
		}

		private void write(String str) {
			this.buff.append(str);
		}

		private void write(char chr) {
			this.buff.append(chr);
		}
	}

	private static void writeLog(String message) {
		System.out.println(message);
	}
}
