package webServices;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public class SockChannel {
	public Socket handler;
	public int id;
	public HTTPBodyOutputStream.IBOS bodyOutputStream;
	public SockServer parent;

	// <---- need init

	/**
	 * スレッド占用タイムアウト_ミリ秒
	 *
	 * -1 == INFINITE
	 */
	public static int threadTimeoutMillis = 100;

	// <---- init if needed

	public boolean firstLineRecving = false;

	/**
	 * セッションタイムアウト日時
	 *
	 * null == INFINITE
	 */
	public Instant sessionTimeoutTime = null;

	/**
	 * 無通信タイムアウト_ミリ秒
	 *
	 * -1 == INFINITE
	 */
	public int currIdleTimeoutMillis = -1;

	private int calcSocketTimeoutMillis(Instant startedTime) throws IOException {
		int timeoutMillis = currIdleTimeoutMillis;

		if (sessionTimeoutTime != null) {
			long remaining = Duration.between(Instant.now(), sessionTimeoutTime).toMillis();

			if (remaining <= 0L) {
				throw new IOException("session timeout");
			}
			if (timeoutMillis == -1 || remaining < timeoutMillis) {
				timeoutMillis = (int)Math.min(Integer.MAX_VALUE, remaining);
			}
		}
		if (timeoutMillis == -1) {
			return 0;
		}
		long elapsed = Duration.between(startedTime, Instant.now()).toMillis();
		long remainingIdle = timeoutMillis - elapsed;

		if (remainingIdle <= 0L) {
			throw new RecvIdleTimeoutException();
		}
		return (int)Math.max(1L, Math.min(Integer.MAX_VALUE, remainingIdle));
	}

	public byte[] recv(int size) throws IOException {
		byte[] data = new byte[size];
		int offset = 0;

		while (0 < size) {
			int recvSize = tryRecv(data, offset, size);
			size -= recvSize;
			offset += recvSize;
		}
		return data;
	}

	public void recv(int size, Consumer<byte[]> a_return) throws IOException {
		a_return.accept(recv(size));
	}

	public int tryRecv(byte[] data, int offset, int size) throws IOException {
		Instant startedTime = Instant.now();
		InputStream reader = handler.getInputStream();

		for (;;) {
			handler.setSoTimeout(calcSocketTimeoutMillis(startedTime));

			try {
				int recvSize = SockCommon.nb("recv", () -> {
					try {
						return reader.read(data, offset, size);
					}
					catch (IOException ex) {
						throw new SockUtilityRuntimeException(ex);
					}
				});

				if (recvSize <= 0) {
					throw new IOException("recv disconnected");
				}
				// 長い無通信時間をモニタする。
				if (10L <= Duration.between(startedTime, Instant.now()).toSeconds()) {
					SockCommon.writeLog(SockCommon.ErrorLevel_e.WARNING, "IDLE-RECV " + Duration.between(startedTime, Instant.now()).toMillis() + "ms");
				}
				return recvSize;
			}
			catch (SockUtilityRuntimeException ex) {
				Throwable cause = ex.getCause();

				if (cause instanceof SocketTimeoutException) {
					throw new RecvIdleTimeoutException();
				}
				if (cause instanceof IOException) {
					throw (IOException)cause;
				}
				throw ex;
			}
		}
	}

	public void send(byte[] data) throws IOException {
		int offset = 0;
		int size = data.length;

		while (0 < size) {
			int vSize = Math.min(4000000, size);
			int sentSize = trySend(data, offset, vSize);
			size -= sentSize;
			offset += sentSize;
		}
	}

	private int trySend(byte[] data, int offset, int size) throws IOException {
		Instant startedTime = Instant.now();
		OutputStream writer = handler.getOutputStream();

		handler.setSoTimeout(calcSocketTimeoutMillis(startedTime));
		try {
			SockCommon.nb("send", () -> {
				try {
					writer.write(data, offset, size);
					writer.flush();
					return 0;
				}
				catch (IOException ex) {
					throw new SockUtilityRuntimeException(ex);
				}
			});
		}
		catch (SockUtilityRuntimeException ex) {
			Throwable cause = ex.getCause();

			if (cause instanceof IOException) {
				throw (IOException)cause;
			}
			throw ex;
		}
		if (10L <= Duration.between(startedTime, Instant.now()).toSeconds()) {
			SockCommon.writeLog(SockCommon.ErrorLevel_e.WARNING, "IDLE-SEND " + Duration.between(startedTime, Instant.now()).toMillis() + "ms");
		}
		return size;
	}

	/**
	 * 受信の無通信タイムアウト
	 */
	public static class RecvIdleTimeoutException extends IOException {
		private static final long serialVersionUID = 1L;
	}

	private static class SockUtilityRuntimeException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public SockUtilityRuntimeException(Throwable cause) {
			super(cause);
		}
	}
}
