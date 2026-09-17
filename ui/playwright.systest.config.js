import { defineConfig } from '@playwright/test';

/**
 * The system suite, against a running Polarion. It is never part of CI: it changes the stored menu
 * order of a live server and reads the menu the server draws.
 *
 *   npm run systest                 the assertions on the drawn order, on this machine
 *   npm run systest:docker          the same, plus the screenshot comparison, in the pinned image
 *   npm run systest:update:docker   rewrites the committed references
 */
export default defineConfig({
  testDir: './systest',
  testMatch: /.*\.systest\.js/,
  snapshotPathTemplate: '{testDir}/expected/{arg}{ext}',
  fullyParallel: false,
  workers: 1,
  // A Polarion page load is slow, and the menu is drawn after it.
  timeout: 180000,
  expect: { timeout: 30000 },
  reporter: [['list']],
  use: {
    baseURL: process.env.POLARION_URL || 'http://localhost',
    viewport: { width: 1600, height: 2000 },
    ignoreHTTPSErrors: true,
  },
});
