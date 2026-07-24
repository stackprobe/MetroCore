package webServices;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class SockUtility {
	public static final byte[] EMPTY_BYTES = new byte[0];
	private static int nbProcMillisLimit = 50;
	private static final SecureRandom secureRandom = new SecureRandom();

	private SockUtility() {
	}

	public interface WriteHandler {
		void write(byte[] data, int offset, int count) throws IOException;
	}

	public enum ErrorLevel {
		INFO,
		WARNING,
		FIRST_LINE_TIMEOUT,
		NETWORK,
		NETWORK_OR_SERVER_LOGIC,
		FATAL,
	}

	public static void writeLog(ErrorLevel errorLevel, Object message) {
		String text = message instanceof Throwable ? ((Throwable)message).getMessage() : String.valueOf(message);

		switch (errorLevel) {
		case INFO:
			System.out.println(text);
			break;
		case WARNING:
			System.out.println("[WARNING] " + text);
			break;
		case FIRST_LINE_TIMEOUT:
			System.out.println("[FIRST-LINE-TIMEOUT]");
			break;
		case NETWORK:
			System.out.println("[NETWORK] " + text);
			break;
		case NETWORK_OR_SERVER_LOGIC:
			System.out.println("[NETWORK-SERVER-LOGIC] " + text);
			break;
		case FATAL:
			System.out.println("[FATAL] " + text);
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public static <T> T nb(String title, Supplier<T> routine) {
		Instant started = Instant.now();
		try {
			return routine.get();
		}
		finally {
			long millis = Duration.between(started, Instant.now()).toMillis();

			if (nbProcMillisLimit < millis) {
				writeLog(ErrorLevel.WARNING, "nb-process took time. " + title + " " + Thread.currentThread().getId() + " " + millis + " (" + nbProcMillisLimit + ")");
				nbProcMillisLimit++;
			}
		}
	}

	public static boolean containsIgnoreCase(String str, String pattern) {
		return str.toLowerCase().contains(pattern.toLowerCase());
	}

	public static List<String> tokenize(String str, String delimiter) {
		return List.of(str.split(java.util.regex.Pattern.quote(delimiter), -1));
	}

	public static boolean isValidUtf8(byte[] data) {
		try {
			StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data));
			return true;
		}
		catch (CharacterCodingException ex) {
			return false;
		}
	}

	public static byte[] readToEnd(InputStream reader) throws IOException {
		try (ByteArrayOutputStream writer = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[4096];
			int readSize;

			while ((readSize = reader.read(buffer)) != -1) {
				writer.write(buffer, 0, readSize);
			}
			return writer.toByteArray();
		}
	}

	public static void readToEnd(InputStream reader, WriteHandler writer) throws IOException {
		byte[] buffer = new byte[4096];
		int readSize;

		while ((readSize = reader.read(buffer)) != -1) {
			writer.write(buffer, 0, readSize);
		}
	}

	public static Path createTempDir() throws IOException {
		return Files.createTempDirectory("MetroCoreWebServices_");
	}

	public static void deletePath(Path path) throws IOException {
		if (path == null || !Files.exists(path)) {
			return;
		}
		if (Files.isDirectory(path)) {
			try (var stream = Files.walk(path)) {
				for (Path p : stream.sorted((a, b) -> b.compareTo(a)).toList()) {
					Files.deleteIfExists(p);
				}
			}
		}
		else {
			Files.deleteIfExists(path);
		}
	}

	public static void shuffleP4(List<?> list) {
		if (list.size() < 14) {
			Collections.shuffle(list);
		}
		else {
			for (int index = 0; index < 7; index++) {
				Collections.swap(list, index, secureRandom.nextInt(list.size()));
			}
		}
	}

	public static byte[] randomBytes(int size) {
		byte[] data = new byte[size];
		secureRandom.nextBytes(data);
		return data;
	}

	public static class IDIssuer {
		private static final int INIT_COUNTER_NUM = 9;
		private static final Queue<Integer> stocks = new ConcurrentLinkedQueue<>();
		private static final AtomicInteger counter = new AtomicInteger(INIT_COUNTER_NUM + 1);

		static {
			for (int id = 1; id <= INIT_COUNTER_NUM; id++) {
				stocks.add(id);
			}
		}

		private IDIssuer() {
		}

		public static int issue() {
			Integer stock = stocks.poll();
			return stock != null ? stock : counter.getAndIncrement();
		}

		public static void discard(int id) {
			stocks.add(id);
		}
	}

	public static class TimeWaitMonitor {
		// 参考値：
		// 動的ポートの数 16384 (49152 ～ 65535), TIME_WAIT-タイムアウト 4 min (240 sec) の場合 (Windowsの既定値)
		// ctrRotSec = 60
		// counterNum = 5       -- 直近 4 ～ 5 分間の切断回数を保持
		// countLimit = 10000   -- 50 ミリ秒間隔で接続＆切断し続けた場合 4 分間に 4800 回 --> TIME_WAIT 数 14800 (countLimit + 4800) を超えない。
		// - - -
		// 動的ポートの数 64511 (1025 ～ 65535), TIME_WAIT-タイムアウト 1 min (60 sec) の場合 (動的ポート最大)
		// ctrRotSec = 30
		// counterNum = 3       -- 直近 1 ～ 1.5 分間の切断回数を保持
		// countLimit = 60000   -- 50 ミリ秒間隔で接続＆切断し続けた場合 1 分間に 1200 回 --> TIME_WAIT 数 61200 (countLimit + 1200) を超えない。
		public static int countLimit = 10000;
		private static final TimeWaitMonitor INSTANCE = new TimeWaitMonitor();
		private final AtomicInteger connectedCount = new AtomicInteger();

		private TimeWaitMonitor() {
		}

		public static TimeWaitMonitor i() {
			return INSTANCE;
		}

		public void connected() {
			int count = connectedCount.incrementAndGet();
			if (countLimit < count) {
				writeLog(ErrorLevel.WARNING, "PORT-EXHAUSTION");
				try {
					// HACK: 送受信も止める。
					Thread.sleep(50L);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
		}

		public void disconnect() {
			connectedCount.updateAndGet(value -> Math.max(0, value - 1));
		}
	}

	public static class CtrCipher implements Closeable {
		public static CtrCipher createTemporary() {
			return new CtrCipher(randomBytes(32), randomBytes(16));
		}

		private final AESCipher transformer;
		private final byte[] initializationVector;
		private final byte[] counter = new byte[16];
		private final byte[] buffer = new byte[16];
		private int index;

		public CtrCipher(byte[] rawKey, byte[] iv) {
			if ((rawKey.length != 16 && rawKey.length != 24 && rawKey.length != 32) || iv.length != 16) {
				throw new IllegalArgumentException();
			}
			this.transformer = new AESCipher(rawKey);
			this.initializationVector = iv.clone();
			reset();
		}

		public void reset() {
			System.arraycopy(initializationVector, 0, counter, 0, 16);
			index = 16;
		}

		public byte next() {
			if (index == 16) {
				transformer.encryptBlock(counter, buffer);
				increment();
				index = 0;
			}
			return buffer[index++];
		}

		private void increment() {
			for (int i = 0; i < 16; i++) {
				int value = counter[i] & 0xff;

				if (value < 255) {
					counter[i] = (byte)(value + 1);
					break;
				}
				counter[i] = 0;
			}
		}

		public void mask(byte[] data) {
			mask(data, 0, data, 0, data.length);
		}

		public void mask(byte[] data, int offset, int count) {
			mask(data, offset, data, offset, count);
		}

		public void mask(byte[] src, int srcOffset, byte[] dest, int destOffset, int count) {
			for (int i = 0; i < count; i++) {
				dest[destOffset + i] = (byte)(src[srcOffset + i] ^ next());
			}
		}

		@Override
		public void close() {
		}
	}

	public static class AESCipher implements Closeable {
		private final Cipher encryptor;

		public AESCipher(byte[] rawKey) {
			try {
				encryptor = Cipher.getInstance("AES/ECB/NoPadding");
				encryptor.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(rawKey, "AES"));
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		public void encryptBlock(byte[] input, byte[] output) {
			if (input.length != 16 || output.length != 16) {
				throw new IllegalArgumentException();
			}
			try {
				byte[] encrypted = encryptor.doFinal(input);
				System.arraycopy(encrypted, 0, output, 0, 16);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public void close() {
		}
	}
}
