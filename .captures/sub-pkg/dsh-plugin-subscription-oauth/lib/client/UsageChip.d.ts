/**
 * The usage chip that rides the composer's bottom bar.
 *
 * The bar itself is a slot the shell owns. `conversation.composer.dock` is a
 * session-scoped `list`, and the shell's own stats pills (`tok/s`, cache hit) and its
 * context meter register into it; this chip joins as the last entry, so it reads to the
 * right of the context meter. It is deliberately compact — an abbreviated route name
 * and the percentage — because the bar is a status line, not a dashboard; the full
 * figure, its reset and the meter appear when the chip is clicked.
 *
 * Two silences are deliberate: a provider this plugin does not own shows nothing rather
 * than a fabricated zero, and switching to a foreign provider keeps the last own reading
 * on screen instead of blanking the bar — the shell's own pills already speak for the
 * foreign one.
 *
 * The pure helpers (the reduction from a route's usage to one line, the label and
 * balance formatters) live in `chip-helpers.ts` so the test suite can assert them
 * without evaluating this module's React and popover imports; everything here is
 * re-exported, so the public surface is unchanged.
 *
 * @module dsh-plugin-subscription-oauth/client/UsageChip
 */
import type { ReactNode } from 'react';
import type { SessionStandardProps } from '@deepseek-ai/dsh-client-ui-slots';
export { accountReadingsOf, balanceOf, formatBalance, formatReset, mergeAccountWindows, pickWindow, providerOf, readingOf, shortLabel, shortWindow, windowAmount, windowName, } from './chip-helpers.js';
export type { AccountReading, ChipBalance, ChipReading, ModelSelection, ModelSelectionProjection, UsageChipInjected, } from './chip-helpers.js';
import type { UsageChipInjected } from './chip-helpers.js';
/**
 * The composer's usage chip.
 *
 * @param props - injected RPC caller, copy, and the session's provider reader.
 * @returns the chip, or null when there is nothing true to say.
 */
export declare function UsageChip({ call, t, isOwnProvider, accountBalance, ccSwitchBalance, useProjection }: UsageChipInjected & SessionStandardProps): ReactNode;
