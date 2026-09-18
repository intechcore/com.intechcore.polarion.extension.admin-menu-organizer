import { expect, test } from '@playwright/test';
import { applyModes, openAdministration, openSection, readMenu, readModes, signIn } from './polarion.mjs';

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
  const page = await browser.newPage();
  await signIn(page);
  await openAdministration(page);
  await applyModes(page, storedModes);
  await page.close();
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
  test.skip(!process.env.PIXEL_REFERENCES, 'The reference is locked to the pinned Playwright image');

  await signIn(page);
  await openAdministration(page);
  await applyModes(page, { foldersAlphabetically: true, entriesInDeclarationOrder: true });
  await openSection(page, SECTION);

  // From the row of the section to the row of its last page: what the extension promises, and
  // nothing of the other extensions installed on the instance.
  const clip = await page.evaluate(
    ([section, lastPage]) => {
      const rowOf = (text) => {
        const leaf = Array.from(document.querySelectorAll('*')).find(
          (element) => element.children.length === 0 && (element.textContent || '').trim() === text,
        );
        return leaf?.closest('tr')?.getBoundingClientRect();
      };
      const first = rowOf(section);
      const last = rowOf(lastPage);
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
