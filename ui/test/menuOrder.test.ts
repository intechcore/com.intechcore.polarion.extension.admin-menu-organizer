import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMenuOrderService } from '../src/services/menuOrder';
import { jsonResponse } from './mockFetch';

type Call = { method: string; url: string; body?: BodyInit; contentType?: string };

function serviceAnswering(response: Response) {
  const calls: Call[] = [];
  const service = createMenuOrderService((request) => {
    calls.push(request);
    return Promise.resolve(response);
  });
  return { service, calls };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('menu order service', () => {
  it('reads the modes of the global Default setting and ignores the rest of it', async () => {
    const { service, calls } = serviceAnswering(
      jsonResponse({
        foldersAlphabetically: true,
        entriesInDeclarationOrder: 'yes',
        bundleTimestamp: '2026-09-14 12:00',
      }),
    );

    expect(await service.loadModes()).toEqual({ foldersAlphabetically: true, entriesInDeclarationOrder: false });
    expect(calls[0]).toMatchObject({ method: 'GET', url: '/settings/menu-order/names/Default/content?scope=' });
  });

  it('reads a revision of the setting', async () => {
    const { service, calls } = serviceAnswering(jsonResponse({}));

    await service.loadModes('42');

    expect(calls[0].url).toBe('/settings/menu-order/names/Default/content?scope=&revision=42');
  });

  it('saves only the two modes', async () => {
    const { service, calls } = serviceAnswering(new Response(null, { status: 204 }));

    await service.saveModes({ foldersAlphabetically: false, entriesInDeclarationOrder: true });

    expect(calls[0]).toMatchObject({
      method: 'PUT',
      url: '/settings/menu-order/names/Default/content?scope=',
      contentType: 'application/json',
    });
    expect(JSON.parse(String(calls[0].body))).toEqual({
      foldersAlphabetically: false,
      entriesInDeclarationOrder: true,
    });
  });

  it('reads the default modes and the revisions', async () => {
    const defaults = serviceAnswering(jsonResponse({ foldersAlphabetically: false, entriesInDeclarationOrder: false }));
    expect(await defaults.service.loadDefaultModes()).toEqual({
      foldersAlphabetically: false,
      entriesInDeclarationOrder: false,
    });
    expect(defaults.calls[0].url).toBe('/settings/menu-order/default-content');

    const revisions = serviceAnswering(jsonResponse([{ name: '42' }]));
    expect(await revisions.service.loadRevisions('Default', '')).toEqual([{ name: '42' }]);
    expect(revisions.calls[0].url).toBe('/settings/menu-order/names/Default/revisions?scope=');
  });

  it('fails with the message the backend sent', async () => {
    const { service } = serviceAnswering(jsonResponse({ errorMessage: 'global scope only' }, 400));

    await expect(service.saveModes({ foldersAlphabetically: true, entriesInDeclarationOrder: true })).rejects.toThrow(
      'global scope only',
    );
  });

  it('falls back to the message field, then to the status', async () => {
    await expect(serviceAnswering(jsonResponse({ message: 'not found' }, 404)).service.loadModes()).rejects.toThrow(
      'not found',
    );
    await expect(serviceAnswering(jsonResponse({}, 500)).service.loadModes()).rejects.toThrow(
      'Request failed with status 500',
    );
    await expect(
      serviceAnswering(new Response('<html>down</html>', { status: 502 })).service.loadDefaultModes(),
    ).rejects.toThrow('Request failed with status 502');
  });
});
