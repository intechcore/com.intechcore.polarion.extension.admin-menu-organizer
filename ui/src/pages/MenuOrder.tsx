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

  const handleSave = async () => {
    toast.dismiss();
    try {
      await service.saveModes(modes);
      setRevisionsToken((t) => t + 1);
      toast.success('Data successfully saved. Reload the Polarion page to see the new menu order.');
    } catch (e) {
      toast.error((e as Error).message || 'Error occurred during saving the data.');
    }
  };

  const handleCancel = async () => {
    if (!(await confirm('Are you sure you want to cancel editing and revert all changes made?'))) return;
    try {
      setModes(await service.loadModes());
      toast.dismiss();
    } catch {
      setLoadingError(true);
    }
  };

  const handleRevertToDefault = async () => {
    if (!(await confirm('Are you sure you want to return the default values?'))) return;
    toast.dismiss();
    try {
      setModes(await service.loadDefaultModes());
      toast.success('Reverted to the default values. Remember to save the configuration.');
    } catch {
      setLoadingError(true);
    }
  };

  const handleRevertToRevision = async (revision: Revision) => {
    try {
      setModes(await service.loadModes(revision.name));
      toast.success(`Reverted to revision ${revision.name}. Don't forget to save.`);
    } catch {
      setLoadingError(true);
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
              name="foldersAlphabetically"
              checked={modes.foldersAlphabetically}
              onChange={(e) => setModes((prev) => ({ ...prev, foldersAlphabetically: e.target.checked }))}
            />
            Sort the sections of extensions alphabetically
          </label>
        </div>
        <div className="menu-order-mode">
          <label>
            <input
              type="checkbox"
              name="entriesInDeclarationOrder"
              checked={modes.entriesInDeclarationOrder}
              onChange={(e) => setModes((prev) => ({ ...prev, entriesInDeclarationOrder: e.target.checked }))}
            />
            Show the pages of each section in the order the extension declares them
          </label>
        </div>
        <p className="menu-order-hint">
          With both options off, the menu keeps the order that Polarion built at startup. Saving applies the order at
          once: reload the Polarion page to see it.
        </p>

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
