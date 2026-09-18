import { expect, test } from '@playwright/test';
import {
  applyModes,
  collapseEverySection,
  extensionSections,
  openAdministration,
  openSection,
  readModes,
  rowRect,
  saveModes,
  signIn,
} from './polarion.mjs';

const SECTION = 'Admin Menu Organizer';
/** The order of META-INF/hivemodule.xml, which is what "pages in declaration order" means. */
const DECLARED_PAGES = ['About', 'Disclaimer', 'REST API', 'Menu Order'];
// MenuOrderPlanner sorts with a java.text.Collator on Locale.ROOT at SECONDARY strength: the case
// is ignored and an accent is not. Intl.Collator with sensitivity 'accent' is the same rule.
const collator = new Intl.Collator('en', { sensitivity: 'accent' });
const sortedCaseInsensitively = (names) => [...names].sort(collator.compare);

test.describe.configure({ mode: 'serial' });

// The modes of the server as the run found them. They are one global setting, which the Java suite
// writes too: run one suite at a time against an instance, or one cleanup puts back what the other
// one was using.
let storedModes;

/** browser.newPage() takes no settings from the project, so the ones that matter are passed on. */
const pageOptions = () => {
  const { ignoreHTTPSErrors, viewport } = test.info().project.use;
  return { ignoreHTTPSErrors, viewport };
};

test.beforeAll(async ({ browser }) => {
  const page = await browser.newPage(pageOptions());
  await signIn(page);
  await openAdministration(page);
  storedModes = await readModes(page);
  await page.close();
});

test.afterAll(async ({ browser }) => {
  // Nothing was read, so there is nothing to put back: a setup that failed before this was captured
  // must not have its failure buried under an invalid cleanup request.
  if (!storedModes) return;
  const page = await browser.newPage(pageOptions());
  try {
    // The session is all this needs. Opening the administration could fail for the very reason the
    // run was investigating, and the live server would keep the modes of a test.
    await signIn(page);
    await saveModes(page, storedModes);
  } finally {
    await page.close();
  }
});

test('sorts the sections of the extensions and keeps the pages of one in declaration order', async ({ page }) => {
  await signIn(page);
  await openAdministration(page);
  await applyModes(page, { foldersAlphabetically: true, entriesInDeclarationOrder: true });

  const { menu, pages } = await openSection(page, SECTION);

  // The sections are the rows that carry an expander, so the pages of the one just opened are not
  // among them and cannot disturb the order.
  const sections = extensionSections(menu);
  expect(sections).toContain(SECTION);
  expect(sections).toEqual(sortedCaseInsensitively(sections));
  expect(pages).toEqual(DECLARED_PAGES);
});

test('returns to the order Polarion builds when both modes are off', async ({ page }) => {
  await signIn(page);
  await openAdministration(page);

  // The order Polarion built at startup, which is what switching the modes off has to return to.
  // It follows identity hash codes, so it is read rather than predicted, and it does not change
  // while the server runs.
  await applyModes(page, { foldersAlphabetically: false, entriesInDeclarationOrder: false });
  const baseline = extensionSections(await collapseEverySection(page));

  await applyModes(page, { foldersAlphabetically: true, entriesInDeclarationOrder: true });
  const sorted = extensionSections(await collapseEverySection(page));
  expect(sorted).toEqual(sortedCaseInsensitively(sorted));

  await applyModes(page, { foldersAlphabetically: false, entriesInDeclarationOrder: false });

  expect(extensionSections(await collapseEverySection(page))).toEqual(baseline);
});

test('the section looks the way it is published', async ({ page }) => {
  // The exact value, as vitest.config.ts checks it: an inherited PIXEL_REFERENCES=0 must not
  // turn a pixel comparison on outside the pinned image.
  test.skip(process.env.PIXEL_REFERENCES !== '1', 'The reference is locked to the pinned Playwright image');

  await signIn(page);
  await openAdministration(page);
  await applyModes(page, { foldersAlphabetically: true, entriesInDeclarationOrder: true });

  // From the row of the section to the row of its last page: what the extension promises, and
  // nothing of the other extensions installed on the instance. The rows are taken by position, the
  // same rows readMenu reports, so a page another extension labels the same way cannot put the
  // frame somewhere else.
  const { pages, pagesAt } = await openSection(page, SECTION);
  expect(pages).toEqual(DECLARED_PAGES);
  const section = await rowRect(page, pagesAt - 1);
  const lastPage = await rowRect(page, pagesAt + pages.length - 1);
  const clip = {
    x: 0,
    y: Math.floor(section.top),
    // The width of the navigation column, so nothing of the content area is in the frame.
    width: Math.ceil(section.left + section.width),
    height: Math.ceil(lastPage.bottom - section.top),
  };

  await expect(page).toHaveScreenshot('menu-section.png', { clip, animations: 'disabled' });
});
