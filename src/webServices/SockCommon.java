package webServices;

import java.util.List;
import java.util.function.Supplier;

public class SockCommon {
	public static final int WSAEWOULDBLOCK = 10035;

	private SockCommon() {
	}

	public enum ErrorLevel_e {
		INFO,
		WARNING,
		FIRST_LINE_TIMEOUT,
		NETWORK,
		NETWORK_OR_SERVER_LOGIC,
		FATAL,
	}

	public static void writeLog(ErrorLevel_e errorLevel, Object message) {
		SockUtility.writeLog(SockUtility.ErrorLevel.valueOf(errorLevel.name()), message);
	}

	public static <T> T nb(String title, Supplier<T> routine) {
		return SockUtility.nb(title, routine);
	}

	public static boolean containsIgnoreCase(String str, String pattern) {
		return SockUtility.containsIgnoreCase(str, pattern);
	}

	public static boolean equalsIgnoreCase(String a, String b) {
		return a.equalsIgnoreCase(b);
	}

	public static void shuffleP4(List<?> list) {
		SockUtility.shuffleP4(list);
	}

	public static class TimeWaitMonitor {
		public static int ctrRotSec = 60;
		public static int counterNum = 5;

		public static int countLimit = SockUtility.TimeWaitMonitor.countLimit;

		private TimeWaitMonitor() {
		}

		public static void apply() {
			SockUtility.TimeWaitMonitor.countLimit = countLimit;
		}
	}
}
