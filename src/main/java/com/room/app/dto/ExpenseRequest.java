package com.room.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ExpenseRequest {
	@NotNull
    private Long memberId;
    
    @NotBlank
    private String description;
    
    @NotNull
    private LocalDate date;
    
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    private Integer messageId;
    
	public ExpenseRequest(Long long1, String description2, LocalDate date2, BigDecimal amount2) {
		// TODO Auto-generated constructor stub
	}

	public ExpenseRequest() {
		// TODO Auto-generated constructor stub
	}

	public Long getMemberId() {
		return memberId;
	}

	public void setMemberId(Long memberId) {
		this.memberId = memberId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public Integer getMessageId() {
		return messageId;
	}

	public void setMessageId(Integer messageId) {
		this.messageId = messageId;
	}

	
}