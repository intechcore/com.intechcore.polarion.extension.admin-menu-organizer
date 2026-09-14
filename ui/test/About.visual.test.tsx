import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import { installFetchMock } from './mockFetch';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// Docker-only full-page snapshot of the About page (shared RSP About component fed this app's
// endpoints, mocked). Covers the extension-info / properties / status tables and the README article.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

describe.skipIf(!__PIXEL_REFERENCES__)('About page visual', () => {
  it('loaded (info + properties + status tables, README article)', async () => {
    installFetchMock([
      {
        method: 'GET',
        match: /\/version$/,
        json: {
          bundleName: 'Admin Menu Organizer',
          bundleVendor: 'Intechcore GmbH',
          supportEmail: 'polarion@intechcore.com',
          automaticModuleName: 'com.intechcore.polarion.extension.admin_menu_organizer',
          bundleVersion: '1.0.0',
          bundleBuildTimestamp: '2026-07-01 10:00',
        },
      },
      {
        method: 'GET',
        match: /\/configuration-properties$/,
        json: {
          properties: [
            {
              key: 'com.intechcore.polarion.extension.admin-menu-organizer.some.property',
              value: 'value',
              defaultValue: 'value',
              description: 'An example configuration property',
            },
          ],
          obsoleteProperties: [],
        },
      },
      {
        method: 'GET',
        match: /\/configuration-status/,
        json: [{ name: 'Admin Menu Organizer', status: 'OK', details: 'ready' }],
      },
      {
        method: 'GET',
        match: /\/readme$/,
        respond: () =>
          new Response(
            '<h1>Admin Menu Organizer Extension for Polarion ALM</h1><p>What this extension does, from README.md.</p>',
            { status: 200 },
          ),
      },
    ]);
    window.history.replaceState({}, '', '?feature=about&embedded=true');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('article.markdown-body')).not.toBeNull());
    const app = document.querySelector('.app') as HTMLElement;
    await settleLayout();
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await settleBeforeCapture();
    await expect(page.elementLocator(app)).toMatchScreenshot('about-loaded');
  });
});
