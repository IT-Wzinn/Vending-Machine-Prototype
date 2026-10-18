import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MachineBanner } from '../components/MachineBanner';
import type { MachineResponse } from '../types/machine';

describe('MachineBanner', () => {
  it('renders active machine information correctly', () => {
    const activeMachine: MachineResponse = {
      machineId: 'VM001',
      name: 'Máy bán hàng tầng 1',
      status: 'ACTIVE',
      message: null,
    };

    render(<MachineBanner machine={activeMachine} />);

    expect(screen.getByText('VM001')).toBeInTheDocument();
    expect(screen.getByText('Máy bán hàng tầng 1')).toBeInTheDocument();
    expect(screen.getByText('Hoạt động')).toBeInTheDocument();
    expect(screen.queryByText(/Máy hiện đang tạm ngừng hoạt động/i)).not.toBeInTheDocument();
  });

  it('renders warning alert when machine is inactive', () => {
    const inactiveMachine: MachineResponse = {
      machineId: 'VM002',
      name: 'Máy bán hàng tầng 2',
      status: 'INACTIVE',
      message: 'Máy đang tạm ngưng để kiểm tra kỹ thuật',
    };

    render(<MachineBanner machine={inactiveMachine} />);

    expect(screen.getByText('VM002')).toBeInTheDocument();
    expect(screen.getByText('Tạm ngừng')).toBeInTheDocument();
    expect(screen.getByText(/Máy hiện đang tạm ngừng hoạt động!/i)).toBeInTheDocument();
    expect(screen.getByText('Máy đang tạm ngưng để kiểm tra kỹ thuật')).toBeInTheDocument();
  });
});
