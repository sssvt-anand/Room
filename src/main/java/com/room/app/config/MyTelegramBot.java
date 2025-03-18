package com.room.app.config;

import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.room.app.dto.Expense;
import com.room.app.service.TelegramBotService;

@Component
public class MyTelegramBot extends TelegramLongPollingBot {

	private static final Logger log = LoggerFactory.getLogger(MyTelegramBot.class);
	private static final String MOBILE_REGEX = "^\\+\\d{10,14}$"; // Properly escaped
//	private static final String MOBILE_REGEX1 = "^\\+[1-9]\\d{1,14}$" ;
	private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final TelegramBotService telegramBotService;

	@Value("${telegram.bot.token}")
	private String botToken;
	@Value("${telegram.bot.username}")
	private String botUsername;

	public MyTelegramBot(TelegramBotService telegramBotService) {
		this.telegramBotService = telegramBotService;
	}

	@Override
	public String getBotUsername() {
		return botUsername;
	}

	@Override
	public String getBotToken() {
		return botToken;
	}

	@Override
	public void onUpdateReceived(Update update) {
		if (update.hasMessage()) {
			Message message = update.getMessage();
			Long userId = message.getFrom().getId();
			Long chatId = message.getChatId();
			if (message.hasText()) {
				handleMessage(userId, chatId, message);
			}
		}
	}

	private void handleMessage(Long userId, Long chatId, Message message) {
	    try {
	        if (message.getReplyToMessage() != null) {
	            handleReply(message, chatId);
	            return;
	        }

	        String text = message.getText();
	        String[] parts = text.split(" ", 2);
	        String commandPart = parts[0].toLowerCase();

	        if (commandPart.startsWith("/") || commandPart.equals("full")) {
	            handleCommand(userId, chatId, message);
	        } else {
	            Expense expense = telegramBotService.processExpenseMessage(text, userId);
	            sendExpenseConfirmation(chatId, expense);
	        }
	    } catch (Exception e) {
	        sendErrorMessage(chatId, e.getMessage());
	    }
	}

	private void sendExpenseConfirmation(Long chatId, Expense expense) {
		try {
			String response = String.format("""
					✅ Expense Added:
					📌 %s
					💰 ₹%.2f
					📅 %s""", expense.getDescription(), expense.getAmount(), expense.getDate().format(DATE_FORMATTER));

			// Send message and save message ID
			Message sentMessage = execute(SendMessage.builder().chatId(chatId.toString()).text(response).build());

			expense.setMessageId(sentMessage.getMessageId());
			telegramBotService.saveExpense(expense); // Critical for reply lookup

		} catch (Exception e) {
			log.error("Confirmation error: {}", e.getMessage());
		}
	}

	private void sendErrorMessage(Long chatId, String message) {
		sendResponse(chatId, "⚠️ Error: " + message);
	}

	private void handleReply(Message message, Long chatId) {
		Long userId = message.getFrom().getId(); // Critical addition
		String response = telegramBotService.processExpenseReplyMessage(message.getText(),
				message.getReplyToMessage().getMessageId(), userId // Pass user ID instead of chat ID
		);
		sendResponse(chatId, response);
	}

	private void handleRegistration(Long userId, Long chatId, String[] parts) {
		try {
			if (parts.length < 2) {
				sendResponse(chatId, "❌ Format: /register [Name] [+Phone]\nExample: /register Alice +917013209225");
				return;
			}

			String registrationText = parts[1].trim();
			int plusIndex = registrationText.indexOf('+');

			if (plusIndex == -1) {
				sendResponse(chatId, "❌ Mobile must start with '+'\nExample: +917013209225");
				return;
			}

			String name = registrationText.substring(0, plusIndex).trim();
			String mobile = registrationText.substring(plusIndex).trim();

			if (!mobile.matches(MOBILE_REGEX)) {
				sendResponse(chatId, "❌ Invalid mobile\nUse 10-14 digits after +\nExample: +917013209225");
				return;
			}

			String response = telegramBotService.processRegisterMember(name, mobile, // Already includes +
					userId, chatId);
			sendResponse(chatId, response);
		} catch (Exception e) {
			log.error("Registration error", e);
			sendResponse(chatId, "⚠️ Registration failed. Contact admin.");
		}
	}

	private void handleAdminPromotion(Long chatId, String mobileNumber) {
		if (mobileNumber.isEmpty()) {
			sendResponse(chatId, "❌ Usage: /makeadmin +CountryCodeNumber");
			return;
		}

		if (!mobileNumber.matches(MOBILE_REGEX)) {
			sendResponse(chatId, "❌ Invalid mobile format. Use +CountryCodeNumber");
			return;
		}

		String response = telegramBotService.promoteToAdmin(mobileNumber, chatId);
		sendResponse(chatId, response);
	}

	private void handleExpenseMessage(Message message, Long chatId, String text) {
		try {
			Expense expense = telegramBotService.processExpenseMessage(text, chatId);
			String response = String.format("""
					✅ Expense Recorded
					📌 %s
					💰 ₹%.2f
					📅 %s
					👤 %s (%s)
					""", expense.getDescription(), expense.getAmount(),
					expense.getDate().format(telegramBotService.getDateFormatter()), expense.getMember().getName(),
					expense.getMember().getMobileNumber());

			Message sentMessage = execute(SendMessage.builder().chatId(chatId.toString()).text(response).build());

			expense.setMessageId(sentMessage.getMessageId());
			telegramBotService.saveExpense(expense);
		} catch (Exception e) {
			log.error("Expense processing error: {}", e.getMessage());
			sendSecureMessage(chatId.toString(), "⚠️ Error processing expense");
		}
	}

	private void sendResponse(Long chatId, String text) {
		try {
			execute(SendMessage.builder().chatId(chatId.toString()).text(text).parseMode("Markdown").build());
		} catch (TelegramApiException e) {
			log.error("Message sending error: {}", e.getMessage());
		}
	}

	private void sendSecureMessage(String chatId, String text) {
		log.info("Secure message sent to {}: {}", chatId, text);
		sendResponse(Long.parseLong(chatId), text);
	}

	private void handleCommand(Long userId, Long chatId, Message message) {
		String[] parts = message.getText().split(" ", 2);
		String command = parts[0].toLowerCase();
		String arguments = parts.length > 1 ? parts[1] : ""; // Extract arguments

		try {
			String response;

			switch (command) {
			case "/addexpense" -> {
				try {
					Expense expense = telegramBotService.processExpenseMessage(arguments, userId);

					String responseText = String.format("""
							✅ Expense Added
							📌 %s
							💰 ₹%.2f
							📅 %s
							👤 %s""", expense.getDescription(), expense.getAmount(),
							expense.getDate().format(DATE_FORMATTER), expense.getMember().getName());

					Message sentMessage = execute(
							SendMessage.builder().chatId(chatId.toString()).text(responseText).build());

					expense.setMessageId(sentMessage.getMessageId());
					telegramBotService.saveExpense(expense);
					response = responseText;

				} catch (Exception e) {
					response = "⚠️ Error: " + e.getMessage();
					log.error("Expense error", e);
				}
			}
			case "/expensesbymember" -> {
			    if (arguments.isEmpty()) {
			        sendResponse(chatId, "❌ Usage: /expensesbymember [Member Name]");
			    } else {
			        String memberName = arguments.trim();
			        response = telegramBotService.getExpensesByMember(memberName);
			        sendResponse(chatId, response);
			    }
			}
			case "/register" -> handleRegistration(userId, chatId, parts);
			case "/makeadmin" -> handleAdminPromotion(chatId, arguments); // ✅ Fixed here
			case "/initadmin" -> sendResponse(chatId, telegramBotService.initAdmin(chatId));
			case "/members" -> sendResponse(chatId, telegramBotService.listAllMembers());
			case "/t" -> sendResponse(chatId, telegramBotService.getAllExpenses());
			case "/summary" -> sendResponse(chatId, telegramBotService.getExpenseSummary());
			case "full" -> sendResponse(chatId, telegramBotService.getDetailedExpenseSummary());
			case "/help" -> sendResponse(chatId, getHelpMessage());
			default -> sendResponse(chatId, "❌ Unknown command. Use /help");
			}
		} catch (Exception e) {
			log.error("Command error: {}", e.getMessage());
		}
	}

	private String getHelpMessage() {
		return """
				📖 *Available Commands*

				*Registration*
				/register [Name] [+CountryCodeNumber] - Private registration
				  Example: /register Anand +917013209225

				*Expenses*
				/expense [Description], [Amount] - Add expense
				/t - Show total expenses
				
				/full -detailed expense summary
				/expensesbymember [Name] - Show expenses for a member

				*Administration*
				/initadmin - Become first admin (private chat)
				/makeadmin [+MobileNumber] - Promote user (admins only)

				*General*
				/members - List all members
				/help - Show this message
				""";
	}
}