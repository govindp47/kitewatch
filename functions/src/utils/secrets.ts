/**
 * Firebase Secret Manager declarations for KiteWatch Cloud Functions.
 *
 * KITE_API_SECRET — the developer API secret from https://developers.kite.trade/
 *   Required for server-side OAuth token exchange (POST /session/token).
 *   NEVER commit this value to source control.
 *   NEVER add it to secrets.properties or BuildConfig.
 *   Provision via: firebase functions:secrets:set KITE_API_SECRET
 *
 * KITE_API_KEY — the developer API key from https://developers.kite.trade/
 *   Required as the api_key parameter in all Kite Connect API calls.
 *   NEVER commit this value to source control.
 *   Provision via: firebase functions:secrets:set KITE_API_KEY
 */

import { defineSecret } from "firebase-functions/params";

export const kiteApiSecret = defineSecret("KITE_API_SECRET");
export const kiteApiKey = defineSecret("KITE_API_KEY");
