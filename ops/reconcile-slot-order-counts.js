#!/usr/bin/env node
/**
 * reconcile-slot-order-counts.js
 * ─────────────────────────────────────────────────────────────────────────
 * One-off ops script: reconciles slotAvailability.{slotId}.orderCount
 * against the actual orders in Firestore.
 *
 * WHY THIS EXISTS
 * ────────────────
 * orderCount is supposed to equal the number of *non-cancelled* orders for
 * a given (shopId, pickupDate, pickupSlot). Before the slot-release fix
 * shipped (see functions/index.js: releaseSlotCapacityOnce), cancellations
 * never decremented this counter, so historical data may already be
 * inflated. This script repairs that by recomputing the correct count
 * directly from the orders collection — the ground truth — rather than
 * trusting or incrementally adjusting whatever orderCount currently holds.
 *
 * DEFINITION OF "CORRECT"
 * ────────────────────────
 * expected orderCount for a slot = count of orders where
 *   shopId === <slot's shopId>
 *   AND pickupDate === <slot's date>
 *   AND pickupSlot === <slot's slot>
 *   AND status !== "cancelled"
 *
 * RACE-CONDITION FIX (v2)
 * ─────────────────────────
 * Reconciliation is split into DISCOVERY (read-only full scan, builds a
 * candidate list only) and APPLY (per-candidate transaction that re-reads
 * everything fresh and writes an absolute count). See reconcileSlot().
 *
 * PAST-VS-FUTURE FILTERING (v3 — this revision)
 * ─────────────────────────────────────────────
 * Investigation (see ops/inspect-slot-schema.js output) established that
 * slotAvailability was empty because historical orders were created via
 * direct client-side Firestore writes, before the createOrder Cloud
 * Function migration — they never touched slotAvailability at all.
 * Backfilling slotAvailability docs for pickupDate values already in the
 * past has zero effect on capacity enforcement (createOrder only checks
 * the slot a NEW order targets, which is always today-or-future). So by
 * default, --apply only reconciles candidate slots whose pickupDate is
 * today or in the future. Pass --include-past to reconcile everything,
 * including historical slots, if full historical correctness is ever
 * wanted for reporting/analytics purposes.
 *
 * ORPHANED-DOC FIX (v3 — this revision)
 * ──────────────────────────────────────
 * Previously, a slotAvailability doc with a nonzero orderCount but ZERO
 * matching orders found in the discovery scan (e.g. all orders for that
 * slot were later deleted) would crash the report phase, because the
 * candidate-building code unconditionally read `group.shopId` even when
 * `group` was undefined. Such docs are now routed into a separate
 * `orphanedSlotDocs` bucket, reported for manual review, and never pushed
 * into the transactional candidate list (there's no shopId/pickupDate/
 * pickupSlot to re-verify against without a group).
 *
 * SAFETY MODEL
 * ─────────────
 * - DRY RUN BY DEFAULT. No Firestore write of any kind happens unless
 *   --apply is passed on the command line.
 * - All corrections are ABSOLUTE writes, never FieldValue.increment().
 *   Re-running this script produces the same end state every time.
 * - Only touches:
 *     - slotAvailability/{slotId}.orderCount (+ maxOrders/shopId/date/slot
 *       fields, only when creating a previously-missing doc)
 *     - orders/{orderId}.slotReleased / slotReleasedAt (only on orders that
 *       are already status === "cancelled"; never touches order.status)
 * - Does not modify functions/index.js, firestore.rules, or app/.
 *
 * USAGE
 * ──────
 *   node ops/reconcile-slot-order-counts.js                      # dry run
 *   node ops/reconcile-slot-order-counts.js --apply               # apply, future-or-today only
 *   node ops/reconcile-slot-order-counts.js --apply --include-past # apply, everything
 *
 * Requires Google Application Default Credentials or
 * GOOGLE_APPLICATION_CREDENTIALS pointing at a service account with
 * Firestore read/write access to the target project.
 * ─────────────────────────────────────────────────────────────────────────
 */

"use strict";

const { getApps, initializeApp, getApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const APPLY = process.argv.includes("--apply");
const INCLUDE_PAST = process.argv.includes("--include-past");

const ORDERS_PAGE_SIZE = 500;
const BATCH_WRITE_MARGIN = 400;
const MALFORMED_SAMPLE_SIZE = 20;
const TRANSACTION_MAX_ATTEMPTS = 5;
const DEFAULT_MAX_ORDERS_PER_SLOT = 5;

// ─── Copied verbatim from functions/index.js so slot IDs match exactly. ───
function buildSlotId(shopId, date, slot) {
  return `${shopId}_${date}_${slot}`
    .replace(/ /g, "_")
    .replace(/:/g, "_")
    .replace(/\//g, "_");
}

function cleanString(value) {
  return typeof value === "string" ? value.trim() : "";
}

// Local calendar date (not UTC) — matches how pickupDate is meant to be
// interpreted by shopkeepers/students in the app's timezone. Printed at
// startup so the operator can see exactly what "today" resolved to.
function getLocalDateString(date = new Date()) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

const TODAY_STR = getLocalDateString();

if (getApps().length === 0) {
  initializeApp();
}

const db = getFirestore();

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

    const fromGcloud = cleanString(stdout).replace(/^\(unset\)$/i, "");

    if (fromGcloud) {
      return { projectId: fromGcloud, source: "gcloud CLI config (fallback)" };
    }
  } catch {
    // fall through
  }

  throw new Error(
    "Could not resolve a target Firebase project ID from the Admin SDK, " +
      "environment variables, or gcloud CLI config. Refusing to proceed " +
      "with Firestore reads/writes against an unknown project."
  );
}

async function scanOrders() {
  const slotGroups = new Map();
  const malformed = {
    total: 0,
    byMissingField: { shopId: [], pickupDate: [], pickupSlot: [] },
  };

  let lastDoc = null;
  let scanned = 0;

  for (;;) {
    let query = db.collection("orders").orderBy("__name__").limit(ORDERS_PAGE_SIZE);
    if (lastDoc) query = query.startAfter(lastDoc);

    const snap = await query.get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      const order = doc.data();
      const shopId = cleanString(order.shopId);
      const pickupDate = cleanString(order.pickupDate);
      const pickupSlot = cleanString(order.pickupSlot);
      const status = cleanString(order.status).toLowerCase();

      scanned += 1;

      if (!shopId || !pickupDate || !pickupSlot) {
        malformed.total += 1;
        if (!shopId) malformed.byMissingField.shopId.push(doc.id);
        if (!pickupDate) malformed.byMissingField.pickupDate.push(doc.id);
        if (!pickupSlot) malformed.byMissingField.pickupSlot.push(doc.id);
        continue;
      }

      const slotId = buildSlotId(shopId, pickupDate, pickupSlot);

      if (!slotGroups.has(slotId)) {
        slotGroups.set(slotId, {
          shopId,
          pickupDate,
          pickupSlot,
          liveCount: 0,
          cancelledUnreleased: [],
        });
      }

      const group = slotGroups.get(slotId);

      if (status === "cancelled") {
        if (order.slotReleased !== true) {
          group.cancelledUnreleased.push({ orderId: doc.id });
        }
      } else {
        group.liveCount += 1;
      }
    }

    lastDoc = snap.docs[snap.docs.length - 1];
    if (snap.docs.length < ORDERS_PAGE_SIZE) break;
  }

  return { slotGroups, malformed, scanned };
}

async function scanSlotAvailability() {
  const slotDocs = new Map();
  let lastDoc = null;

  for (;;) {
    let query = db.collection("slotAvailability").orderBy("__name__").limit(ORDERS_PAGE_SIZE);
    if (lastDoc) query = query.startAfter(lastDoc);

    const snap = await query.get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      const data = doc.data();
      slotDocs.set(doc.id, {
        orderCount: Number(data?.orderCount || 0),
        maxOrders: data?.maxOrders,
      });
    }

    lastDoc = snap.docs[snap.docs.length - 1];
    if (snap.docs.length < ORDERS_PAGE_SIZE) break;
  }

  return slotDocs;
}

async function getShopMaxOrdersPerSlotInTx(tx, shopId) {
  const shopSnap = await tx.get(db.collection("shops").doc(shopId));
  const value = Number(shopSnap.exists ? shopSnap.data()?.maxOrdersPerSlot : NaN);
  return Number.isInteger(value) && value > 0 ? value : DEFAULT_MAX_ORDERS_PER_SLOT;
}

async function reconcileSlot({ slotId, shopId, pickupDate, pickupSlot }) {
  const slotRef = db.collection("slotAvailability").doc(slotId);

  return db.runTransaction(async (tx) => {
    const ordersSnap = await tx.get(db.collection("orders").where("shopId", "==", shopId));

    let expectedOrderCount = 0;

    for (const doc of ordersSnap.docs) {
      const order = doc.data();

      if (
        cleanString(order.pickupDate) !== pickupDate ||
        cleanString(order.pickupSlot) !== pickupSlot
      ) {
        continue;
      }

      if (cleanString(order.status).toLowerCase() !== "cancelled") {
        expectedOrderCount += 1;
      }
    }

    const slotSnap = await tx.get(slotRef);

    if (!slotSnap.exists) {
      if (expectedOrderCount === 0) {
        return { slotId, action: "none" };
      }

      const maxOrders = await getShopMaxOrdersPerSlotInTx(tx, shopId);
      const now = Date.now();

      tx.set(
        slotRef,
        {
          slotId,
          shopId,
          date: pickupDate,
          slot: pickupSlot,
          maxOrders,
          orderCount: expectedOrderCount,
          updatedAt: now,
        },
        { merge: true }
      );

      return { slotId, action: "created", orderCount: expectedOrderCount };
    }

    const currentOrderCount = Number(slotSnap.data()?.orderCount || 0);

    if (currentOrderCount === expectedOrderCount) {
      return { slotId, action: "already_correct" };
    }

    tx.set(slotRef, { orderCount: expectedOrderCount, updatedAt: Date.now() }, { merge: true });

    return { slotId, action: "corrected", from: currentOrderCount, to: expectedOrderCount };
  }, { maxAttempts: TRANSACTION_MAX_ATTEMPTS });
}

async function commitReleaseFlagFixes(writes) {
  if (!APPLY || writes.length === 0) return;

  for (let i = 0; i < writes.length; i += BATCH_WRITE_MARGIN) {
    const chunk = writes.slice(i, i + BATCH_WRITE_MARGIN);
    const batch = db.batch();

    for (const write of chunk) {
      batch.set(write.ref, write.data, { merge: true });
    }

    await batch.commit();
    console.log(
      `  [apply] committed orders.slotReleased batch ` +
        `${Math.floor(i / BATCH_WRITE_MARGIN) + 1} (${chunk.length} writes)`
    );
  }
}

function printMalformedSample(label, ids) {
  console.log(`  missing ${label}: ${ids.length}`);
  for (const id of ids.slice(0, MALFORMED_SAMPLE_SIZE)) {
    console.log(`    - ${id} → missing ${label}`);
  }
  if (ids.length > MALFORMED_SAMPLE_SIZE) {
    console.log(`    ... and ${ids.length - MALFORMED_SAMPLE_SIZE} more`);
  }
}

async function main() {
  let resolvedProject;

  try {
    resolvedProject = await resolveTargetProjectOrThrow();
  } catch (err) {
    console.error(err.message);
    console.error("\nAborting before any Firestore reads or writes.");
    process.exit(1);
    return;
  }

  console.log(
    `Target Firebase project: ${resolvedProject.projectId} (resolved via ${resolvedProject.source})`
  );
  console.log(`Resolved "today" as: ${TODAY_STR} (local server date)`);
  console.log("");

  console.log(
    APPLY
      ? `Running in APPLY mode (${INCLUDE_PAST ? "including past slots" : "future-or-today slots only"}) — writes WILL be committed.`
      : "Running in DRY-RUN mode (default) — no writes will be made. Pass --apply to write."
  );
  console.log("");

  console.log("Scanning orders collection (discovery phase)...");
  const { slotGroups, malformed, scanned } = await scanOrders();
  console.log(`  scanned ${scanned} order documents`);
  console.log(`  found ${slotGroups.size} distinct slot groups referenced by orders`);
  console.log("");

  console.log("Scanning slotAvailability collection...");
  const slotDocs = await scanSlotAvailability();
  console.log(`  found ${slotDocs.size} existing slotAvailability documents`);
  console.log("");

  const allSlotIds = new Set([...slotGroups.keys(), ...slotDocs.keys()]);

  const candidates = [];
  const alreadyCorrectAtDiscovery = [];
  const releaseFlagFixes = [];
  const skippedPastCandidates = [];
  const orphanedSlotDocs = [];

  for (const slotId of allSlotIds) {
    const group = slotGroups.get(slotId);
    const existingDoc = slotDocs.get(slotId);
    const discoveryExpected = group ? group.liveCount : 0;

    const looksMismatched =
      (existingDoc && existingDoc.orderCount !== discoveryExpected) ||
      (!existingDoc && group && discoveryExpected > 0);

    if (looksMismatched) {
      if (!group) {
        // existingDoc has a nonzero orderCount but zero matching orders
        // were found in this scan — can't safely build a candidate (no
        // shopId/pickupDate/pickupSlot to re-verify against). Flag for
        // manual review instead of guessing or crashing.
        orphanedSlotDocs.push({ slotId, orderCount: existingDoc.orderCount });
      } else {
        const isFutureOrToday = group.pickupDate >= TODAY_STR;

        if (isFutureOrToday || INCLUDE_PAST) {
          candidates.push({
            slotId,
            shopId: group.shopId,
            pickupDate: group.pickupDate,
            pickupSlot: group.pickupSlot,
            isFutureOrToday,
          });
        } else {
          skippedPastCandidates.push({ slotId, pickupDate: group.pickupDate });
        }
      }
    } else {
      alreadyCorrectAtDiscovery.push(slotId);
    }

    if (group) {
      for (const { orderId } of group.cancelledUnreleased) {
        releaseFlagFixes.push({ orderId, slotId });
      }
    }
  }

  console.log("─".repeat(72));
  console.log("RECONCILIATION REPORT");
  console.log("─".repeat(72));

  console.log(`\nMalformed orders (missing shopId/pickupDate/pickupSlot): ${malformed.total}`);
  if (malformed.total > 0) {
    printMalformedSample("shopId", malformed.byMissingField.shopId);
    printMalformedSample("pickupDate", malformed.byMissingField.pickupDate);
    printMalformedSample("pickupSlot", malformed.byMissingField.pickupSlot);
  }

  console.log(`\nCandidate slots to re-verify (future-or-today): ${candidates.length}`);
  console.log(
    `Candidate slots skipped (pickupDate in the past — re-run with --include-past to reconcile anyway): ` +
      `${skippedPastCandidates.length}`
  );
  console.log(`Slots already correct at discovery time: ${alreadyCorrectAtDiscovery.length}`);
  console.log(
    `\nOrphaned slotAvailability docs (nonzero orderCount, zero matching orders found — needs manual review): ` +
      `${orphanedSlotDocs.length}`
  );
  for (const o of orphanedSlotDocs.slice(0, 20)) {
    console.log(`  ${o.slotId}: orderCount=${o.orderCount}`);
  }
  if (orphanedSlotDocs.length > 20) {
    console.log(`  ... and ${orphanedSlotDocs.length - 20} more`);
  }

  console.log(
    `\nCancelled orders with slotReleased missing/false to flag as released: ${releaseFlagFixes.length}`
  );
  for (const f of releaseFlagFixes.slice(0, 50)) {
    console.log(`  order ${f.orderId} (slot ${f.slotId})`);
  }
  if (releaseFlagFixes.length > 50) {
    console.log(`  ... and ${releaseFlagFixes.length - 50} more`);
  }
  console.log("");
  console.log("─".repeat(72));

  if (!APPLY) {
    console.log(
      "\nDRY RUN complete. Exact corrections are computed fresh, per-slot, " +
        "inside a transaction at apply time, so this dry run intentionally " +
        "does not print before/after counts. Re-run with --apply to reconcile " +
        "future-or-today slots, or --apply --include-past for everything."
    );
    return;
  }

  console.log(`\nReconciling ${candidates.length} candidate slot(s)...`);

  const results = { corrected: [], created: [], already_correct: [], none: [], failed: [] };

  for (const candidate of candidates) {
    try {
      const result = await reconcileSlot(candidate);
      results[result.action].push(result);

      if (result.action === "corrected") {
        console.log(`  [apply] ${result.slotId}: ${result.from} -> ${result.to}`);
      } else if (result.action === "created") {
        console.log(`  [apply] ${result.slotId}: created with orderCount=${result.orderCount}`);
      }
    } catch (err) {
      results.failed.push({ slotId: candidate.slotId, error: err?.message || String(err) });
      console.error(`  [apply] FAILED to reconcile ${candidate.slotId}: ${err?.message || err}`);
    }
  }

  console.log("\nApplying orders.slotReleased fix-ups...");
  const releaseWrites = releaseFlagFixes.map((f) => ({
    ref: db.collection("orders").doc(f.orderId),
    data: { slotReleased: true, slotReleasedAt: Date.now() },
  }));
  await commitReleaseFlagFixes(releaseWrites);

  console.log("\n" + "─".repeat(72));
  console.log("APPLY SUMMARY");
  console.log("─".repeat(72));
  console.log(`  corrected:          ${results.corrected.length}`);
  console.log(`  created:            ${results.created.length}`);
  console.log(`  already correct:    ${results.already_correct.length}`);
  console.log(`  no-op:              ${results.none.length}`);
  console.log(`  failed:             ${results.failed.length}`);
  console.log(`  skipped (past):     ${skippedPastCandidates.length}`);
  console.log(`  slotReleased fixed: ${releaseWrites.length}`);

  if (results.failed.length > 0) {
    console.log("\nThe following slots FAILED to reconcile and should be investigated or re-run:");
    for (const f of results.failed) {
      console.log(`  - ${f.slotId}: ${f.error}`);
    }
  }

  console.log(
    "\nAPPLY complete. All writes were computed from fresh, transaction-time " +
      "reads, so it is safe to re-run this script at any time."
  );
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Reconciliation script failed:", err);
    process.exit(1);
  });