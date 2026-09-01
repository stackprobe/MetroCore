package sandboxes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import tools.CsvFileReader;
import tools.TCommon;

public class Test20260833_AutoPrompts {
	public static void main(String[] args) {
		try {
			test01();
		}
		catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private static void test01() throws Exception {
		test01_a(
				"C:\\temp\\Format.txt",
				"C:\\temp\\Parameters.csv",
				"C:\\temp\\Prompts"
				);
	}

	private static void test01_a(String formatTextFile, String parametersCsvFile, String outputDir) throws Exception {
		deleteAndCreateDir(outputDir);

		int promptCount = test01_a1(formatTextFile, parametersCsvFile, outputDir);
		test01_a2(promptCount, outputDir);
	}

	private static int test01_a1(String formatTextFile, String parametersCsvFile, String outputDir) throws IOException {
		String format = TCommon.readAllText(formatTextFile, TCommon.CHARSET_SJIS);
		List<String[]> parameters = CsvFileReader.readToEnd(parametersCsvFile);

		parameters = parametersFilter(parameters);
		List<String> texts = getTexts(format, parameters);

		int index = 0;
		for (String text : texts) {
			TCommon.writeAllText(
					Path.of(outputDir, String.format("Prompt_%02d.txt", ++index)).toString(),
					text,
					TCommon.CHARSET_SJIS
					);
		}
		return index;
	}

	private static List<String[]> parametersFilter(List<String[]> parameters) {
		return parameters.stream()
				.map(row -> {
					String[] trimmed = new String[row.length];

					for (int index = 0; index < row.length; index++) {
						trimmed[index] = row[index].trim();
					}
					return trimmed;
				})
				.filter(row -> {
					for (String cell : row) {
						if (!cell.equals("")) {
							return true;
						}
					}
					return false;
				})
				.toList();
	}

	private static List<String> getTexts(String format, List<String[]> parameters) {
		return parameters.stream()
				.map(parameter -> getText(format, parameter))
				.toList();
	}

	private static String getText(String format, String[] parameter) {
		StringBuilder dest = new StringBuilder();

		for (;;) {
			int lPos = format.indexOf("{{");

			if (lPos == -1) {
				break;
			}
			int rPos = format.indexOf("}}", lPos + 2);

			if (rPos == -1) {
				break;
			}
			int index = Integer.parseInt(format.substring(lPos + 2, rPos).trim());
			String p = index <= parameter.length ? parameter[index - 1] : "";

			dest.append(format.substring(0, lPos));
			dest.append(p);

			format = format.substring(rPos + 2);
		}
		dest.append(format);
		return dest.toString();
	}

	private static void test01_a2(int promptCount, String outputDir) throws IOException {
		List<String> allRunLines = java.util.stream.IntStream.range(0, promptCount)
				.mapToObj(index -> {
					String batchFile = Path.of(outputDir, String.format("Batch_%02d.bat", index + 1)).toString();
					String promptFile = Path.of(outputDir, String.format("Prompt_%02d.txt", index + 1)).toString();

					TCommon.re(() -> TCommon.writeAllText(
							batchFile,
							"Codex -C \"C:\\temp\" exec ^\r\n" +
							"\t--skip-git-repo-check ^\r\n" +
							"\t--sandbox workspace-write ^\r\n" +
							"\t\"" + promptFile + " (SJIS) を読み、その内容を今回の作業指示として実行してください。\"\r\n",
							TCommon.CHARSET_SJIS
							));

					return "CALL \"" + batchFile + "\"";
				})
				.toList();

		TCommon.writeAllLines(
				Path.of(outputDir, "runall.bat").toString(),
				allRunLines,
				TCommon.CHARSET_SJIS
				);
	}

	private static void deleteAndCreateDir(String dirPath) throws IOException {
		Path dir = Path.of(dirPath);

		if (Files.exists(dir)) {
			try (var paths = Files.walk(dir)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
		Files.createDirectories(dir);
	}
}
