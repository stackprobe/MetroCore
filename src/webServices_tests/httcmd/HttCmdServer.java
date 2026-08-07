package webServices_tests.httcmd;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

import webServices.HTTPServer;
import webServices.HTTPServerChannel;
import webServices.SockChannel;
import webServices.SockCommon;

public class HttCmdServer {
	private Path docRoot;

	public void run(String docRoot) {
		this.docRoot = Path.of(docRoot).toAbsolutePath().normalize();

		if (!Files.isDirectory(this.docRoot)) {
			throw new IllegalArgumentException("DocRoot is not found: " + this.docRoot);
		}

		HTTPServer hs = new HTTPServer();

		hs.portNo = 80;
		hs.backlog = 300;
		hs.connectMax = 100;
		hs.httpConnected = channel -> {
			try {
				connected(channel);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		};

		SockChannel.threadTimeoutMillis = 100;

		HTTPServer.keepAliveTimeoutMillis = 5000;

		HTTPServerChannel.requestTimeoutMillis = 10000;
		HTTPServerChannel.responseTimeoutMillis = -1;
		HTTPServerChannel.firstLineTimeoutMillis = 2000;
		HTTPServerChannel.idleTimeoutMillis = 600000;
		HTTPServerChannel.bodySizeMax = 0;
		HTTPServerChannel.bodyOnStorage = false;

		SockCommon.TimeWaitMonitor.ctrRotSec = 60;
		SockCommon.TimeWaitMonitor.counterNum = 5;
		SockCommon.TimeWaitMonitor.countLimit = 10000;
		SockCommon.TimeWaitMonitor.apply();

		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "HTTCmd-Start");
		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "DocRoot: " + this.docRoot);
		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "PortNo: " + hs.portNo);

		hs.run();

		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "HTTCmd-End");
	}

	private void connected(HTTPServerChannel channel) throws Exception {
		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "CLIENT " + remoteEndpoint(channel));

		if (10 < channel.method.length()) {
			throw new IOException("Received method is too long");
		}

		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "METHOD " + channel.method);

		boolean head;
		if (channel.method.equals("HEAD")) {
			head = true;
		}
		else if (channel.method.equals("GET")) {
			head = false;
		}
		else {
			throw new IOException("Unsupported method: " + channel.method);
		}

		String host = getHeaderValue(channel, "Host");
		String urlPath = removeQuery(channel.pathQuery);

		if (1000 < urlPath.length()) {
			throw new IOException("Received path is too long");
		}

		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "URL-PT " + urlPath);

		Path path;
		String relPath;
		boolean targetToFile = false;

		if (urlPath.equals("/")) {
			relPath = "";
			path = docRoot;
		}
		else {
			relPath = toFairRelPath(urlPath);
			path = docRoot.resolve(relPath).normalize();
		}

		if (urlPath.endsWith("/")) {
			Path htm = path.resolve("index.htm");
			Path html = path.resolve("index.html");

			path = Files.exists(htm) ? htm : html;
			targetToFile = true;
		}

		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "DST-PT " + path);

		if (!targetToFile && Files.isDirectory(path)) {
			if (host == null) {
				throw new IOException("No HOST header value");
			}
			channel.resStatus = 301;
			channel.resHeaderPairs.add(new String[] { "Location", "http://" + host + "/" + encodePathSegments(relPath) });
			channel.resBody = null;
			channel.resBodyLength = -1L;
		}
		else if (Files.isRegularFile(path)) {
			long fileSize = Files.size(path);

			channel.resStatus = 200;
			channel.resHeaderPairs.add(new String[] { "Content-Type", ContentTypeCollection.i.getContentType(getExtension(path)) });
			channel.resBody = readFile(path);
			channel.resBodyLength = fileSize;

			if (head) {
				channel.resHeaderPairs.add(new String[] { "Content-Length", Long.toString(fileSize) });
				channel.resHeaderPairs.add(new String[] { "X-Last-Modified-Time", Long.toString(Files.getLastModifiedTime(path).toMillis()) });
				channel.resBody = null;
			}
		}
		else {
			channel.resStatus = 404;
			channel.resBody = null;
			channel.resBodyLength = -1L;
		}

		channel.resHeaderPairs.add(new String[] { "Server", "HTTCmd" });

		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "RES-STATUS " + channel.resStatus);
		for (String[] pair : channel.resHeaderPairs) {
			SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "RES-HEADER " + pair[0] + " = " + pair[1]);
		}
		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "RES-BODY " + (channel.resBody != null));
		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "RES-BODY-LENGTH " + channel.resBodyLength);
	}

	private static String remoteEndpoint(HTTPServerChannel channel) {
		if (channel.channel.handler.getRemoteSocketAddress() instanceof InetSocketAddress) {
			InetSocketAddress endpoint = (InetSocketAddress)channel.channel.handler.getRemoteSocketAddress();
			return endpoint.toString();
		}
		return String.valueOf(channel.channel.handler.getRemoteSocketAddress());
	}

	private static String getHeaderValue(HTTPServerChannel channel, String name) {
		for (String[] pair : channel.headerPairs) {
			if (pair[0].equalsIgnoreCase(name)) {
				return pair[1];
			}
		}
		return null;
	}

	private static String removeQuery(String pathQuery) {
		int index = pathQuery.indexOf('?');
		return index == -1 ? pathQuery : pathQuery.substring(0, index);
	}

	private String toFairRelPath(String urlPath) throws IOException {
		String path = urlPath.replace('\\', '/');

		while (path.startsWith("/")) {
			path = path.substring(1);
		}
		if (path.isEmpty()) {
			throw new IOException("bad path");
		}

		try {
			Path relPath = Path.of(path).normalize();

			if (relPath.isAbsolute() || relPath.startsWith("..")) {
				throw new IOException("bad path");
			}
			Path resolved = docRoot.resolve(relPath).normalize();

			if (!resolved.startsWith(docRoot)) {
				throw new IOException("bad path");
			}
			return relPath.toString();
		}
		catch (InvalidPathException ex) {
			throw new IOException("bad path", ex);
		}
	}

	private static String encodePathSegments(String relPath) {
		String normalized = relPath.replace('\\', '/');
		StringBuilder buff = new StringBuilder();

		for (String segment : normalized.split("/", -1)) {
			if (!segment.isEmpty()) {
				buff.append(encodeUrl(segment));
				buff.append('/');
			}
		}
		return buff.toString();
	}

	private static String encodeUrl(String str) {
		StringBuilder buff = new StringBuilder();

		for (byte chr : str.getBytes(StandardCharsets.UTF_8)) {
			buff.append('%');
			buff.append(String.format("%02x", chr & 0xff));
		}
		return buff.toString();
	}

	private static String getExtension(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot == -1 ? "" : name.substring(dot);
	}

	private static Iterable<byte[]> readFile(Path file) {
		return () -> new Iterator<byte[]>() {
			private static final int READ_SIZE = 512 * 1024;
			private InputStream reader;
			private byte[] next;
			private boolean ended;

			@Override
			public boolean hasNext() {
				if (next == null && !ended) {
					readNext();
				}
				return next != null;
			}

			@Override
			public byte[] next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				byte[] ret = next;
				next = null;
				return ret;
			}

			private void readNext() {
				try {
					if (reader == null) {
						reader = Files.newInputStream(file);
					}

					byte[] buffer = new byte[READ_SIZE];
					int size = reader.read(buffer);

					if (size == -1) {
						close();
						return;
					}
					if (size == buffer.length) {
						next = buffer;
					}
					else {
						next = new byte[size];
						System.arraycopy(buffer, 0, next, 0, size);
					}
				}
				catch (IOException ex) {
					close();
					throw new UncheckedIOException(ex);
				}
			}

			private void close() {
				ended = true;

				if (reader != null) {
					try {
						reader.close();
					}
					catch (IOException ex) {
						throw new UncheckedIOException(ex);
					}
					finally {
						reader = null;
					}
				}
			}
		};
	}
}
