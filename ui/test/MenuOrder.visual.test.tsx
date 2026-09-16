import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import { installFetchMock } from './mockFetch';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// Docker-only full-page snapshot of the Menu Order page, the extension's own administration-menu entry
// (hivemodule.xml, extender `menu-order`). This is the product surface: the two ordering modes with
// their descriptions and the shared configuration buttons.
//
// Only the stored content is mocked. The page loads nothing else on mount - the revisions table is
// fetched when the user opens it, and the defaults when the user reverts - so an unmocked call cannot
// raise an error toast over the capture.

const CONTENT = /\/settings\/menu-order\/names\/Default\/content\?scope=$/;

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

describe.skipIf(!__PIXEL_REFERENCES__)('Menu Order page visual', () => {
  it('loaded (both ordering modes on, configuration buttons)', async () => {
    installFetchMock([
      {
        method: 'GET',
        match: CONTENT,
        json: { foldersAlphabetically: true, entriesInDeclarationOrder: true, bundleTimestamp: '2026-09-14 12:00' },
      },
    ]);
    window.history.replaceState({}, '', '?feature=menu-order&embedded=true');
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('.menu-order-page')).not.toBeNull());
    const app = document.querySelector('.app') as HTMLElement;
    await settleLayout();
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await settleBeforeCapture();
    await expect(page.elementLocator(app)).toMatchScreenshot('menu-order-loaded');
  });
});
