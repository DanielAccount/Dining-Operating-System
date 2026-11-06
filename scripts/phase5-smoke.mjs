// Phase 5 (Kitchen / KDS) end-to-end smoke test.
//
// Requires a running backend + Postgres with the dev seeder applied.
// Seeder logins all use the password "Password123!".
//
//   cd Backend/ordering && ./mvnw spring-boot:run
//   node scripts/phase5-smoke.mjs            # defaults to http://localhost:8080
//   BASE=http://localhost:9090 node scripts/phase5-smoke.mjs
//
// Exit code 0 = all checks passed.
//
// What this script protects:
//   1. Only accepted orders enter the kitchen board.
//   2. A chef can view and advance a line: PENDING -> PREPARING -> READY -> SERVED.
//   3. Stages cannot be skipped, while repeating a completed action is safe.
//   4. The parent order follows the slowest unfinished line and finally COMPLETED.
//   5. KDS permissions are enforced (a waiter cannot read the board).
//   6. Tenant and branch walls prevent one kitchen from seeing/processing
//      another kitchen's lines.
//   7. Retiring a table does not break its historical order/KDS ticket.
//
// Cleanup caveat: like Phase 4, orders and their items have no delete
// endpoint. Every fixture is run-unique so reruns stay green.
const BASE = process.env.BASE ?? 'http://localhost:8080';
let token = null;
const H = () => (token ? { Authorization: `Bearer ${token}` } : {});

async function req(method, path, body) {
  const headers = { ...H() };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not json */ }
  return { status: res.status, json, text };
}

const results = [];
function check(name, res, expect = 200) {
  const ok = res.status === expect;
  results.push({ name, status: res.status, expect, ok, body: res.text.slice(0, 260) });
  return res.json;
}

function assert(name, ok, body = '') {
  results.push({ name, status: ok ? 200 : 0, expect: 200, ok, body });
}

async function login(email) {
  const saved = token;
  token = null;
  try {
    const r = await req('POST', '/api/auth/login', { email, password: 'Password123!' });
    if (!r.json?.accessToken) {
      throw new Error(`login failed for ${email}: ${r.status} ${r.text}`);
    }
    token = r.json.accessToken;
    return r;
  } finally {
    if (!token) token = saved;
  }
}

const uuid = () =>
  (globalThis.crypto?.randomUUID?.() ?? `k-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`);

const linesOf = (value) => (Array.isArray(value) ? value : []);

async function createOrderFixture(branchId, run, label) {
  const category = check(
    `${label}: create menu category`,
    await req('POST', '/api/categories', { localizedNames: { en: `${run} ${label}` } }),
    201,
  );

  const menuItem = check(
    `${label}: create menu item`,
    await req('POST', '/api/menu-items', {
      categoryId: category.id,
      localizedNames: { en: `${run} ${label} dish` },
      basePrice: 10,
    }),
    201,
  );

  const table = check(
    `${label}: create table`,
    await req('POST', `/api/branches/${branchId}/tables`, {
      tableNumber: `${run}-${label}`.slice(0, 64),
    }),
    201,
  );

  const order = check(
    `${label}: place staff order`,
    await req('POST', `/api/branches/${branchId}/tables/${table.id}/orders`, {
      idempotencyKey: uuid(),
      items: [{ menuItemId: menuItem.id, quantity: 2 }],
      customerNote: `${run} kitchen note`,
    }),
    201,
  );

  return {
    category,
    menuItem,
    table,
    order,
    itemId: order.items?.[0]?.id,
  };
}

const run = async () => {
  const RUN = 'S5-' + Date.now().toString(36).slice(-6);

  // ── Brew Cafe fixture ───────────────────────────────────────────────
  await login('brew.owner@example.com');
  const brewBranches = (await req('GET', '/api/branches')).json ?? [];
  const brewBranch = brewBranches.find((branch) => branch.name.includes('Main'));
  assert('brew owner has a kitchen branch', !!brewBranch, JSON.stringify(brewBranches));

  const brew = await createOrderFixture(brewBranch.id, RUN, 'BREW');
  assert('brew fixture has an order line', Number.isInteger(brew.itemId), JSON.stringify(brew.order));

  // ── 1. An unaccepted order is not kitchen work yet ──────────────────
  await login('brew.chef@example.com');
  let kitchen = check(
    'chef reads the empty/previous KDS board',
    await req('GET', `/api/branches/${brewBranch.id}/kds`),
  );
  const kitchenLines = linesOf(kitchen);
  assert(
    'PENDING order is absent before acceptance',
    !kitchenLines.some((item) => item.id === brew.itemId),
    JSON.stringify(kitchenLines.map((item) => item.id)),
  );

  await login('brew.owner@example.com');
  const accepted = check(
    'owner accepts the order',
    await req('POST', `/api/branches/${brewBranch.id}/orders/${brew.order.id}/accept`),
  );
  assert('accepted order has a kitchen number', accepted.status === 'ACCEPTED' && !!accepted.invoiceNumber,
    JSON.stringify(accepted));

  await login('brew.chef@example.com');
  kitchen = check(
    'chef sees the accepted line',
    await req('GET', `/api/branches/${brewBranch.id}/kds`),
  );
  const visible = linesOf(kitchen).find((item) => item.id === brew.itemId);
  assert('accepted line is on the board with its ticket context',
    !!visible && visible.itemStatus === 'PENDING' && visible.orderStatus === 'ACCEPTED' &&
      visible.orderId === brew.order.id && visible.customerNote === `${RUN} kitchen note`,
    JSON.stringify(visible));

  // ── 2. Permission gate ──────────────────────────────────────────────
  await login('brew.waiter1@example.com');
  check('waiter without KDS_VIEW cannot read the board',
    await req('GET', `/api/branches/${brewBranch.id}/kds`), 403);
  check('waiter without KDS_UPDATE cannot change a line',
    await req('POST', `/api/branches/${brewBranch.id}/kds/items/${brew.itemId}/preparing`), 403);

  // ── 3. A retired table must not break its historical ticket ─────────
  await login('brew.owner@example.com');
  check('owner retires the table with an active order',
    await req('DELETE', `/api/branches/${brewBranch.id}/tables/${brew.table.id}`), 204);

  await login('brew.chef@example.com');
  kitchen = check(
    'chef reads the ticket after its table was retired',
    await req('GET', `/api/branches/${brewBranch.id}/kds`),
  );
  assert('retired-table ticket is still on the board',
    linesOf(kitchen).some((item) => item.id === brew.itemId),
    JSON.stringify(linesOf(kitchen).map((item) => item.id)));

  // ── 4. Transition contract ──────────────────────────────────────────
  await login('brew.chef@example.com');
  const skipped = check(
    'PENDING cannot jump straight to READY',
    await req('POST', `/api/branches/${brewBranch.id}/kds/items/${brew.itemId}/ready`),
    409,
  );
  assert('skip conflict explains the required previous stage',
    (skipped.message ?? '').includes('PREPARING'), skipped.message ?? skipped.text);

  const preparing = check(
    'mark line PREPARING',
    await req('POST', `/api/branches/${brewBranch.id}/kds/items/${brew.itemId}/preparing`),
  );
  assert('line and order both become PREPARING',
    preparing.itemStatus === 'PREPARING' && preparing.orderStatus === 'PREPARING',
    JSON.stringify(preparing));

  const repeated = check(
    'repeating PREPARING is a safe retry',
    await req('POST', `/api/branches/${brewBranch.id}/kds/items/${brew.itemId}/preparing`),
  );
  assert('retry did not move the line backwards or fail',
    repeated.itemStatus === 'PREPARING' && repeated.orderStatus === 'PREPARING',
    JSON.stringify(repeated));

  const ready = check(
    'mark line READY',
    await req('POST', `/api/branches/${brewBranch.id}/kds/items/${brew.itemId}/ready`),
  );
  assert('line and order both become READY',
    ready.itemStatus === 'READY' && ready.orderStatus === 'READY' && !!ready.readyAt,
    JSON.stringify(ready));

  const served = check(
    'mark line SERVED',
    await req('POST', `/api/branches/${brewBranch.id}/kds/items/${brew.itemId}/served`),
  );
  assert('served line completes the order',
    served.itemStatus === 'SERVED' && served.orderStatus === 'COMPLETED' && !!served.servedAt,
    JSON.stringify(served));

  kitchen = check(
    'chef reloads the board after service',
    await req('GET', `/api/branches/${brewBranch.id}/kds`),
  );
  assert('served line has left the active board',
    !linesOf(kitchen).some((item) => item.id === brew.itemId),
    JSON.stringify(linesOf(kitchen).map((item) => item.id)));

  await login('brew.owner@example.com');
  const detail = check(
    'owner reads the final order timeline',
    await req('GET', `/api/branches/${brewBranch.id}/orders/${brew.order.id}`),
  );
  const history = (detail.history ?? []).map((entry) => entry.status);
  assert('history contains the complete kitchen progression',
    JSON.stringify(history) === JSON.stringify(['PENDING', 'ACCEPTED', 'PREPARING', 'READY', 'COMPLETED']),
    history.join(','));

  // ── 5. Cancelled work cannot be processed ───────────────────────────
  const cancelledFixture = await createOrderFixture(brewBranch.id, RUN, 'CANCEL');
  check('owner accepts the cancellation fixture',
    await req('POST', `/api/branches/${brewBranch.id}/orders/${cancelledFixture.order.id}/accept`));
  check('owner cancels the fixture',
    await req('POST', `/api/branches/${brewBranch.id}/orders/${cancelledFixture.order.id}/cancel`, {
      reason: 'phase 5 smoke test',
    }));

  await login('brew.chef@example.com');
  check('chef cannot process a cancelled line',
    await req('POST', `/api/branches/${brewBranch.id}/kds/items/${cancelledFixture.itemId}/preparing`), 409);

  // ── 6. Tenant wall and cross-kitchen item isolation ─────────────────
  await login('ember.owner@example.com');
  const emberBranches = (await req('GET', '/api/branches')).json ?? [];
  const emberBranch = emberBranches.find((branch) => branch.name.includes('Mandalay'));
  const ember = await createOrderFixture(emberBranch.id, RUN, 'EMBER');

  check('another tenant cannot read this kitchen board',
    await req('GET', `/api/branches/${brewBranch.id}/kds`), 404);

  await login('brew.chef@example.com');
  check('chef cannot process another kitchen\'s item',
    await req('POST', `/api/branches/${brewBranch.id}/kds/items/${ember.itemId}/preparing`), 404);

  kitchen = check(
    'brew board still excludes the ember line',
    await req('GET', `/api/branches/${brewBranch.id}/kds`),
  );
  assert('ember item is absent from brew board',
    !linesOf(kitchen).some((item) => item.id === ember.itemId),
    JSON.stringify(linesOf(kitchen).map((item) => item.id)));

  // ── Report ──────────────────────────────────────────────────────────
  console.log('\n================ PHASE 5 SMOKE TEST ================');
  let pass = 0;
  let fail = 0;
  for (const result of results) {
    const tag = result.ok ? 'PASS' : 'FAIL';
    result.ok ? pass++ : fail++;
    console.log(`[${tag}] ${result.name}  (got ${result.status}, want ${result.expect})`);
    if (!result.ok) console.log(`        ${result.body}`);
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  console.log(`(run ${RUN}: ordered fixtures remain by design)`);
  process.exit(fail ? 1 : 0);
};

run().catch((error) => {
  console.error('PHASE 5 SMOKE TEST CRASHED:', error);
  let pass = 0;
  let fail = 0;
  for (const result of results) {
    const tag = result.ok ? 'PASS' : 'FAIL';
    result.ok ? pass++ : fail++;
    console.log(`[${tag}] ${result.name}  (got ${result.status}, want ${result.expect})`);
    if (!result.ok) console.log(`        ${result.body}`);
  }
  console.log(`\n${pass} passed, ${fail} failed (crashed)`);
  process.exit(2);
});
