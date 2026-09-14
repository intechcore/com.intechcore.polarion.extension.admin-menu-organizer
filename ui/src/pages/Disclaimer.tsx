import { type ReactNode, useEffect, useState } from 'react';
import { PageLayout } from '@sbb-polarion/react-sbb-polarion';
import useRemote from '../services/useRemote';

/** Where this extension's sources live; used when the build-generated article is missing. */
const PROJECT_URL = 'https://github.com/intechcore/com.intechcore.polarion.extension.admin-menu-organizer';

/**
 * Disclaimer: the build-generated DISCLAIMER article, read from generic's `/disclaimer` endpoint.
 * The endpoint answers with an empty body when nothing was generated; that case points at the online
 * source.
 */
export default function Disclaimer() {
  const { sendRequest } = useRemote();
  const [html, setHtml] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    let cancelled = false;
    sendRequest({ method: 'GET', url: '/disclaimer' })
      .then(async (response) => {
        const article = response.ok ? (await response.text()).trim() : '';
        if (cancelled) return;
        if (article) {
          setHtml(article);
        } else {
          setMissing(true);
        }
      })
      .catch(() => {
        if (!cancelled) setMissing(true);
      });
    return () => {
      cancelled = true;
    };
  }, [sendRequest]);

  let content: ReactNode;
  if (missing) {
    content = (
      <p>
        No disclaimer has been generated during build. Please check{' '}
        <a href={`${PROJECT_URL}/blob/main/DISCLAIMER.md`} target="_blank" rel="noreferrer">
          the online documentation
        </a>
        .
      </p>
    );
  } else if (html === null) {
    content = <p>Loading...</p>;
  } else {
    // Trusted, build-generated HTML from DISCLAIMER.md.
    content = <article className="markdown-body user-guide-page" dangerouslySetInnerHTML={{ __html: html }} />;
  }

  return <PageLayout title="Disclaimer">{content}</PageLayout>;
}
