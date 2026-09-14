import type { ComponentType } from 'react';
import About from './pages/About';
import Disclaimer from './pages/Disclaimer';
import MenuOrder from './pages/MenuOrder';

/**
 * A single navigable page of the app. The `id` is what appears in the URL as `?feature=<id>` and is
 * also what `hivemodule.xml` points its admin extenders at. Keep the ids stable and aligned with the
 * existing extender ids.
 *
 * Every entry needs an `<extender>` with the same id in hivemodule.xml - a mismatch is a blank page in
 * Polarion and no test catches it.
 */
export interface Feature {
  id: string;
  label: string;
  description: string;
  component: ComponentType;
}

export const FEATURES: Feature[] = [
  {
    id: 'menu-order',
    label: 'Menu Order',
    description: 'The ordering options of the Administration menu.',
    component: MenuOrder,
  },
  {
    id: 'about',
    label: 'About',
    description: 'Extension version and general information.',
    component: About,
  },
  {
    id: 'disclaimer',
    label: 'Disclaimer',
    description: 'The problem this extension shows, what it does, and the risks of using it.',
    component: Disclaimer,
  },
];

export function findFeature(id: string | null): Feature | undefined {
  return FEATURES.find((f) => f.id === id);
}
