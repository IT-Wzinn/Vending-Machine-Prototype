import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AdminLoginPage } from '../pages/admin/AdminLoginPage';
import { AdminAuthProvider } from '../context/AdminAuthContext';
import * as adminApi from '../services/adminApi';

vi.mock('../services/adminApi', async (importOriginal) => {
  const actual = await importOriginal<typeof adminApi>();
  return {
    ...actual,
    loginAdmin: vi.fn(),
  };
});

describe('AdminLoginPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  const renderLoginPage = (onSuccess = vi.fn()) => {
    return render(
      <AdminAuthProvider>
        <AdminLoginPage onLoginSuccess={onSuccess} />
      </AdminAuthProvider>
    );
  };

  it('renders login form with username, password, and submit button', () => {
    renderLoginPage();

    expect(screen.getByText('Đăng nhập Quản trị')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Nhập username quản trị...')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('••••••••••••')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Đăng nhập Hệ thống/i })
    ).toBeInTheDocument();
  });

  it('toggles password visibility when eye icon button is clicked', () => {
    renderLoginPage();

    const passwordInput = screen.getByPlaceholderText('••••••••••••') as HTMLInputElement;
    expect(passwordInput.type).toBe('password');

    const toggleButton = screen.getByLabelText('Hiện mật khẩu');
    fireEvent.click(toggleButton);

    expect(passwordInput.type).toBe('text');

    const hideButton = screen.getByLabelText('Ẩn mật khẩu');
    fireEvent.click(hideButton);

    expect(passwordInput.type).toBe('password');
  });

  it('shows client-side validation error when username is empty', async () => {
    renderLoginPage();

    const submitButton = screen.getByRole('button', { name: /Đăng nhập Hệ thống/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Vui lòng nhập tên đăng nhập quản trị.')).toBeInTheDocument();
    });
    expect(adminApi.loginAdmin).not.toHaveBeenCalled();
  });

  it('shows client-side validation error when password is empty', async () => {
    renderLoginPage();

    const usernameInput = screen.getByPlaceholderText('Nhập username quản trị...');
    fireEvent.change(usernameInput, { target: { value: 'admin' } });

    const submitButton = screen.getByRole('button', { name: /Đăng nhập Hệ thống/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Vui lòng nhập mật khẩu.')).toBeInTheDocument();
    });
    expect(adminApi.loginAdmin).not.toHaveBeenCalled();
  });

  it('handles successful login, invokes callback, and never writes password to storage', async () => {
    const onSuccessMock = vi.fn();
    vi.mocked(adminApi.loginAdmin).mockResolvedValue({
      username: 'superadmin',
      token: 'jwt-admin-token',
      expiresInSeconds: 3600,
      message: 'Login successful',
    });

    renderLoginPage(onSuccessMock);

    const usernameInput = screen.getByPlaceholderText('Nhập username quản trị...');
    const passwordInput = screen.getByPlaceholderText('••••••••••••');
    const submitButton = screen.getByRole('button', { name: /Đăng nhập Hệ thống/i });

    fireEvent.change(usernameInput, { target: { value: 'superadmin ' } }); // with trailing space
    fireEvent.change(passwordInput, { target: { value: 'CorrectPassword123' } });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(adminApi.loginAdmin).toHaveBeenCalledWith({
        username: 'superadmin',
        password: 'CorrectPassword123',
      });
      expect(onSuccessMock).toHaveBeenCalledTimes(1);
    });

    // Check storage is clean
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('displays error banner when login fails with invalid credentials', async () => {
    vi.mocked(adminApi.loginAdmin).mockRejectedValue(
      new Error('Tên đăng nhập hoặc mật khẩu không chính xác.')
    );

    renderLoginPage();

    const usernameInput = screen.getByPlaceholderText('Nhập username quản trị...');
    const passwordInput = screen.getByPlaceholderText('••••••••••••');
    const submitButton = screen.getByRole('button', { name: /Đăng nhập Hệ thống/i });

    fireEvent.change(usernameInput, { target: { value: 'admin' } });
    fireEvent.change(passwordInput, { target: { value: 'WrongPass' } });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(
        screen.getByText('Tên đăng nhập hoặc mật khẩu không chính xác.')
      ).toBeInTheDocument();
    });
  });

  it('shows loading indicator and disables submit button during in-flight request', async () => {
    let resolveLogin: (val: any) => void = () => {};
    vi.mocked(adminApi.loginAdmin).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveLogin = resolve;
        })
    );

    renderLoginPage();

    const usernameInput = screen.getByPlaceholderText('Nhập username quản trị...');
    const passwordInput = screen.getByPlaceholderText('••••••••••••');
    const submitButton = screen.getByRole('button', { name: /Đăng nhập Hệ thống/i });

    fireEvent.change(usernameInput, { target: { value: 'admin' } });
    fireEvent.change(passwordInput, { target: { value: 'Password123' } });
    fireEvent.click(submitButton);

    expect(screen.getByText('Đang xác thực...')).toBeInTheDocument();
    expect(submitButton).toBeDisabled();

    // Resolve login
    resolveLogin({
      username: 'admin',
      token: 'token',
      expiresInSeconds: 3600,
      message: 'OK',
    });

    await waitFor(() => {
      expect(screen.queryByText('Đang xác thực...')).not.toBeInTheDocument();
    });
  });
});
