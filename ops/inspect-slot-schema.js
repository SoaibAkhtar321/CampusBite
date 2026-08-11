#!/usr/bin/env node
/**
 * inspect-slot-schema.js
 * ─────────────────────────────────────────────────────────────────────────
 * READ-ONLY diagnostic script. Makes zero Firestore writes, ever. No
 * --apply flag exists for this script — there is nothing to apply.
 *
 * WHY THIS EXISTS
 * ────────────────
 * reconcile-slot-order-counts.js reported 245 orders / 146 distinct slot
 * groups but 0 existing slotAvailability documents. Before running that
 * script with --apply (which would create ~120 slotAvailability docs based
 * on the reconciliation script's assumptions about collection name, doc ID
 * format, and field names), we need independent, minimal confirmation of:
 *
 *   1. What top-level collections actually exist in this project — does
 *      "slotAvailability" exist at all, under a different name, or nested
 *      under something else (e.g. shops/{shopId}/slotAvailability)?
 *   2. What a real order document's shopId/pickupDate/pickupSlot fields
 *      actually look like — do they match the exact format buildSlotId()
 *      and the PICKUP_SLOT_REGEX in functions/index.js expect?
 *   3. If any slotAvailability-like documents exist anywhere, what do
 *      their raw fields look like?
 *
 * This script does NOT depend on, import, or duplicate logic from
 * reconcile-slot-order-counts.js or functions/index.js. It is intentionally
 * dumb and literal: list collections, grab a few raw docs, print them.
 *
 * USAGE
 * ──────
 *   node ops/inspect-slot-schema.js
 *
 * Requires the same credentials as reconcile-slot-order-counts.js
 * (GOOGLE_APPLICATION_CREDENTIALS or ADC).
 * ─────────────────────────────────────────────────────────────────────────
 */

"use strict";

const { getApps, initializeApp, getApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const SAMPLE_SIZE = 3;

if (getApps().length === 0) {
  initializeApp();
}

const db = getFirestore();

/**
 * Same project-resolution-or-abort logic as reconcile-slot-order-counts.js,
 * duplicated deliberately (not imported) to keep this script fully
 * standalone and independently trustworthy — if this script's own
 * resolution disagrees with the reconciliation script's, that mismatch
 * itself would be a useful signal.
 */
async function resolveTargetProjectOrThrow() {
  const fromAdminSdk = getApp().options.projectId;

  if (fromAdminSdk) {
    return { projectId: fromAdminSdk, source: "Admin SDK (service account / ADC)" };
  }

  const fromEnv =
    process.env.GOOGLE_CLOUD_PROJECT ||
    process.env.GCLOUD_PROJECT ||
    process.env.FIREBASE_CONFIG_PROJECT_ID ||
    null;

  if (fromEnv) {
    return { projectId: fromEnv, source: "environment variable" };
  }

  try {
    const { execFile } = require("child_process");
    const { promisify } = require("util");
    const execFileAsync = promisify(execFile);

    const { stdout } = await execFileAsync(
      "gcloud",
      ["config", "get-value", "project"],
      { timeout: 5000, shell: true }
    );

    const fromGcloud = String(stdout || "").trim().replace(/^\(unset\)$/i, "");

    if (fromGcloud) {
      return { projectId: fromGcloud, source: "gcloud CLI config (fallback)" };
    }
  } catch {
    // fall through
  }

  throw new Error(
    "Could not resolve a target Firebase project ID from the Admin SDK, " +
      "environment variables, or gcloud CLI config. Refusing to proceed."
  );
}

function printDoc(label, doc) {
  console.log(`  ${label}: ${doc.id}`);
  const data = doc.data();
  const keys = Object.keys(data).sort();

  for (const key of keys) {
    let value = data[key];

    // Keep output readable: truncate arrays/objects, show primitives as-is.
    if (Array.isArray(value)) {
      value = `[Array(${value.length})]`;
    } else if (value && typeof value === "object" && typeof value.toDate === "function") {
      // Firestore Timestamp
      value = `Timestamp(${value.toDate().toISOString()})`;
    } else if (value && typeof value === "object") {
      value = JSON.stringify(value);
    }

    console.log(`    ${key}: ${JSON.stringify(value)}`);
  }
  console.log("");
}

async function main() {
  let resolvedProject;

  try {
    resolvedProject = await resolveTargetProjectOrThrow();
  } catch (err) {
    console.error(err.message);
    console.error("\nAborting before any Firestore reads.");
    process.exit(1);
    return;
  }

  console.log(
    `Target Firebase project: ${resolvedProject.projectId} ` +
      `(resolved via ${resolvedProject.source})`
  );
  console.log("This script is READ-ONLY. No writes will be made.\n");
  console.log("─".repeat(72));

  // ── 1. List every top-level collection that actually exists ────────────
  console.log("\nTop-level collections in this project:");
  const collections = await db.listCollections();
  const collectionIds = collections.map((c) => c.id).sort();

  if (collectionIds.length === 0) {
    console.log("  (none found — check project ID / credentials scope)");
  } else {
    for (const id of collectionIds) {
      console.log(`  - ${id}`);
    }
  }

  const hasOrders = collectionIds.includes("orders");
  const hasSlotAvailability = collectionIds.includes("slotAvailability");
  const hasShops = collectionIds.includes("shops");

  console.log("");
  console.log(`  "orders" collection exists at top level: ${hasOrders}`);
  console.log(`  "slotAvailability" collection exists at top level: ${hasSlotAvailability}`);
  console.log(`  "shops" collection exists at top level: ${hasShops}`);
  console.log("");
  console.log("─".repeat(72));

  // ── 2. Sample raw order documents ───────────────────────────────────────
  console.log(`\nSample "orders" documents (up to ${SAMPLE_SIZE}):`);

  if (hasOrders) {
    const ordersSnap = await db.collection("orders").limit(SAMPLE_SIZE).get();

    if (ordersSnap.empty) {
      console.log("  (collection exists but returned 0 documents from this query)");
    } else {
      ordersSnap.docs.forEach((doc, i) => printDoc(`order[${i}]`, doc));
    }
  } else {
    console.log("  (skipped — no top-level \"orders\" collection found)");
  }

  console.log("─".repeat(72));

  // ── 3. Sample raw slotAvailability documents, if any exist anywhere ────
  console.log(`\nSample "slotAvailability" documents (up to ${SAMPLE_SIZE}):`);

  if (hasSlotAvailability) {
    const slotSnap = await db.collection("slotAvailability").limit(SAMPLE_SIZE).get();

    if (slotSnap.empty) {
      console.log(
        "  Collection exists but is genuinely empty (0 documents). This " +
          "confirms the reconciliation script's finding was accurate, not " +
          "a path/name mismatch — see next section for whether this is " +
          "expected given how slotAvailability docs get created."
      );
    } else {
      slotSnap.docs.forEach((doc, i) => printDoc(`slotAvailability[${i}]`, doc));
    }
  } else {
    console.log(
      "  No top-level \"slotAvailability\" collection found at all. This " +
        "means the reconciliation script's assumption (a top-level " +
        "\"slotAvailability\" collection, doc ID = " +
        "`${shopId}_${pickupDate}_${pickupSlot}` with spaces/colons/slashes " +
        "replaced by underscores) does not match what's actually in this " +
        "project. Check the collections list above for a similarly-named " +
        "collection, or check whether it might be nested under shops/{id}/."
    );
  }

  console.log("");
  console.log("─".repeat(72));

  // ── 4. Sample shops documents, to check for nested subcollections ──────
  console.log(`\nSample "shops" documents (up to ${SAMPLE_SIZE}), checking for subcollections:`);

  if (hasShops) {
    const shopsSnap = await db.collection("shops").limit(SAMPLE_SIZE).get();

    if (shopsSnap.empty) {
      console.log("  (collection exists but returned 0 documents)");
    } else {
      for (const doc of shopsSnap.docs) {
        printDoc(`shop`, doc);

        const subcols = await doc.ref.listCollections();
        const subcolIds = subcols.map((c) => c.id);

        console.log(
          `    subcollections under shops/${doc.id}: ` +
            (subcolIds.length > 0 ? subcolIds.join(", ") : "(none)")
        );
        console.log("");
      }
    }
  } else {
    console.log("  (skipped — no top-level \"shops\" collection found)");
  }

  console.log("─".repeat(72));
  console.log(
    "\nInspection complete. No writes were made. Compare the fields above " +
      "against buildSlotId() and PICKUP_SLOT_REGEX in functions/index.js " +
      "before deciding whether reconcile-slot-order-counts.js's " +
      "assumptions are correct for this project."
  );
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Inspection script failed:", err);
    process.exit(1);
  });