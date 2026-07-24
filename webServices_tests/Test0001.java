package webServices_tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import webServices.HTTPServer;
import webServices.HTTPServerChannel;
import webServices.SockChannel;
import webServices.SockCommon;

public class Test0001 {
	public static void main(String[] args) {
		try {
			testMain();
		}
		catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private static void testMain() throws IOException {
		HTTPServer hs = new HTTPServer();

		hs.portNo = 80;
		hs.backlog = 300;
		hs.connectMax = 100;
		hs.interlude = () -> !isConsoleKeyAvailable();
		hs.httpConnected = channel -> {
			// 以下は安全に表示可能な文字列であることが保証される。
			// -- firstLine == ASCII && not-null
			// -- method == ASCII && not-null
			// -- pathQuery == SJIS && not-null
			// -- httpVersion == ASCII && not-null
			// -- headerPairs == not-null && (全てのキーと値について ASCII && not-null)
			// ---- ASCII == [\u0020-\u007e]*
			// ---- SJIS == ToJString(, true, false, false, true)
			// 以下も保証される。
			// -- body == not-null

			try {
				System.out.println(channel.firstLine);
				System.out.println(channel.method);
				System.out.println(channel.pathQuery);
				System.out.println(channel.httpVersion);
				System.out.println(channel.headerPairs.stream()
						.map(pair -> pair[0] + "=" + pair[1])
						.collect(Collectors.joining(", ")));
				System.out.println(toBitConverterString(channel.body.toByteArray()));

				channel.resStatus = 200;
				channel.resHeaderPairs.add(new String[] { "Content-Type", "text/plain; charset=US-ASCII" });
				channel.resHeaderPairs.add(new String[] { "X-ResHeader-001", "123" });
				channel.resHeaderPairs.add(new String[] { "X-ResHeader-002", "ABC" });
				channel.resHeaderPairs.add(new String[] { "X-ResHeader-003", "abc" });
				channel.resBody = List.of("Hello, Happy World!".getBytes(StandardCharsets.US_ASCII));
				channel.resBodyLength = -1L;
			}
			catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		};

		SockChannel.threadTimeoutMillis = 100;

		HTTPServer.keepAliveTimeoutMillis = 5000;

		HTTPServerChannel.requestTimeoutMillis = -1;
		HTTPServerChannel.responseTimeoutMillis = -1;
		HTTPServerChannel.firstLineTimeoutMillis = 2000;
		HTTPServerChannel.idleTimeoutMillis = 180000; // 3 min
		HTTPServerChannel.bodySizeMax = 512000000; // 512 MB
		HTTPServerChannel.bodyOnStorage = false;

		SockCommon.TimeWaitMonitor.ctrRotSec = 60;
		SockCommon.TimeWaitMonitor.counterNum = 5;
		SockCommon.TimeWaitMonitor.countLimit = 10000;
		SockCommon.TimeWaitMonitor.apply();

		hs.run();
	}

	private static String toBitConverterString(byte[] data) {
		StringBuilder buff = new StringBuilder();

		for (int index = 0; index < data.length; index++) {
			if (0 < index) {
				buff.append('-');
			}
			buff.append(String.format("%02X", data[index] & 0xff));
		}
		return buff.toString();
	}

	private static boolean isConsoleKeyAvailable() {
		try {
			return System.in.available() != 0;
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
}
