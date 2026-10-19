package com.vendingmachine.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SePayWebhookDto {

    private Long id;
    private String gateway;
    private String transactionDate;
    private String accountNumber;
    private String subAccount;
    private String code;
    private String content;
    private String transferType;
    private String description;
    private Integer transferAmount;
    private Long accumulated;
    private String referenceCode;

    public SePayWebhookDto() {
    }

    public SePayWebhookDto(Long id, String gateway, String transactionDate, String accountNumber,
                           String subAccount, String code, String content, String transferType,
                           String description, Integer transferAmount, Long accumulated, String referenceCode) {
        this.id = id;
        this.gateway = gateway;
        this.transactionDate = transactionDate;
        this.accountNumber = accountNumber;
        this.subAccount = subAccount;
        this.code = code;
        this.content = content;
        this.transferType = transferType;
        this.description = description;
        this.transferAmount = transferAmount;
        this.accumulated = accumulated;
        this.referenceCode = referenceCode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getTransactionDate() { return transactionDate; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getSubAccount() { return subAccount; }
    public void setSubAccount(String subAccount) { this.subAccount = subAccount; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getTransferAmount() { return transferAmount; }
    public void setTransferAmount(Integer transferAmount) { this.transferAmount = transferAmount; }

    public Long getAccumulated() { return accumulated; }
    public void setAccumulated(Long accumulated) { this.accumulated = accumulated; }

    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }
}
