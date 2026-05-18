package tools_tests.sandboxes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tools.CsvFileReader;
import tools.CsvFileWriter;
import tools.TCommon;

public class Test20260519 {
    public static void main(String[] args) {
        try {
            run();
        }
        catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void run() throws IOException {
        List<String[]> rows = CsvFileReader.readToEnd("C:\\temp\\input.csv");
        List<String[]> dest = new ArrayList<String[]>();

        for (String[] row : rows) {
            String[] destRow;

            if (row[0].endsWith(".vsl")) {

                String vslFile = row[0];
                vslFile = vslFile.replace("/", "\\");
                vslFile = "C:\\home\\Repos" + vslFile;

                byte[] vslData = TCommon.readAllBytes(vslFile);
                String vslText = new String(vslData, TCommon.CHARSET_UTF8);
                byte[] vslData2 = vslText.getBytes(TCommon.CHARSET_UTF8);

                boolean probablyUtf8 = TCommon.compare(vslData, vslData2) == 0;

                if (probablyUtf8) {
                    destRow = new String[] { "UTF-8" };
                }
                else {
                    destRow = new String[] { "★" };
                }
            }
            else {
                destRow = new String[] { "" };
            }
            dest.add(destRow);
        }
        CsvFileWriter.writeRows("C:\\temp\\output.csv", dest);
    }
}
