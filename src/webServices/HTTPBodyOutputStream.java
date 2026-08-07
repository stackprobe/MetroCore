package webServices;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class HTTPBodyOutputStream {
	private HTTPBodyOutputStream() {
	}

	public static IBOS create(boolean fileMode) throws IOException {
		return fileMode ? new FileBOS() : new MemoryBOS();
	}

	public interface IBOS extends Closeable {
		/**
		 * バイト列を書き込む。
		 *
		 * @param data 書き込むバイト列
		 */
		void write(byte[] data) throws IOException;

		/**
		 * 書き込んだ総バイト数を返す。
		 *
		 * @return 書き込んだ総バイト数
		 */
		long getWroteSize();

		/**
		 * 書き込んだバイト列を返し、リセットする。
		 *
		 * @return 書き込んだバイト列
		 */
		byte[] toByteArray() throws IOException;

		/**
		 * 書き込んだバイト列をファイルに出力し、リセットする。
		 *
		 * @param destFile 出力ファイル
		 */
		void toFile(String destFile) throws IOException;

		/**
		 * 書き込んだバイト列を取得し、リセットする。
		 *
		 * @param writer 取得メソッド
		 */
		void readToEnd(SockUtility.WriteHandler writer) throws IOException;
	}

	private static class FileBOS implements IBOS {
		private Path workingDir;
		private Path bufferFile;
		private long wroteSize;
		private SockUtility.CtrCipher ctrCipher = SockUtility.CtrCipher.createTemporary();

		public FileBOS() throws IOException {
			workingDir = SockUtility.createTempDir();
			bufferFile = workingDir.resolve("body.bin");
		}

		@Override
		public void write(byte[] data) throws IOException {
			byte[] maskedPart = new byte[data.length];
			ctrCipher.mask(data, 0, maskedPart, 0, data.length);

			try (OutputStream writer = Files.newOutputStream(bufferFile, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
				writer.write(maskedPart);
			}
			wroteSize += data.length;
		}

		@Override
		public long getWroteSize() {
			return wroteSize;
		}

		@Override
		public byte[] toByteArray() throws IOException {
			if (wroteSize == 0L) {
				return SockUtility.EMPTY_BYTES;
			}
			byte[] data = Files.readAllBytes(bufferFile);
			Files.deleteIfExists(bufferFile);
			wroteSize = 0L;

			ctrCipher.reset();
			ctrCipher.mask(data);
			ctrCipher.reset();

			return data;
		}

		@Override
		public void toFile(String destFile) throws IOException {
			if (wroteSize == 0L) {
				Files.write(Path.of(destFile), SockUtility.EMPTY_BYTES);
				return;
			}
			ctrCipher.reset();

			try (InputStream reader = Files.newInputStream(bufferFile);
					OutputStream writer = Files.newOutputStream(Path.of(destFile))) {
				byte[] buffer = new byte[4096];
				int readSize;

				while ((readSize = reader.read(buffer)) != -1) {
					ctrCipher.mask(buffer, 0, readSize);
					writer.write(buffer, 0, readSize);
				}
			}

			Files.deleteIfExists(bufferFile);
			wroteSize = 0L;
			ctrCipher.reset();
		}

		@Override
		public void readToEnd(SockUtility.WriteHandler writer) throws IOException {
			if (wroteSize == 0L) {
				return;
			}
			ctrCipher.reset();

			try (InputStream reader = Files.newInputStream(bufferFile)) {
				byte[] buffer = new byte[4096];
				int readSize;

				while ((readSize = reader.read(buffer)) != -1) {
					ctrCipher.mask(buffer, 0, readSize);
					writer.write(buffer, 0, readSize);
				}
			}

			Files.deleteIfExists(bufferFile);
			wroteSize = 0L;
			ctrCipher.reset();
		}

		@Override
		public void close() throws IOException {
			if (workingDir != null) {
				SockUtility.deletePath(workingDir);
				workingDir = null;
				bufferFile = null;
				ctrCipher.close();
				ctrCipher = null;
			}
		}
	}

	private static class MemoryBOS implements IBOS {
		private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		@Override
		public void write(byte[] data) {
			buffer.write(data, 0, data.length);
		}

		@Override
		public long getWroteSize() {
			return buffer.size();
		}

		@Override
		public byte[] toByteArray() {
			byte[] data = buffer.toByteArray();
			buffer.reset();
			return data;
		}

		@Override
		public void toFile(String destFile) throws IOException {
			Files.write(Path.of(destFile), toByteArray());
		}

		@Override
		public void readToEnd(SockUtility.WriteHandler writer) throws IOException {
			try (ByteArrayInputStream reader = new ByteArrayInputStream(toByteArray())) {
				SockUtility.readToEnd(reader, writer);
			}
		}

		@Override
		public void close() throws IOException {
			if (buffer != null) {
				buffer.close();
				buffer = null;
			}
		}
	}
}
