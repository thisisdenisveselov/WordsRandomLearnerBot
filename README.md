# WordsRandomLearnerBot
Telegram Bot, written in Java, that helps in memorizing new foreign words, added to Google Translate saved phrases list.

### Adding your own phrase list:
1. Export saved phrases to Google Sheets
2. Save as .xlsx file
3. Upload file to bot

![image](https://github.com/user-attachments/assets/e6eb7c16-551c-40d5-abea-83eeea65690b)

### Logic for showing the next phrase:
The bot shows a random phrase from a list of phrases that have the highest priority and have not been shown for 20 iterations(STEPS_AMOUNT)
"I know" button decreases priority. "I don't know" - increases

![image](https://github.com/user-attachments/assets/babd550a-a623-4f21-90aa-64e81571a86f)

Stack - Spring Boot, TelegramLongPollingBot library, Lombok, Postgresql
