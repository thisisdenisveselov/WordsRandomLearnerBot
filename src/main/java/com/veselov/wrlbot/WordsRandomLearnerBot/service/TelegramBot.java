package com.veselov.wrlbot.WordsRandomLearnerBot.service;

import com.vdurmont.emoji.EmojiParser;
import com.veselov.wrlbot.WordsRandomLearnerBot.config.BotConfig;
import com.veselov.wrlbot.WordsRandomLearnerBot.dao.TranslationDAO;
import com.veselov.wrlbot.WordsRandomLearnerBot.model.*;
import com.veselov.wrlbot.WordsRandomLearnerBot.repositories.TranslationRepository;
import com.veselov.wrlbot.WordsRandomLearnerBot.repositories.UserRepository;
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
    private static final int NO_BUTTONS = 0;
    private static final int ONE_BUTTON = 1;

    private static final int TWO_BUTTONS = 2;
    private static final int THREE_BUTTONS = 3;
    private static final int FOUR_BUTTONS = 4;
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
                case NEXT_BUTTON -> showNext(chatId, null);
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
        /*Random random = new Random();
        int tableSize = (int) translationRepository.count();
        int randomId = random.nextInt(tableSize);
        Translation translation = translationRepository.findById(randomId).orElse(null);*/

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
                    prepareAndSendMessage(translation.getPhraseEng(), chatId, FOUR_BUTTONS);
                else
                    prepareAndSendMessage("Phrase list is empty", chatId, NO_BUTTONS);
            }
            case RUSSIAN -> {
                if (translation != null)
                    prepareAndSendMessage(translation.getPhraseRu(), chatId, FOUR_BUTTONS);
                else
                    prepareAndSendMessage("Phrase list is empty", chatId, NO_BUTTONS);
            }
        }

        int currentStepNumber = user.getCurrentStepNumber() + 1;
        translation.setStepNumber(currentStepNumber);
        translation.setUser(userRepository.findById(chatId).orElse(null));
        translationRepository.save(translation);

        user.setCurrentStepNumber(currentStepNumber);
        user.setLastPhraseId(translation.getId());
//        user.setLastPhraseId(randomId);
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
                case ENGLISH -> translation.ifPresent(x -> prepareAndSendMessage(x.getPhraseRu(), chatId, THREE_BUTTONS));
                case RUSSIAN -> translation.ifPresent(x -> prepareAndSendMessage(x.getPhraseEng(), chatId, THREE_BUTTONS));
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
        prepareAndSendMessage(answer, chatId, TWO_BUTTONS);
    }

    private void showStart(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                "Hi, " + name + "! :smiley:" + " Nice to meet you! I am a Words Random Learner Bot, created by Denis Veselov \n " +
                        "At first, you need to update your phrases list!");
        prepareAndSendMessage(answer, chatId, ONE_BUTTON);
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
        prepareAndSendMessage(answer, chatId, NO_BUTTONS);

    }

    private void prepareAndSendMessage(String textToSend, long chatId, int buttonsAmount) {
        SendMessage message = new SendMessage();

        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        if(buttonsAmount > 0)
            addButtons(message, buttonsAmount);

        sendMessage(message);
    }

    private void sendMessage(SendMessage msg) {
        try {
            execute(msg); // Sending our message object to user
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }

    private void addButtons(SendMessage message, int buttonsAmount) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> inLineRows = new ArrayList<>();
        List<InlineKeyboardButton> inLineRow = new ArrayList<>();

        switch (buttonsAmount) {
            case 1 -> {
                addButton(inLineRow, "Update phrases list", UPDATE_BUTTON);
            }
            case 2 -> {
                addButton(inLineRow, "From English", FROM_ENG_BUTTON);
                addButton(inLineRow, "From Russian", FROM_RU_BUTTON);
            }
            case 3 -> {
                addButton(inLineRow, "Next", NEXT_BUTTON);
                addButton(inLineRow, "I know", KNOW_BUTTON);
                addButton(inLineRow, "I don't know", DO_NOT_KNOW_BUTTON);
            }
            case 4 -> {
                addButton(inLineRow, "Translation", TRANSLATION_BUTTON);
                addButton(inLineRow, "Next", NEXT_BUTTON);
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


//********** second playlist of tutorials

/*
@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JokeRepository jokeRepository;

    static final String HELP_TEXT = "This bot is created to make it easier to remember new English words. \n\n" +
            "You can execute commands from the main menu on the left or by typing a command: \n\n" +
            "Type /start to see a welcome message\n\n" +
            "Type /mydata to see data stored about yourself\n\n" +
            "Type /help to see this message again";

    private static final String ERROR_TEXT = "Error occurred: ";

    private static final int MAX_JOKE_ID_MINUS_ONE = 3772;

    private static final String NEXT_JOKE = "NEXT_JOKE";

    public TelegramBot(BotConfig config) {
        this.config = config;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/joke", "get a random joke"));
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
                    showStart(chatId, update.getMessage().getChat().getFirstName());
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        TypeFactory typeFactory = objectMapper.getTypeFactory();
                        List<Joke> jokeList = objectMapper.readValue(new File("db/stupidstuff.json"),
                                typeFactory.constructCollectionType(List.class, Joke.class));
                        jokeRepository.saveAll(jokeList);
                    } catch (Exception e) {
                        log.error(Arrays.toString(e.getStackTrace()));
                    }
                }

                case "/joke" -> {

                    var joke = getRandomJoke();

                    joke.ifPresent(randomJoke -> addButtonAndSendMessage(randomJoke.getBody(), chatId));

                }
                default -> commandNotFound(chatId);
            }

        } else if (update.hasCallbackQuery()) {
            String callBackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
           // long messageId = update.getCallbackQuery().getMessage().getMessageId();

            if (callBackData.equals(NEXT_JOKE)) {
                var joke = getRandomJoke();

                joke.ifPresent(randomJoke -> addButtonAndSendMessage(randomJoke.getBody(), chatId));
            }




        }
    }

    private Optional<Joke> getRandomJoke() {
        var r = new Random();
        var randomId = r.nextInt(MAX_JOKE_ID_MINUS_ONE) + 1;

        return jokeRepository.findById(randomId);
    }

    private void addButtonAndSendMessage(String joke, long chatId) {
        SendMessage message = new SendMessage();
        message.setText(joke);
        message.setChatId(chatId);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        var inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setCallbackData(NEXT_JOKE);
        inlineKeyboardButton.setText(EmojiParser.parseToUnicode("next joke " + ":rolling_on_the_floor_laughing:"));
        rowInline.add(inlineKeyboardButton);
        rowsInLine.add(rowInline);
        markupInline.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInline);
        send(message);
    }

    private void showStart(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                "Hi, " + name + "! :smile:" + " Nice to meet you! I am a Simple Random Joke Bot created by Dmitrijs Finaskins from proj3c.io \n");
        sendMessage(answer, chatId);
    }

    private void commandNotFound(long chatId) {

        String answer = EmojiParser.parseToUnicode(
                "Command not recognized, please verify and try again :stuck_out_tongue_winking_eye: ");
        sendMessage(answer, chatId);

    }

    private void sendMessage(String textToSend, long chatId) {
        SendMessage message = new SendMessage(); // Create a message object object
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        send(message);
    }

    private void send(SendMessage msg) {
        try {
            execute(msg); // Sending our message object to user
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }
}*/


// *********** first playlist of tutorials
/*
@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdsRepository adsRepository;

    static final String HELP_TEXT = "This bot is created to make it easier to remember new English words. \n\n" +
            "You can execute commands from the main menu on the left or by typing a command: \n\n" +
            "Type /start to see a welcome message\n\n" +
            "Type /mydata to see data stored about yourself\n\n" +
            "Type /help to see this message again";

    private static final String YES_BUTTON = "YES_BUTTON";
    private static final String NO_BUTTON = "NO_BUTTON";
    private static final String ERROR_TEXT = "Error occurred: ";

    public TelegramBot(BotConfig config) {
        this.config = config;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/mydata", "get your stored data"));
        listOfCommands.add(new BotCommand("/deletedata", "delete your stored data"));
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

            if(messageText.contains("/send") && config.getOwnerId() == chatId) {
                var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                var users = userRepository.findAll();
                for (User user: users) {
                    sendMessage(user.getChatId(), textToSend, null);
                }
            } else {
                switch (messageText) {
                    case "/start":
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;
                    case "/help":
                        sendMessage(chatId, HELP_TEXT, null);
                        break;
                    case "/register":
                        register(chatId);
                        break;
                    default:
                        sendMessage(chatId, "Sorry, command was not recognized", null);
                }
            }
        } else if (update.hasCallbackQuery()) {
            String callBackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callBackData.equals(YES_BUTTON)) {
                String text = "You pressed YES button";
                executeEditMessageText(text, chatId, messageId);

            } else if (callBackData.equals(NO_BUTTON)) {
                String text = "You pressed NO button";
                executeEditMessageText(text, chatId, messageId);
            }
        }
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

            userRepository.save(user);
            log.info("User saved: " + user);
        }

    }

    private void startCommandReceived(long chatId, String name) {

        String answer = EmojiParser.parseToUnicode("Hi, " + name + ", nice to meet you!" + " :blush:");
        //String answer = "Hi, " + name + ", nice to meet you!";
        log.info("Replied to user " + name);

        sendMessage(chatId, answer, creatKeyboard());
    }

    private void sendMessage(long chatId, String textToSend, ReplyKeyboardMarkup keyboardMarkup) {
        SendMessage message = prepareMessage(chatId, textToSend);
        if (keyboardMarkup != null)
            message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    private void register(long chatId) {

        SendMessage message = prepareMessage(chatId, "Do you rely want ot register?");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> inLineRows = new ArrayList<>();
        List<InlineKeyboardButton> inLineRow = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("Yes");
        yesButton.setCallbackData(YES_BUTTON);

        var noButton = new InlineKeyboardButton();

        noButton.setText("No");
        noButton.setCallbackData(NO_BUTTON);

        inLineRow.add(yesButton);
        inLineRow.add(noButton);

        inLineRows.add(inLineRow);

        inlineKeyboardMarkup.setKeyboard(inLineRows);
        message.setReplyMarkup(inlineKeyboardMarkup);

        executeMessage(message);
    }

    private ReplyKeyboardMarkup creatKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();

        row.add("weather");
        row.add("get smth");

        keyboardRows.add(row);

        row = new KeyboardRow();

        row.add("register");
        row.add("check data");
        row.add("delete my data");

        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        return keyboardMarkup;
    }

    private void executeEditMessageText(String text, long chatId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int) messageId);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private SendMessage prepareMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        return message;
    }

    @Scheduled(cron = "${cron.scheduler}") // * - all value (0 0 * * * *) - every hour
    private void sendAds() {
        var ads = adsRepository.findAll();
        var users = userRepository.findAll();

        for (Ads ad : ads) {
            for (User user: users) {
                sendMessage(user.getChatId(), ad.getAd(), null);
            }
        }
    }
}
 */