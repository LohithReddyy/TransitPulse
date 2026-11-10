import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import api, { client } from '../services/api';

describe('api client', () => {
  let getSpy;

  beforeEach(() => {
    getSpy = vi.spyOn(client, 'get');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('unwraps the data field from the standard envelope', async () => {
    getSpy.mockResolvedValue({
      data: { success: true, message: 'OK', data: [{ routeId: 'R01' }] },
    });
    await expect(api.getRoutes()).resolves.toEqual([{ routeId: 'R01' }]);
  });

  it("surfaces the server's message so the user sees what to do", async () => {
    getSpy.mockRejectedValue({
      response: {
        status: 503,
        data: {
          message: 'Run: cd data-engine && python -m transitpulse_engine.pipeline',
        },
      },
    });
    await expect(api.getPropagation()).rejects.toThrow(
      /python -m transitpulse_engine\.pipeline/
    );
  });

  it('includes field errors in the thrown message', async () => {
    const postSpy = vi.spyOn(client, 'post').mockRejectedValue({
      response: {
        status: 400,
        data: {
          message: 'One or more fields are invalid',
          fieldErrors: { incidentType: 'must be one of ...' },
        },
      },
    });
    await expect(api.createIncident({})).rejects.toThrow(/incidentType/);
    postSpy.mockRestore();
  });

  it('explains how to start the backend when it is unreachable', async () => {
    getSpy.mockRejectedValue({ message: 'Network Error' });
    await expect(api.getDashboard()).rejects.toThrow(/Network Error/);
  });

  it('builds the expected URLs', async () => {
    getSpy.mockResolvedValue({ data: { data: {} } });
    await api.getTripInvestigation('T0001');
    expect(getSpy).toHaveBeenCalledWith('/api/trips/T0001/investigation', {
      params: undefined,
    });
  });
});
