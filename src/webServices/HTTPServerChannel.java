package webServices;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HTTPServerChannel {
	/**
	 * 要求タイムアウト_ミリ秒
	 *
	 * -1 == INFINITE
	 */
	public static int requestTimeoutMillis = -1;

	/**
	 * 応答タイムアウト_ミリ秒
	 *
	 * -1 == INFINITE
	 */
	public static int responseTimeoutMillis = -1;

	// memo: チャンク毎のタイムアウトは idleTimeoutMillis で代替する。

	/**
	 * リクエストの最初の行のみの無通信タイムアウト_ミリ秒
	 *
	 * -1 == INFINITE
	 */
	public static int firstLineTimeoutMillis = 2000;

	/**
	 * リクエストの最初の行以外の(レスポンスも含む)無通信タイムアウト_ミリ秒
	 *
	 * -1 == INFINITE
	 */
	public static int idleTimeoutMillis = 180000;

	/**
	 * リクエストのボディの最大サイズ_バイト数
	 *
	 * -1 == INFINITE
	 */
	public static long bodySizeMax = 512000000L;

	/**
	 * リクエストのボディをストレージ上でバッファリングする。
	 */
	public static boolean bodyOnStorage = false;

	// <---- init if needed

	public SockChannel channel;

	public void recvRequest() throws Exception {
		resetRequestState();

		channel.sessionTimeoutTime = timeoutMillisToInstant(requestTimeoutMillis);
		channel.currIdleTimeoutMillis = firstLineTimeoutMillis;
		channel.firstLineRecving = true;
		firstLine = recvLine();
		channel.firstLineRecving = false;

		List<String> tokens = SockUtility.tokenize(firstLine, " ");
		if (tokens.size() < 3) {
			throw new IOException("bad first line");
		}
		method = tokens.get(0);
		pathQuery = decodeURL(tokens.get(1));
		httpVersion = tokens.get(2);

		channel.currIdleTimeoutMillis = idleTimeoutMillis;
		recvHeader();
		checkHeader();

		if (expect100Continue) {
			sendLine("HTTP/1.1 100 CONTINUE");
			channel.send(CRLF);
		}
		recvBody();
	}

	private void resetRequestState() throws IOException {
		firstLine = null;
		method = null;
		pathQuery = null;
		httpVersion = null;
		headerPairs.clear();
		contentLength = 0L;
		chunked = false;
		contentType = null;
		expect100Continue = false;
		keepAlive = false;
		body = null;

		if (channel.bodyOutputStream != null) {
			channel.bodyOutputStream.close();
		}
		channel.bodyOutputStream = HTTPBodyOutputStream.create(bodyOnStorage);
	}

	private static Instant timeoutMillisToInstant(int millis) {
		return millis == -1 ? null : Instant.now().plusMillis(millis);
	}

	private static String decodeURL(String path) throws IOException {
		byte[] src = path.getBytes(StandardCharsets.US_ASCII);

		try (ByteArrayOutputStream writer = new ByteArrayOutputStream()) {
			for (int index = 0; index < src.length; index++) {
				if (src[index] == 0x25) { // ? '%'
					if (src.length <= index + 2) {
						throw new IOException("bad url escape");
					}
					int hi = Character.digit((char)src[index + 1], 16);
					int lo = Character.digit((char)src[index + 2], 16);

					if (hi == -1 || lo == -1) {
						throw new IOException("bad url escape");
					}
					writer.write((hi << 4) | lo);
					index += 2;
				}
				else if (src[index] == 0x2b) { // ? '+'
					writer.write(0x20); // ' '
				}
				else {
					writer.write(src[index]);
				}
			}

			byte[] bytes = writer.toByteArray();
			if (!SockUtility.isValidUtf8(bytes)) {
				throw new IOException("URL is not UTF-8");
			}
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	public String firstLine;
	public String method;
	public String pathQuery;
	public String httpVersion;
	public List<String[]> headerPairs = new ArrayList<>();
	public HTTPBodyOutputStream.IBOS body;

	private static final byte CR = 0x0d;
	private static final byte LF = 0x0a;
	private static final byte[] CRLF = new byte[] { CR, LF };

	private String recvLine() throws IOException {
		final int LINE_LEN_MAX = 128 * 1024;

		try (ByteArrayOutputStream writer = new ByteArrayOutputStream()) {
			int wroteSize = 0;

			for (;;) {
				byte chr = channel.recv(1)[0];

				if (chr == CR) {
					continue;
				}
				if (chr == LF) {
					break;
				}
				if (LINE_LEN_MAX <= wroteSize) {
					throw new IOException("received line is too long");
				}
				// ? not ASCII -> SPACE
				if (chr < 0x20 || 0x7e < chr) {
					chr = 0x20;
				}
				writer.write(chr);
				wroteSize++;
			}
			return writer.toString(StandardCharsets.US_ASCII);
		}
	}

	private void recvHeader() throws IOException {
		final int WEIGHT = 256;
		final int HEADER_LEN_MAX = 128 * 1024 + 256 * WEIGHT;

		int roughHeaderLength = 0;

		for (;;) {
			String line = recvLine();

			if (line.isEmpty()) {
				break;
			}

			roughHeaderLength += line.length() + WEIGHT;
			if (HEADER_LEN_MAX < roughHeaderLength) {
				throw new IOException("received header is too long");
			}

			if (line.charAt(0) <= ' ') {
				// HACK: 行折り畳み(line folding)対応
				// 行折り畳みは廃止されたっぽいけど念のため対応しておく。
				if (headerPairs.isEmpty()) {
					throw new IOException("bad folded header line");
				}
				String[] lastPair = headerPairs.get(headerPairs.size() - 1);
				lastPair[1] += " " + line.trim();
			}
			else {
				int colon = line.indexOf(':');

				if (colon == -1) {
					throw new IOException("bad header line");
				}
				headerPairs.add(new String[] {
						line.substring(0, colon).trim(),
						line.substring(colon + 1).trim(),
				});
			}
		}
	}

	public long contentLength = 0L;
	public boolean chunked = false;
	public String contentType = null;
	public boolean expect100Continue = false;
	public boolean keepAlive = false;

	private void checkHeader() throws IOException {
		for (String[] pair : headerPairs) {
			String key = pair[0];
			String value = pair[1];

			// rough limit
			if (1000 < key.length() || 1000 < value.length()) {
				SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "ignore header key and value: too long");
				continue;
			}

			if (key.equalsIgnoreCase("Content-Length")) {
				if (value.length() < 1 || 19 < value.length()) {
					throw new IOException("bad Content-Length value");
				}
				contentLength = Long.parseLong(value);
			}
			else if (key.equalsIgnoreCase("Transfer-Encoding")) {
				chunked = SockUtility.containsIgnoreCase(value, "chunked");
			}
			else if (key.equalsIgnoreCase("Content-Type")) {
				contentType = value;
			}
			else if (key.equalsIgnoreCase("Expect")) {
				expect100Continue = SockUtility.containsIgnoreCase(value, "100-continue");
			}
			else if (key.equalsIgnoreCase("Connection")) {
				keepAlive = SockUtility.containsIgnoreCase(value, "keep-alive");
			}
		}
	}

	private void recvBody() throws IOException {
		final int READ_SIZE_MAX = 1024 * 1024;
		HTTPBodyOutputStream.IBOS buff = channel.bodyOutputStream;

		if (chunked) {
			for (;;) {
				String line = recvLine();
				int semicolon = line.indexOf(';');

				// chunk-extension の削除
				if (semicolon != -1) {
					line = line.substring(0, semicolon);
				}
				line = line.trim();

				if (line.length() < 1 || 8 < line.length()) {
					throw new IOException("bad chunk-size line");
				}

				int size = Integer.parseInt(line, 16);

				if (size == 0) {
					break;
				}
				if (size < 0) {
					throw new IOException("bad chunk size");
				}
				if (bodySizeMax != -1L && bodySizeMax - buff.getWroteSize() < size) {
					throw new IOException("body is too large");
				}

				long chunkEnd = buff.getWroteSize() + size;

				while (buff.getWroteSize() < chunkEnd) {
					int readSize = (int)Math.min(READ_SIZE_MAX, chunkEnd - buff.getWroteSize());
					buff.write(channel.recv(readSize));
				}
				channel.recv(2);
			}

			// RFC 7230 4.1.2 Chunked Trailer Part
			for (;;) {
				if (recvLine().isEmpty()) {
					break;
				}
			}
		}
		else {
			if (contentLength < 0L) {
				throw new IOException("bad body size");
			}
			if (bodySizeMax != -1L && bodySizeMax < contentLength) {
				throw new IOException("body is too large");
			}

			while (buff.getWroteSize() < contentLength) {
				int readSize = (int)Math.min(READ_SIZE_MAX, contentLength - buff.getWroteSize());
				buff.write(channel.recv(readSize));
			}
		}
		body = buff;
	}

	// httpConnected 内で(必要に応じて)設定しなければならないフィールド -->

	public int resStatus = 200;
	public List<String[]> resHeaderPairs = new ArrayList<>();

	// ゼロバイトの要素を含んでも良い。
	// null == 応答ボディ無し(ゼロバイトの応答ボディではないことに注意)
	public Iterable<byte[]> resBody = null;

	// 応答ボディの長さをセットすること。
	// -1L == チャンクで応答する。
	public long resBodyLength = -1L;

	// <-- httpConnected 内で(必要に応じて)設定しなければならないフィールド

	public void sendResponse() throws Exception {
		body = null;
		channel.sessionTimeoutTime = timeoutMillisToInstant(responseTimeoutMillis);
		channel.currIdleTimeoutMillis = idleTimeoutMillis;

		sendLine("HTTP/1.1 " + resStatus + " AFTER SCHOOL TEA TIME");

		for (String[] pair : resHeaderPairs) {
			sendLine(pair[0] + ": " + pair[1]);
		}

		if (resBody == null) {
			endHeader();
		}
		else if (resBodyLength != -1L) {
			sendLine("Content-Length: " + resBodyLength);
			endHeader();

			long sentLength = 0L;
			for (byte[] resBodyPart : resBody) {
				channel.send(resBodyPart);
				sentLength += resBodyPart.length;
			}
			if (sentLength != resBodyLength) {
				throw new IOException("bad resBodyLength");
			}
		}
		else {
			Iterator<byte[]> iterator = resBody.iterator();

			if (iterator.hasNext()) {
				byte[] first = iterator.next();

				if (iterator.hasNext()) {
					sendLine("Transfer-Encoding: chunked");
					endHeader();
					sendChunk(first);

					do {
						sendChunk(iterator.next());
					}
					while (iterator.hasNext());

					sendLine("0");
					channel.send(CRLF);
				}
				else {
					sendLine("Content-Length: " + first.length);
					endHeader();
					channel.send(first);
				}
			}
			else {
				sendLine("Content-Length: 0");
				endHeader();
			}
		}
	}

	private void endHeader() throws IOException {
		sendLine("Connection: " + (keepAlive ? "keep-alive" : "close"));
		channel.send(CRLF);
	}

	private void sendChunk(byte[] chunk) throws IOException {
		if (1 <= chunk.length) {
			sendLine(Integer.toHexString(chunk.length));
			channel.send(chunk);
			channel.send(CRLF);
		}
	}

	private void sendLine(String line) throws IOException {
		channel.send(line.getBytes(StandardCharsets.US_ASCII));
		channel.send(CRLF);
	}
}
