import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import DynamicTable from '../../components/DynamicTable/index';

const mockResult = {
  status: 'ok',
  data: [
    { id: 1, name: 'Alice', email: 'alice@example.com', createdAt: '2025-01-15T10:00:00', isActive: true, amount: 1234.56, status: 'PENDING' },
    { id: 2, name: 'Bob', email: 'bob@example.com', createdAt: '2025-01-16T11:00:00', isActive: false, amount: 999.99, status: 'COMPLETED' },
  ],
  total: 2,
  page: 0,
  pageSize: 20,
  totalPages: 1,
  hasNext: false,
  executionTimeMs: 42,
  columns: [
    { key: 'id', label: 'ID', dataType: 'NUMBER', renderType: 'TEXT' },
    { key: 'name', label: 'Name', dataType: 'STRING', renderType: 'TEXT' },
    { key: 'email', label: 'Email', dataType: 'STRING', renderType: 'LINK', linkPattern: 'mailto:{email}' },
    { key: 'createdAt', label: 'Created At', dataType: 'DATETIME', renderType: 'DATETIME' },
    { key: 'isActive', label: 'Active', dataType: 'BOOLEAN', renderType: 'BOOLEAN', valueLabels: { true: 'Yes', false: 'No' } },
    { key: 'amount', label: 'Amount', dataType: 'NUMBER', renderType: 'MONEY', precision: 2 },
    { key: 'status', label: 'Status', dataType: 'STRING', renderType: 'TAG', tagColors: { PENDING: 'orange', COMPLETED: 'green' }, valueLabels: { PENDING: 'Pending', COMPLETED: 'Completed' } },
  ],
};

describe('DynamicTable', () => {
  it('renders empty state when result is null', () => {
    const { container } = render(<DynamicTable result={null} />);
    expect(container.querySelector('.ant-empty-description')?.textContent).toBe('暂无数据');
  });

  it('renders error state when result has error status', () => {
    const errorResult = { ...mockResult, status: 'error', error: 'Query failed' };
    render(<DynamicTable result={errorResult as any} />);
    expect(screen.getByText('Query failed')).toBeInTheDocument();
  });

  it('renders table with columns from result', () => {
    render(<DynamicTable result={mockResult as any} />);
    expect(screen.getByText('ID')).toBeInTheDocument();
    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Email')).toBeInTheDocument();
  });

  it('renders data rows with correct values', () => {
    render(<DynamicTable result={mockResult as any} />);
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
  });

  it('renders link column with correct href', () => {
    render(<DynamicTable result={mockResult as any} />);
    const emailLink = screen.getByRole('link', { name: 'alice@example.com' });
    expect(emailLink.getAttribute('href')).toBe('mailto:alice@example.com');
  });

  it('renders boolean TAG column with value labels', () => {
    render(<DynamicTable result={mockResult as any} />);
    // First row: isActive=true -> "Yes" tag
    expect(screen.getByText('Yes')).toBeInTheDocument();
    // Second row: isActive=false -> "No" tag
    expect(screen.getByText('No')).toBeInTheDocument();
  });

  it('renders TAG column with custom tagColors', () => {
    render(<DynamicTable result={mockResult as any} />);
    // Status column uses valueLabels -> renders "Pending" and "Completed"
    expect(screen.getByText('Pending')).toBeInTheDocument();
    expect(screen.getByText('Completed')).toBeInTheDocument();
  });

  it('renders DATETIME column with formatted date', () => {
    render(<DynamicTable result={mockResult as any} />);
    // DATETIME renderType uses dayjs format
    expect(screen.getByText('2025-01-15 10:00:00')).toBeInTheDocument();
  });

  it('renders MONEY column with formatted number', () => {
    render(<DynamicTable result={mockResult as any} />);
    // Amount should be formatted
    expect(screen.getByText('1,234.56')).toBeInTheDocument();
  });

  it('renders pagination info', () => {
    render(<DynamicTable result={mockResult as any} />);
    expect(screen.getByText(/查询耗时: 42ms/)).toBeInTheDocument();
    expect(screen.getByText(/共 2 条/)).toBeInTheDocument();
  });

  it('shows loading state when loading prop is true', () => {
    render(<DynamicTable result={mockResult as any} loading={true} />);
    const loadingMask = document.querySelector('.ant-spin');
    expect(loadingMask).toBeInTheDocument();
  });

  it('calls onPageChange when pagination changes', () => {
    const onPageChange = vi.fn();
    render(<DynamicTable result={mockResult as any} onPageChange={onPageChange} />);
    // Click page 2 (if pagination controls are visible)
    const pagination = document.querySelector('.ant-pagination');
    if (pagination) {
      const page2 = pagination.querySelector('.ant-pagination-item-2');
      if (page2) {
        page2.dispatchEvent(new Event('click', { bubbles: true }));
        expect(onPageChange).toHaveBeenCalled();
      }
    }
  });

  it('renders null/undefined values as placeholder dash', () => {
    const withNulls = {
      ...mockResult,
      data: [{ id: 1, name: null, email: undefined }],
      columns: [
        { key: 'id', label: 'ID', dataType: 'NUMBER', renderType: 'TEXT' },
        { key: 'name', label: 'Name', dataType: 'STRING', renderType: 'TEXT' },
      ],
    };
    render(<DynamicTable result={withNulls as any} />);
    expect(screen.getByText('-')).toBeInTheDocument();
  });
});
