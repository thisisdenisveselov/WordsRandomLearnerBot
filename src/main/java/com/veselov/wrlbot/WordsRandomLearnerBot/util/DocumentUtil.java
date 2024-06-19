package com.veselov.wrlbot.WordsRandomLearnerBot.util;

import com.veselov.wrlbot.WordsRandomLearnerBot.model.Translation;
import com.veselov.wrlbot.WordsRandomLearnerBot.model.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Component
@Slf4j
public class DocumentUtil {
    private static final String ENGLISH = "English";
    private static final String RUSSIAN = "Russian";
    private static final int DEFAULT_PRIORITY = 0;
    private static final int DEFAULT_STEP_NUMBER = 0;

    public XSSFWorkbook getDataFromFile(User user, String fileId, String botToken) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId);

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(url.openStream()));
            String getFileResponse = bufferedReader.readLine();

            JSONObject jResult = new JSONObject(getFileResponse);
            JSONObject path = jResult.getJSONObject("result");
            String filePath = path.getString("file_path");

            InputStream inputStream = new URL(
                    "https://api.telegram.org/file/bot" + botToken + "/" + filePath).openStream();


            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);

            bufferedReader.close();
            inputStream.close();

            return workbook;

        } catch (Exception e) {
            log.error(Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    public List<Translation> parseExcelToList(XSSFWorkbook workbook, User user) {
        XSSFSheet sheet = workbook.getSheetAt(0);
        Iterator iterator = sheet.iterator();

        List<Translation> translations = new ArrayList<>();


        while (iterator.hasNext()) {
            XSSFRow row = (XSSFRow) iterator.next();

            Iterator cellIterator = row.cellIterator();

            int columnNumber = 0;
            String languageFrom = "";
            String languageTo = "";
            String phrase = "";
            String phraseTranslation = "";

            while (cellIterator.hasNext()) {
                XSSFCell cell = (XSSFCell) cellIterator.next();
                String value = cell.getStringCellValue();

                switch (columnNumber) {
                    case 0 -> languageFrom = value;
                    case 1 -> languageTo = value;
                    case 2 -> phrase = value;
                    case 3 -> phraseTranslation = value;
                }

                columnNumber++;

            }

            Translation translation = new Translation();

            if ((!languageFrom.equals(ENGLISH) && !languageFrom.equals(RUSSIAN))
                    || (!languageTo.equals(ENGLISH) && !languageTo.equals(RUSSIAN)))  //filter if neither English nor Russian
                continue;

            switch (languageFrom) {
                case ENGLISH -> {
                    translation.setPhraseEng(phrase);
                    translation.setPhraseRu(phraseTranslation);
                }
                case RUSSIAN -> {
                    translation.setPhraseEng(phraseTranslation);
                    translation.setPhraseRu(phrase);
                }
            }

            translation.setPriority(DEFAULT_PRIORITY);
            translation.setUser(user);
            translation.setStepNumber(DEFAULT_STEP_NUMBER);

            translations.add(translation);
        }
        return translations;
    }
}
