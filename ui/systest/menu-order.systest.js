import { expect, test } from '@playwright/test';
import { applyModes, openAdministration, openSection, readMenu, readModes, signIn } from './polarion.mjs';

const SECTION = 'Admin Menu Organizer';
/** The order of META-INF/hivemodule.xml, which is what "pages in declaration order" means. */
const DECLARED_PAGES = ['About', 'Disclaimer', 'REST API', 'Menu Order'];
/** Polarion's own sections come first and keep their fixed order; the extensions follow this one. */
const ANCHOR = 'Configuration Properties';

const sortedCaseInsensitively = (names) => [...names].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
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

  const { menu, pages } = await openSection(page, SECTION);

  expect(menu).toContain(SECTION);
  const sections = extensionSections(menu).filter((entry) => !pages.includes(entry));
  expect(sections).toEqual(sortedCaseInsensitively(sections));
  expect(pages).toEqual(DECLARED_PAGES);
});

test('returns to the order Polarion builds when both modes are off', async ({ page }) => {
  await signIn(page);
  await openAdministration(page);

  await applyModes(page, { foldersAlphabetically: true, entriesInDeclarationOrder: true });
  const sorted = extensionSections(await readMenu(page));

  await applyModes(page, { foldersAlphabetically: false, entriesInDeclarationOrder: false });
  const own = extensionSections(await readMenu(page));

  // The same sections, in the order Polarion built at startup. That order follows identity hash
  // codes, so the only safe assertions are the set and that it is not the sorted one.
  expect([...own].sort()).toEqual([...sorted].sort());
  expect(own).not.toEqual(sortedCaseInsensitively(own));
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
