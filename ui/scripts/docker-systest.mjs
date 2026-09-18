// Runs the system suite inside the pinned Playwright Docker image, so the screenshots match the
// committed references. The suite talks to a Polarion running on the host: inside the container that
// is host.docker.internal, which Docker Desktop resolves, and which --add-host provides elsewhere.
//
//   npm run systest:docker           assert against the references
//   npm run systest:update:docker    rewrite them
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const uiDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Defaults live in this process, not in the docker arguments, so they can be passed by name below.
process.env.POLARION_USER ??= 'admin';
process.env.POLARION_PASSWORD ??= 'admin';
const extraArgs = process.argv.slice(2);

let playwrightVersion;
try {
  const pkg = JSON.parse(readFileSync(resolve(uiDir, 'node_modules/playwright/package.json'), 'utf8'));
  playwrightVersion = pkg.version;
} catch {
  console.error('Cannot read node_modules/playwright - run `npm install` first.');
  process.exit(1);
}
const image = `mcr.microsoft.com/playwright:v${playwrightVersion}-noble`;

// Inside the container Polarion is reached as localhost, through systest/host-bridge.mjs. It
// answers only requests whose Host header matches its base.url, and a browser writes that header
// from the URL it opens. The bridge listens on the port that URL names, not always on 80.
const polarionUrl = process.env.POLARION_URL || 'http://localhost';
const url = new URL(polarionUrl);
const bridgePort = url.port || (url.protocol === 'https:' ? '443' : '80');

const args = [
  'run',
  '--rm',
  '--shm-size=1g',
  '--add-host',
  'host.docker.internal:host-gateway',
  '-e',
  'PIXEL_REFERENCES=1',
  '-e',
  `POLARION_URL=${polarionUrl}`,
  '-e',
  `BRIDGE_PORT=${bridgePort}`,
  // The name alone: docker takes the value from this process, so the password never reaches argv,
  // where the process list of the machine would show it.
  '-e',
  'POLARION_USER',
  '-e',
  'POLARION_PASSWORD',
  '-v',
  `${uiDir}:/work`,
  '-v',
  '/work/node_modules',
  '-w',
  '/work',
  image,
  'bash',
  '-c',
  // The bridge runs in the background for as long as the suite does. The extra arguments are passed
  // as positional parameters: joined into the command they would lose their boundaries, so
  // --grep "two words" would arrive as two arguments.
  'npm ci && (node systest/host-bridge.mjs &) && sleep 2 && npx playwright test -c playwright.systest.config.js "$@"',
  'bash',
  ...extraArgs,
];

// The secrets are passed by name, so they are not in the command at all. This keeps any value that
// is still written as NAME=value out of a terminal or a log, should one be added.
const SECRETS = ['POLARION_PASSWORD', 'POLARION_TOKEN'];
const redacted = args.map((arg) =>
  SECRETS.some((name) => arg.startsWith(`${name}=`)) ? `${arg.split('=')[0]}=***` : arg,
);
console.log(`> docker ${redacted.join(' ')}`);
const result = spawnSync('docker', args, { stdio: 'inherit' });
if (result.error) {
  console.error(`Failed to launch docker: ${result.error.message}. Is Docker installed and running?`);
  process.exit(1);
}
process.exit(result.status ?? 1);
