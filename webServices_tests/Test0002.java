package webServices_tests;

import webServices_tests.httcmd.HttCmdServer;

public class Test0002 {
	public static void main(String[] args) {
		try {
			testMain();
		}
		catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private static void testMain() {
		run("C:\\home\\TestData\\HTTCmd\\DocRoot");
	}

	public static void run(String docRoot) {
		new HttCmdServer().run(docRoot);
	}
}
