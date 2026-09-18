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

/** Every entry of the administration menu, in the order the page draws them. */
export async function readMenu(page) {
  return page.evaluate((anchor) => {
    const leafWith = (text) =>
      Array.from(document.querySelectorAll('*')).find(
        (element) => element.children.length === 0 && (element.textContent || '').trim() === text,
      );
    const start = leafWith(anchor);
    if (!start) return [];
    let container = start;
    while (container.parentElement && container.querySelectorAll('*').length <= 80) {
      container = container.parentElement;
    }
    const texts = Array.from(container.querySelectorAll('*'))
      .filter((element) => element.children.length === 0 && (element.textContent || '').trim())
      .map((element) => (element.textContent || '').trim());
    return texts.filter((text, index) => texts.indexOf(text) === index);
  }, ANCHOR);
}

/** Expands a section so its pages are drawn, and answers the pages in the order they appear. */
export async function openSection(page, section) {
  let before = await readMenu(page);
  await page.getByText(section, { exact: true }).first().click();
  await page.waitForTimeout(3000);
  let after = await readMenu(page);
  if (after.length < before.length) {
    // The session returned with the section already expanded, so that click collapsed it.
    before = after;
    await page.getByText(section, { exact: true }).first().click();
    await page.waitForTimeout(3000);
    after = await readMenu(page);
  }
  const at = after.indexOf(section);
  const pages = [];
  for (let i = at + 1; i < after.length; i++) {
    if (before.includes(after[i])) break; // the next section, which was there before the click
    pages.push(after[i]);
  }
  return { menu: after, pages };
}

export async function readModes(page) {
  const response = await page.request.get(`${BASE_URL}${SETTINGS}`);
  const content = await response.json();
  return {
    foldersAlphabetically: content.foldersAlphabetically === true,
    entriesInDeclarationOrder: content.entriesInDeclarationOrder === true,
  };
}

/** Saves the modes and reloads, because the menu is drawn once per page load. */
export async function applyModes(page, modes) {
  const response = await page.request.put(`${BASE_URL}${SETTINGS}`, { data: modes });
  if (!response.ok() && response.status() !== 204) {
    throw new Error(`Saving the modes answered ${response.status()}: ${await response.text()}`);
  }
  await page.reload({ waitUntil: 'domcontentloaded' });
  await openAdministration(page);
}
