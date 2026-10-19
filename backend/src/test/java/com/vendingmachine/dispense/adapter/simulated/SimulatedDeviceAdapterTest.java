package com.vendingmachine.dispense.adapter.simulated;

import com.vendingmachine.dispense.dto.DeviceEventMessage;
import com.vendingmachine.dispense.dto.DispenseCommandMessage;
import com.vendingmachine.dispense.dto.EventAckMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedDeviceAdapterTest {

    private SimulatedDeviceAdapter adapter;
    private List<DeviceEventMessage> receivedEvents;

    @BeforeEach
    void setUp() {
        adapter = new SimulatedDeviceAdapter("SUCCESS", false);
        receivedEvents = new CopyOnWriteArrayList<>();
        adapter.setEventListener(receivedEvents::add);
    }

    private DispenseCommandMessage createCommand(UUID commandId) {
        return new DispenseCommandMessage(
                commandId,
                "VM001",
                UUID.randomUUID(),
                2,
                Instant.now(),
                Instant.now().plusSeconds(45),
                Map.of("motorRunMs", 800)
        );
    }

    @Test
    @DisplayName("Kịch bản SUCCESS: Nhận lệnh, gửi COMMAND_ACCEPTED và DISPENSE_RESULT(SUCCESS, MOTOR_CYCLE_ONLY)")
    void scenario_success() {
        adapter.setScenario(SimulationScenario.SUCCESS);
        UUID cmdId = UUID.randomUUID();

        adapter.sendDispenseCommand(createCommand(cmdId));

        assertThat(receivedEvents).hasSize(2);
        DeviceEventMessage accepted = receivedEvents.get(0);
        assertThat(accepted.getEventType()).isEqualTo("COMMAND_ACCEPTED");
        assertThat(accepted.getCommandId()).isEqualTo(cmdId);
        assertThat(accepted.getSlotNumber()).isEqualTo(2);

        DeviceEventMessage result = receivedEvents.get(1);
        assertThat(result.getEventType()).isEqualTo("DISPENSE_RESULT");
        assertThat(result.getCommandId()).isEqualTo(cmdId);
        assertThat(result.getResult()).isEqualTo("SUCCESS");
        assertThat(result.getVerificationLevel()).isEqualTo("MOTOR_CYCLE_ONLY");
        assertThat(result.getFailureCode()).isNull();
    }

    @Test
    @DisplayName("Kịch bản FAILURE: Gửi COMMAND_ACCEPTED và DISPENSE_RESULT(FAILURE, MOTOR_TIMEOUT)")
    void scenario_failure() {
        adapter.setScenario(SimulationScenario.FAILURE);
        UUID cmdId = UUID.randomUUID();

        adapter.sendDispenseCommand(createCommand(cmdId));

        assertThat(receivedEvents).hasSize(2);
        assertThat(receivedEvents.get(0).getEventType()).isEqualTo("COMMAND_ACCEPTED");
        DeviceEventMessage result = receivedEvents.get(1);
        assertThat(result.getEventType()).isEqualTo("DISPENSE_RESULT");
        assertThat(result.getResult()).isEqualTo("FAILURE");
        assertThat(result.getFailureCode()).isEqualTo("MOTOR_TIMEOUT");
    }

    @Test
    @DisplayName("Kịch bản OFFLINE: isDeviceOnline trả về false và không dispatch event nào")
    void scenario_offline() {
        adapter.setScenario(SimulationScenario.OFFLINE);
        assertThat(adapter.isDeviceOnline("VM001")).isFalse();

        adapter.sendDispenseCommand(createCommand(UUID.randomUUID()));

        assertThat(receivedEvents).isEmpty();
    }

    @Test
    @DisplayName("Kịch bản TIMEOUT: Chỉ gửi COMMAND_ACCEPTED, không bao giờ gửi DISPENSE_RESULT")
    void scenario_timeout() {
        adapter.setScenario(SimulationScenario.TIMEOUT);
        UUID cmdId = UUID.randomUUID();

        adapter.sendDispenseCommand(createCommand(cmdId));

        assertThat(receivedEvents).hasSize(1);
        assertThat(receivedEvents.get(0).getEventType()).isEqualTo("COMMAND_ACCEPTED");
    }

    @Test
    @DisplayName("Kịch bản UNCERTAIN: Gửi COMMAND_ACCEPTED và DISPENSE_RESULT(UNCERTAIN, REBOOT_DURING_EXECUTION)")
    void scenario_uncertain() {
        adapter.setScenario(SimulationScenario.UNCERTAIN);
        UUID cmdId = UUID.randomUUID();

        adapter.sendDispenseCommand(createCommand(cmdId));

        assertThat(receivedEvents).hasSize(2);
        assertThat(receivedEvents.get(0).getEventType()).isEqualTo("COMMAND_ACCEPTED");
        DeviceEventMessage result = receivedEvents.get(1);
        assertThat(result.getEventType()).isEqualTo("DISPENSE_RESULT");
        assertThat(result.getResult()).isEqualTo("UNCERTAIN");
        assertThat(result.getFailureCode()).isEqualTo("REBOOT_DURING_EXECUTION");
    }

    @Test
    @DisplayName("Chống lặp lệnh: Gửi lại cùng commandId lần thứ hai sẽ bị từ chối với DUPLICATE_COMMAND")
    void duplicateCommandRejection() {
        adapter.setScenario(SimulationScenario.SUCCESS);
        UUID cmdId = UUID.randomUUID();

        // Lần 1: Thành công
        adapter.sendDispenseCommand(createCommand(cmdId));
        assertThat(receivedEvents).hasSize(2);

        // Lần 2: Cùng commandId -> Bị từ chối
        adapter.sendDispenseCommand(createCommand(cmdId));
        assertThat(receivedEvents).hasSize(3);
        DeviceEventMessage rejected = receivedEvents.get(2);
        assertThat(rejected.getEventType()).isEqualTo("COMMAND_REJECTED");
        assertThat(rejected.getRejectionReason()).isEqualTo("DUPLICATE_COMMAND");
    }

    @Test
    @DisplayName("Tiếp nhận EVENT_ACK: Lưu vào acknowledgedEventIds")
    void sendEventAck_recordsEventId() {
        UUID eventId = UUID.randomUUID();
        UUID cmdId = UUID.randomUUID();
        EventAckMessage ack = new EventAckMessage(UUID.randomUUID(), eventId, cmdId, "VM001", UUID.randomUUID(), "PROCESSED", Instant.now());

        adapter.sendEventAck(ack);

        assertThat(adapter.getAcknowledgedEventIds()).contains(eventId);
    }
}
