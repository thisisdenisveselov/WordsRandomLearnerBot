package com.veselov.wrlbot.WordsRandomLearnerBot.service;

import com.vdurmont.emoji.EmojiParser;
import com.veselov.wrlbot.WordsRandomLearnerBot.config.BotConfig;
import com.veselov.wrlbot.WordsRandomLearnerBot.dao.TranslationDAO;
import com.veselov.wrlbot.WordsRandomLearnerBot.model.*;
import com.veselov.wrlbot.WordsRandomLearnerBot.repositories.TranslationRepository;
import com.veselov.wrlbot.WordsRandomLearnerBot.repositories.UserRepository;
import com.veselov.wrlbot.WordsRandomLearnerBot.util.ButtonsVariations;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.io.FileInputStream;
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
    @Autowired
    private TranslationDAO translationDAO;

    static final String HELP_TEXT = "This bot is created to make it easier to remember new English words. \n\n" +
            "You can execute commands from the main menu on the left or by typing a command: \n\n" +
            "Type /start to see a welcome message\n\n" +
            "Type /mydata to see data stored about yourself\n\n" +
            "Type /help to see this message again";
    private static final String ENGLISH = "English";
    private static final String RUSSIAN = "Russian";
    /*private static final int NO_BUTTONS = 0;
    private static final int ONE_BUTTON = 1;

    private static final int TWO_BUTTONS = 2;
    private static final int THREE_BUTTONS = 3;
    private static final int FOUR_BUTTONS = 4;*/
    private static final int STEPS_AMOUNT = 30;  // the number of iterations after which the phrase can be shown again
    private static final String UPDATE_BUTTON = "UPDATE_BUTTON";
    private static final String FROM_ENG_BUTTON = "FROM_ENG_BUTTON";
    private static final String FROM_RU_BUTTON = "FROM_RU_BUTTON";
    private static final String TRANSLATION_BUTTON = "TRANSLATION_BUTTON";
    private static final String NEXT_BUTTON = "NEXT_BUTTON";
    private static final String KNOW_BUTTON = "KNOW_BUTTON";
    private static final String DO_NOT_KNOW_BUTTON = "DO_NOT_KNOW_BUTTON";

    public TelegramBot(BotConfig config) {
        this.config = config;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/update", "update your phrases list"));
        listOfCommands.add(new BotCommand("/nexteng", "next phrase on English"));
        listOfCommands.add(new BotCommand("/nextru", "next phrase on Russian"));
        listOfCommands.add(new BotCommand("/translation", "show translation"));
        listOfCommands.add(new BotCommand("/help", "info how to use this bot"));
        listOfCommands.add(new BotCommand("/settings", "set your settings"));

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

            switch (messageText) {
                case "/start" -> {
                    registerUser(update.getMessage());
                    showStart(chatId, update.getMessage().getChat().getFirstName());
                }
                case "/update" -> updatePhrases(chatId);
                case "/nexteng" -> {
                    setCurrentLanguage(ENGLISH, chatId);
                    showNext(chatId, null);
                }
                case "/nextru" -> {
                    setCurrentLanguage(RUSSIAN, chatId);
                    showNext(chatId, null);
                }
                case "/translation" -> showTranslation(chatId);
                default -> commandNotFound(chatId);
            }

        } else if (update.hasCallbackQuery()) {
            String callBackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
           // long messageId = update.getCallbackQuery().getMessage().getMessageId();

            switch (callBackData) {
                case UPDATE_BUTTON -> updatePhrases(chatId);
                case FROM_ENG_BUTTON -> showNext(chatId, ENGLISH); //happened, if "Phrases list has been updated" occurred, and "From English" pressed
                case FROM_RU_BUTTON -> showNext(chatId, RUSSIAN); //happened, if "Phrases list has been updated" occurred, and "From Russian" pressed
                //case NEXT_BUTTON -> showNext(chatId, null);
                case TRANSLATION_BUTTON -> showTranslation(chatId);
                case KNOW_BUTTON -> {
                    changePriority(chatId, false); //phrase should occur less frequently
                    showNext(chatId, null);
                }
                case DO_NOT_KNOW_BUTTON -> {
                    changePriority(chatId, true);  // phrase should occur more frequently
                    showNext(chatId, null);
                }
            }
        }
    }

    private void changePriority(long chatId, boolean increase) {
        User user = userRepository.findById(chatId).orElse(null);
        Integer lastPhraseId = user.getLastPhraseId();

        Translation translation = translationRepository.findById(lastPhraseId).orElse(null);
        if (increase)
            translation.setPriority(translation.getPriority() + 1);
        else
            translation.setPriority(translation.getPriority() - 1);

        translationRepository.save(translation);
    }

    private void setCurrentLanguage(String language, long chatId) {
        User user = userRepository.findById(chatId).orElse(null);

        if (user != null)
            user.setCurrentLanguage(language);

        userRepository.save(user);
    }
    private void showNext(long chatId, String forcedLanguage) {
        Translation translation = translationDAO.getTranslation(chatId, STEPS_AMOUNT);

        User user = userRepository.findById(chatId).orElse(null);

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
        translation.setUser(userRepository.findById(chatId).orElse(null));
        translationRepository.save(translation);

        user.setCurrentStepNumber(currentStepNumber);
        user.setLastPhraseId(translation.getId());
        user.setCurrentLanguage(currentLanguage);
        userRepository.save(user);
    }

    private void showTranslation(long chatId) {
        User user = userRepository.findById(chatId).orElse(null);  // check if it is ok to write like that !

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

    private void updatePhrases(long chatId) {
        try {
            String excelFilePath = ".\\db\\Saved translations.xlsx";
            FileInputStream inputStream = new FileInputStream(excelFilePath);

            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            XSSFSheet sheet = workbook.getSheetAt(0);

            Iterator iterator = sheet.iterator();

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
                        || (!languageTo.equals(ENGLISH) && !languageTo.equals(RUSSIAN)))  //if neither English nor Russian
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

                translation.setPriority(0);
                translation.setUser(userRepository.findById(chatId).orElse(null));
                translation.setStepNumber(0);

                translationRepository.save(translation);
            }
        } catch (Exception e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }

        String answer = "Phrases list has been updated";
        prepareAndSendMessage(answer, chatId, ButtonsVariations.FROM_RU_ENG);
    }

    private void showStart(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                "Hi, " + name + "! :smiley:" + " Nice to meet you! I am a Words Random Learner Bot, created by Denis Veselov \n " +
                        "At first, you need to update your phrases list!");
        prepareAndSendMessage(answer, chatId, ButtonsVariations.UPDATE);
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
            user.setCurrentStepNumber(0);

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
            case UPDATE -> {
                addButton(inLineRow, "Update phrases list", UPDATE_BUTTON);
            }
            case FROM_RU_ENG -> {
                addButton(inLineRow, "From English", FROM_ENG_BUTTON);
                addButton(inLineRow, "From Russian", FROM_RU_BUTTON);
            }
            case KNOW_DO_NOT_KNOW -> {
              //  addButton(inLineRow, "Next", NEXT_BUTTON);
                addButton(inLineRow, "I know", KNOW_BUTTON);
                addButton(inLineRow, "I don't know", DO_NOT_KNOW_BUTTON);
            }
            case WITH_TRANSLATION -> {
                addButton(inLineRow, "Translation", TRANSLATION_BUTTON);
             //   addButton(inLineRow, "Next", NEXT_BUTTON);
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