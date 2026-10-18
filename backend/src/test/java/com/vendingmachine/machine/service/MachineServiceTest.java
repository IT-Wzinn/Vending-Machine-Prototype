package com.vendingmachine.machine.service;

import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.ResourceNotFoundException;
import com.vendingmachine.machine.dto.MachineResponseDto;
import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.entity.MachineStatus;
import com.vendingmachine.machine.repository.MachineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineServiceTest {

    @Mock
    private MachineRepository machineRepository;

    @InjectMocks
    private MachineService machineService;

    private Machine activeMachine;
    private Machine inactiveMachine;

    @BeforeEach
    void setUp() {
        activeMachine = new Machine(
                UUID.randomUUID(),
                "VM001",
                "Máy bán hàng tầng 1",
                MachineStatus.ACTIVE,
                null
        );

        inactiveMachine = new Machine(
                UUID.randomUUID(),
                "VM002",
                "Máy bán hàng tầng 2",
                MachineStatus.INACTIVE,
                "Máy tạm ngừng hoạt động"
        );
    }

    @Test
    @DisplayName("getMachineByCode: Thành công trả về DTO khi máy tồn tại")
    void getMachineByCode_Success() {
        when(machineRepository.findByMachineCode("VM001")).thenReturn(Optional.of(activeMachine));

        MachineResponseDto dto = machineService.getMachineByCode("VM001");

        assertThat(dto).isNotNull();
        assertThat(dto.getMachineId()).isEqualTo("VM001");
        assertThat(dto.getName()).isEqualTo("Máy bán hàng tầng 1");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getMessage()).isNull();
    }

    @Test
    @DisplayName("getMachineByCode: Ném ResourceNotFoundException khi máy không tồn tại")
    void getMachineByCode_NotFound() {
        when(machineRepository.findByMachineCode("NON_EXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> machineService.getMachineByCode("NON_EXISTENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Máy bán hàng không tồn tại.")
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MACHINE_NOT_FOUND));
    }

    @Test
    @DisplayName("getActiveMachineEntity: Thành công trả về entity khi máy đang ACTIVE")
    void getActiveMachineEntity_Success() {
        when(machineRepository.findByMachineCode("VM001")).thenReturn(Optional.of(activeMachine));

        Machine machine = machineService.getActiveMachineEntity("VM001");

        assertThat(machine).isNotNull();
        assertThat(machine.getStatus()).isEqualTo(MachineStatus.ACTIVE);
    }

    @Test
    @DisplayName("getActiveMachineEntity: Ném BusinessException với HTTP 422 khi máy INACTIVE")
    void getActiveMachineEntity_Inactive() {
        when(machineRepository.findByMachineCode("VM002")).thenReturn(Optional.of(inactiveMachine));

        assertThatThrownBy(() -> machineService.getActiveMachineEntity("VM002"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Máy bán hàng hiện đang tạm ngừng hoạt động.")
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.MACHINE_INACTIVE);
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
    }
}
