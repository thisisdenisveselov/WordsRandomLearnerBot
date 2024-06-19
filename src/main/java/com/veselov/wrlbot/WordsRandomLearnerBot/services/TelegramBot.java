package com.veselov.wrlbot.WordsRandomLearnerBot.services;

import com.vdurmont.emoji.EmojiParser;
import com.veselov.wrlbot.WordsRandomLearnerBot.config.BotConfig;
import com.veselov.wrlbot.WordsRandomLearnerBot.model.*;
import com.veselov.wrlbot.WordsRandomLearnerBot.repositories.TranslationRepository;
import com.veselov.wrlbot.WordsRandomLearnerBot.repositories.UserRepository;
import com.veselov.wrlbot.WordsRandomLearnerBot.util.ButtonsVariations;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.net.URL;
import java.sql.Timestamp;
import java.util.*;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TranslationRepository translationRepository;

    static final String HELP_TEXT = "This bot is created to make it easier to remember English words " +
            "that had been added to \"Saved\" list in Google Translate. \n\n" +
            "You can execute commands from the main menu on the left or by typing a command: \n\n" +
            "Type /start to see a welcome message\n\n" +
            "Type /update to update/upload new phrases list\n\n" +
            "Type /nexteng to get next phrase on English\n\n" +
            "Type /nextru to get next phrase on Russian\n\n" +
            "Type /help to see this message again";
    private static final String ENGLISH = "English";
    private static final String RUSSIAN = "Russian";
    private static final int STEPS_AMOUNT = 20;  // the number of iterations after which the phrase can be shown again
    private static final String DEFAULT_PHRASES = "DEFAULT_PHRASES";
    private static final String FROM_ENG_BUTTON = "FROM_ENG_BUTTON";
    private static final String FROM_RU_BUTTON = "FROM_RU_BUTTON";
    private static final String TRANSLATION_BUTTON = "TRANSLATION_BUTTON";
    private static final String KNOW_BUTTON = "KNOW_BUTTON";
    private static final String DO_NOT_KNOW_BUTTON = "DO_NOT_KNOW_BUTTON";
    private static final Long ADMIN_CHAT_ID = 291573027L;
    private static final int DEFAULT_PRIORITY = 0;
    private static final int DEFAULT_STEP_NUMBER = 0;


    public TelegramBot(BotConfig config) {
        this.config = config;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/update", "update your phrases list"));
        listOfCommands.add(new BotCommand("/nexteng", "next phrase on English"));
        listOfCommands.add(new BotCommand("/nextru", "next phrase on Russian"));
        listOfCommands.add(new BotCommand("/translation", "show translation"));
        listOfCommands.add(new BotCommand("/help", "info how to use this bot"));

        try{
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public  String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            User user = userRepository.findById(chatId).orElse(null);

            switch (messageText) {
                case "/start" -> {
                    registerUser(update.getMessage());
                    showStart(chatId, update.getMessage().getChat().getFirstName());
                }
                case "/update" -> showStart(chatId, update.getMessage().getChat().getFirstName());
                case "/nexteng" -> showNext(chatId, user, ENGLISH);

                case "/nextru" -> showNext(chatId, user, RUSSIAN);
                case "/translation" -> showTranslation(chatId, user);
                case "/help" -> showHelp(chatId);
                default -> commandNotFound(chatId);
            }

        } else if (update.hasMessage() && update.getMessage().hasDocument()) {
            long chatId = update.getMessage().getChatId();
            Document document = update.getMessage().getDocument();
            User user = userRepository.findById(chatId).orElse(null);
            
            if (document != null) {
                String fileId = document.getFileId();
                
                uploadFile(user, fileId);

                String answer = "The list of phrases has been uploaded.\n" +
                        "Choose \"From English to Russian\" or \"From Russian to English\" translations:";

                prepareAndSendMessage(answer, chatId, ButtonsVariations.FROM_RU_ENG);
            }

        } else if (update.hasCallbackQuery()) {
            String callBackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            User user = userRepository.findById(chatId).orElse(null);
            if (user == null)
                prepareAndSendMessage("User not found", chatId, ButtonsVariations.NO_BUTTONS);


            switch (callBackData) {
                case DEFAULT_PHRASES -> useDefaultPhrases(chatId, user);
                case FROM_ENG_BUTTON -> showNext(chatId, user, ENGLISH); //happened, if "Phrases list has been updated" occurred, and "From English" pressed
                case FROM_RU_BUTTON -> showNext(chatId, user, RUSSIAN); //happened, if "Phrases list has been updated" occurred, and "From Russian" pressed
                case TRANSLATION_BUTTON -> showTranslation(chatId, user);
                case KNOW_BUTTON -> {
                    changePriority(user, false); //phrase should occur less frequently
                    showNext(chatId, user, null);
                }
                case DO_NOT_KNOW_BUTTON -> {
                    changePriority(user, true);  // phrase should occur more frequently
                    showNext(chatId, user, null);
                }
            }
        }
    }

    private void showHelp(long chatId) {
        prepareAndSendMessage(HELP_TEXT, chatId, ButtonsVariations.NO_BUTTONS);
    }

    private void uploadFile(User user, String fileId) {
        try {
            InputStream inputStream = getDataFromFile(fileId);

            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);

            XSSFSheet sheet = workbook.getSheetAt(0);
            Iterator iterator = sheet.iterator();

            List<Translation> translations = new ArrayList<>();
            List<Translation> translationsOld = translationRepository.findAllByUser(user);

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

            if (!translationsOld.isEmpty()) {
                List<Translation> translationsToDelete = new ArrayList<>(translationsOld);

                translationsToDelete.removeAll(translations);
                translations.removeAll(translationsOld);

                translationRepository.deleteAll(translationsToDelete); //remove records from db that don't exist in new file
            }
            translationRepository.saveAll(translations); //add new records

        } catch (Exception e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }

    }

    private InputStream getDataFromFile(String fileId) {
        InputStream inputStream = null;
        try {
            URL url = new URL("https://api.telegram.org/bot" + config.getToken() + "/getFile?file_id=" + fileId);

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(url.openStream()));
            String getFileResponse = bufferedReader.readLine();

            JSONObject jResult = new JSONObject(getFileResponse);
            JSONObject path = jResult.getJSONObject("result");
            String filePath = path.getString("file_path");

            inputStream = new URL(
                    "https://api.telegram.org/file/bot" + config.getToken() + "/" + filePath).openStream();

            bufferedReader.close();
            inputStream.close();
        } catch (Exception e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
        return inputStream;
    }

    private void changePriority(User user, boolean increase) {
        Integer lastPhraseId = user.getLastPhraseId();

        Translation translation = translationRepository.findById(lastPhraseId).orElse(null);
        if (increase)
            translation.setPriority(translation.getPriority() + 1);
        else
            translation.setPriority(translation.getPriority() - 1);

        translationRepository.save(translation);
    }
    
    private void showNext(long chatId, User user, String forcedLanguage) {

        Translation translation = translationRepository.findCustomRandomPhrase(chatId, STEPS_AMOUNT, user.getCurrentStepNumber());

        String currentLanguage = "";

        if (forcedLanguage == null)
            currentLanguage = user.getCurrentLanguage();
        else
            currentLanguage = forcedLanguage;

        switch (currentLanguage) {
            case ENGLISH -> {
                if (translation != null)
                    prepareAndSendMessage(translation.getPhraseEng(), chatId, ButtonsVariations.WITH_TRANSLATION);
                else
                    prepareAndSendMessage("Phrase list is empty", chatId, ButtonsVariations.NO_BUTTONS);
            }
            case RUSSIAN -> {
                if (translation != null)
                    prepareAndSendMessage(translation.getPhraseRu(), chatId, ButtonsVariations.WITH_TRANSLATION);
                else
                    prepareAndSendMessage("Phrase list is empty", chatId, ButtonsVariations.NO_BUTTONS);
            }
        }

        int currentStepNumber = user.getCurrentStepNumber() + 1;
        translation.setStepNumber(currentStepNumber);
        translationRepository.save(translation);

        user.setCurrentStepNumber(currentStepNumber);
        user.setLastPhraseId(translation.getId());
        user.setCurrentLanguage(currentLanguage);
        userRepository.save(user);
    }

    private void showTranslation(long chatId, User user) {
        Integer lastPhraseId = user.getLastPhraseId();
        String currentLanguage = user.getCurrentLanguage();

        if (lastPhraseId != null) {
            var translation = translationRepository.findById(lastPhraseId);

            switch (currentLanguage) {
                case ENGLISH -> translation.ifPresent(x -> prepareAndSendMessage(x.getPhraseRu(), chatId, ButtonsVariations.KNOW_DO_NOT_KNOW));
                case RUSSIAN -> translation.ifPresent(x -> prepareAndSendMessage(x.getPhraseEng(), chatId, ButtonsVariations.KNOW_DO_NOT_KNOW));
            }
        }
    }

    private void useDefaultPhrases(long chatId, User user) {

        Optional<User> sourceUser = userRepository.findById(ADMIN_CHAT_ID);
        if(sourceUser.isPresent())
            copyPhrases(user, sourceUser.get());  //should copy default phrases to translations table with users id
        else
            prepareAndSendMessage("No default list of phrases", chatId, ButtonsVariations.NO_BUTTONS);

        String answer = "Choose \"From English to Russian\" or \"From Russian to English\" translations";
        prepareAndSendMessage(answer, chatId, ButtonsVariations.FROM_RU_ENG);
    }

    private void copyPhrases(User user, User sourceUser) { // Test it !!!!
        List<Translation> translations = translationRepository.findAllByUser(sourceUser);

        for(Translation translation : translations) {
            translation.setId(null);
            translation.setPriority(DEFAULT_PRIORITY);
            translation.setStepNumber(DEFAULT_STEP_NUMBER);
            translation.setUser(user);
        }

        translationRepository.saveAll(translations);

    }

    private void showStart(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                "Hi, " + name + "! :smiley:" + " Nice to meet you! I am a Words Random Learner Bot\n " +
                        "You can upload your phrases list with :paperclip: button. An attached file must be an excel file, exported from google translate saved phrases.\n\n" +
                        "Or you can test the bot with the default set of phrases.");
        prepareAndSendMessage(answer, chatId, ButtonsVariations.DEFAULT_PHRASES);
    }

    private void registerUser(Message msg) {

        if(userRepository.findById(msg.getChatId()).isEmpty()) {

            var chatId = msg.getChatId();
            var chat = msg.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            user.setCurrentStepNumber(DEFAULT_STEP_NUMBER);

            userRepository.save(user);
            log.info("User saved: " + user);
        }

    }

    private void commandNotFound(long chatId) {

        String answer = EmojiParser.parseToUnicode(
                "Command not recognized, please verify and try again :stuck_out_tongue_winking_eye: ");
        prepareAndSendMessage(answer, chatId, ButtonsVariations.NO_BUTTONS);

    }

    private void prepareAndSendMessage(String textToSend, long chatId, ButtonsVariations buttonsVariations) {
        SendMessage message = new SendMessage();

        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        if(buttonsVariations != ButtonsVariations.NO_BUTTONS)
            addButtons(message, buttonsVariations);

        sendMessage(message);
    }

    private void sendMessage(SendMessage msg) {
        try {
            execute(msg); // Sending our message object to user
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }

    private void addButtons(SendMessage message, ButtonsVariations buttonsVariations) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> inLineRows = new ArrayList<>();
        List<InlineKeyboardButton> inLineRow = new ArrayList<>();

        switch (buttonsVariations) {
            case DEFAULT_PHRASES -> {
                addButton(inLineRow, "Use bot with the default set of phrases", DEFAULT_PHRASES);
            }
            case FROM_RU_ENG -> {
                addButton(inLineRow, "From English", FROM_ENG_BUTTON);
                addButton(inLineRow, "From Russian", FROM_RU_BUTTON);
            }
            case KNOW_DO_NOT_KNOW -> {
                addButton(inLineRow, "I know", KNOW_BUTTON);
                addButton(inLineRow, "I don't know", DO_NOT_KNOW_BUTTON);
            }
            case WITH_TRANSLATION -> {
                addButton(inLineRow, "Translation", TRANSLATION_BUTTON);
                addButton(inLineRow, "I know", KNOW_BUTTON);
                addButton(inLineRow, "I don't know", DO_NOT_KNOW_BUTTON);
            }
        }

        inLineRows.add(inLineRow);

        inlineKeyboardMarkup.setKeyboard(inLineRows);
        message.setReplyMarkup(inlineKeyboardMarkup);
    }

    private void addButton(List<InlineKeyboardButton> inLineRow, String text, String data) {
        var translationButton = new InlineKeyboardButton();

        translationButton.setText(text);
        translationButton.setCallbackData(data);

        inLineRow.add(translationButton);
    }
}