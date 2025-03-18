package com.room.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.text.DecimalFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.room.app.dto.Expense;
import com.room.app.dto.ExpenseRequest;
import com.room.app.dto.Member;
import com.room.app.repository.ExpenseRepository;

@Service
public class TelegramBotService {

	private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
	private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);
	private static final int MAX_MESSAGE_LENGTH = 4096;
	private static final String MOBILE_REGEX = "^\\+\\d{10,14}$"; // Must match exactly
	private static final Pattern MOBILE_PATTERN = Pattern.compile("^\\+\\d{10,14}$");

	private final ExpenseService expenseService;
	private final MemberService memberService;
	private final ExpenseRepository expenseRepository;

	private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	public TelegramBotService(ExpenseService expenseService, MemberService memberService,
			ExpenseRepository expenseRepository) {
		this.expenseService = expenseService;
		this.memberService = memberService;
		this.expenseRepository = expenseRepository;
	}

	public Expense processExpenseMessage(String messageText, Long userId) throws Exception {
		try {
			Member member = memberService.getMemberByUserId(userId)
					.orElseThrow(() -> new Exception("❌ Register first with /register"));

			String[] parts = messageText.split("\\s*,\\s*", 3);

			if (parts.length < 2) {
				throw new IllegalArgumentException("""
						❌ Invalid format! Use:
						<description>, <amount>
						Example: 'Groceries, 2500'""");
			}

			if (!parts[1].matches("^[\\d.,]+$")) {
				throw new IllegalArgumentException("❌ Amount must be a number!");
			}

			String description = sanitizeInput(parts[0]);
			BigDecimal amount = parseAmount(parts[1]);
			LocalDate date = parseOptionalDate(parts.length > 2 ? parts[2] : null);

			ExpenseRequest request = new ExpenseRequest();
			request.setMemberId(member.getId());
			request.setDescription(description);
			request.setAmount(amount);
			request.setDate(date);

			// 6. Save and return expense
			return expenseService.addExpense(request);

		} catch (Exception e) {
			logger.error("Expense processing failed for input: '{}'", messageText, e);
			throw new Exception("⚠️ Failed to process expense: " + e.getMessage());
		}
	}

	private String validateDescription(String input) {
		String cleaned = input.trim();
		if (cleaned.isEmpty()) {
			throw new IllegalArgumentException("Description cannot be empty");
		}
		if (cleaned.length() > 100) {
			throw new IllegalArgumentException("Description too long (max 100 characters)");
		}
		return cleaned;
	}

	private BigDecimal parseAmount(String amountStr) {
		try {
			// Handle international number formats
			String normalized = amountStr.trim().replaceAll("[^\\d.,]", "") // Remove non-numeric characters
					.replaceAll("(?<=\\d)[.,](?=\\d{3})", ""); // Remove thousand separators

			// Handle comma decimal separator
			normalized = normalized.replace(',', '.');

			// Validate final format
			if (!normalized.matches("^\\d+(\\.\\d{1,2})?$")) {
				throw new NumberFormatException("Invalid amount format");
			}

			return new BigDecimal(normalized);
		} catch (Exception e) {
			throw new NumberFormatException("Invalid amount: " + amountStr + " | Valid formats: 40, 40.50, 1,000");
		}
	}

	private LocalDate parseOptionalDate(String dateStr) {
		if (dateStr == null || dateStr.isEmpty()) {
			return LocalDate.now();
		}

		try {
			return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("Invalid date format. Use DD/MM or DD/MM/YYYY");
		}
	}

	public String getAllExpenses() {
		try {
			List<Expense> expenses = expenseService.getAllActiveExpenses();
			if (expenses.isEmpty()) {
				return "📂 No expenses recorded yet.";
			}

			DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
			DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

			StringBuilder response = new StringBuilder("📜 All Expenses:\n\n");
			DecimalFormat df = new DecimalFormat("#.00");

			expenses.forEach(expense -> {
				String status = "";
				if (expense.getClearedAmount() != null && expense.getClearedAmount().compareTo(BigDecimal.ZERO) > 0) {
					if (expense.getClearedAmount().compareTo(expense.getAmount()) < 0) {
						// Partially cleared
						status = String.format(" - Partially cleared ₹%s (%s remaining)",
								df.format(expense.getClearedAmount()),
								df.format(expense.getAmount().subtract(expense.getClearedAmount())));
					} else {
						// Fully cleared
						status = String.format(" | ✅ Cleared by %s at %s",
								expense.getClearedBy() != null ? expense.getClearedBy().getName() : "N/A",
								expense.getClearedAt() != null ? expense.getClearedAt().format(dateTimeFormatter)
										: "N/A");
					}
				}

				String entry = String.format("👤 %s | 📌 %s | 📅 %s | 💰 ₹%s%s\n",
						expense.getMember() != null ? expense.getMember().getName() : "N/A",
						truncate(expense.getDescription(), 15), expense.getDate().format(dateFormatter),
						df.format(expense.getAmount()), status);

				if (response.length() + entry.length() > MAX_MESSAGE_LENGTH) {
					response.append("\n... (message truncated)");
					return;
				}
				response.append(entry);
			});

			// Add totals summary
			BigDecimal total = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

			BigDecimal clearedTotal = expenses.stream()
					.map(e -> e.getClearedAmount() != null ? e.getClearedAmount() : BigDecimal.ZERO)
					.reduce(BigDecimal.ZERO, BigDecimal::add);

			response.append("\n📊 Totals:").append("\n▶️ Total Expenses: ₹").append(df.format(total))
					.append("\n✅ Cleared Amount: ₹").append(df.format(clearedTotal)).append("\n⏳ Remaining: ₹")
					.append(df.format(total.subtract(clearedTotal)));

			return response.toString();
		} catch (Exception e) {
			return "⚠️ Error fetching expenses: " + e.getMessage();
		}
	}

	private String truncate(String text, int maxLength) {
		return text.length() > maxLength ? text.substring(0, maxLength - 3) + "..." : text;
	}

	
	public String registerMember(String[] parts, Long userId, Long chatId) { // Add userId parameter
		if (parts.length != 2 || !parts[1].startsWith("+")) {
			return "❌ Use: /register [Name] [+91xxxxxxxxxx]\nExample: /register Anand +917013209225";
		}

		String name = parts[0].trim();
		String mobile = parts[1].trim();

		if (!mobile.matches("^\\+\\d{10,14}$")) {
			return "❌ Invalid mobile format. Use +CountryCodeNumber";
		}

		try {
			// Check by USER ID instead of chat ID
			Member existing = memberService.getMemberByUserId(userId)
					.orElseThrow(() -> new ResourceNotFoundException("Not registered"));
			return "❌ Already registered as: " + existing.getName();
		} catch (ResourceNotFoundException e) {
			// Proceed with registration
		}

		if (memberService.existsByMobileNumber(mobile)) {
			return "❌ Mobile number already registered!";
		}

		Member member = new Member();
		member.setName(name);
		member.setMobileNumber(mobile);
		member.setUserId(userId); // Set unique user ID
		member.setChatId(chatId); // Store current chat ID
		memberService.save(member);

		return "✅ Registration successful!\nName: " + name + "\nMobile: " + mobile;
	}

	private String sanitizeInput(String input) {
		return input.trim().replaceAll("\\s+", " ");
	}

	private String truncateResponse(String text) {
		return text.length() > MAX_MESSAGE_LENGTH
				? text.substring(0, MAX_MESSAGE_LENGTH - 20) + "\n... (message truncated)"
				: text;
	}

	public String listAllMembers() {
		try {
			List<Member> members = memberService.getAllMembers();
			if (members.isEmpty()) {
				return "👥 No members registered yet";
			}

			StringBuilder response = new StringBuilder("📋 Registered Members:\n");
			members.forEach(member -> {
				String adminBadge = member.isAdmin() ? " 👑" : "";
				response.append(
						String.format("• %s%s\n  📱 %s\n", member.getName(), adminBadge, member.getMobileNumber()));
			});
			return truncateResponse(response.toString());
		} catch (Exception e) {
			log.error("Error listing members: {}", e.getMessage());
			return "⚠️ Error fetching member list. Please try later.";
		}
	}

	public String processExpenseReplyMessage(String messageText, Integer repliedMessageId, Long userId) {
	    try {
	        // Validate message format
	        String[] parts = messageText.trim().split("\\s+");
	        if (parts.length != 2 || !parts[1].equalsIgnoreCase("given")) {
	            return "❌ Invalid format! Use: `<amount> given`\nExample: `50 given`";
	        }

	        // Parse and validate amount
	        BigDecimal amount = parseAmount(parts[0]);
	        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
	            return "❌ Amount must be positive!";
	        }

	        // Verify clearing member
	        Member clearingMember = memberService.getMemberByUserId(userId)
	                .orElseThrow(() -> new SecurityException("Unregistered admin - Complete registration first"));

	        if (!clearingMember.isAdmin()) {
	            logger.warn("Non-admin clearance attempt by {}", clearingMember.getMobileNumber());
	            return "❌ Admin privileges required!\nUse /requestadmin to get access";
	        }

	        // Retrieve expense
	        Expense expense = expenseService.getExpenseByMessageId(repliedMessageId)
	                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));

	        // Check clearance status
	        if (expense.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
	            String clearedInfo = String.format("""
	                    ⚠️ Already fully cleared!
	                    Total: ₹%.2f
	                    Cleared by: %s (%s)
	                    Time: %s""", expense.getAmount(), expense.getClearedBy().getName(),
	                    expense.getClearedAt().format(dateTimeFormatter));
	            return clearedInfo;
	        }

	        // Process payment through service layer
	        Expense updatedExpense = expenseService.clearExpense(
	            expense.getId(),
	            clearingMember.getId(),
	            amount
	        );

	        // Build response from updated expense
	        String status = updatedExpense.isCleared() ? "FULLY CLEARED" : "PARTIALLY CLEARED";
	        return String.format("""
	                ✅ %s
	                Total: ₹%.2f
	                New Payment: ₹%.2f
	                Total Cleared: ₹%.2f
	                Remaining: ₹%.2f
	                Cleared by: %s (%s)
	                Time: %s""",
	                status,
	                updatedExpense.getAmount(),
	                updatedExpense.getLastClearedAmount(),
	                updatedExpense.getClearedAmount(),
	                updatedExpense.getRemainingAmount(),
	                clearingMember.getName(),
	                clearingMember.getMobileNumber(),
	                updatedExpense.getLastClearedAt().format(dateTimeFormatter));

	    } catch (NumberFormatException e) {
	        logger.error("Invalid amount format: {}", messageText);
	        return "❌ Invalid amount format! Use numbers only\nExample: `50` or `29.99`";
	    } catch (SecurityException e) {
	        logger.warn("Security violation: {}", e.getMessage());
	        return "❌ Security violation: " + e.getMessage();
	    } catch (ResourceNotFoundException e) {
	        logger.error("Resource not found: {}", e.getMessage());
	        return "❌ Error: " + e.getMessage();
	    } catch (IllegalArgumentException e) {
	        logger.error("Validation error: {}", e.getMessage());
	        return "❌ Validation error: " + e.getMessage();
	    } catch (Exception e) {
	        logger.error("System error clearing expense", e);
	        return "⚠️ System error: Unable to process request";
	    }
	}
	public void saveExpense(Expense expense) {
		expenseService.saveExpense(expense);
	}
	public String getExpenseSummary() {
	    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	    DecimalFormat df = new DecimalFormat("#.00");

	    return expenseRepository.findAllActive().stream().map(e -> {
	        BigDecimal cleared = e.getClearedAmount() != null ? e.getClearedAmount() : BigDecimal.ZERO;
	        String remaining = cleared.compareTo(BigDecimal.ZERO) == 0 ? "₹0.00" 
	                : "₹" + df.format(e.getAmount().subtract(cleared));

	        String lastCleared = e.getClearedAt() != null ? e.getClearedAt().format(dateTimeFormatter) : "Never";
	        // Ensure clearedBy uses the correct name; fallback to "N/A" if null
	        String clearedBy = e.getClearedBy() != null ? e.getClearedBy().getName() : "N/A";

	        return String.format("""
	            📌 %s
	            Total: ₹%s | Cleared: ₹%s | Remaining: %s
	            Last cleared: %s by %s""", 
	            e.getDescription(), 
	            df.format(e.getAmount()), 
	            df.format(cleared),
	            remaining, 
	            lastCleared, 
	            clearedBy);
	    }).collect(Collectors.joining("\n\n"));
	}

	public String getDetailedExpenseSummary() {
		try {
			Map<String, BigDecimal> totalSummary = getExpenseSummaryByMember();
			Map<String, Map<String, BigDecimal>> clearedSummary = getClearedSummaryByMember();

			if (totalSummary.isEmpty()) {
				return "📭 No expenses recorded yet";
			}

			StringBuilder response = new StringBuilder("📊 Detailed Expense Summary\n\n");

			totalSummary.forEach((memberName, total) -> {
				// Get cleared amounts for this member
				Map<String, BigDecimal> clearers = clearedSummary.getOrDefault(memberName, new HashMap<>());

				// Sum all partial clearances
				BigDecimal clearedTotal = clearers.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

				// Calculate remaining balance
				BigDecimal remaining = total.subtract(clearedTotal);

				// Build output line
				response.append("• ").append(memberName).append(": ₹").append(total.setScale(2, RoundingMode.HALF_UP));

				if (clearedTotal.compareTo(BigDecimal.ZERO) > 0) {
					response.append(" -> Cleared: ");
					clearers.forEach((clearer, amount) -> response.append("₹").append(amount.setScale(2)).append(" by ")
							.append(clearer).append(", "));
					response.setLength(response.length() - 2); // Remove last comma
				}

				response.append(" | Remaining: ₹").append(remaining.setScale(2, RoundingMode.HALF_UP)).append("\n");
			});

			return truncateResponse(response.toString());
		} catch (Exception e) {
			return "⚠️ Error generating summary: " + e.getMessage();
		}
	}

	private Map<String, BigDecimal> getExpenseSummaryByMember() {
		return expenseRepository.findAllActive().stream()
				.collect(Collectors.groupingBy(e -> e.getMember().getName(),
						Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, // Total original amount
								BigDecimal::add)));
	}

	private Map<String, Map<String, BigDecimal>> getClearedSummaryByMember() {
		return expenseRepository.findAllActive().stream().filter(e -> e.getClearedAmount() != null)
				.filter(e -> e.getClearedAmount().compareTo(BigDecimal.ZERO) > 0)
				.filter(e -> (e.getClearedBy() != null && e.getClearedBy().getName() != null)
						|| (e.getLastClearedBy() != null && e.getLastClearedBy().getName() != null))
				.collect(Collectors.groupingBy(e -> e.getMember().getName(), Collectors.groupingBy(e -> {
					if (e.getClearedBy() != null) {
						return e.getClearedBy().getName();
					} else {
						return e.getLastClearedBy().getName();
					}
				}, Collectors.reducing(BigDecimal.ZERO, Expense::getClearedAmount, BigDecimal::add))));
	}

	public String processMakeAdminCommand(String mobileNumber, Long requesterChatId) {
		try {
			Member requester = memberService.getMemberByChatId(requesterChatId)
					.orElseThrow(() -> new SecurityException("Unauthorized access attempt"));

			if (!requester.isAdmin()) {
				log.warn("Non-admin promotion attempt by {}", requester.getMobileNumber());
				return "❌ Administrator privileges required!";
			}

			Member target = memberService.getMemberByMobile(mobileNumber)
					.orElseThrow(() -> new IllegalArgumentException("Member not found"));

			if (target.isAdmin()) {
				return "ℹ️ User is already an administrator";
			}

			target.setAdmin(true);
			memberService.save(target);

			return String.format("✅ Success!\n%s (%s) is now an administrator", target.getName(),
					target.getMobileNumber());
		} catch (SecurityException e) {
			return "🔒 Security violation detected!";
		} catch (IllegalArgumentException e) {
			return "❌ " + e.getMessage();
		} catch (Exception e) {
			log.error("Admin promotion error: {}", e.getMessage());
			return "⚠️ System error during admin promotion";
		}
	}

	public String initAdmin(Long chatId) {
		try {
			if (memberService.hasExistingAdmins()) {
				return "❌ Initial admin already exists!";
			}

			Member member = memberService.getMemberByChatId(chatId)
					.orElseThrow(() -> new IllegalArgumentException("Complete registration first!"));

			member.setAdmin(true);
			memberService.save(member);

			return String.format("✅ You are now the primary administrator!\nName: %s\nMobile: %s", member.getName(),
					member.getMobileNumber());
		} catch (IllegalArgumentException e) {
			return "❌ " + e.getMessage();
		} catch (Exception e) {
			log.error("Init admin error: {}", e.getMessage());
			return "⚠️ System error during admin initialization";
		}
	}

	// In TelegramBotService.java
	public String promoteToAdmin(String mobileNumber, Long requesterChatId) { // Rename parameter
		try {
			Member requester = memberService.getMemberByChatId(requesterChatId)
					.orElseThrow(() -> new Exception("❌ You're not registered!"));
			if (!requester.isAdmin())
				return "❌ Only admins can promote members!";

			// Use mobile number instead of name
			Member target = memberService.getMemberByMobileNumber(mobileNumber)
					.orElseThrow(() -> new Exception("❌ Member not found with mobile: " + mobileNumber));

			target.setAdmin(true);
			memberService.save(target);
			return "✅ " + target.getName() + " is now an admin!";
		} catch (Exception e) {
			return "⚠️ Error: " + e.getMessage();
		}
	}

	public String processRegisterMember(String name, String mobileNumber, Long chatId) {
		try {
			if (memberService.getMemberByChatId(chatId).isPresent()) {
				return "❌ You're already registered!";
			}

			if (!mobileNumber.matches("^\\+\\d{10,14}$")) {
				return "❌ Invalid mobile format. Use +CountryCodeNumber";
			}

			if (memberService.existsByMobileNumber(mobileNumber)) {
				return "❌ Mobile number already registered!";
			}

			Member newMember = new Member();
			newMember.setName(name.trim());
			newMember.setMobileNumber(mobileNumber.trim());
			newMember.setChatId(chatId);
			memberService.save(newMember);

			return String.format("""
					✅ Registration Successful!
					Name: %s
					Mobile: %s
					""", name, mobileNumber);

		} catch (Exception e) {
			log.error("Registration error: {}", e.getMessage());
			return "⚠️ Registration failed: " + e.getMessage();
		}
	}

	public DateTimeFormatter getDateFormatter() {
		return dateFormatter;
	}

	// In TelegramBotService.java
	public String processRegisterMember(String name, String mobile, Long userId, Long chatId) {
		// Validate mobile format
		if (!getMobilePattern().matcher(mobile).matches()) {
			throw new IllegalArgumentException("❌ Invalid mobile number format");
		}

		// Check for existing registration
		if (memberService.existsByMobileNumber(mobile)) {
			return "❌ Already registered!";
		}

		// Save member
		Member member = new Member();
		member.setName(name);
		member.setMobileNumber(mobile);
		member.setUserId(userId);
		member.setChatId(chatId);
		memberService.save(member);

		return "✅ Registration successful!\nName: " + name + "\nMobile: " + mobile;
	}

	public static Pattern getMobilePattern() {
		return MOBILE_PATTERN;
	}
	public String getExpensesByMember(String memberName) {
	    try {
	        List<Expense> expenses = expenseService.getExpensesByMemberName(memberName);
	        if (expenses.isEmpty()) {
	            return "📭 No expenses found for member: " + memberName;
	        }

	        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	        DecimalFormat df = new DecimalFormat("#.00");

	        StringBuilder response = new StringBuilder("📊 Expenses for " + memberName + ":\n\n");
	        BigDecimal total = BigDecimal.ZERO;

	        for (Expense expense : expenses) {
	            String entry = String.format("""
	                📌 %s
	                💰 ₹%s | 📅 %s
	                %s
	                """,
	                expense.getDescription(),
	                df.format(expense.getAmount()),
	                expense.getDate().format(dateFormatter),
	                expense.getClearedAmount().compareTo(BigDecimal.ZERO) > 0 ?
	                    "✅ Cleared: ₹" + df.format(expense.getClearedAmount()) :
	                    "⏳ Pending"
	            );

	            if (response.length() + entry.length() > MAX_MESSAGE_LENGTH) {
	                response.append("\n... (message truncated)");
	                break;
	            }
	            response.append(entry);
	            total = total.add(expense.getAmount());
	        }

	        response.append("\n💵 Total: ₹").append(df.format(total));
	        return response.toString();

	    } catch (Exception e) {
	        logger.error("Error fetching expenses for member: {}", memberName, e);
	        return "⚠️ Error: " + e.getMessage();
	    }
	}

	
}
