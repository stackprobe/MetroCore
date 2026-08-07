package webServices;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

public abstract class SockServer {
	/**
	 * ポート番号
	 */
	public int portNo = 59999;

	/**
	 * 接続待ちキューの長さ
	 */
	public int backlog = 300;

	/**
	 * 最大同時接続数
	 */
	public int connectMax = 100;

	/**
	 * 処理の合間に呼ばれる処理
	 *
	 * 戻り値：
	 * -- サーバーを継続するか
	 */
	public BooleanSupplier interlude = () -> true;
	public volatile boolean stopRequested = false;

	// <---- init if needed

	private ExecutorService executor;
	private Semaphore connectionLimiter;

	/**
	 * サーバーロジック
	 *
	 * C# 版では通信量を返す協調処理だったが、Java 版では接続ごとの worker thread で完了まで処理する。
	 *
	 * @param channel 接続チャネル
	 */
	protected abstract void connected(SockChannel channel) throws Exception;

	public void run() {
		SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "server starting...");

		executor = Executors.newCachedThreadPool();
		connectionLimiter = new Semaphore(connectMax);

		try (ServerSocket listener = new ServerSocket(portNo, backlog)) {
			listener.setSoTimeout(100);
			SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "server started.");

			while (!stopRequested && interlude.getAsBoolean()) {
				try {
					if (!connectionLimiter.tryAcquire()) {
						Thread.sleep(10L);
						continue;
					}

					Socket handler;
					try {
						handler = listener.accept();
					}
					catch (SocketTimeoutException ex) {
						connectionLimiter.release();
						continue;
					}

					SockUtility.TimeWaitMonitor.i().connected();
					executor.submit(() -> handleAcceptedSocket(handler));
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "server stopping...");
		}
		catch (Exception ex) {
			SockCommon.writeLog(SockCommon.ErrorLevel_e.FATAL, ex);
		}
		finally {
			if (executor != null) {
				executor.shutdownNow();
				executor = null;
			}
			SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "server stopped.");
		}
	}

	public void stop() {
		stopRequested = true;
	}

	private void handleAcceptedSocket(Socket handler) {
		SockChannel channel = new SockChannel();

		try {
			channel.handler = handler;
			channel.id = SockUtility.IDIssuer.issue();
			channel.bodyOutputStream = HTTPBodyOutputStream.create(HTTPServerChannel.bodyOnStorage);
			channel.parent = this;

			SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "connection started " + channel.id);
			connected(channel);
		}
		catch (Exception ex) {
			if (channel.firstLineRecving && ex instanceof SockChannel.RecvIdleTimeoutException) {
				SockCommon.writeLog(SockCommon.ErrorLevel_e.FIRST_LINE_TIMEOUT, null);
			}
			else {
				SockCommon.writeLog(SockCommon.ErrorLevel_e.NETWORK_OR_SERVER_LOGIC, ex);
			}
		}
		finally {
			SockCommon.writeLog(SockCommon.ErrorLevel_e.INFO, "connection ended " + channel.id);
			disconnect(channel);
			SockUtility.TimeWaitMonitor.i().disconnect();
			connectionLimiter.release();
		}
	}

	private void disconnect(SockChannel channel) {
		try {
			if (channel.handler != null) {
				channel.handler.close();
			}
		}
		catch (IOException ex) {
			SockCommon.writeLog(SockCommon.ErrorLevel_e.NETWORK, ex);
		}

		try {
			if (channel.bodyOutputStream != null) {
				channel.bodyOutputStream.close();
			}
		}
		catch (IOException ex) {
			SockCommon.writeLog(SockCommon.ErrorLevel_e.NETWORK, ex);
		}

		SockUtility.IDIssuer.discard(channel.id);
	}
}
