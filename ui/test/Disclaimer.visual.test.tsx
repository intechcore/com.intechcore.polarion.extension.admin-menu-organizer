import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import { installFetchMock } from './mockFetch';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// Docker-only full-page snapshot of the Disclaimer page, one of this extension's administration-menu
// entries (hivemodule.xml, extender `disclaimer`). It renders the build-generated DISCLAIMER article,
// so the article markup is mocked here rather than read from the jar.
//
// ARTICLE is what the build makes of DISCLAIMER.md (webapp/.../html/disclaimer.html). Keeping the real
// shape - the lead paragraph, the section bands, the lists - is the point: this reference exists to
// catch a styling change in the help article, and a one-paragraph stand-in would show almost none of
// it. Edit it when DISCLAIMER.md gains or loses a kind of element, not on every wording change.

const ARTICLE = [
  '<p><strong>Use this extension in production at your own risk.</strong> We made it to show a problem in ',
  'Polarion 2606 and ways to solve it. Test it on a non-production server first.</p>',
  '<h2 id="the-problem">The problem</h2>',
  '<p>Polarion 2606 shows the Administration menu sections of extensions in a different order after each ',
  'restart. The pages inside each section change their order too.</p>',
  '<p>Up to Polarion 2512, the menu followed the order in which each extension declares its pages. Polarion ',
  '2606 collects the menu entries in an unordered set, and the order of that set changes each time the ',
  'server starts.</p>',
  '<h2 id="what-this-extension-does">What this extension does</h2>',
  '<p>The extension changes the order of the menu entries that Polarion already loaded. It does not add ',
  'entries and does not remove entries.</p>',
  '<p>By default, the extension changes nothing. An administrator can turn on two options:</p>',
  '<ul><li>Sort the sections of extensions alphabetically.</li>',
  '<li>Show the pages of each section in the order the extension declares them.</li></ul>',
  '<p>When the administrator turns both options off, the menu returns to the order that Polarion built at ',
  'startup.</p>',
  '<h2 id="why-it-is-risky">Why it is risky</h2>',
  '<ul><li>A future Polarion release can change how it builds the Administration menu. The extension then ',
  'writes a warning to the log and leaves the menu as Polarion built it.</li>',
  '<li>The extension changes the menu while Polarion is running. For a very short time, a user can see one ',
  'menu page twice. The next page load shows the correct menu.</li></ul>',
].join('');

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

describe.skipIf(!__PIXEL_REFERENCES__)('Disclaimer page visual', () => {
  it('loaded (build-generated article)', async () => {
    installFetchMock([
      {
        method: 'GET',
        match: /\/disclaimer$/,
        respond: () => new Response(ARTICLE, { status: 200, headers: { 'Content-Type': 'text/html' } }),
      },
    ]);
    window.history.replaceState({}, '', '?feature=disclaimer&embedded=true');
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('article.markdown-body')).not.toBeNull());
    const app = document.querySelector('.app') as HTMLElement;
    await settleLayout();
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await settleBeforeCapture();
    await expect(page.elementLocator(app)).toMatchScreenshot('disclaimer-loaded');
  });
});
