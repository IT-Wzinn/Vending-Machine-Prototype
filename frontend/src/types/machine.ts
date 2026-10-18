export type MachineStatus = 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE';

export interface MachineResponse {
  machineId: string;
  name: string;
  status: MachineStatus;
  message: string | null;
}
