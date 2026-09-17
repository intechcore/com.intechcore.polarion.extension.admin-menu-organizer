# CLAUDE.md

Guidance for working in this repository.

## What this is

`com.intechcore.polarion.extension.admin-menu-organizer` - a Polarion ALM extension that keeps the
entries of the Administration menu in a stable order. It builds on the SBB
`ch.sbb.polarion.extension.generic` framework (parent POM) and targets
**Polarion 2606 / Tomcat 11 / Jakarta EE 11**.

Polarion 2606 collects the administration menu entries in an unordered set, so their order changes
at every restart. This extension reorders what Polarion already loaded. It adds no entries and
removes none.

## Build & verify

```bash
mvn clean package            # do NOT pass -s .mvn/settings.xml locally, it is for CI only
mvn clean install -P local-install-into-polarion   # deploy into $POLARION_HOME
```

- The React UI build is **inherited from the generic parent** (its `ui-build-react-app` profile,
  activated by the presence of `ui/package.json`): it installs Node, runs `npm ci` + `npm run build`
  in `ui/`, and copies `ui/dist` into `src/main/resources/webapp/admin-menu-organizer-app/`. This pom
  carries no UI build of its own - do not add one back.
- **Tests** run on `mvn test`/`package`: Java via surefire (JUnit 5 + AssertJ + Mockito, inherited
  from the parent) and the frontend suite through the parent's test-phase execution.
  `-DskipJsTests=true` skips only the JS tests. Frontend tests live in `ui/test/**` and run in
  **Vitest browser mode** (a real Chromium via Playwright), with an istanbul **90%** gate on all four
  metrics. There are no Playwright e2e tests here.
- **Visual regression**: `ui/test/*.visual.test.tsx` capture `ui/test/expected/**`. They are
  pixel-locked to the pinned Playwright Docker image and skip themselves anywhere else
  (`__PIXEL_REFERENCES__`). Regenerate only with `npm run test:update:docker`, never by hand.
- **After a failed `local-install-into-polarion`, check the jar landed.** A failure empties this
  extension's own plugins directory, and Polarion then starts without the extension.

UI dev loop (hot reload against a running Polarion):

```bash
cd ui
cp .env.local.template .env.local   # set VITE_BASE_URL and (optional) VITE_BEARER_TOKEN
npm run dev                          # http://localhost:5173/?feature=menu-order (or ?feature=about)
npx tsc --noEmit                     # type-check (vite build does NOT type-check)
```

## CI

GitHub Actions runs five workflows:

- `ci.yml`: the `build` job runs `mvn -s .mvn/settings.xml clean verify` with the Java and UI tests
  and the Polarion compatibility check. It fails when the build changes `docs/openapi.json`. The
  `pre-commit` job runs all hooks, except `no-commit-to-branch`, the git identity check and the
  Docker UI tests.
- `actionlint.yml`: lints the workflows when they change.
- `pr.yml`: checks the pull request title and its commits with commitizen.
- `bump-version.yml`: dispatched by hand with `patch`, `minor` or `major`. It sets the release
  version in the pom, commits it, tags it `v<version>` and pushes. It checks out with `PAT_TOKEN`
  on purpose: a tag pushed with the default `GITHUB_TOKEN` starts no further workflow, so
  `release.yml` would never see it.
- `release.yml`: runs on a `v*` tag. It builds and tests the tag, then attaches the jars to a
  GitHub release. It stops at `verify` - nothing is published to a Maven repository yet. A second
  job returns `main` to the next `-SNAPSHOT`.

The Polarion artifacts come from the Intechcore Nexus through the repository secrets `NEXUS_URL`,
`NEXUS_USERNAME` and `NEXUS_PASSWORD`. `.mvn/settings.xml` declares Central first, so Nexus only
serves the Polarion artifacts. **Do not reintroduce a groupId remote-repository filter** (`.mvn/rrf`):
the Maven 3.9 resolver matches a groupId exactly, without sub-groups, and it rejected
`com.polarion.alm.projects`. The Maven cache leaves `com/polarion` and `com/siemens` out, because
pull requests from forks can restore the caches of `main`.

## Architecture

Two layers: a Java/Polarion backend and a React frontend.

**Java** (`src/main/java/.../admin_menu_organizer/`)

- `menu/MenuOrderRestorer` - the whole trick. It reads the menu configuration from the HiveMind
  registry, which still holds it in declaration order, and writes the planned order back through
  `AdministrationPageExtenderProvider.setAdministrationPageExtenders(Set)`. Both steps use public
  Polarion API, so **the class holds no reflection**: a signature that changes in a future Polarion
  breaks the build instead of a customer's menu. The setter keeps the iteration order of the set it
  receives, which `MenuOrderRestorerTest` pins against the real Polarion class. Everything it
  touches is guarded: an unexpected shape is logged **once** as a warning and the menu is left as
  Polarion built it. Its `LOCK` is deliberately a private static field, not a shared one.
- `menu/MenuOrderPlanner` - the pure part: given the extenders and the two flags, it computes the
  order. Sections sort through a `Collator` with `SECONDARY` strength, so the order does not depend
  on the platform locale. This is where the 501 unit tests live.
- `MenuOrderStartupListener` - applies the stored order once per server start. It is declared as a
  `<listener>` in `webapp/admin-menu-organizer-app/WEB-INF/web.xml`, not driven from the servlet.
- `settings/MenuOrderSettings` + `MenuOrderSettingsModel` - the persisted settings. The order lives
  in the **global scope under the `Default` name only**; storing it under another name is rejected.
  `delete` re-applies whatever remains, so removing the setting cannot leave a stale menu.
- `rest/AdminMenuOrganizerRestApplication` - registers the settings with generic's
  `NamedSettingsRegistry`. The extension adds no REST controller of its own: the `/api` and
  `/internal` endpoints all come from generic.
- `AdminMenuOrganizerAppServlet` - a `GenericUiServlet` subclass serving the
  `admin-menu-organizer-app` webapp context.

**React** (`ui/`) - Vite + React 19 + TypeScript SPA on the shared
`@sbb-polarion/react-sbb-polarion` (RSP) library. One app, page selected by `?feature=`:

- `pages/MenuOrder.tsx` - the product surface: the two ordering modes, the shared
  `ConfigurationButtons` and the revisions table. It loads only the stored content on mount; the
  revisions and the defaults are fetched when the user asks for them.
- `pages/About.tsx` - RSP's shared `About` component, fed this extension's endpoints.
- `pages/Disclaimer.tsx` - the build-generated DISCLAIMER article.
- `pages/Landing.tsx` - a dev-only stub listing every feature, so the app can be exercised in
  `vite dev` without Polarion. An unknown or missing `?feature=` falls back to it.
- `services/useRemote.ts` - REST hook. **Convention: the UI always calls `/internal/*` (in-session);
  a `VITE_BEARER_TOKEN` in `vite dev` switches it to `/api/*`.**

`src/main/resources/plugin.xml` declares **two webapp contexts** (extension point
`com.polarion.portal.tomcat.webapps`): `admin-menu-organizer` for the REST API and
`admin-menu-organizer-app` for the React app and the menu icons. Adding a
`webapp/<name>/WEB-INF/web.xml` alone is not enough. The URLs in `hivemodule.xml` must point at a
real file (`.../ui/app/index.html?...`), not at a directory - `GenericUiServlet` rejects
extension-less paths.

## Conventions & gotchas

- **Jakarta, not javax.** All code is on `jakarta.*`, and the
  `polarion-compatibility-maven-plugin` fails the build when the bundle or a nested jar refers to a
  package Polarion 2606 rejects. `web.xml` descriptors use the Jakarta `web-app_6_1.xsd`.
- **Don't hardcode `Require-Bundle`** in `META-INF/MANIFEST.MF`. The list comes from the `generic`
  parent. Source keeps only `Bundle-Name`; the rest of the manifest is generated from the
  `maven-jar-plugin.*` properties in the pom.
- **Icons.** The administration section icon is
  `webapp/admin-menu-organizer-app/images/menu/30x30/_parent.svg` and the page icons live in
  `images/menu/16x16/`. `ui/src/assets/app-icon.svg` is a separate file: Vite inlines it into the
  bundle for the About page, and the About reference screenshot pins it.
- **`DISCLAIMER.md` is a build input.** `markdown2html-maven-plugin` converts it to
  `webapp/admin-menu-organizer-app/html/disclaimer.html` at `generate-resources`, which the
  Disclaimer page fetches. Editing the markdown changes the page.
- **Two parent plugin blocks are deliberately overridden**: `copy-vite-build` (restated with
  `changeDetection`, the replacement of the deprecated `overwrite` flag) and the compiler args
  (the parent's `-Alog4j.graalvm.*` options are unrecognized here). Maven merges plugin
  configuration, so both had to be overridden whole - keep them in step on every generic bump.
- **A visual test that resizes the viewport or parks the pointer must run last in its file.** Both
  outlive a test, because one browser page runs the whole file, and every reference captured after
  such a case would be taken under a layout it was not generated with.
- **No tests that assert log output.** Behavior is asserted through the effect, not through the
  logger.
- **OpenAPI**: `docs/openapi.json` is regenerated by the swagger-jakarta plugin on build, and CI
  fails when the build changes it. The file is formatted by a pre-commit hook, so CI installs
  pre-commit before building.
- **Generated files end with a newline.** A file written without one is rewritten by the
  end-of-file pre-commit hook, which aborts the commit.
- **Commits**: a pre-commit hook requires `user.email` to match
  `firstname.lastname@intechcore.com`. Breaking changes use `feat!: ...` or a `BREAKING CHANGE:`
  footer.
