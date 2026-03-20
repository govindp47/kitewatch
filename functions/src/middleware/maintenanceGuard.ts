/**
 * Maintenance mode guard for Cloud Functions.
 *
 * Reads config/system.maintenance_mode from Firestore before every request.
 * If true, responds 503 immediately and blocks function logic.
 *
 * Usage:
 *   import { checkMaintenance } from "../middleware/maintenanceGuard";
 *
 *   export const myFn = onRequest({ ... }, async (req, res) => {
 *     if (await checkMaintenance(res)) return;
 *     // ... function logic
 *   });
 */

import { getFirestore } from "firebase-admin/firestore";
import type { Response } from "express";

const CONFIG_COLLECTION = "config";
const SYSTEM_DOC = "system";

/**
 * Checks Firestore config/system.maintenance_mode.
 * Writes a 503 response and returns true if maintenance is active.
 * Returns false if the app is operational — caller should continue.
 */
export async function checkMaintenance(res: Response): Promise<boolean> {
  try {
    const doc = await getFirestore()
      .collection(CONFIG_COLLECTION)
      .doc(SYSTEM_DOC)
      .get();

    const maintenanceMode = doc.exists && doc.get("maintenance_mode") === true;

    if (maintenanceMode) {
      res.status(503).json({
        error: "service_unavailable",
        message: "System is temporarily disabled. Please try again later.",
      });
      return true;
    }
  } catch (err) {
    // Fail open: if Firestore is unreachable, do not block the function.
    // A billing-triggered shutdown will have already set the flag before
    // Cloud Functions lose Firestore access.
    console.error("maintenanceGuard: failed to read config/system", err);
  }
  return false;
}
