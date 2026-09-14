import type { Revision, SendRequest } from '@sbb-polarion/react-sbb-polarion';

const FEATURE = 'menu-order';
const GLOBAL_SCOPE = '';

/** The single, always-present setting name of the generic settings framework. */
export const DEFAULT_NAME = 'Default';

/** The two ordering modes of the Administration menu, as the menu-order setting stores them. */
export interface MenuOrderModes {
  foldersAlphabetically: boolean;
  entriesInDeclarationOrder: boolean;
}

export interface MenuOrderService {
  loadModes(revision?: string): Promise<MenuOrderModes>;
  saveModes(modes: MenuOrderModes): Promise<void>;
  loadDefaultModes(): Promise<MenuOrderModes>;
  loadRevisions(name: string, scope: string): Promise<Revision[]>;
}

async function errorOf(response: Response): Promise<Error> {
  try {
    const body = await response.json();
    return new Error(body?.errorMessage ?? body?.message ?? `Request failed with status ${response.status}`);
  } catch {
    return new Error(`Request failed with status ${response.status}`);
  }
}

async function json<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw await errorOf(response);
  }
  return response.json();
}

async function ok(response: Response): Promise<void> {
  if (!response.ok) {
    throw await errorOf(response);
  }
}

/** Only the two modes: anything else in a stored setting, such as its bundle timestamp, stays out of the form. */
function modesOf(content: Partial<MenuOrderModes>): MenuOrderModes {
  return {
    foldersAlphabetically: content.foldersAlphabetically === true,
    entriesInDeclarationOrder: content.entriesInDeclarationOrder === true,
  };
}

/** The calls of the Menu Order page, over generic's settings endpoints for the global Default setting. */
export function createMenuOrderService(sendRequest: SendRequest): MenuOrderService {
  const contentUrl = `/settings/${FEATURE}/names/${DEFAULT_NAME}/content?scope=${GLOBAL_SCOPE}`;

  return {
    loadModes: (revision) =>
      sendRequest({
        method: 'GET',
        url: revision ? `${contentUrl}&revision=${encodeURIComponent(revision)}` : contentUrl,
      })
        .then((r) => json<Partial<MenuOrderModes>>(r))
        .then(modesOf),

    saveModes: (modes) =>
      sendRequest({
        method: 'PUT',
        url: contentUrl,
        contentType: 'application/json',
        body: JSON.stringify(modesOf(modes)),
      }).then(ok),

    loadDefaultModes: () =>
      sendRequest({ method: 'GET', url: `/settings/${FEATURE}/default-content` })
        .then((r) => json<Partial<MenuOrderModes>>(r))
        .then(modesOf),

    loadRevisions: (name, scope) =>
      sendRequest({
        method: 'GET',
        url: `/settings/${FEATURE}/names/${encodeURIComponent(name)}/revisions?scope=${encodeURIComponent(scope)}`,
      }).then((r) => json<Revision[]>(r)),
  };
}
