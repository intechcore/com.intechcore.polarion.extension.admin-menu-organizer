import { useEffect, useMemo, useState } from 'react';
import {
  ConfigurationButtons,
  PageLayout,
  type Revision,
  RevisionsTable,
  useConfirm,
} from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import { DEFAULT_NAME, type MenuOrderModes, createMenuOrderService } from '../services/menuOrder';
import useRemote from '../services/useRemote';

const BOTH_OFF: MenuOrderModes = { foldersAlphabetically: false, entriesInDeclarationOrder: false };

const LOAD_ERROR = 'Error occurred loading the data.';

/** The message of a failed call, or the fallback when the call carries none. */
function messageOf(error: unknown, fallback: string): string {
  return (error as Error).message || fallback;
}

/** The Menu Order page: the two ordering modes of the Administration menu, in the global scope. */
export default function MenuOrder() {
  const { sendRequest } = useRemote();
  const service = useMemo(() => createMenuOrderService(sendRequest), [sendRequest]);
  const { confirm, confirmDialog } = useConfirm();

  const [modes, setModes] = useState<MenuOrderModes>(BOTH_OFF);
  const [loaded, setLoaded] = useState(false);
  const [loadingError, setLoadingError] = useState(false);
  const [showRevisions, setShowRevisions] = useState(false);
  const [revisionsToken, setRevisionsToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    service
      .loadModes()
      .then((stored) => {
        if (!cancelled) setModes(stored);
      })
      .catch(() => {
        if (!cancelled) setLoadingError(true);
      })
      .finally(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [service]);

  /** Puts loaded modes into the form. A load that succeeds also clears the error of an earlier one. */
  const showModes = (loadedModes: MenuOrderModes) => {
    setModes(loadedModes);
    setLoadingError(false);
  };

  const handleSave = async () => {
    toast.dismiss();
    try {
      await service.saveModes(modes);
      setRevisionsToken((t) => t + 1);
      toast.success('Data successfully saved. Reload the Polarion page to see the new menu order.');
    } catch (e) {
      toast.error(messageOf(e, 'Error occurred during saving the data.'));
    }
  };

  const handleCancel = async () => {
    if (!(await confirm('Are you sure you want to cancel editing and revert all changes made?'))) return;
    toast.dismiss();
    try {
      showModes(await service.loadModes());
    } catch (e) {
      toast.error(messageOf(e, LOAD_ERROR));
    }
  };

  const handleRevertToDefault = async () => {
    if (!(await confirm('Are you sure you want to return the default values?'))) return;
    toast.dismiss();
    try {
      showModes(await service.loadDefaultModes());
      toast.success('Reverted to the default values. Remember to save the configuration.');
    } catch (e) {
      toast.error(messageOf(e, LOAD_ERROR));
    }
  };

  const handleRevertToRevision = async (revision: Revision) => {
    toast.dismiss();
    try {
      showModes(await service.loadModes(revision.name));
      toast.success(`Reverted to revision ${revision.name}. Don't forget to save.`);
    } catch (e) {
      toast.error(messageOf(e, LOAD_ERROR));
    }
  };

  if (!loaded) {
    return (
      <PageLayout title="Menu Order">
        <p>Loading...</p>
      </PageLayout>
    );
  }

  return (
    <PageLayout title="Menu Order">
      {loadingError && (
        <div className="alert alert-error">
          Error occurred loading the data. Be sure Polarion is started and accessible.
        </div>
      )}

      <div className="form-wrapper menu-order-page">
        <div className="menu-order-mode">
          <label>
            <input
              type="checkbox"
              name="entriesInDeclarationOrder"
              checked={modes.entriesInDeclarationOrder}
              onChange={(e) => setModes((prev) => ({ ...prev, entriesInDeclarationOrder: e.target.checked }))}
            />
            Pages in the order the extension defines them
          </label>
          <p className="menu-order-mode-description">
            Inside each section, the pages keep the order that the extension defines. Without this option, Polarion
            shows them in a different order after each restart.
          </p>
        </div>
        <div className="menu-order-mode">
          <label>
            <input
              type="checkbox"
              name="foldersAlphabetically"
              checked={modes.foldersAlphabetically}
              onChange={(e) => setModes((prev) => ({ ...prev, foldersAlphabetically: e.target.checked }))}
            />
            Sections in alphabetical order
          </label>
          <p className="menu-order-mode-description">
            The sections of all extensions appear from A to Z. Without this option, the sections stay where Polarion put
            them at startup.
          </p>
        </div>
        <p className="menu-order-hint">Saving applies the order at once. Reload the Polarion page to see it.</p>

        <ConfigurationButtons
          onSave={handleSave}
          onCancel={handleCancel}
          onRevertToDefault={handleRevertToDefault}
          onToggleRevisions={() => setShowRevisions((v) => !v)}
          revisionsShown={showRevisions}
        />

        {showRevisions && (
          <RevisionsTable
            name={DEFAULT_NAME}
            scope=""
            reloadToken={revisionsToken}
            loadRevisions={service.loadRevisions}
            onRevert={handleRevertToRevision}
          />
        )}
      </div>

      {confirmDialog}
    </PageLayout>
  );
}
