package com.room.app.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.room.app.dto.Expense;
import com.room.app.dto.ExpenseRequest;
import com.room.app.dto.PaymentHistory;
import com.room.app.dto.User;
import com.room.app.exception.AccessDeniedException;
import com.room.app.repository.UserRepository;
import com.room.app.service.ExpenseService;
import com.room.app.service.ResourceNotFoundException;

@RestController
@RequestMapping("/api/expenses")
@CrossOrigin(origins = "http://localhost:3000")
public class ExpenseController<ExpenseResponse> {
	private final ExpenseService expenseService;
	private final UserRepository userRepository;

	@Autowired
	public ExpenseController(ExpenseService expenseService, UserRepository userRepository) {
		this.expenseService = expenseService;
		this.userRepository = userRepository;
	}

	@GetMapping
	public List<Expense> getAllExpenses() {
		return expenseService.getAllActiveExpenses();
	}

	@PostMapping
	public ResponseEntity<Expense> createExpense(@RequestBody ExpenseRequest request) throws ResourceNotFoundException {
		return new ResponseEntity<>(expenseService.addExpense(request), HttpStatus.CREATED);
	}

	@GetMapping("/summary")
	public ResponseEntity<Map<String, Map<String, BigDecimal>>> getMemberBalances() {
		Map<String, BigDecimal> totalMap = expenseService.getExpenseSummaryByMember();
		Map<String, BigDecimal> clearedMap = expenseService.getClearedSummaryByMember();

		Map<String, Map<String, BigDecimal>> result = totalMap.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> {
					BigDecimal total = entry.getValue();
					BigDecimal cleared = clearedMap.getOrDefault(entry.getKey(), BigDecimal.ZERO);
					return Map.of("total", total, "cleared", cleared, "remaining", total.subtract(cleared));
				}));

		return ResponseEntity.ok(result);
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@PutMapping("/{id}")
	public ResponseEntity<Expense> updateExpense(@PathVariable Long id, @RequestBody ExpenseRequest request,
			Principal principal) throws AccessDeniedException, ResourceNotFoundException {
		User user = getAuthenticatedUser(principal);
		return ResponseEntity.ok(expenseService.updateExpense(id, request, user));
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteExpense(@PathVariable Long id, Principal principal)
			throws AccessDeniedException, ResourceNotFoundException {

		// Get authenticated admin user
		User admin = userRepository.findByUsername(principal.getName())
				.orElseThrow(() -> new AccessDeniedException("User not authenticated"));

		// Perform soft delete with audit info
		expenseService.softDeleteExpense(id, admin);

		return ResponseEntity.noContent().build();
	}

	private User getAuthenticatedUser(Principal principal) {
		return userRepository.findByUsername(principal.getName())
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping("/clear/{expenseId}")
    public ResponseEntity<?> clearExpense(
        @PathVariable Long expenseId,
        @RequestParam("memberId") Long memberId,
        @RequestParam("amount") BigDecimal amount
    ) {
        try {
            Expense clearedExpense = expenseService.clearExpense(expenseId, memberId, amount);
            return ResponseEntity.ok(clearedExpense);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Expense not found", "details", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid request", "details", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Server error", "details", e.getMessage()));
        }
    }
	@GetMapping("/{expenseId}/payments")
	public ResponseEntity<List<PaymentHistory>> getPaymentHistory(@PathVariable Long expenseId) {
	    List<PaymentHistory> payments = expenseService.getPaymentHistoryByExpense(expenseId);
	    return ResponseEntity.ok(payments);
	}
	
}
