package tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TCommon {
	public static final Charset CHARSET_SJIS = Charset.forName("Windows-31J");
	public static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");

	private TCommon() {
	}

	public interface RunnableEx {
		void run() throws Exception;
	}

	public static void re(RunnableEx routine) {
		try {
			routine.run();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> T re(Callable<T> routine) {
		try {
			return routine.call();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static List<String> getFiles(String dirPath) throws IOException {
		return getFiles(dirPath, "*", false);
	}

	public static List<String> getFiles(String dirPath, String wildCard, boolean allDirectories) throws IOException {
		List<String> result = new ArrayList<>();

		Path base = Paths.get(dirPath);
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + wildCard);

		if (allDirectories) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
				Files.walk(base)
						.filter(p -> Files.isRegularFile(p))
						.filter(p -> matcher.matches(p.getFileName()))
						.forEach(p -> result.add(p.toString()));
			}
		}
		else {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
				for (Path p : stream) {
					if (Files.isRegularFile(p) &&
							matcher.matches(p.getFileName())
							) {
						result.add(p.toString());
					}
				}
			}
		}

		return result;
	}

	public static List<String> getDirectories(String dirPath) throws IOException {
		return getDirectories(dirPath, "*", false);
	}

	public static List<String> getDirectories(String dirPath, String wildCard, boolean allDirectories) throws IOException {
		List<String> result = new ArrayList<>();

		Path base = Paths.get(dirPath);
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + wildCard);

		if (allDirectories) {
			Files.walk(base)
					.filter(p -> Files.isDirectory(p))
					.filter(p -> !p.equals(base)) // 自分自身は除外する。
					.filter(p -> matcher.matches(p.getFileName()))
					.forEach(p -> result.add(p.toString()));
		}
		else {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
				for (Path p : stream) {
					if (Files.isDirectory(p) &&
							matcher.matches(p.getFileName())
							) {
						result.add(p.toString());
					}
				}
			}
		}

		return result;
	}

	public static String getFileName(String strPath) {
		int p = Math.max(
				strPath.lastIndexOf('\\'),
				strPath.lastIndexOf('/')
				);
		if (p != -1) {
			return strPath.substring(p + 1);
		}
		return strPath;
	}

	public static String getFileNameWithoutExtension(String strPath) {
		strPath = getFileName(strPath);
		int p = strPath.lastIndexOf('.');
		if (p >= 1) {
			return strPath.substring(0, p);
		}
		return strPath;
	}

	public static String getExtension(String strPath) {
		strPath = getFileName(strPath);
		int p = strPath.lastIndexOf('.');
		if (p >= 1) {
			return strPath.substring(p);
		}
		return "";
	}

	// バイナリファイル open 読み込み
	public static FileInputStream openBinaryFileForRead(String filePath) throws IOException {
		return new FileInputStream(filePath);
	}

	// バイナリファイル open 書き出し(新規 or 上書き)
	public static FileOutputStream openBinaryFileForWrite(String filePath) throws IOException {
		return new FileOutputStream(filePath, false); // 上書き
	}

	// バイナリファイル open 書き出し(追記)
	public static FileOutputStream openBinaryFileForAppend(String filePath) throws IOException {
		return new FileOutputStream(filePath, true); // 追記
	}

	// テキストファイル open 読み込み
	public static BufferedReader openTextFileForRead(String filePath, Charset charset) throws IOException {
		return new BufferedReader(
			new InputStreamReader(
				new FileInputStream(filePath),
				charset
			)
		);
	}

	// テキストファイル open 書き出し(新規 or 上書き)
	public static BufferedWriter openTextFileForWrite(String filePath, Charset charset) throws IOException {
		return new BufferedWriter(
			new OutputStreamWriter(
				new FileOutputStream(filePath, false),
				charset
			)
		);
	}

	// テキストファイル open 書き出し(追記)
	public static BufferedWriter openTextFileForAppend(String filePath, Charset charset) throws IOException {
		return new BufferedWriter(
			new OutputStreamWriter(
				new FileOutputStream(filePath, true),
				charset
			)
		);
	}

	// バイナリ全部読み込み
	public static byte[] readAllBytes(String filePath) throws IOException {
		return Files.readAllBytes(Paths.get(filePath));
	}

	// テキスト全部読み込み（String）
	public static String readAllText(String filePath, Charset charset) throws IOException {
		return new String(readAllBytes(filePath), charset);
	}

	// 行単位で読み込み
	public static List<String> readAllLines(String filePath, Charset charset) throws IOException {
		return Files.readAllLines(Paths.get(filePath), charset);
	}

	public static byte[] readResource(Class<?> classObject, String resourcePath) throws IOException {
	 	try (InputStream reader = classObject.getResourceAsStream(resourcePath)) {
			return readToEnd(reader);
		}
	}

	public static byte[] readToEnd(InputStream reader) throws IOException {
		try (ByteArrayOutputStream mem = new ByteArrayOutputStream()) {
			byte[] buff = new byte[4096];
			int readSize;
			while ((readSize = reader.read(buff, 0, buff.length)) != -1) {
				mem.write(buff, 0, readSize);
			}
			return mem.toByteArray();
		}
	}

	// バイナリ全部書き出し
	public static void writeAllBytes(String filePath, byte[] data) throws IOException {
		Files.write(Paths.get(filePath), data);
	}

	// テキスト全部書き出し
	public static void writeAllText(String filePath, String text, Charset charset) throws IOException {
		writeAllBytes(filePath, text.getBytes(charset));
	}

	// 行単位で書き出し
	public static void writeAllLines(String filePath, List<String> lines, Charset charset) throws IOException {
		writeAllText(filePath, linesToText(lines), charset);
		//Files.write(Paths.get(filePath), lines, charset); // old -- 改行コードを制御できない。
	}

	public static int compare(int a, int b) {
		return a < b ? -1 : a > b ? 1 : 0;
	}

	public static int compare(long a, long b) {
		return a < b ? -1 : a > b ? 1 : 0;
	}

	public static int compare(double a, double b) {
		return a < b ? -1 : a > b ? 1 : 0;
	}

	public static int compare(String a, String b) {
		return a.compareTo(b);
	}

	public static int compareIgnoreCase(String a, String b) {
		return a.compareToIgnoreCase(b);
	}

	public static <T> int compare(Function<Integer, T> get1,
			Supplier<Integer> getSize1,
			Function<Integer, T> get2,
			Supplier<Integer> getSize2,
			Comparator<T> comp
			) {
		int s1 = getSize1.get();
		int s2 = getSize2.get();
		int s = Math.min(s1, s2);
		for (int i = 0; i < s; i++) {
			T v1 = get1.apply(i);
			T v2 = get2.apply(i);
			int ret = comp.compare(v1, v2);
			if (ret != 0) {
				return ret;
			}
		}
		return compare(s1, s2);
	}

	public static int compare(byte[] a, byte[] b) {
		return TCommon.<Integer>compare(i -> (int)a[i],
				() -> a.length,
				i -> (int)b[i],
				() -> b.length,
				(v1, v2) -> compare(v1, v2)
				);
	}

	public static int compare(int[] a, int[] b) {
		return TCommon.<Integer>compare(i -> a[i],
				() -> a.length,
				i -> b[i],
				() -> b.length,
				(v1, v2) -> compare(v1, v2)
				);
	}

	public static int compare(long[] a, long[] b) {
		return TCommon.<Long>compare(i -> a[i],
				() -> a.length,
				i -> b[i],
				() -> b.length,
				(v1, v2) -> compare(v1, v2)
				);
	}

	public static int compare(double[] a, double[] b) {
		return TCommon.<Double>compare(i -> a[i],
				() -> a.length,
				i -> b[i],
				() -> b.length,
				(v1, v2) -> compare(v1, v2)
				);
	}

	public static int compare(String[] a, String[] b) {
		return TCommon.<String>compare(i -> a[i],
				() -> a.length,
				i -> b[i],
				() -> b.length,
				(v1, v2) -> compare(v1, v2)
				);
	}

	public static int compareIgnoreCase(String[] a, String[] b) {
		return TCommon.<String>compare(i -> a[i],
				() -> a.length,
				i -> b[i],
				() -> b.length,
				(v1, v2) -> compareIgnoreCase(v1, v2)
				);
	}

	/**
	 * 行リストをテキストに変換します。
	 * @param lines 行リスト
	 * @return テキスト
	 */
	public static String linesToText(List<String> lines) {
		if (lines.size() == 0) {
			return "";
		}
		return String.join("\r\n", lines) + "\r\n";
	}

	/**
	 * テキストを行リストに変換します。
	 * @param text テキスト
	 * @return 行リスト
	 */
	public static List<String> textToLines(String text) {
		text = text.replace("\r", "");

		List<String> lines = TCommon.tokenize(text, "\n");

		if (lines.size() >= 1 && lines.get(lines.size() - 1).equals("")) {
			lines.remove(lines.size() - 1);
		}
		return lines;
	}

	public static List<String> tokenize(String str, String delimiters) {
		return tokenize(str, delimiters, false);
	}

	public static List<String> tokenize(String str, String delimiters, boolean meaningFlag) {
		return tokenize(str, delimiters, meaningFlag, false);
	}

	public static List<String> tokenize(String str, String delimiters, boolean meaningFlag, boolean ignoreEmpty) {
		return tokenize(str, delimiters, meaningFlag, ignoreEmpty, -1);
	}

	/**
	 * 文字列を区切り文字で分割する。
	 *
	 * @param str 文字列
	 * @param delimiters 区切り文字の集合
	 * @param meaningFlag 区切り文字(delimiters)以外を区切り文字とするか
	 * @param ignoreEmpty 空文字列のトークンを除去するか
	 * @param limit 最大トークン数(2～), -1 == 無制限
	 * @return トークン配列
	 */
	public static List<String> tokenize(String str, String delimiters, boolean meaningFlag, boolean ignoreEmpty, int limit) {
		List<String> tokens = new ArrayList<>();
		StringBuilder buff = new StringBuilder();

		for (int i = 0; i < str.length(); i++) {
			char chr = str.charAt(i);

			if ((delimiters.indexOf(chr) != -1) == meaningFlag || tokens.size() + 1 == limit) {
				buff.append(chr);
			}
			else {
				tokens.add(buff.toString());
				buff.setLength(0);
			}
		}
		tokens.add(buff.toString());

		if (ignoreEmpty) {
			tokens.removeIf(token -> token.equals(""));
		}
		return tokens;
	}

	public static String toHexString(byte[] data) {
		StringBuilder buff = new StringBuilder(data.length * 2);

		for (byte b : data) {
			buff.append(String.format("%02x", b & 0xFF));
		}
		return buff.toString();
	}

	public static byte[] hexStringToBytes(String strHex) {
		int len = strHex.length();

		if (len % 2 != 0) {
			throw new RuntimeException("Bad strHex");
		}
		byte[] result = new byte[len / 2];

		for (int i = 0; i < len; i += 2) {
			int hi = Character.digit(strHex.charAt(i + 0), 16);
			int lw = Character.digit(strHex.charAt(i + 1), 16);

			if (hi == -1 || lw == -1) {
				throw new RuntimeException("Bad strHex[i]");
			}
			result[i / 2] = (byte) ((hi << 4) + lw);
		}
		return result;
	}

	public static void createDir(String dirPath) throws IOException {
		Files.createDirectories(Paths.get(dirPath));
	}

	public static void deletePath(String strPath) throws IOException, InterruptedException {
	    batch(Arrays.asList(
	    		"DEL /F /Q \"" + strPath + "\"",
	    		"RD  /S /Q \"" + strPath + "\""
		        ));
	}

	public static void batch(
			List<String> commands
			) throws IOException, InterruptedException {

		batch(commands, "");
	}

	public static void batch(
			List<String> commands,
			String workingDir
			) throws IOException, InterruptedException {

		Path baseDir = Paths.get("C:\\temp");
		//Files.createDirectories(baseDir);

		Path tempDir = baseDir.resolve("batch_" + UUID.randomUUID().toString());
		Files.createDirectories(tempDir);

		//Path tempDir = Files.createTempDirectory("batch_");
		Path batFile = tempDir.resolve("run.bat");

		try {
			Files.write(batFile, commands, CHARSET_SJIS);

			List<String> bootCommand = new ArrayList<>();
			bootCommand.add("cmd");
			bootCommand.add("/c");
			bootCommand.add(batFile.toAbsolutePath().toString());

			ProcessBuilder pb = new ProcessBuilder(bootCommand);

			if (!"".equals(workingDir)) {
				pb.directory(new File(workingDir));
			}

			// コンソールにそのまま表示
			pb.inheritIO();

			// 同期実行
			pb.start().waitFor();
		}
		finally {
			try {
				Files.deleteIfExists(batFile);
				Files.deleteIfExists(tempDir);
			}
			catch (IOException ex) {
				// none
			}
		}
	}

	private static CRandom _cRandom = null;

	public static CRandom cRandom() {
		if (_cRandom == null) {
			_cRandom = new CRandom();
		}
		return _cRandom;
	}
}
