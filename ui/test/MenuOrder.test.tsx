import { Toaster } from '@sbb-polarion/react-sbb-polarion';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import MenuOrder from '../src/pages/MenuOrder';
import { type Route, installFetchMock, jsonResponse } from './mockFetch';

const CONTENT = /\/settings\/menu-order\/names\/Default\/content\?scope=$/;
const REVISION_CONTENT = /\/settings\/menu-order\/names\/Default\/content\?scope=&revision=42$/;
const DEFAULTS = /\/settings\/menu-order\/default-content$/;
const REVISIONS = /\/settings\/menu-order\/names\/Default\/revisions/;

const stored = (foldersAlphabetically: boolean, entriesInDeclarationOrder: boolean): Route => ({
  method: 'GET',
  match: CONTENT,
  json: { foldersAlphabetically, entriesInDeclarationOrder, bundleTimestamp: '2026-09-14 12:00' },
});

const checkbox = (name: string): HTMLInputElement =>
  document.querySelector<HTMLInputElement>(`.menu-order-page input[name="${name}"]`)!;

const button = (label: string): HTMLButtonElement => {
  const found = Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn')).find(
    (b) => (b.textContent ?? '').trim() === label,
  );
  if (!found) throw new Error(`button "${label}" not found`);
  return found;
};

async function answerDialog(label: 'OK' | 'Cancel') {
  await vi.waitFor(() => expect(document.querySelector('.rsp-modal')).not.toBeNull());
  const target = Array.from(document.querySelectorAll<HTMLButtonElement>('.rsp-modal-footer .sbb-btn')).find(
    (b) => (b.textContent ?? '').trim() === label,
  );
  if (!target) throw new Error(`dialog button "${label}" not found`);
  target.click();
}

async function mount(routes: Route[]) {
  const fetchMock = installFetchMock(routes);
  render(
    <>
      <Toaster />
      <MenuOrder />
    </>,
  );
  await vi.waitFor(() => expect(document.querySelector('.menu-order-page')).not.toBeNull());
  return fetchMock;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('Menu Order page', () => {
  it('shows the stored modes', async () => {
    await mount([stored(true, false)]);

    expect(checkbox('foldersAlphabetically').checked).toBe(true);
    expect(checkbox('entriesInDeclarationOrder').checked).toBe(false);
  });

  it('saves the modes as they are ticked', async () => {
    const fetchMock = await mount([
      stored(true, false),
      { method: 'PUT', match: CONTENT, status: 204, respond: () => new Response(null, { status: 204 }) },
    ]);

    checkbox('entriesInDeclarationOrder').click();
    await vi.waitFor(() => expect(checkbox('entriesInDeclarationOrder').checked).toBe(true));
    button('Save').click();

    await vi.waitFor(() => expect(document.body.textContent).toContain('Data successfully saved'));
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')!;
    expect(JSON.parse(String(put[1]!.body))).toEqual({ foldersAlphabetically: true, entriesInDeclarationOrder: true });
  });

  it('reports a failed save with the message the backend sent', async () => {
    await mount([
      stored(false, false),
      { method: 'PUT', match: CONTENT, respond: () => jsonResponse({ errorMessage: 'global scope only' }, 400) },
    ]);

    button('Save').click();

    await vi.waitFor(() => expect(document.body.textContent).toContain('global scope only'));
  });

  it('reverts unsaved changes on Cancel once confirmed, and keeps them otherwise', async () => {
    await mount([stored(false, false)]);

    checkbox('foldersAlphabetically').click();
    await vi.waitFor(() => expect(checkbox('foldersAlphabetically').checked).toBe(true));

    button('Cancel').click();
    await answerDialog('Cancel');
    await vi.waitFor(() => expect(document.querySelector('.rsp-modal')).toBeNull());
    expect(checkbox('foldersAlphabetically').checked).toBe(true);

    button('Cancel').click();
    await answerDialog('OK');
    await vi.waitFor(() => expect(checkbox('foldersAlphabetically').checked).toBe(false));
  });

  it('loads the default modes once confirmed', async () => {
    await mount([
      stored(true, true),
      { method: 'GET', match: DEFAULTS, json: { foldersAlphabetically: false, entriesInDeclarationOrder: false } },
    ]);

    button('Default').click();
    await answerDialog('OK');

    await vi.waitFor(() => expect(checkbox('foldersAlphabetically').checked).toBe(false));
    expect(checkbox('entriesInDeclarationOrder').checked).toBe(false);
    await vi.waitFor(() => expect(document.body.textContent).toContain('Reverted to the default values'));
  });

  it('loads a revision into the form', async () => {
    await mount([
      {
        method: 'GET',
        match: REVISION_CONTENT,
        json: { foldersAlphabetically: false, entriesInDeclarationOrder: true },
      },
      stored(true, false),
      { method: 'GET', match: REVISIONS, json: [{ name: '42', date: '2026-09-14 12:00', author: 'admin' }] },
    ]);

    button('Revisions').click();
    await vi.waitFor(() => expect(document.querySelector('.revert-to-revision-button')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.revert-to-revision-button')!.click();

    await vi.waitFor(() => expect(checkbox('entriesInDeclarationOrder').checked).toBe(true));
    expect(checkbox('foldersAlphabetically').checked).toBe(false);
    await vi.waitFor(() => expect(document.body.textContent).toContain('Reverted to revision 42'));
  });

  it('says so when the modes cannot be loaded', async () => {
    await mount([{ method: 'GET', match: CONTENT, respond: () => jsonResponse({}, 500) }]);

    expect(document.querySelector('.alert-error')!.textContent).toContain('Error occurred loading the data');
    expect(checkbox('foldersAlphabetically').checked).toBe(false);
  });

  it('reports defaults that cannot be loaded in a message and keeps the form', async () => {
    await mount([stored(true, true), { method: 'GET', match: DEFAULTS, respond: () => jsonResponse({}, 500) }]);

    button('Default').click();
    await answerDialog('OK');

    await vi.waitFor(() => expect(document.body.textContent).toContain('Request failed with status 500'));
    expect(document.querySelector('.alert-error')).toBeNull();
    expect(checkbox('foldersAlphabetically').checked).toBe(true);
  });

  it('reports stored modes that cannot be reloaded on Cancel in a message', async () => {
    let reads = 0;
    await mount([
      {
        method: 'GET',
        match: CONTENT,
        respond: () => {
          reads += 1;
          return reads === 1 ? jsonResponse({ foldersAlphabetically: true }) : jsonResponse({}, 500);
        },
      },
    ]);

    button('Cancel').click();
    await answerDialog('OK');

    await vi.waitFor(() => expect(document.body.textContent).toContain('Request failed with status 500'));
    expect(document.querySelector('.alert-error')).toBeNull();
  });

  it('clears the load error once Cancel reloads the stored modes', async () => {
    let reads = 0;
    await mount([
      {
        method: 'GET',
        match: CONTENT,
        respond: () => {
          reads += 1;
          return reads === 1 ? jsonResponse({}, 500) : jsonResponse({ foldersAlphabetically: true });
        },
      },
    ]);
    expect(document.querySelector('.alert-error')).not.toBeNull();

    button('Cancel').click();
    await answerDialog('OK');

    await vi.waitFor(() => expect(checkbox('foldersAlphabetically').checked).toBe(true));
    expect(document.querySelector('.alert-error')).toBeNull();
  });

  it('reports a revision that cannot be loaded in a message', async () => {
    await mount([
      { method: 'GET', match: REVISION_CONTENT, respond: () => jsonResponse({}, 500) },
      stored(true, false),
      { method: 'GET', match: REVISIONS, json: [{ name: '42', date: '2026-09-14 12:00', author: 'admin' }] },
    ]);

    button('Revisions').click();
    await vi.waitFor(() => expect(document.querySelector('.revert-to-revision-button')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.revert-to-revision-button')!.click();

    await vi.waitFor(() => expect(document.body.textContent).toContain('Request failed with status 500'));
    expect(document.querySelector('.alert-error')).toBeNull();
  });

  it('dismisses earlier messages when a revision is loaded', async () => {
    await mount([
      {
        method: 'GET',
        match: REVISION_CONTENT,
        json: { foldersAlphabetically: false, entriesInDeclarationOrder: true },
      },
      stored(true, false),
      { method: 'PUT', match: CONTENT, respond: () => new Response(null, { status: 204 }) },
      { method: 'GET', match: REVISIONS, json: [{ name: '42', date: '2026-09-14 12:00', author: 'admin' }] },
    ]);

    button('Save').click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('Data successfully saved'));
    button('Revisions').click();
    await vi.waitFor(() => expect(document.querySelector('.revert-to-revision-button')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.revert-to-revision-button')!.click();

    await vi.waitFor(() => expect(document.body.textContent).toContain('Reverted to revision 42'));
    await vi.waitFor(() => expect(document.body.textContent).not.toContain('Data successfully saved'));
  });

  it('keeps the form when loading the defaults is not confirmed', async () => {
    await mount([stored(true, true)]);

    button('Default').click();
    await answerDialog('Cancel');

    await vi.waitFor(() => expect(document.querySelector('.rsp-modal')).toBeNull());
    expect(checkbox('foldersAlphabetically').checked).toBe(true);
    expect(checkbox('entriesInDeclarationOrder').checked).toBe(true);
  });

  it('reports a failed save that carries no message with a generic one', async () => {
    await mount([
      stored(false, false),
      { method: 'PUT', match: CONTENT, respond: () => jsonResponse({ errorMessage: '' }, 400) },
    ]);

    button('Save').click();

    await vi.waitFor(() => expect(document.body.textContent).toContain('Error occurred during saving the data.'));
  });

  it.each(['answers', 'fails'] as const)('ignores a load that %s after the page was closed', async (outcome) => {
    let settle: () => void = () => {};
    const pending = new Promise<Response>((resolve, reject) => {
      settle = () =>
        outcome === 'answers' ? resolve(jsonResponse({ foldersAlphabetically: true })) : reject(new Error('offline'));
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(() => pending),
    );
    const screen = await render(<MenuOrder />);
    await vi.waitFor(() => expect(screen.container.textContent).toContain('Loading...'));

    screen.unmount();
    settle();
    await pending.catch(() => undefined);

    expect(screen.container.textContent).toBe('');
  });
});
