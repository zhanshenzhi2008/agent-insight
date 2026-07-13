import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RequestSearchPage from '../../pages/RequestSearch/index';

const { mockNavigate, mockSearch, mockInstances } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
  mockSearch: vi.fn(),
  mockInstances: vi.fn(),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

vi.mock('../../services/api', () => ({
  requestApi: {
    search: mockSearch,
    instances: mockInstances,
  },
}));

const mockData = {
  code: 0,
  message: 'success',
  data: {
    content: [
      {
        requestId: 'req-001',
        topAgentName: 'DataAgent',
        agentId: 100,
        taskStatus: 2,
        success: true,
        totalTaskCount: 3,
        failedTaskCount: 0,
        totalDuration: 60000,
        createTime: '2025-01-15T10:00:00',
        subAgentNames: [],
      },
      {
        requestId: 'req-002',
        topAgentName: 'SubAgent',
        agentId: 101,
        taskStatus: 3,
        success: false,
        totalTaskCount: 5,
        failedTaskCount: 2,
        totalDuration: 120000,
        createTime: '2025-01-15T11:00:00',
        subAgentNames: ['ChildAgent'],
      },
    ],
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 20,
    hasNext: false,
  },
};

describe('RequestSearchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearch.mockResolvedValue({ data: mockData });
    mockInstances.mockResolvedValue({
      data: { code: 0, message: 'success', data: [] },
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the page title', async () => {
    render(<RequestSearchPage />);
    await waitFor(() => {
      expect(screen.getByText('请求检索')).toBeInTheDocument();
    });
  });

  it('renders the search form fields', async () => {
    render(<RequestSearchPage />);
    await waitFor(() => {
      expect(screen.getByPlaceholderText('Request ID')).toBeInTheDocument();
    });
    expect(screen.getByText('Agent 名称')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /搜索/i })).toBeInTheDocument();
  });

  it('renders table with data after loading', async () => {
    render(<RequestSearchPage />);
    await waitFor(() => {
      expect(screen.getByText('req-001')).toBeInTheDocument();
    });
    expect(screen.getByText('DataAgent')).toBeInTheDocument();
  });

  it('renders loading state while fetching', async () => {
    let resolveFetch: (value: { data: typeof mockData }) => void;
    const pendingFetch = new Promise<{ data: typeof mockData }>((resolve) => {
      resolveFetch = resolve;
    });
    mockSearch.mockImplementation(() => pendingFetch);

    render(<RequestSearchPage />);

    await waitFor(() => {
      const table = document.querySelector('.ant-table');
      expect(table).toBeInTheDocument();
    });

    resolveFetch!({ data: mockData });
    await waitFor(() => expect(screen.getByText('req-001')).toBeInTheDocument());
  });

  it('navigates to overview when clicking overview button', async () => {
    const user = userEvent.setup();
    render(<RequestSearchPage />);
    await waitFor(() => {
      expect(screen.getByText('req-001')).toBeInTheDocument();
    });
    const overviewBtn = screen.getAllByText('概览')[0];
    await user.click(overviewBtn);
    expect(mockNavigate).toHaveBeenCalledWith('/overview/req-001');
  });

  it('navigates to trace when clicking trace button', async () => {
    const user = userEvent.setup();
    render(<RequestSearchPage />);
    await waitFor(() => {
      expect(screen.getByText('req-001')).toBeInTheDocument();
    });
    const traceBtn = screen.getAllByText('轨迹')[0];
    await user.click(traceBtn);
    expect(mockNavigate).toHaveBeenCalledWith('/trace/req-001');
  });

  it('shows error message when API returns error', async () => {
    mockSearch.mockResolvedValue({
      data: { code: -1, message: '查询失败', data: null },
    });
    render(<RequestSearchPage />);
    await waitFor(() => {
      expect(screen.getByText('请求检索')).toBeInTheDocument();
    });
  });
});
