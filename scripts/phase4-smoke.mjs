// Phase 4 (Ordering) end-to-end smoke test.
//
// Requires a running backend + Postgres with the dev seeder applied.
// Seeder logins all use the password "Password123!".
//
//   cd Backend/ordering && ./mvnw spring-boot:run
//   node scripts/phase4-smoke.mjs            # defaults to http://localhost:8080
//   BASE=http://localhost:9090 node scripts/phase4-smoke.mjs
//
// Exit code 0 = all checks passed.
//
// What this script protects, in the order M6 listed them:
//   1. Double-submit replay: the same idempotency key replays the original
//      order - with a different body, and even after the menu repriced.
//   2. Rejection: a cart touching an unorderable item (sold out, retired,
//      not offered) fails WHOLESALE with a per-item reason, and a rejected
//      cart leaves nothing behind - no order row, no side effects.
//   3. Session lifecycle: the first order opens a session, later orders
//      reuse it, the phone's session code re-reads "your orders", and a
//      wrong or missing code is an empty list, never an error.
//   4. Isolation gates: the tenant wall on staff routes, branch pinning
//      on staff writes, public paths ignoring bearer tokens entirely, and
//      idempotency keys being tenant-scoped.
//
// Cleanup caveat (why this differs from the earlier phases' scripts):
// Phase 4 has no order-deletion endpoint and the V5 foreign keys
// (orders -> table_sessions -> restaurant_tables, order_items ->
// menu_items) are plain REFERENCES with no ON DELETE, so anything an
// order touched must stay behind. Every fixture this script creates is
// run-unique, so reruns stay green; only the never-ordered throwaway
// items are deleted at the end.
const BASE = process.env.BASE ?? 'http://localhost:8080';
let token = null;
const H = () => (token ? { Authorization: `Bearer ${token}` } : {});

async function req(method, path, body) {
  const headers = { ...H() };
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not json */ }
  return { status: res.status, json, text };
}

const results = [];
function check(name, res, expect = 200) {
  const ok = res.status === expect;
  results.push({ name, status: res.status, expect, ok, body: res.text.slice(0, 220) });
  return res.json;
}

function assert(name, ok, body = '') {
  results.push({ name, status: ok ? 200 : 0, expect: 200, ok, body });
}

async function login(email) {
  // An anonymous request on purpose: a login must never inherit a previous
  // user's token (the phase 3 regression this guards still applies).
  const saved = token;
  token = null;
  try {
    const r = await req('POST', '/api/auth/login', { email, password: 'Password123!' });
    if (!r.json?.accessToken) throw new Error(`login failed for ${email}: ${r.status} ${r.text}`);
    token = r.json.accessToken;
    return r;
  } finally {
    if (!token) token = saved;
  }
}

// Anonymous call with the token cleared - exactly what the phone that just
// scanned a sticker sends. Supports POST (order submission needs it).
async function anon(method, path, body) {
  const saved = token;
  token = null;
  try {
    return await req(method, path, body);
  } finally {
    token = saved;
  }
}

// Client-generated retry key. UUID per the API contract; the Math fallback
// only matters on a Node without global crypto.randomUUID.
const uuid = () =>
  (globalThis.crypto?.randomUUID?.() ?? `k-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`);

const money = (v) => Number(v);

const run = async () => {
  const RUN = 'S4-' + Date.now().toString(36).slice(-6);
  const name = (n) => `${RUN} ${n}`;

  // ── Setup: run-unique fixtures in Brew Cafe (single branch) ──────
  await login('brew.owner@example.com');
  const brewBranch = (await req('GET', '/api/branches')).json.find(b => b.name.includes('Main'));
  const branchId = brewBranch.id;

  const cat = (await req('POST', '/api/categories', { localizedNames: { en: RUN } })).json;

  // One group, two options: an available +2 pick and an available +3 pick.
  const group = (await req('POST', '/api/modifier-groups', {
    localizedNames: { en: name('Opts') },
    isRequired: false,
    minSelect: 0,
    maxSelect: 1,
    options: [
      { localizedNames: { en: 'Yes' }, extraPrice: 2, isAvailable: true },
      { localizedNames: { en: 'No' }, extraPrice: 3, isAvailable: true },
    ],
  })).json;

  const mk = (n, basePrice, withGroup) => req('POST', '/api/menu-items', {
    categoryId: cat.id,
    localizedNames: { en: name(n) },
    basePrice,
    ...(withGroup
      ? { modifierGroups: [{ modifierGroupId: group.id, displayOrder: 0, isActive: true, requiredOverride: false }] }
      : {}),
  });

  const plain = (await mk('Plain', 5, false)).json;      // ordered later
  const withMods = (await mk('Options', 10, true)).json; // ordered later
  const dead = (await mk('Dead', 5, false)).json;        // only ever rejected -> deletable
  const retired = (await mk('Retired', 5, false)).json;  // only ever rejected -> deletable

  const table = (await req('POST', `/api/branches/${branchId}/tables`, {
    tableNumber: RUN,
  })).json;
  const slug = table.qrSlug;

  // The phone's first round trip: who am I ordering from, what's fresh.
  const menu = check('anonymous scan -> menu', await anon('GET', `/api/public/tables/${slug}/menu`));
  assert('menu names the business and this table',
    menu.businessName === 'Brew Cafe' && menu.tableNumber === RUN,
    JSON.stringify({ businessName: menu.businessName, tableNumber: menu.tableNumber }));

  const findItem = (n) => menu.categories.flatMap(c => c.items).find(i => i.localizedNames.en === name(n));
  const plainView = findItem('Plain');
  const modsView = findItem('Options');
  const optionId = modsView?.modifierGroups?.[0]?.options?.[0]?.id;

  assert('fresh item is orderable, priced, not sold out',
    !!plainView && plainView.soldOut === false && money(plainView.price) === 5,
    JSON.stringify(plainView));
  assert('the public menu exposes the modifier option id needed to order',
    Number.isInteger(optionId), JSON.stringify(modsView?.modifierGroups));

  // ── 1. Double-submit replay ──────────────────────────────────────
  const key1 = uuid();
  const first = check('public submit -> 201', await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: key1,
    items: [{ menuItemId: plain.id, quantity: 2 }],
  }), 201);

  assert('order comes back PENDING, source QR_CUSTOMER, DINE_IN at this table',
    first.status === 'PENDING' && first.source === 'QR_CUSTOMER' &&
      first.orderType === 'DINE_IN' && first.tableNumber === RUN,
    JSON.stringify({ status: first.status, source: first.source, orderType: first.orderType }));
  assert('a session was opened for the phone',
    typeof first.sessionCode === 'string' && first.sessionCode.length > 0, first.sessionCode ?? 'null');
  assert('server-side pricing: 2 x 5 = 10, no client amounts involved',
    money(first.subtotal) === 10 && money(first.finalAmount) === 10,
    JSON.stringify({ subtotal: first.subtotal, finalAmount: first.finalAmount }));
  const firstLine = first.items?.[0];
  assert('line snapshot: name, unit price, line total, quantity',
    firstLine && firstLine.itemNameSnapshot?.en === name('Plain') &&
      money(firstLine.unitPrice) === 5 && money(firstLine.lineTotal) === 10 &&
      firstLine.quantity === 2 && firstLine.itemStatus === 'PENDING',
    JSON.stringify(firstLine));
  const order1Id = first.id;
  const sessionCode1 = first.sessionCode;

  // Same key, DIFFERENT body: the replay must ignore the new payload.
  const replay = check('same key replay -> 201 (not a second order)', await anon('POST',
    `/api/public/tables/${slug}/orders`, {
      idempotencyKey: key1,
      items: [{ menuItemId: plain.id, quantity: 5 }],
    }), 201);
  assert('replay returns the ORIGINAL order id', replay.id === order1Id,
    `first=${order1Id} replay=${replay.id}`);
  assert('replay ignored the new body (still 2 x 5, not 5 x 5)',
    replay.items?.[0]?.quantity === 2 && money(replay.finalAmount) === 10,
    JSON.stringify({ qty: replay.items?.[0]?.quantity, finalAmount: replay.finalAmount }));

  // Reprice the menu, then replay again: the stored order must not move.
  await req('PUT', `/api/menu-items/${plain.id}`, {
    categoryId: cat.id, localizedNames: { en: name('Plain') }, basePrice: 500,
  });
  const replayPriced = await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: key1,
    items: [{ menuItemId: plain.id, quantity: 2 }],
  });
  check('replay after a menu reprice -> 201', replayPriced, 201);
  assert('replay survives menu changes (original 10, not the new 1000)',
    replayPriced.json?.id === order1Id && money(replayPriced.json?.finalAmount) === 10,
    JSON.stringify({ id: replayPriced.json?.id, finalAmount: replayPriced.json?.finalAmount }));
  await req('PUT', `/api/menu-items/${plain.id}`, {
    categoryId: cat.id, localizedNames: { en: name('Plain') }, basePrice: 5,
  });

  // ── 2. Rejection: sold out, retired, unknown - whole cart, no spill ─
  // Menu-state changes need MENU_MANAGE: the owner, not the waiter.
  // (Counts use ORDER_VIEW, which the owner has too.)
  await login('brew.owner@example.com');
  const orderCount = async () =>
    ((await req('GET', `/api/branches/${branchId}/orders`)).json ?? []).length;

  const base = await orderCount();

  check('owner marks the throwaway item sold out at this branch',
    await req('PUT', `/api/branches/${branchId}/menu-items/${dead.id}`, {
      priceOverride: null, isAvailable: true, isInStock: false,
    }));

  const soldOut = await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: uuid(),
    items: [{ menuItemId: dead.id, quantity: 1 }],
  });
  check('sold-out cart -> 409', soldOut, 409);
  assert('409 names the honest reason: sold out right now',
    (soldOut.json?.message ?? '').includes('sold out right now'), soldOut.text.slice(0, 200));
  assert('rejected cart created no order row', (await orderCount()) === base);

  const mixed = await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: uuid(),
    items: [{ menuItemId: plain.id, quantity: 1 }, { menuItemId: dead.id, quantity: 1 }],
  });
  check('one bad line rejects the WHOLE cart', mixed, 409);
  assert('mixed cart: no partial order created', (await orderCount()) === base);

  const menuWithDead = await anon('GET', `/api/public/tables/${slug}/menu`);
  const deadView = menuWithDead.json.categories.flatMap(c => c.items)
    .find(i => i.localizedNames.en === name('Dead'));
  assert('what the storefront shows matches what submit rejects (soldOut=true)',
    deadView && deadView.soldOut === true, JSON.stringify(deadView));

  check('owner restores the item to in stock',
    await req('PUT', `/api/branches/${branchId}/menu-items/${dead.id}`, {
      priceOverride: null, isAvailable: true, isInStock: true,
    }));

  check('owner retires the throwaway item tenant-wide',
    await req('PUT', `/api/menu-items/${retired.id}`, {
      categoryId: cat.id, localizedNames: { en: name('Retired') }, basePrice: 5, isActive: false,
    }));
  const gone = await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: uuid(),
    items: [{ menuItemId: retired.id, quantity: 1 }],
  });
  check('retired item cart -> 409', gone, 409);
  assert('retired reads as "no longer available", not "sold out"',
    (gone.json?.message ?? '').includes('no longer available'), gone.text.slice(0, 200));
  assert('retired rejection created no order row', (await orderCount()) === base);

  const unknown = await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: uuid(),
    items: [{ menuItemId: 999999999, quantity: 1 }],
  });
  check('unknown menu item -> 409', unknown, 409);
  assert('unknown item rejection created no order row', (await orderCount()) === base);

  const emptyCart = await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: uuid(),
    items: [],
  });
  check('empty cart -> 400', emptyCart, 400);

  const noKey = await anon('POST', `/api/public/tables/${slug}/orders`, {
    items: [{ menuItemId: plain.id, quantity: 1 }],
  });
  check('missing idempotency key -> 400', noKey, 400);

  const zeroQty = await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: uuid(),
    items: [{ menuItemId: plain.id, quantity: 0 }],
  });
  check('quantity 0 -> 400', zeroQty, 400);

  const hugeQty = await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: uuid(),
    items: [{ menuItemId: plain.id, quantity: 101 }],
  });
  check('quantity 101 -> 400 (max 99 per line)', hugeQty, 400);

  const deadSlug = await anon('POST', '/api/public/tables/zzDeadCode99/orders', {
    idempotencyKey: uuid(),
    items: [{ menuItemId: plain.id, quantity: 1 }],
  });
  check('unknown slug submit -> 404', deadSlug, 404);

  // restore the retired fixture so it can be cleaned up at the end
  await req('PUT', `/api/menu-items/${retired.id}`, {
    categoryId: cat.id, localizedNames: { en: name('Retired') }, basePrice: 5, isActive: true,
  });

  // ── 3. Session lifecycle ─────────────────────────────────────────
  const key7 = uuid();
  const modOrder = check('submit with a modifier option -> 201', await anon('POST',
    `/api/public/tables/${slug}/orders`, {
      idempotencyKey: key7,
      items: [{ menuItemId: withMods.id, quantity: 1, modifiers: [{ modifierOptionId: optionId }] }],
      customerNote: 'no sugar',
    }), 201);
  assert('second order REUSES the open session (same session code)',
    modOrder.sessionCode === sessionCode1,
    `${sessionCode1} -> ${modOrder.sessionCode}`);
  assert('option extra price is included: 10 + 2 = 12',
    money(modOrder.finalAmount) === 12 && money(modOrder.subtotal) === 12,
    JSON.stringify({ subtotal: modOrder.subtotal, finalAmount: modOrder.finalAmount }));
  assert('modifier snapshot carries the name and the price charged',
    modOrder.items?.[0]?.modifiers?.[0]?.name === 'Yes' &&
      money(modOrder.items[0].modifiers[0].price) === 2,
    JSON.stringify(modOrder.items?.[0]?.modifiers));
  assert('customer note round-trips', modOrder.customerNote === 'no sugar', modOrder.customerNote);

  const key8 = uuid();
  const third = check('third order -> 201', await anon('POST', `/api/public/tables/${slug}/orders`, {
    idempotencyKey: key8,
    items: [{ menuItemId: plain.id, quantity: 1 }],
  }), 201);
  assert('third order still on the same session', third.sessionCode === sessionCode1,
    `${sessionCode1} -> ${third.sessionCode}`);

  const yourOrders = check('your orders (session code)', await anon('GET',
    `/api/public/tables/${slug}/orders?sessionCode=${encodeURIComponent(sessionCode1)}`));
  const ids = (yourOrders ?? []).map(o => o.id);
  assert('"your orders" returns every order this phone placed',
    ids.includes(order1Id) && ids.includes(modOrder.id) && ids.includes(third.id),
    ids.join(','));
  assert('"your orders" is newest first',
    ids[0] === third.id, `first=${ids[0]} expected=${third.id}`);

  const wrongCode = await anon('GET',
    `/api/public/tables/${slug}/orders?sessionCode=NOT-A-REAL-CODE`);
  check('wrong session code -> 200, empty list (not an error)', wrongCode);
  assert('wrong session code answers an empty array',
    Array.isArray(wrongCode.json) && wrongCode.json.length === 0, wrongCode.text.slice(0, 120));

  const noCode = await anon('GET', `/api/public/tables/${slug}/orders`);
  assert('no session code answers an empty array', Array.isArray(noCode.json) && noCode.json.length === 0,
    noCode.text.slice(0, 120));

  // ── Ember fixtures: a run-unique category/item/table for the
  //    cross-tenant gates below. Seeded data stays pristine.
  await login('ember.owner@example.com');
  const emberBranches = (await req('GET', '/api/branches')).json;
  const mdy = emberBranches.find(b => b.name.includes('Mandalay'));
  const ygn = emberBranches.find(b => b.name.includes('Yangon'));

  const eCat = (await req('POST', '/api/categories', { localizedNames: { en: RUN + ' E' } })).json;
  const eItem = (await req('POST', '/api/menu-items', {
    categoryId: eCat.id, localizedNames: { en: name('Ember Dish') }, basePrice: 7,
  })).json;
  const eTable = (await req('POST', `/api/branches/${mdy.id}/tables`, {
    tableNumber: RUN + '-E',
  })).json;
  // Captured NOW, while a tenant-wide user is in hand: a pinned MDY
  // waiter cannot list Yangon's tables (that is the pinning under test).
  const ygnTable = ((await req('GET', `/api/branches/${ygn.id}/tables`)).json ?? [])[0];

  const crossRead = await anon('GET',
    `/api/public/tables/${eTable.qrSlug}/orders?sessionCode=${encodeURIComponent(sessionCode1)}`);
  check("another tenant's table with Brew's session code -> 200", crossRead);
  assert('session codes are table-scoped: the brew code reads nothing on an ember table',
    Array.isArray(crossRead.json) && crossRead.json.length === 0, crossRead.text.slice(0, 120));

  // ── 4. Staff flow: list, detail, create, accept, cancel ───────────
  await login('brew.waiter1@example.com');
  assert('waiter is pinned to the brew branch',
    (await req('GET', '/api/me')).json.branch?.id === branchId);

  const staffList = check('waiter list includes the QR orders', await req('GET', `/api/branches/${branchId}/orders`));
  const staffIds = (staffList ?? []).map(o => o.id);
  assert('customer orders are visible to staff (session lifecycle end-to-end)',
    staffIds.includes(order1Id) && staffIds.includes(modOrder.id) && staffIds.includes(third.id),
    `list has ${staffIds.length} rows`);

  const detail = check('order detail -> 200', await req('GET', `/api/branches/${branchId}/orders/${order1Id}`));
  assert('detail wraps the order plus its status timeline',
    detail.order?.id === order1Id &&
      Array.isArray(detail.history) && detail.history.length >= 1 &&
      detail.history[0].status === 'PENDING',
    JSON.stringify({ orderId: detail.order?.id, history: detail.history?.length }));
  assert('the timeline remembers who placed it: QR customer',
    (detail.history?.[0]?.remarks ?? '').includes('QR'), detail.history?.[0]?.remarks ?? '');

  const staffOrder = check('waiter creates an order at the table -> 201', await req('POST',
    `/api/branches/${branchId}/tables/${table.id}/orders`, {
      idempotencyKey: uuid(),
      items: [{ menuItemId: plain.id, quantity: 1 }],
    }), 201);
  assert('staff order is recorded as WAITER-sourced, on the same session',
    staffOrder.source === 'WAITER' && staffOrder.sessionCode === sessionCode1 &&
      staffOrder.tableNumber === RUN,
    JSON.stringify({ source: staffOrder.source, sessionCode: staffOrder.sessionCode }));

  // Accept and cancel need ORDER_UPDATE + ORDER_CANCEL. The waiter holds
  // ORDER_UPDATE (he accepted nothing here yet) but not ORDER_CANCEL - by
  // design that belongs to cashiers/owners/managers - so the owner finishes
  // the lifecycle. Brew seeds no cashier, so the owner it is.
  await login('brew.owner@example.com');
  const accepted = check('accept -> 200', await req('POST',
    `/api/branches/${branchId}/orders/${staffOrder.id}/accept`));
  assert('accept mints the invoice number and moves PENDING -> ACCEPTED',
    accepted.status === 'ACCEPTED' && typeof accepted.invoiceNumber === 'string' &&
      accepted.invoiceNumber.startsWith('INV-'),
    JSON.stringify({ status: accepted.status, invoiceNumber: accepted.invoiceNumber }));

  const doubleAccept = await req('POST', `/api/branches/${branchId}/orders/${staffOrder.id}/accept`);
  check('double accept -> 409', doubleAccept, 409);
  assert('double accept is refused honestly',
    (doubleAccept.json?.message ?? '').includes('Only PENDING'), doubleAccept.text.slice(0, 200));

  const cancelled = check('cancel with reason -> 200', await req('POST',
    `/api/branches/${branchId}/orders/${staffOrder.id}/cancel`, { reason: 'smoke test cleanup' }));
  assert('cancel moves the order to CANCELLED', cancelled.status === 'CANCELLED',
    cancelled.status);

  const reCancel = await req('POST',
    `/api/branches/${branchId}/orders/${staffOrder.id}/cancel`, { reason: 'again' });
  check('cancel of a cancelled order -> 409', reCancel, 409);
  assert('second cancel refused with the current state',
    (reCancel.json?.message ?? '').includes('CANCELLED'), reCancel.text.slice(0, 200));

  // ── 5. Isolation gates ───────────────────────────────────────────
  // (a) The tenant wall: a branchless owner naming another tenant's branch
  //     -> 404 on both reads and writes. Ember's owner is the branchless
  //     one (brew's owner is seeded WITH a branch, so for him the path id is
  //     ignored by design - that is pinning, covered in (b), not the wall).
  await login('ember.owner@example.com');
  check('tenant wall: owner listing brew orders -> 404',
    await req('GET', `/api/branches/${branchId}/orders`), 404);
  const wallWrite = await req('POST', `/api/branches/${branchId}/tables/${table.id}/orders`, {
    idempotencyKey: uuid(),
    items: [{ menuItemId: plain.id, quantity: 1 }],
  });
  check('tenant wall: owner writing into brew -> 404', wallWrite, 404);

  // (b) Branch pinning: pinned staff operate on their own branch whatever
  //     id they pass in the path.
  await login('ember.mdy.waiter@example.com');
  const pinnedWrite = await req('POST', `/api/branches/${ygn.id}/tables/${ygnTable.id}/orders`, {
    idempotencyKey: uuid(),
    items: [{ menuItemId: eItem.id, quantity: 1 }],
  });
  check('pinned MDY waiter writing under the Yangon branch -> 404', pinnedWrite, 404);

  const pinnedPositive = check('positive control: the same waiter CAN order at his own branch',
    await req('POST', `/api/branches/${mdy.id}/tables/${eTable.id}/orders`, {
      idempotencyKey: uuid(),
      items: [{ menuItemId: eItem.id, quantity: 1 }],
    }), 201);
  assert('the positive control really landed in Mandalay',
    pinnedPositive.tableNumber === RUN + '-E', pinnedPositive.tableNumber ?? 'null');

  // The read side of pinning: naming Yangon in the path answers with his
  // OWN branch's list (200), never Yangon's - the path id is ignored.
  const pinnedRead = await req('GET', `/api/branches/${ygn.id}/orders`);
  check('pinned MDY waiter reading under the Yangon branch -> 200 (his own branch)', pinnedRead);
  assert('and the list he got back is Mandalay\'s (contains his own order)',
    (pinnedRead.json ?? []).some(o => o.id === pinnedPositive.id),
    `${(pinnedRead.json ?? []).length} rows, no ${pinnedPositive.id}`);

  // (c) Public paths ignore bearer tokens: an EMBER token attached to a
  //     BREW submit must not move the order into ember.
  await login('ember.owner@example.com');
  const stray = check('brew submit with an ember token attached -> 201', await req('POST',
    `/api/public/tables/${slug}/orders`, {
      idempotencyKey: uuid(),
      items: [{ menuItemId: plain.id, quantity: 1 }],
    }), 201);
  assert('stray token changed nothing: the order still belongs to Brew',
    stray.tableNumber === RUN && stray.id !== order1Id,
    JSON.stringify({ tableNumber: stray.tableNumber, id: stray.id }));

  // (e) Idempotency is tenant-scoped: Brew's key replays nobody's order
  //     in Ember - a second tenant starts fresh.
  await login('ember.owner@example.com');
  const crossReplay = check("brew's key submitted at ember's table -> 201 (fresh order)", await anon('POST',
    `/api/public/tables/${eTable.qrSlug}/orders`, {
      idempotencyKey: key1,
      items: [{ menuItemId: eItem.id, quantity: 1 }],
    }), 201);
  assert('cross-tenant key replay minted a NEW order, not Brew\'s',
    crossReplay.id !== order1Id && money(crossReplay.finalAmount) === 7,
    JSON.stringify({ id: crossReplay.id, brewOrderId: order1Id, finalAmount: crossReplay.finalAmount }));

  // ── Cleanup: only fixtures no order ever referenced ──────────────
  await login('brew.owner@example.com');
  check('cleanup: never-ordered sold-out item deleted', await req('DELETE', `/api/menu-items/${dead.id}`), 204);
  check('cleanup: never-ordered retired item deleted',
    await req('DELETE', `/api/menu-items/${retired.id}`), 204);
  // The run category/group/ordered items, both run tables and every order
  // stay: V5's foreign keys (no ON DELETE) and the lack of an order-delete
  // endpoint make them undeletable. Run-unique names keep reruns green.

  // ── Report ───────────────────────────────────────────────────────
  console.log('\n================ SMOKE TEST ================');
  let pass = 0, fail = 0;
  for (const r of results) {
    const tag = r.ok ? 'PASS' : 'FAIL';
    r.ok ? pass++ : fail++;
    console.log(`[${tag}] ${r.name}  (got ${r.status}, want ${r.expect})`);
    if (!r.ok) console.log(`        ${r.body}`);
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  console.log(`(run ${RUN}: ordered fixtures left behind by design - see the header note)`);
  process.exit(fail ? 1 : 0);
};

run().catch(e => {
  console.error('SMOKE TEST CRASHED:', e);
  console.log('\n================ SMOKE TEST (partial) ================');
  let pass = 0, fail = 0;
  for (const r of results) {
    const tag = r.ok ? 'PASS' : 'FAIL';
    r.ok ? pass++ : fail++;
    console.log(`[${tag}] ${r.name}  (got ${r.status}, want ${r.expect})`);
    if (!r.ok) console.log(`        ${r.body}`);
  }
  console.log(`\n${pass} passed, ${fail} failed (crashed)`);
  process.exit(2);
});
