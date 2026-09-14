import { useEffect } from 'react';
import { About } from '@sbb-polarion/react-sbb-polarion';
import appIcon from '../assets/app-icon.svg';
import useRemote from '../services/useRemote';

/** The Disclaimer page of this extension in Polarion's Administration menu. */
export const DISCLAIMER_PAGE = '/polarion/#/administration/admin-menu-organizer/disclaimer';

function openInPolarion(url: string) {
  (window.top ?? window).location.assign(url);
}

interface AboutPageProps {
  navigate?: (url: string) => void;
}

/**
 * Feeds the shared About page this extension's REST hook, app icon and REST-token-test URL.
 *
 * The README it shows links to DISCLAIMER.md, which the build turns into a link to the repository. Inside
 * Polarion that link opens the Disclaimer page of the Administration menu instead.
 */
export default function AboutPage({ navigate = openInPolarion }: Readonly<AboutPageProps>) {
  const { sendRequest } = useRemote();

  useEffect(() => {
    const openDisclaimerInPolarion = (event: MouseEvent) => {
      const link = (event.target as Element | null)?.closest('article.markdown-body a');
      if (!link?.getAttribute('href')?.endsWith('/DISCLAIMER.md')) return;
      event.preventDefault();
      navigate(DISCLAIMER_PAGE);
    };
    document.addEventListener('click', openDisclaimerInPolarion, true);
    return () => document.removeEventListener('click', openDisclaimerInPolarion, true);
  }, [navigate]);

  return (
    <About sendRequest={sendRequest} appIcon={appIcon} restApiUrl="/polarion/admin-menu-organizer/rest/api/version" />
  );
}
