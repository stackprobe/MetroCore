package tools_tests;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.xml.stream.XMLStreamException;

import tools.JsonTools;
import tools.XMLTools;

public class Test0001 {
	public static void main(String[] args) {
		try {
			// -- choose one --

			test01();
			test02();

			// --
		}
		catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private static void test01() throws IOException, XMLStreamException, URISyntaxException {
		String testDataFilePath = new File(Test0001.class.getResource("res/XMLTestData.xml").toURI()).getCanonicalPath();
		System.out.println("< " + testDataFilePath);

		XMLTools.Node root = XMLTools.loadFromFile(testDataFilePath);

		XMLTools.writeToFile(root, "C:\\temp\\xml-output.xml");
	}

	private static void test02() throws IOException, URISyntaxException {
		String testDataFilePath_1 = new File(Test0001.class.getResource("res/JsonTestData.json").toURI()).getCanonicalPath();
		String testDataFilePath_2 = new File(Test0001.class.getResource("res/JsonTestData_trailing-comma.json").toURI()).getCanonicalPath();
		System.out.println("< " + testDataFilePath_1);
		System.out.println("< " + testDataFilePath_2);

		JsonTools.Node root_1 = JsonTools.loadFromFile(testDataFilePath_1);
		JsonTools.Node root_2 = JsonTools.loadFromFile(testDataFilePath_2);

		JsonTools.writeToFile(root_1, "C:\\temp\\json-output.json");
		JsonTools.writeToFile(root_2, "C:\\temp\\json-output_trailing-comma.json");
	}
}
