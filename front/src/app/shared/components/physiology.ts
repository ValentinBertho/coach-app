/**
 * Primitives physiologie Darilab — composants standalone présentationnels qui
 * encodent les invariants métier de l'UI (cf. docs/ux-redesign-blueprint.md §4).
 * Aucun calcul métier ici : les valeurs proviennent des moteurs backend.
 */
export { DataOriginTagComponent, type DataOrigin } from './data-origin-tag/data-origin-tag.component';
export { IntensityZoneBadgeComponent, type IntensityZone } from './intensity-zone-badge/intensity-zone-badge.component';
export { RangePrescriptionPillComponent } from './range-prescription-pill/range-prescription-pill.component';
export { ReadinessGaugeComponent, type FormLevel } from './readiness-gauge/readiness-gauge.component';
export { AcwrIndicatorComponent } from './acwr-indicator/acwr-indicator.component';
export { EffortBadgeComponent, type EffortKind } from './effort-badge/effort-badge.component';
export { PainFatigueSelectorComponent, type FeedbackKind } from './pain-fatigue-selector/pain-fatigue-selector.component';
