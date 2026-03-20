/**
 * KiteWatch Cloud Functions — entry point.
 * All function exports are re-exported from this file.
 * Function logic is implemented in subsequent tasks (FM-007+).
 *
 * admin.initializeApp() is called exactly once here.
 * All other modules use getFirestore() / getAuth() from firebase-admin subpackages.
 */

import { initializeApp } from "firebase-admin/app";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

import { kiteApiSecret, kiteApiKey } from "./utils/secrets";
import { checkMaintenance } from "./middleware/maintenanceGuard";
import { REGION } from "./utils/region";

initializeApp();

// Health-check — deployment validation (removed in FM-034)
export const healthCheck = onRequest(
  { region: REGION, secrets: [kiteApiSecret, kiteApiKey] },
  async (_req, res) => {
    if (await checkMaintenance(res)) return;
    logger.info("healthCheck invoked");
    res.json({ status: "ok" });
  }
);

// Staging-only — confirm Secret Manager access (removed in FM-034)
export const validateSecretAccess = onRequest(
  { region: REGION, secrets: [kiteApiSecret, kiteApiKey] },
  async (_req, res) => {
    if (await checkMaintenance(res)) return;
    const configured = kiteApiSecret.value().length > 0;
    logger.info("validateSecretAccess", { configured });
    res.json({ configured });
  }
);

// Stub re-exports for future function modules (auth, proxy, middleware)
export * from "./auth/index";
export * from "./proxy/index";
export { stopBilling } from "./billingShutdown";
