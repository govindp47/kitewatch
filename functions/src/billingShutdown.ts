/**
 * Billing kill switch — Cloud Budget alert handler.
 *
 * Triggered by a Google Cloud Billing budget alert published to the
 * "billing-alerts" Pub/Sub topic. When cumulative spend exceeds the
 * configured threshold, sets config/system.maintenance_mode = true,
 * which blocks all Cloud Function requests via maintenanceGuard.
 *
 * Setup (one-time manual steps):
 *   1. Create a billing budget in Google Cloud Console → Billing → Budgets & alerts.
 *   2. Set the Pub/Sub topic to "billing-alerts" in alert delivery options.
 *   3. Deploy this function. It auto-subscribes to the topic.
 *
 * Cost threshold: $0.50 USD — adjust BUDGET_THRESHOLD_USD below.
 */

import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { getFirestore } from "firebase-admin/firestore";
import { REGION } from "./utils/region";

/** Monthly spend limit in USD. Adjust to match your Cloud Billing budget. */
const BUDGET_THRESHOLD_USD = 0.5;

export const stopBilling = onMessagePublished({ topic: "billing-alerts", region: REGION }, async (event) => {
  try {
    const dataStr = Buffer.from(event.data.message.data, "base64").toString();
    const data = JSON.parse(dataStr) as { costAmount?: number };
    const cost = data.costAmount ?? 0;

    console.log(`Billing alert received. costAmount: $${cost}`);

    if (cost > BUDGET_THRESHOLD_USD) {
      console.warn(`Budget exceeded ($${cost} > $${BUDGET_THRESHOLD_USD}). Activating kill switch.`);

      await getFirestore()
        .collection("config")
        .doc("system")
        .set({ maintenance_mode: true }, { merge: true });

      console.log("Kill switch activated: config/system.maintenance_mode = true");
    }
  } catch (err) {
    console.error("stopBilling: failed to process billing alert", err);
    // Do not rethrow — a Pub/Sub function that throws will retry indefinitely.
  }
});
