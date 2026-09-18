import { expect, test } from '@playwright/test';
import { applyModes, openAdministration, openSection, readMenu, readModes, saveModes, signIn } from './polarion.mjs';

const SECTION = 'Admin Menu Organizer';
/** The order of META-INF/hivemodule.xml, which is what "pages in declaration order" means. */
const DECLARED_PAGES = ['About', 'Disclaimer', 'REST API', 'Menu Order'];
/** Polarion's own sections come first and keep their fixed order; the extensions follow this one. */
const ANCHOR = 'Configuration Properties';

// MenuOrderPlanner sorts with a java.text.Collator on Locale.ROOT at SECONDARY strength: the case
// is ignored and an accent is not. Intl.Collator with sensitivity 'accent' is the same rule.
const collator = new Intl.Collator('en', { sensitivity: 'accent' });
const sortedCaseInsensitively = (names) => [...names].sort(collator.compare);
const extensionSections = (menu) => menu.slice(menu.indexOf(ANCHOR) + 1);

test.describe.configure({ mode: 'serial' });

let storedModes;

test.beforeAll(async ({ browser }) => {
  const page = await browser.newPage();
  await signIn(page);
  await openAdministration(page);
  storedModes = await readModes(page);
  await page.close();
});

test.afterAll(async ({ browser }) => {
  // Nothing was read, so there is nothing to put back: a setup that failed before this was captured
  // must not have its failure buried under an invalid cleanup request.
  if (!storedModes) return;
  const page = await browser.newPage();
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

  const { menu, pages, pagesAt } = await openSection(page, SECTION);

  expect(menu).toContain(SECTION);
  // The pages are removed by position, not by label: a page and a section may share one.
  const withoutPages = menu.filter((_, index) => index < pagesAt || index >= pagesAt + pages.length);
  const sections = extensionSections(withoutPages);
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
  const baseline = extensionSections(await readMenu(page));

  await applyModes(page, { foldersAlphabetically: true, entriesInDeclarationOrder: true });
  const sorted = extensionSections(await readMenu(page));
  expect(sorted).toEqual(sortedCaseInsensitively(sorted));

  await applyModes(page, { foldersAlphabetically: false, entriesInDeclarationOrder: false });

  expect(extensionSections(await readMenu(page))).toEqual(baseline);
});

test('the section looks the way it is published', async ({ page }) => {
  // The exact value, as vitest.config.ts checks it: an inherited PIXEL_REFERENCES=0 must not
  // turn a pixel comparison on outside the pinned image.
  test.skip(process.env.PIXEL_REFERENCES !== '1', 'The reference is locked to the pinned Playwright image');

  await signIn(page);
  await openAdministration(page);
  await applyModes(page, { foldersAlphabetically: true, entriesInDeclarationOrder: true });
  await openSection(page, SECTION);

  // From the row of the section to the row of its last page: what the extension promises, and
  // nothing of the other extensions installed on the instance.
  const clip = await page.evaluate(
    ([section, lastPage]) => {
      // The leaves in the order the document holds them, so the page can be looked for below its
      // section: another extension may well contribute a page with the same label, and searching
      // the whole document could land on its row.
      const leaves = Array.from(document.querySelectorAll('*')).filter(
        (element) => element.children.length === 0 && (element.textContent || '').trim(),
      );
      const at = (text, from) =>
        leaves.findIndex((leaf, index) => index >= from && (leaf.textContent || '').trim() === text);
      const sectionLeaf = at(section, 0);
      const lastPageLeaf = sectionLeaf < 0 ? -1 : at(lastPage, sectionLeaf + 1);
      if (sectionLeaf < 0 || lastPageLeaf < 0) return null;

      // The innermost row of each: the menu is drawn as nested tables, and an outer row holds all
      // of it.
      const first = leaves[sectionLeaf].closest('tr')?.getBoundingClientRect();
      const last = leaves[lastPageLeaf].closest('tr')?.getBoundingClientRect();
      if (!first || !last) return null;
      // The width of the navigation column, so nothing of the content area is in the frame.
      const column = first.left + first.width;
      return { x: 0, y: Math.floor(first.top), width: Math.ceil(column), height: Math.ceil(last.bottom - first.top) };
    },
    [SECTION, DECLARED_PAGES[DECLARED_PAGES.length - 1]],
  );

  expect(clip).not.toBeNull();

  await expect(page).toHaveScreenshot('menu-section.png', { clip, animations: 'disabled' });
});
