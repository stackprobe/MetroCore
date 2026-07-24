package webServices;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public class HTTPServer extends SockServer {
	/**
	 * サーバーロジック
	 *
	 * 引数：
	 * -- channel: 接続チャネル
	 */
	public Consumer<HTTPServerChannel> httpConnected = channel -> {
	};

	/**
	 * キープアライブのタイムアウト_ミリ秒
	 *
	 * -1 == INFINITE
	 */
	public static int keepAliveTimeoutMillis = 5000;

	// <---- init if needed

	public HTTPServer() {
		portNo = 80;
	}

	@Override
	protected void connected(SockChannel channel) throws Exception {
		Instant startedTime = Instant.now();

		for (;;) {
			HTTPServerChannel hsChannel = new HTTPServerChannel();
			hsChannel.channel = channel;

			hsChannel.recvRequest();

			SockCommon.nb("svlg", () -> {
				httpConnected.accept(hsChannel);
				return -1;
			});

			if (keepAliveTimeoutMillis != -1 && keepAliveTimeoutMillis < Duration.between(startedTime, Instant.now()).toMillis()) {
				hsChannel.keepAlive = false;
			}

			hsChannel.sendResponse();

			if (!hsChannel.keepAlive) {
				break;
			}
		}
	}
}
