package com.vendingmachine.machine.service;

import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.ResourceNotFoundException;
import com.vendingmachine.machine.dto.MachineResponseDto;
import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.entity.MachineStatus;
import com.vendingmachine.machine.repository.MachineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MachineService {

    private final MachineRepository machineRepository;

    public MachineService(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    public MachineResponseDto getMachineByCode(String machineCode) {
        Machine machine = machineRepository.findByMachineCode(machineCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.MACHINE_NOT_FOUND,
                        "Máy bán hàng không tồn tại."
                ));
        return MachineResponseDto.fromEntity(machine);
    }

    public Machine getActiveMachineEntity(String machineCode) {
        Machine machine = machineRepository.findByMachineCode(machineCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.MACHINE_NOT_FOUND,
                        "Máy bán hàng không tồn tại."
                ));

        if (machine.getStatus() != MachineStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.MACHINE_INACTIVE,
                    "Máy bán hàng hiện đang tạm ngừng hoạt động.",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        return machine;
    }
}
