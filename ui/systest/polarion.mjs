/**
 * What the system suite needs from a running Polarion: a signed-in page, the Administration area,
 * the menu as it is drawn, and the two ordering modes.
 *
 * Polarion's administration menu is drawn by GWT into tables without stable class names, so the
 * entries are found by walking up from an entry that is always there.
 */

export const BASE_URL = process.env.POLARION_URL || 'http://localhost';
const USER = process.env.POLARION_USER || 'admin';
const PASSWORD = process.env.POLARION_PASSWORD || 'admin';

const SETTINGS = '/polarion/admin-menu-organizer/rest/internal/settings/menu-order/names/Default/content?scope=';

/** A Polarion built-in section, used as the anchor of the menu in the page. */
const ANCHOR = 'Configuration Properties';
/** The first entry Polarion draws in the administration menu, used to find the whole menu. */
const FIRST_ENTRY = 'Projects';

export async function signIn(page) {
  await page.goto(`${BASE_URL}/polarion/`, { waitUntil: 'domcontentloaded' });
  if (await page.locator('#j_username').count()) {
    await page.fill('#j_username', USER);
    await page.fill('#j_password', PASSWORD);
    await page.press('#j_password', 'Enter');
    await page.waitForLoadState('domcontentloaded');
  }
  await page.waitForTimeout(4000);
}

/**
 * Opens the Administration area, from wherever the session left the portal.
 *
 * Polarion draws the portal with GWT after the load event, and a session that was last in the
 * administration returns there by itself. Which of the two states the page reaches, and when, is not
 * something a single wait can express: the settings gear is in the page while the administration is
 * still loading, and it is gone once it has loaded. So the state is polled, and whatever is
 * actionable at that moment is acted on.
 */
export async function openAdministration(page) {
  const deadline = Date.now() + 120000;
  while (Date.now() < deadline) {
    if (await page.locator('text=Return to Portal').count()) {
      await page.waitForFunction((anchor) => document.body.innerText.includes(anchor), ANCHOR, { timeout: 60000 });
      await page.waitForTimeout(2500);
      return;
    }
    const settings = page.locator('[title="Show Settings"]').first();
    if (await settings.isVisible().catch(() => false)) {
      await settings.click().catch(() => {});
      const administration = page.getByText('Administration', { exact: true }).first();
      if (await administration.isVisible().catch(() => false)) {
        await administration.click().catch(() => {});
      }
    }
    await page.waitForTimeout(2000);
  }
  throw new Error('The administration did not open within two minutes');
}

/**
 * Every entry of the administration menu, in the order the page draws them.
 *
 * Polarion draws each entry as two rows, one inside the other, and the first image of the inner one
 * is the tree column that says what the entry is: `T.gif` a page, `T+_lite` a collapsed section,
 * `T-_lite` an expanded one. The last entry of a branch carries the same images spelled with `L`,
 * the elbow of the tree. Reading that instead of the labels keeps two entries with the same label
 * apart, which two extensions may well produce.
 *
 * Answers an empty list until the menu is there, so a caller can wait for it.
 */
export async function readMenu(page) {
  return page.evaluate(
    ([anchor, first]) => {
      const leafWith = (text) =>
        Array.from(document.querySelectorAll('*')).find(
          (element) => element.children.length === 0 && (element.textContent || '').trim() === text,
        );
      const start = leafWith(anchor);
      if (!start) return [];
      // The smallest ancestor that holds the whole menu, which is the one that also holds the entry
      // Polarion draws first. Without that entry the menu is not drawn yet: climbing on would reach
      // the document and take the page for the menu.
      let container = start;
      while (container.parentElement && !(container.textContent || '').includes(first)) {
        container = container.parentElement;
      }
      if (!(container.textContent || '').includes(first)) return [];

      const treeImage = (row) => {
        const image = row.querySelector('img');
        return image ? (image.getAttribute('src') || '').split('/').pop().split('?')[0] : '';
      };
      const rows = Array.from(container.querySelectorAll('tr'))
        .map((row) => ({ row, tree: treeImage(row), label: (row.innerText || '').trim().split('\n')[0].trim() }))
        .filter((entry) => entry.label && /^[TL](\.gif|[+-]_lite\.svg)$/.test(entry.tree));
      // One row per entry: the tables are nested, so a row carrying the tree column can hold
      // another one that carries it too. The inner one is the entry.
      return rows
        .filter((entry) => !rows.some((other) => other !== entry && entry.row.contains(other.row)))
        .map((entry) => ({
          label: entry.label,
          section: entry.tree.includes('_lite'),
          open: entry.tree.includes('-_lite'),
        }));
    },
    [ANCHOR, FIRST_ENTRY],
  );
}

/** The labels of the sections the extensions contribute: everything below the Polarion built-ins. */
export function extensionSections(menu) {
  const anchor = menu.findIndex((entry) => entry.label === ANCHOR);
  return menu
    .slice(anchor + 1)
    .filter((entry) => entry.section)
    .map((entry) => entry.label);
}

/**
 * Clicks the expander of one row, found by what it is rather than by its label: a page carrying the
 * same label has no expander and cannot be hit. Marks the image, clicks the marker and takes it off.
 */
async function clickExpander(page, index) {
  const marked = await page.evaluate(
    ([at, anchor, first]) => {
      const leafWith = (text) =>
        Array.from(document.querySelectorAll('*')).find(
          (element) => element.children.length === 0 && (element.textContent || '').trim() === text,
        );
      let container = leafWith(anchor);
      while (container && container.parentElement && !(container.textContent || '').includes(first)) {
        container = container.parentElement;
      }
      if (!container) return false;
      const treeImage = (row) => {
        const image = row.querySelector('img');
        return image ? (image.getAttribute('src') || '').split('/').pop().split('?')[0] : '';
      };
      const qualifying = Array.from(container.querySelectorAll('tr')).filter((row) => {
        const tree = treeImage(row);
        return (row.innerText || '').trim() && /^[TL](\.gif|[+-]_lite\.svg)$/.test(tree);
      });
      // The same one row per entry as readMenu keeps, so the indexes of the two agree.
      const rows = qualifying.filter((row) => !qualifying.some((other) => other !== row && row.contains(other)));
      const image = rows[at]?.querySelector('img');
      if (!image) return false;
      image.setAttribute('data-systest-expander', '');
      return true;
    },
    [index, ANCHOR, FIRST_ENTRY],
  );
  if (!marked) throw new Error(`No row to expand at index ${index}`);
  await page.click('[data-systest-expander]');
  await page.evaluate(() =>
    document
      .querySelectorAll('[data-systest-expander]')
      .forEach((image) => image.removeAttribute('data-systest-expander')),
  );
}

/** Waits until the menu answers a click, because GWT inserts the rows when it is ready. */
async function waitForTheMenu(page, predicate, what, timeout = 30000) {
  const deadline = Date.now() + timeout;
  let menu = await readMenu(page);
  while (!predicate(menu) && Date.now() < deadline) {
    await page.waitForTimeout(500);
    menu = await readMenu(page);
  }
  if (!predicate(menu)) throw new Error(`The menu did not ${what} within ${timeout}ms`);
  return menu;
}

/**
 * Closes every open section, so the menu is measured from a known state. A session returns to
 * wherever it was left, and an open section would otherwise put its pages among the sections.
 */
export async function collapseEverySection(page) {
  // An empty menu is one that is not drawn yet, not one without open sections.
  let menu = await waitForTheMenu(page, (rows) => rows.length > 0, 'appear');
  for (let guard = 0; guard < 50; guard++) {
    const open = menu.findIndex((entry) => entry.section && entry.open);
    if (open < 0) return menu;
    await clickExpander(page, open);
    // The row has to be there and closed: a row that vanished during a redraw is not an answer.
    menu = await waitForTheMenu(page, (rows) => rows[open] !== undefined && !rows[open].open, 'close a section');
  }
  throw new Error('The menu still holds an open section after fifty clicks');
}

/**
 * Expands one section and answers its pages: the entries between it and the next section. The rows
 * are taken from the menu itself rather than from a count, so another section closing at the same
 * time cannot shift them.
 */
export async function openSection(page, section) {
  const menu = await collapseEverySection(page);
  const at = menu.findIndex((entry) => entry.section && entry.label === section);
  if (at < 0) throw new Error(`No section "${section}" in the administration menu`);

  await clickExpander(page, at);
  const opened = await waitForTheMenu(page, (rows) => rows[at] !== undefined && rows[at].open, `open "${section}"`);

  const pages = [];
  for (let i = at + 1; i < opened.length && !opened[i].section; i++) {
    pages.push(opened[i].label);
  }
  return { menu: opened, pages, pagesAt: at + 1 };
}

/**
 * Polarion answers a call without a session with the login page and status 200, not with a redirect
 * and not with 401. A status alone therefore proves nothing about a settings call, and this is what
 * tells the two apart.
 */
async function rejectAPageInsteadOfTheSetting(response, what) {
  if ((response.headers()['content-type'] ?? '').includes('text/html')) {
    throw new Error(`${what} was answered with a page rather than the setting: there is no session`);
  }
  if (!response.ok() && response.status() !== 204) {
    throw new Error(`${what} answered ${response.status()}: ${await response.text()}`);
  }
}

export async function readModes(page) {
  const response = await page.request.get(`${BASE_URL}${SETTINGS}`);
  // Parsing an error body would answer "both modes off", and that answer would later be written
  // back to the live server as if it were what it held before the run.
  await rejectAPageInsteadOfTheSetting(response, 'Reading the modes');
  const content = await response.json();
  // An answer of the wrong shape, such as {} or a field that is not a boolean, would arrive here as
  // "both modes off", and the run would remember that as what the server held.
  for (const field of ['foldersAlphabetically', 'entriesInDeclarationOrder']) {
    if (typeof content?.[field] !== 'boolean') {
      throw new Error(`The setting holds no boolean "${field}": ${JSON.stringify(content)}`);
    }
  }
  return {
    foldersAlphabetically: content.foldersAlphabetically,
    entriesInDeclarationOrder: content.entriesInDeclarationOrder,
  };
}

/**
 * Stores the modes. Nothing is drawn again: use it where only the stored value matters, such as
 * putting back what the run found, which must not depend on a page that opens.
 */
export async function saveModes(page, modes) {
  const response = await page.request.put(`${BASE_URL}${SETTINGS}`, { data: modes });
  await rejectAPageInsteadOfTheSetting(response, 'Saving the modes');

  // Read back, because a write this suite believed and the server dropped is exactly the failure
  // that would leave a live Polarion carrying the modes of a test.
  const stored = await readModes(page);
  if (
    stored.foldersAlphabetically !== modes.foldersAlphabetically ||
    stored.entriesInDeclarationOrder !== modes.entriesInDeclarationOrder
  ) {
    throw new Error(`The modes were not stored: the server holds ${JSON.stringify(stored)}`);
  }
}

/** Saves the modes and reloads, because the menu is drawn once per page load. */
export async function applyModes(page, modes) {
  await saveModes(page, modes);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await openAdministration(page);
}

/** The rectangle of one menu row, addressed the way readMenu addresses it: by position, not by label. */
export async function rowRect(page, index) {
  const rect = await page.evaluate(
    ([at, anchor, first]) => {
      const leafWith = (text) =>
        Array.from(document.querySelectorAll('*')).find(
          (element) => element.children.length === 0 && (element.textContent || '').trim() === text,
        );
      let container = leafWith(anchor);
      while (container && container.parentElement && !(container.textContent || '').includes(first)) {
        container = container.parentElement;
      }
      if (!container) return null;
      const treeImage = (row) => {
        const image = row.querySelector('img');
        return image ? (image.getAttribute('src') || '').split('/').pop().split('?')[0] : '';
      };
      const qualifying = Array.from(container.querySelectorAll('tr')).filter(
        (row) => (row.innerText || '').trim() && /^[TL](\.gif|[+-]_lite\.svg)$/.test(treeImage(row)),
      );
      const rows = qualifying.filter((row) => !qualifying.some((other) => other !== row && row.contains(other)));
      const box = rows[at]?.getBoundingClientRect();
      return box ? { top: box.top, left: box.left, width: box.width, bottom: box.bottom } : null;
    },
    [index, ANCHOR, FIRST_ENTRY],
  );
  if (!rect) throw new Error(`No menu row at index ${index}`);
  return rect;
}
