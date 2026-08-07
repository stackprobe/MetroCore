package tools;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class CsvFileWriter implements Closeable {
	public static final Charset CHARSET_SJIS = Charset.forName("Windows-31J");

	public static final char DELIMITER_COMMA = ',';   // for .csv
	public static final char DELIMITER_SPACE = ' ';   // for .ssv
	public static final char DELIMITER_TAB   = '\t';  // for .tsv

	private final char delimiter;
	private BufferedWriter writer;

	/**
	 * 次に書き込むセルが行の最初のセルか
	 */
	private boolean firstCell = true;

	public CsvFileWriter(String filePath) throws IOException {
		this(filePath, false);
	}

	public CsvFileWriter(String filePath, boolean append) throws IOException {
		this(filePath, append, CHARSET_SJIS);
	}

	public CsvFileWriter(String filePath, boolean append, Charset encoding) throws IOException {
		this(filePath, append, encoding, DELIMITER_COMMA);
	}

	public CsvFileWriter(String filePath, boolean append, Charset encoding, char delimiter) throws IOException {
		this.delimiter = delimiter;

		Path path = Paths.get(filePath);

		if (append) {
			this.writer = Files.newBufferedWriter(
					path,
					encoding,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND
					);
		}
		else {
			this.writer = Files.newBufferedWriter(
					path,
					encoding,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
					);
		}
	}

	public void writeCell(String cell) throws IOException {
		if (cell == null) {
			cell = "";
		}

		if (this.firstCell) {
			this.firstCell = false;
		}
		else {
			this.writer.write(this.delimiter);
		}

		if (cell.indexOf('"') != -1 ||
				cell.indexOf('\r') != -1 ||
				cell.indexOf('\n') != -1 ||
				cell.indexOf(this.delimiter) != -1
				) {

			this.writer.write('"');
			this.writer.write(cell.replace("\"", "\"\""));
			this.writer.write('"');
		}
		else {
			this.writer.write(cell);
		}
	}

	public void endRow() throws IOException {
		this.writer.write("\r\n");
		this.firstCell = true;
	}

	public void writeCells(List<String> cells) throws IOException {
		for (String cell : cells) {
			this.writeCell(cell);
		}
	}

	public void writeRow(List<String> row) throws IOException {
		for (String cell : row) {
			this.writeCell(cell);
		}
		this.endRow();
	}

	public void writeRows(List<String[]> rows) throws IOException {
		for (String[] row : rows) {
			for (String cell : row) {
				this.writeCell(cell);
			}
			this.endRow();
		}
	}

	@Override
	public void close() throws IOException {
		if (this.writer != null) {
			this.writer.close();
			this.writer = null;
		}
	}

	public static void writeRows(String filePath, List<String[]> rows) throws IOException {
		writeRows(filePath, false, rows);
	}

	public static void writeRows(String filePath, boolean append, List<String[]> rows) throws IOException {
		writeRows(filePath, append, CHARSET_SJIS, rows);
	}

	public static void writeRows(String filePath, boolean append, Charset encoding, List<String[]> rows) throws IOException {
		writeRows(filePath, append, encoding, DELIMITER_COMMA, rows);
	}

	public static void writeRows(String filePath, boolean append, Charset encoding, char delimiter, List<String[]> rows) throws IOException {
		try (CsvFileWriter writer = new CsvFileWriter(filePath, append, encoding, delimiter)) {
			for (String[] row : rows) {
				for (String cell : row) {
					writer.writeCell(cell);
				}
				writer.endRow();
			}
		}
	}
}
