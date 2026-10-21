/**
 * Scoped stylesheet for the Subscriptions page and its manage dialog.
 *
 * The host loads plugin markup without its own control classes, and a bare
 * `<button>` therefore renders with the browser's default raised chrome — which
 * reads as a stray highlight in a dark theme. Rather than invent a look, this
 * follows the control vocabulary of the sibling `dsh-plugin-subscriptions`
 * plugin (same author-facing design as the shell's settings forms): a 6 px
 * radius small control, a transparent fill, a hairline border from the theme's
 * alias tokens, and the theme's hover/active/focus tokens for state.
 *
 * The tokens all carry fallbacks, so the page still renders correctly on a host
 * build that does not define them.
 *
 * @module dsh-plugin-subscription-oauth/client/styles
 */
/**
 * Everything is scoped to this plugin's own roots, so no other surface in the
 * shell can be affected by a rule here.
 */
const scope = ':is(.dsh-subscriptions-settings, .dsh-subscription-manager)';
/** The page's and dialog's stylesheet. */
export const subscriptionsCss = `
.dsh-subscriptions-settings,
.dsh-subscription-manager {
  font-family: inherit;
  font-size: 13px;
  line-height: 1.5;
  /* Native controls (checkbox, dialog) follow the host theme, not the OS. Dark
     is signalled by the alias tokens themselves: body[data-ds-dark-theme]
     only remaps the --dsw-alias-* values at the root (design-platform.css), so
     a scope reading the tokens is already themed and needs no selector of its
     own. color-scheme names the palette for the *platform* control chrome —
     set per scope rather than inherited, the way the shell does for its own
     forced-light surfaces. */
  color-scheme: light;
}
.dsh-subscription-manager {
  box-sizing: border-box;
  padding: 0;
  border: 0;
  border-radius: var(--dsw-radius-panel, 16px);
  background: var(--dsw-alias-bg-layer-2, var(--dsw-alias-bg-layer-1));
  color: var(--dsw-alias-label-primary);
  box-shadow: var(--dsw-elevation-prominent, 0 20px 70px #0004);
}
.dsh-subscription-manager::backdrop {
  background: var(--dsw-alias-bg-mask-1, #0006);
  backdrop-filter: var(--dsw-mask-blur, none);
}
${scope} h2 {
  margin: 0;
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
}
${scope} strong,
${scope} .dsh-subscription-label {
  font-size: 13px;
  line-height: 1.5;
  font-weight: 500;
}
${scope} small,
${scope} .dsh-subscription-hint,
${scope} [role="status"] {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--dsw-alias-label-tertiary);
}
${scope} .dsh-subscription-error {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--dsw-alias-state-error-primary, #b42318);
}
${scope} .dsh-subscription-success {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--dsw-alias-state-success-primary, #067647);
}
${scope} label:has(> input[type="checkbox"]) {
  cursor: pointer;
}
${scope} input[type="checkbox"] {
  accent-color: var(--dsw-alias-brand-primary);
}
/*
 * A model's on/off control is a switch, not a checkbox: it takes effect
 * immediately and its state is what matters, not a tick. Drawn from the theme
 * tokens with appearance: none, so it follows the host palette in both themes
 * instead of the platform's checkbox chrome.
 */
${scope} input[type="checkbox"].dsh-subscription-switch {
  appearance: none;
  -webkit-appearance: none;
  position: relative;
  box-sizing: border-box;
  flex-shrink: 0;
  width: 34px;
  height: 20px;
  margin: 0;
  border: 0.5px solid var(--dsw-alias-border-l3, var(--dsw-alias-border-l2));
  border-radius: 999px;
  background: var(--dsw-alias-bg-layer-3, var(--dsw-alias-bg-layer-2));
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
}
${scope} input[type="checkbox"].dsh-subscription-switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--dsw-alias-label-primary-foreground, #fff);
  box-shadow: 0 1px 2px #0003;
  transition: transform 120ms ease;
}
${scope} input[type="checkbox"].dsh-subscription-switch:checked {
  border-color: transparent;
  background: var(--dsw-alias-state-business-primary, var(--dsw-alias-brand-primary, #067647));
}
${scope} input[type="checkbox"].dsh-subscription-switch:checked::after {
  transform: translateX(14px);
}
${scope} input[type="checkbox"].dsh-subscription-switch:disabled {
  opacity: 0.4;
  cursor: default;
}
${scope} button {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  min-height: 28px;
  padding: 0 10px;
  border: 0.5px solid var(--dsw-alias-border-l3, var(--dsw-alias-border-l2));
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-primary);
  font: inherit;
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
}
${scope} button:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover, var(--dsw-alias-bg-layer-3));
}
${scope} button:active:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-active, var(--dsw-alias-bg-layer-3));
}
${scope} button:disabled {
  opacity: 0.4;
  cursor: default;
}
/* A control that is present but should not read as a bordered button. */
${scope} button.dsh-subscription-quiet {
  min-height: 22px;
  padding: 0 4px;
  border-color: transparent;
  color: var(--dsw-alias-label-secondary, var(--dsw-alias-label-primary));
}
/* The free-text field for a hand-added model id. */
${scope} input.dsh-subscription-input {
  box-sizing: border-box;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 28px;
  padding: 0 10px;
  border: 0.5px solid var(--dsw-alias-border-l3, var(--dsw-alias-border-l2));
  border-radius: var(--dsw-radius-sm, 6px);
  background: var(--dsw-alias-bg-layer-1);
  color: var(--dsw-alias-label-primary);
  font: inherit;
  font-size: 12px;
  line-height: 18px;
}
${scope} input.dsh-subscription-input::placeholder {
  color: var(--dsw-alias-label-tertiary);
}
${scope} input.dsh-subscription-input:focus-visible {
  outline: var(--dsw-focus-ring-width, 2px) solid var(--dsw-focus-ring-color, var(--dsw-alias-state-business-primary));
  outline-offset: 2px;
}
/* A drag handle: no border or fill, so it reads as a grip rather than a button. */
${scope} button.dsh-subscription-handle {
  min-height: 18px;
  height: 18px;
  padding: 0 2px;
  border-color: transparent;
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-tertiary, var(--dsw-alias-label-secondary));
  font-size: 12px;
  line-height: 18px;
  cursor: grab;
}
${scope} button.dsh-subscription-handle:hover:not(:disabled) {
  background: transparent;
  color: var(--dsw-alias-label-primary);
}
${scope} button.dsh-subscription-handle:active:not(:disabled) { cursor: grabbing; }
/* Controls inside the manage dialog are one step larger, like a form footer. */
${scope} .dsh-subscription-dialog-actions button {
  min-height: 34px;
  padding: 0 14px;
  border-radius: var(--dsw-radius-md, 8px);
  font-size: 13px;
  line-height: 1.5;
}
${scope} button.dsh-subscription-primary {
  border-color: transparent;
  background: var(--dsw-alias-button-primary-fill, var(--dsw-alias-label-primary));
  color: var(--dsw-alias-label-primary-foreground, var(--dsw-alias-bg-layer-1));
  font-weight: 500;
}
${scope} button.dsh-subscription-primary:hover:not(:disabled) {
  background: var(--dsw-alias-button-primary-hover, var(--dsw-alias-label-secondary));
}
${scope} button:focus-visible,
${scope} input[type="checkbox"]:focus-visible,
${scope} .dsh-subscription-disclosure:focus-visible {
  outline: var(--dsw-focus-ring-width, 2px) solid var(--dsw-focus-ring-color, var(--dsw-alias-state-business-primary));
  outline-offset: 2px;
}
/* The catalog heading is a summary-style disclosure, not a bordered button. */
${scope} .dsh-subscription-disclosure {
  border-color: transparent;
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  padding: 0 4px;
  font-size: 12px;
  line-height: 18px;
  font-weight: 500;
  color: var(--dsw-alias-label-secondary, var(--dsw-alias-label-primary));
}
${scope} .dsh-subscription-disclosure:hover:not(:disabled) {
  background: transparent;
  color: var(--dsw-alias-label-primary);
}
/* A usage meter: the track, with the fill sized by the caller. */
${scope} .dsh-subscription-meter {
  height: 6px;
  border-radius: 999px;
  /* The track must stay visible when nothing is spent — a 0 % window otherwise reads as
     "no meter at all", which is the opposite of "nothing used". The layer token falls
     back to the card's own colour on a host that does not define it, so the neutral is
     explicit and translucent, and the inset ring keeps the empty track visible on a
     light theme as well as a dark one. */
  background: #8a8f9826;
  box-shadow: inset 0 0 0 1px #8a8f9833;
  overflow: hidden;
}
${scope} .dsh-subscription-meter > span {
  display: block;
  height: 100%;
  background: var(--dsw-alias-state-success-primary, #067647);
}
${scope} .dsh-subscription-meter > span[data-exhausted="true"] {
  background: var(--dsw-alias-state-error-primary, #b42318);
}
`;
/**
 * The composer usage chip's stylesheet.
 *
 * Separate from the page's, because the chip renders in the composer's own bar rather
 * than inside either page container — the page stylesheet is scoped to those
 * containers on purpose, so nothing here may leak into the shell.
 *
 * The chip inherits the bar's type and only draws a meter, so it sits beside the shell's
 * own stats pills without looking like a second design. The trigger is a bare control:
 * the host gives plugin markup no control classes, and a default `<button>` would
 * otherwise read as a raised chip. Every token carries a fallback for hosts that do not
 * define it.
 */
export const usageChipCss = `
.dsh-subscription-usage-chip {
  position: relative;
  display: inline-flex;
  align-items: center;
  /* The bar is a centred flex row whose children are this slot's entries followed by the
     shell's context meter, and a list slot renders its entries as bare children (no
     wrapper). So the chip is itself a flex item: a higher order places it after the
     context meter, and no auto margin is set, so it sits immediately beside it rather
     than being pushed to the edge of the bar. */
  order: 1;
  min-width: 0;
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
}
.dsh-subscription-usage-chip-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 2px 6px;
  border: 0;
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-secondary, #8a8f98);
  font: inherit;
  cursor: pointer;
}
.dsh-subscription-usage-chip-trigger:hover {
  background: var(--dsw-alias-interactive-bg-hover, #ffffff14);
}
.dsh-subscription-usage-chip-trigger:focus-visible {
  outline: var(--dsw-focus-ring-width, 2px) solid var(--dsw-focus-ring-color, var(--dsw-alias-state-business-primary));
  outline-offset: 1px;
}
.dsh-subscription-usage-chip-label {
  overflow: hidden;
  max-width: 6ch;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dsh-subscription-usage-chip-cell {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
}
.dsh-subscription-usage-chip-window {
  opacity: 0.75;
}
.dsh-subscription-usage-chip-value {
  color: var(--dsw-alias-label-primary, #e8e8e8);
  font-variant-numeric: tabular-nums;
}
.dsh-subscription-usage-chip-account {
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* A hairline between accounts, so two sign-ins read as two groups not one run. */
  padding-top: 6px;
  border-top: 1px solid var(--dsw-alias-border-l2, #ffffff14);
}
.dsh-subscription-usage-chip-account:first-of-type {
  padding-top: 0;
  border-top: 0;
}
.dsh-subscription-usage-chip-panel {
  /* The chip portals this to the body and positions it from the trigger's
     viewport rect, so the coordinates useAnchoredPosition returns are
     viewport-relative: the panel must be fixed. It sits above the shell's
     modal layer, which is z-index 1000. */
  position: fixed;
  z-index: 1100;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 180px;
  max-width: min(360px, calc(100vw - 24px));
  padding: 12px 14px;
  border: 1px solid var(--dsw-alias-border-l2, #ffffff1f);
  border-radius: var(--dsw-radius-md, 10px);
  background: var(--dsw-alias-bg-layer-2, #26262a);
  box-shadow: var(--dsw-elevation-prominent, 0 12px 32px #0007);
  color: var(--dsw-alias-label-primary, #e8e8e8);
  white-space: nowrap;
}
.dsh-subscription-usage-chip-full {
  font-size: 11px;
  color: var(--dsw-alias-label-secondary, #8a8f98);
}
.dsh-subscription-usage-chip-meter {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.dsh-subscription-usage-chip-meterHead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}
.dsh-subscription-usage-chip-detail {
  color: var(--dsw-alias-label-secondary, #8a8f98);
  font-variant-numeric: tabular-nums;
}
.dsh-subscription-usage-chip-track {
  display: block;
  width: 100%;
  height: 5px;
  border-radius: 999px;
  /* As on the settings meter: an unspent window has to show its track, so the neutral and
     its ring are explicit rather than a layer token that may match the panel. */
  background: #8a8f9826;
  box-shadow: inset 0 0 0 1px #8a8f9833;
  overflow: hidden;
}
.dsh-subscription-usage-chip-fill {
  display: block;
  height: 100%;
  background: var(--dsw-alias-state-success-primary, #067647);
}
`;
/**
 * The Models-page provider-card block's stylesheet.
 *
 * Scoped to its own root so nothing reaches the section's own cards. It borrows
 * the section page's own dot and meter geometry so a subscription card reads as
 * one row of the same family rather than a foreign design. Tokens carry
 * fallbacks for hosts that define none.
 */
export const providerCardCss = `
.dsh-subscription-models-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.5;
  color: var(--dsw-alias-label-primary);
}
.dsh-subscription-models-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.dsh-subscription-models-card-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border: 1.5px solid var(--dsw-alias-label-dimmed);
  border-radius: 50%;
  background: transparent;
}
.dsh-subscription-models-card-dot-live {
  border-color: var(--dsw-alias-state-success-primary, #067647);
  background: var(--dsw-alias-state-success-primary, #067647);
}
.dsh-subscription-models-card-dot-busy {
  border-color: var(--dsw-alias-state-warn-primary, #f5a524);
  background: var(--dsw-alias-state-warn-primary, #f5a524);
}
.dsh-subscription-models-card-name {
  font-weight: 600;
  font-size: 13px;
  line-height: 20px;
  color: var(--dsw-alias-label-primary);
}
.dsh-subscription-models-card-state {
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-tertiary);
}
.dsh-subscription-models-card-usage {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dsh-subscription-models-card-meter {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.dsh-subscription-models-card-meterHead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}
.dsh-subscription-models-card-detail {
  color: var(--dsw-alias-label-secondary);
  font-variant-numeric: tabular-nums;
}
.dsh-subscription-models-card-track {
  display: block;
  width: 100%;
  height: 5px;
  border-radius: 999px;
  background: #8a8f9826;
  box-shadow: inset 0 0 0 1px #8a8f9833;
  overflow: hidden;
}
.dsh-subscription-models-card-fill {
  display: block;
  height: 100%;
  background: var(--dsw-alias-state-success-primary, #067647);
}
.dsh-subscription-models-card-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.dsh-subscription-models-card-actions button {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  padding: 0 10px;
  border: 0.5px solid var(--dsw-alias-border-l3, var(--dsw-alias-border-l2));
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-primary);
  font: inherit;
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
}
.dsh-subscription-models-card-actions button:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover, var(--dsw-alias-bg-layer-3));
}
.dsh-subscription-models-card-actions button:disabled {
  opacity: 0.4;
  cursor: default;
}
`;
/**
 * The composer's quick-mode button stylesheet.
 *
 * It renders in the composer's own control row — the same row as the model selector —
 * so it borrows that row's geometry: a compact, borderless control that only colours on
 * hover and when the mode is on. Tokens carry fallbacks for hosts that define none.
 */
export const fastModeCss = `
.dsh-subscription-fast-mode {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  padding: 3px;
  border: 0;
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-secondary, #8a8f98);
  font: inherit;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}
.dsh-subscription-fast-mode:hover {
  background: var(--dsw-alias-interactive-bg-hover, #ffffff14);
}
/* A click has to look like one, even before the selection lands. */
.dsh-subscription-fast-mode:active {
  background: var(--dsw-alias-interactive-bg-active, #ffffff2e);
}
.dsh-subscription-fast-mode[data-pending="true"] {
  opacity: 0.7;
  cursor: progress;
}
/* A switch the host refused is marked rather than left looking merely off, so a failure
   is visible where it happened; the reason rides the title and the console. */
.dsh-subscription-fast-mode[data-failed="true"] {
  color: var(--dsw-alias-state-error-primary, #b42318);
}
.dsh-subscription-fast-mode:focus-visible {
  outline: var(--dsw-focus-ring-width, 2px) solid var(--dsw-focus-ring-color, var(--dsw-alias-state-business-primary));
  outline-offset: 1px;
}
/* Off: the bolt is dim, so an available mode reads as an invitation, not a state. */
.dsh-subscription-fast-mode[data-active="false"] .dsh-subscription-fast-mode-bolt {
  opacity: 0.55;
}
/* On: the bolt takes the accent and glows, which is the only thing distinguishing the
   fast variant from its base — both answer with the same model name. */
.dsh-subscription-fast-mode[data-active="true"] .dsh-subscription-fast-mode-bolt {
  color: var(--dsw-alias-state-warn-primary, #f5a524);
  filter: drop-shadow(0 0 6px var(--dsw-alias-state-warn-primary, #f5a52455));
}
.dsh-subscription-fast-mode-reason {
  max-width: 14ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}
`;
