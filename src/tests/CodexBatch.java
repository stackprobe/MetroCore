package tests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.CsvFileReader;
import tools.TCommon;

public class CodexBatch {
	public static void main(String[] args) {
		try {

			test01("C:\\temp\\PromptBase.txt", "C:\\temp\\Parameters.csv");

		}
		catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private static final String TEMP_DIR = "C:\\temp\\CodexBatch";

	private static void test01(String promptBaseTextFilePath, String parametersCsvFilePath) throws IOException, InterruptedException {

		TCommon.deletePath(TEMP_DIR);
		TCommon.createDir(TEMP_DIR);

		String promptBase = TCommon.readAllText(promptBaseTextFilePath, TCommon.CHARSET_SJIS).trim();
		List<String[]> parameterRows = CsvFileReader.readToEnd(parametersCsvFilePath);

		List<String> prompts = getPrompts(promptBase, parameterRows);

		previewPrompts(prompts);

		System.out.println("Press ENTER.");
		System.in.read();

		for (String prompt : prompts) {
			System.out.println("prompt: " + prompt);

			execute(prompt);

			System.out.println("done");
		}
		System.out.println("done!");
	}

	private static void execute(String prompt) throws IOException, InterruptedException {

		String tempPromptFilePath = TEMP_DIR + "\\Prompt.txt";

		TCommon.writeAllText(tempPromptFilePath, prompt, TCommon.CHARSET_SJIS);

		TCommon.batch(Arrays.asList(
				"CD /D C:\\temp",
				"Codex -C \"C:\\temp\" exec --skip-git-repo-check --sandbox workspace-write \""
						+ tempPromptFilePath
						+ " を読み、その内容を今回の作業指示として実行してください。\""
				));
	}

	private static void previewPrompts(List<String> prompts) {
		for (String prompt : prompts) {
			System.out.println("====");
			System.out.println();
			System.out.println(prompt);
			System.out.println();
		}
	}

	private static List<String> getPrompts(String promptBase, List<String[]> parameterRows) {

		List<String> prompts = new ArrayList<String>();
		int maxColumnNo = getMaxColumnNo(promptBase);

		for (String[] row : parameterRows) {
			String[] cells = trimCells(row);

			if (cells.length < maxColumnNo || isEmptyRow(cells)) {
				continue;
			}
			prompts.add(replacePlaceholders(promptBase, cells));
		}
		return prompts;
	}

	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([1-9][0-9]*)\\s*\\}\\}");

	private static int getMaxColumnNo(String text) {
		Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
		int maxColumnNo = 0;

		while (matcher.find()) {
			maxColumnNo = Math.max(maxColumnNo, Integer.parseInt(matcher.group(1)));
		}
		return maxColumnNo;
	}

	private static String[] trimCells(String[] row) {
		String[] cells = new String[row.length];

		for (int index = 0; index < row.length; index++) {
			cells[index] = row[index].trim();
		}
		return cells;
	}

	private static boolean isEmptyRow(String[] cells) {
		for (String cell : cells) {
			if (!cell.equals("")) {
				return false;
			}
		}
		return true;
	}

	private static String replacePlaceholders(String promptBase, String[] cells) {
		Matcher matcher = PLACEHOLDER_PATTERN.matcher(promptBase);
		StringBuffer buff = new StringBuffer();

		while (matcher.find()) {
			int columnNo = Integer.parseInt(matcher.group(1));

			matcher.appendReplacement(buff, Matcher.quoteReplacement(cells[columnNo - 1]));
		}
		matcher.appendTail(buff);

		return buff.toString();
	}
}
