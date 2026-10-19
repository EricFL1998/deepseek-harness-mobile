/**
 * Wire types of the `bridge/v1` protocol.
 *
 * These types are the single source of truth for the JSON that native clients
 * consume. Everything a provider returns must be JSON-serializable: the bridge
 * never hands out class instances, functions, or cyclic values.
 */
/** Protocol version reported by `GET /manifest`. Clients refuse an unknown major. */
export const BRIDGE_VERSION = '1.0.0';
/** URL prefix every route lives under. */
export const BRIDGE_BASE_PATH = '/dsh-mobile/bridge/v1';
/** Route serving the provider catalog. */
export const MANIFEST_PATH = `${BRIDGE_BASE_PATH}/manifest`;
/** Route serving one provider query. */
export const QUERY_PATH = `${BRIDGE_BASE_PATH}/query`;
/** Route plugins POST a transient message to. */
export const EVENTS_PATH = '/dsh-mobile/bridge/v1/events';
