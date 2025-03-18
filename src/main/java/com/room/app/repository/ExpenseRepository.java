package com.room.app.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.room.app.dto.Expense;
import com.room.app.dto.PaymentHistory;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
	@Query("SELECT e FROM Expense e WHERE e.isDeleted = 'N'")
	List<Expense> findAllActive();

	@Query("SELECT e FROM Expense e WHERE e.isDeleted = 'N' AND e.id = :id")
	Optional<Expense> findActiveById(@Param("id") Long id);

	List<Expense> findByIsDeleted(String isDeleted);

	@Query("SELECT e.member, SUM(e.amount) FROM Expense e GROUP BY e.member")
	List<Object[]> getTotalExpensesByMember();

	@Query("SELECT e.member, SUM(e.amount) as total FROM Expense e GROUP BY e.member ORDER BY total DESC")
	List<Object[]> getTopSpenderWithAmount();

	List<Expense> findByMemberId(Long memberId);

	List<Expense> findByMemberIsNull();

	Optional<Expense> findByMessageId(Integer messageId);

	@Query("SELECT SUM(e.amount) FROM Expense e WHERE e.member.name = :member AND e.cleared = true")
	Optional<BigDecimal> findClearedAmountByMember(@Param("member") String member);

	List<Expense> findByClearedTrue();

	@Query("SELECT e FROM Expense e WHERE e.date BETWEEN :startDate AND :endDate")
	List<Expense> findByDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	@Query("SELECT e FROM Expense e WHERE e.member.name ILIKE %:name% AND e.isDeleted = 'N'")
	List<Expense> findByMemberNameContainingIgnoreCase(@Param("name") String name);

	
	

}
