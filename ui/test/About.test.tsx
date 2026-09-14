import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import AboutPage, { DISCLAIMER_PAGE } from '../src/pages/About';
import { type Route, installFetchMock } from './mockFetch';

const withReadme = (readme: string): Route[] => [
  { method: 'GET', match: /\/version$/, json: { bundleName: 'Admin Menu Organizer', bundleVersion: '1.0.0' } },
  { method: 'GET', match: /\/configuration-properties$/, json: { properties: [], obsoleteProperties: [] } },
  { method: 'GET', match: /\/configuration-status/, json: [] },
  { method: 'GET', match: /\/readme$/, respond: () => new Response(readme, { status: 200 }) },
];

const readmeLink = () => document.querySelector<HTMLAnchorElement>('article.markdown-body a');

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('About page', () => {
  it('opens the Disclaimer page of the Administration menu from the README link', async () => {
    installFetchMock(
      withReadme(
        '<p>Read the <a href="https://github.com/intechcore/extension/tree/main/DISCLAIMER.md" target="_blank">Disclaimer</a>.</p>',
      ),
    );
    const navigate = vi.fn();
    render(<AboutPage navigate={navigate} />);
    await vi.waitFor(() => expect(readmeLink()).not.toBeNull());

    readmeLink()!.click();

    expect(navigate).toHaveBeenCalledWith(DISCLAIMER_PAGE);
  });

  it('leaves the other links of the README alone', async () => {
    installFetchMock(withReadme('<p><a href="#rest-api">REST API</a></p>'));
    const navigate = vi.fn();
    render(<AboutPage navigate={navigate} />);
    await vi.waitFor(() => expect(readmeLink()).not.toBeNull());

    readmeLink()!.click();

    expect(navigate).not.toHaveBeenCalled();
  });
});
