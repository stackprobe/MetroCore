package tools;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CsvFileReader implements Closeable {
	public static final Charset CHARSET_SJIS = Charset.forName("Windows-31J");
	public static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");

	public static final char DELIMITER_COMMA = ',';  // for .csv
	public static final char DELIMITER_SPACE = ' ';  // for .ssv
	public static final char DELIMITER_TAB   = '\t'; // for .tsv

	private final char delimiter;
	private BufferedReader reader;

	public CsvFileReader(String filePath) throws IOException {
		this(filePath, getFileEncoding(filePath));
	}

	public CsvFileReader(String filePath, char delimiter) throws IOException {
		this(filePath, getFileEncoding(filePath), delimiter);
	}

	public CsvFileReader(String filePath, Charset encoding) throws IOException {
		this(filePath, encoding, DELIMITER_COMMA);
	}

	public CsvFileReader(String filePath, Charset encoding, char delimiter) throws IOException {
		this.delimiter = delimiter;
		this.reader = Files.newBufferedReader(Paths.get(filePath), encoding);
	}

	private static Charset getFileEncoding(String filePath) throws IOException {
		Path path = Paths.get(filePath);

		try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path))) {
			int b1 = in.read();
			int b2 = in.read();
			int b3 = in.read();

			// ? UTF-8 with BOM
			if (b1 == 0xEF &&
					b2 == 0xBB &&
					b3 == 0xBF
					) {
				return CHARSET_UTF8;
			}
		}
		return CHARSET_SJIS;
	}

	private int lastChar;

	private int readChar() throws IOException {
		do {
			this.lastChar = this.reader.read();
		}
		while (this.lastChar == '\r');

		return this.lastChar;
	}

	private boolean enclosedCell;

	private String readCell() throws IOException {
		StringBuilder buff = new StringBuilder();

		if (this.readChar() == '"') {
			while (this.readChar() != -1 && (this.lastChar != '"' || this.readChar() == '"')) {
				buff.append((char)this.lastChar);
			}
			this.enclosedCell = true;
		}
		else {
			while (this.lastChar != -1 && this.lastChar != '\n' && this.lastChar != this.delimiter) {
				buff.append((char)this.lastChar);
				this.readChar();
			}
			this.enclosedCell = false;
		}
		return buff.toString();
	}

	public String[] readRow() throws IOException {
		List<String> row = new ArrayList<String>();

		do {
			row.add(this.readCell());
		}
		while (this.lastChar != -1 && this.lastChar != '\n');

		if (this.lastChar == -1 && row.size() == 1 && row.get(0).equals("") && !this.enclosedCell) {
			return null;
		}

		return row.toArray(new String[0]);
	}

	public List<String[]> readToEnd() throws IOException {
		List<String[]> rows = new ArrayList<String[]>();

		for (;;) {
			String[] row = this.readRow();

			if (row == null) {
				break;
			}
			rows.add(row);
		}
		return rows;
	}

	@Override
	public void close() throws IOException {
		if (this.reader != null) {
			this.reader.close();
			this.reader = null;
		}
	}

	public static List<String[]> readToEnd(String filePath) throws IOException {
		return readToEnd(filePath, getFileEncoding(filePath));
	}

	public static List<String[]> readToEnd(String filePath, char delimiter) throws IOException {
		return readToEnd(filePath, getFileEncoding(filePath), delimiter);
	}

	public static List<String[]> readToEnd(String filePath, Charset encoding) throws IOException {
		return readToEnd(filePath, encoding, DELIMITER_COMMA);
	}

	public static List<String[]> readToEnd(String filePath, Charset encoding, char delimiter) throws IOException {
		try (CsvFileReader reader = new CsvFileReader(filePath, encoding, delimiter)) {
			return reader.readToEnd();
		}
	}
}
