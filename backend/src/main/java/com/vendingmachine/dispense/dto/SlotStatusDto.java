package com.vendingmachine.dispense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlotStatusDto {

    private Integer slotNumber;
    private String lastDispenseResult;
    private Instant lastDispenseAt;

    public SlotStatusDto() {
    }

    public SlotStatusDto(Integer slotNumber, String lastDispenseResult, Instant lastDispenseAt) {
        this.slotNumber = slotNumber;
        this.lastDispenseResult = lastDispenseResult;
        this.lastDispenseAt = lastDispenseAt;
    }

    public Integer getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(Integer slotNumber) {
        this.slotNumber = slotNumber;
    }

    public String getLastDispenseResult() {
        return lastDispenseResult;
    }

    public void setLastDispenseResult(String lastDispenseResult) {
        this.lastDispenseResult = lastDispenseResult;
    }

    public Instant getLastDispenseAt() {
        return lastDispenseAt;
    }

    public void setLastDispenseAt(Instant lastDispenseAt) {
        this.lastDispenseAt = lastDispenseAt;
    }
}
