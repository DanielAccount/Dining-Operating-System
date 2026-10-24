// Phase 3 (Tables & QR) end-to-end smoke test.
//
// Requires a running backend + Postgres with the dev seeder applied.
// Seeder logins all use the password "Password123!".
//
//   cd Backend/ordering && ./mvnw spring-boot:run
//   node scripts/phase3-smoke.mjs            # defaults to http://localhost:8080
//   BASE=http://localhost:9090 node scripts/phase3-smoke.mjs
//
// Exit code 0 = all checks passed. Creates its own table in the dev tenant
// (run-unique number) and deletes it at the end, so reruns stay green.
//
// What this script protects, in order:
//   1. The printed-code contract: slug format, qrUrl shape, rename never
//      changes a slug, regenerate orphans the old slug IMMEDIATELY.
//   2. The public scan flow: anonymous phones, stray staff tokens, dead
//      codes answering a generic 404 that reveals nothing.
//   3. Tenant/branch isolation: pinned staff get their own branch's tables
//      whatever branch id they pass; nobody sees across tenants.
const BASE = process.env.BASE ?? 'http://localhost:8080';
let token = null;
const H = () => (token ? { Authorization: `Bearer ${token}` } : {});

async function req(method, path, body, isForm) {
  const headers = { ...H() };
  if (body && !isForm) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: isForm ? body : body ? JSON.stringify(body) : undefined,
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
  // Deliberately sends NO Authorization header: the login endpoint is
  // tenant-less, and attaching a previous user's token used to pin the
  // wrong tenant onto the login transaction (caught by the cross-tenant
  // regression checks below). A login must be an anonymous request.
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

/** Login WITH an explicit stale Authorization header - the regression that
 *  once pinned the caller's tenant onto another tenant's login. */
async function loginWithStaleToken(email, staleToken) {
  token = staleToken;
  const r = await req('POST', '/api/auth/login', { email, password: 'Password123!' });
  token = r.json?.accessToken ?? null;
  return r;
}

// Anonymous call (exactly what a phone that just scanned a sticker does):
// no Authorization header at all, even if a staff token is in hand.
async function anon(path) {
  const saved = token;
  token = null;
  try {
    return await req('GET', path);
  } finally {
    token = saved;
  }
}

const run = async () => {
  const RUN = 'S3-' + Date.now().toString(36).slice(-6);
  const numberA = `${RUN}-A`;
  const numberB = `${RUN}-B`;

  // ── Owner CRUD + the printed-code contract ─────────────────────
  await login('brew.owner@example.com');
  const brewBranch = (await req('GET', '/api/branches')).json.find(b => b.name.includes('Main'));

  const created = check('create table', await req('POST', `/api/branches/${brewBranch.id}/tables`, {
    tableNumber: numberA,
  }), 201);

  assert('slug is 10 url-safe chars', /^[A-Za-z0-9]{10}$/.test(created.qrSlug ?? ''), created.qrSlug ?? '');
  assert('qrUrl points at the storefront route',
    typeof created.qrUrl === 'string' && created.qrUrl.endsWith('/t/' + created.qrSlug),
    created.qrUrl ?? '');

  check('duplicate number is 409 (case-insensitive)', await req('POST', `/api/branches/${brewBranch.id}/tables`, {
    tableNumber: numberA.toLowerCase(),
  }), 409);

  const listed = check('list tables includes it', await req('GET', `/api/branches/${brewBranch.id}/tables`));
  assert('list finds the created table', Array.isArray(listed) && listed.some(t => t.id === created.id));

  const renamed = check('rename table', await req('PUT', `/api/branches/${brewBranch.id}/tables/${created.id}`, {
    tableNumber: numberB,
  }));
  assert('rename keeps the slug stable (printed codes survive)', renamed.qrSlug === created.qrSlug,
    `${created.qrSlug} -> ${renamed.qrSlug}`);

  // ── QR lifecycle: regenerate orphans the old code immediately ──
  const regenerated = check('regenerate-qr', await req('POST', `/api/branches/${brewBranch.id}/tables/${created.id}/regenerate-qr`));
  assert('regenerate mints a NEW slug', regenerated.qrSlug !== created.qrSlug,
    `${created.qrSlug} -> ${regenerated.qrSlug}`);
  assert('regenerated qrUrl follows the new slug', regenerated.qrUrl.endsWith('/t/' + regenerated.qrSlug));

  // ── The public scan, exactly as a phone does it ────────────────
  const menu = check('anonymous scan of live slug -> 200', await anon(`/api/public/tables/${regenerated.qrSlug}/menu`));
  assert('menu says whose table it is', menu.businessName === 'Brew Cafe' && menu.tableNumber === numberB,
    JSON.stringify({ businessName: menu.businessName, tableNumber: menu.tableNumber }));

  const stale = await anon(`/api/public/tables/${created.qrSlug}/menu`);
  assert('old slug is dead right after regenerate', stale.status === 404, `status=${stale.status}`);

  const withStrayToken = await req('GET', `/api/public/tables/${regenerated.qrSlug}/menu`);
  assert('stray staff token on a public route is tolerated', withStrayToken.status === 200,
    `status=${withStrayToken.status}`);

  const dead = await anon('/api/public/tables/zzDeadCode/menu');
  assert('unknown slug -> generic 404', dead.status === 404 && dead.json?.message === 'Menu not found',
    dead.text.slice(0, 120));

  assert('no other tenant leaks into a scan', !JSON.stringify(menu).includes('Ember'));

  // ── Delete frees the number ────────────────────────────────────
  check('delete table -> 204', await req('DELETE', `/api/branches/${brewBranch.id}/tables/${created.id}`), 204);

  const recreated = check('number is free again after delete', await req('POST', `/api/branches/${brewBranch.id}/tables`, {
    tableNumber: numberA,
  }), 201);
  check('cleanup: delete recreated table -> 204',
    await req('DELETE', `/api/branches/${brewBranch.id}/tables/${recreated.id}`), 204);

  // ── Permissions: waiters read, never write ──────────────────────
  await login('brew.waiter1@example.com');
  const waiterBranch = (await req('GET', '/api/me')).json.branch?.id;
  check('waiter can list tables (TABLE_VIEW)', await req('GET', `/api/branches/${waiterBranch}/tables`));
  check('waiter cannot create tables -> 403', await req('POST', `/api/branches/${waiterBranch}/tables`, {
    tableNumber: `${RUN}-W`,
  }), 403);

  // ── Branch pinning: the path branch id is ignored for pinned staff ──
  await login('ember.owner@example.com');
  const emberBranches = (await req('GET', '/api/branches')).json;
  const mdy = emberBranches.find(b => b.name.includes('Mandalay'));
  const ygn = emberBranches.find(b => b.name.includes('Yangon'));

  const mdyTables = (await req('GET', `/api/branches/${mdy.id}/tables`)).json ?? [];
  const ygnTables = (await req('GET', `/api/branches/${ygn.id}/tables`)).json ?? [];
  const mdyIds = mdyTables.map(t => t.id).sort().join(',');
  const ygnIds = ygnTables.map(t => t.id).sort().join(',');

  await login('ember.mdy.manager@example.com');
  const viaYgn = (await req('GET', `/api/branches/${ygn.id}/tables`)).json ?? [];
  const viaYgnIds = viaYgn.map(t => t.id).sort().join(',');
  assert('MDY manager passing Yangon id still gets MDY tables (pinned)', viaYgnIds === mdyIds && viaYgnIds !== ygnIds,
    `viaYgn=${viaYgnIds} mdy=${mdyIds} ygn=${ygnIds}`);

  // ── Tenant wall ────────────────────────────────────────────────
  await login('ember.owner@example.com');
  check('tenant-wide Ember owner asking for Brew branch -> 404',
    await req('GET', `/api/branches/${brewBranch.id}/tables`), 404);

  // ── Cross-tenant contamination regressions ───────────────────────
  // Both were once real bugs this script exists to keep dead:
  // a login or a public scan must ignore whatever tenant the caller's
  // (still valid) token belongs to.
  await login('brew.waiter1@example.com');
  const brewWaiterToken = token;

  const crossLogin = await loginWithStaleToken('ember.owner@example.com', brewWaiterToken);
  assert('cross-tenant login with a stale Bearer token attached -> 200',
    crossLogin.status === 200 && crossLogin.json?.accessToken, `status=${crossLogin.status}`);

  const emberBranches2 = (await req('GET', '/api/branches')).json;
  const emberTable = (await req('GET', `/api/branches/${emberBranches2.find(b => b.name.includes('Mandalay')).id}/tables`)).json[0];
  token = brewWaiterToken;
  const crossScan = await req('GET', `/api/public/tables/${emberTable.qrSlug}/menu`);
  assert('public scan of another tenant\'s table with a foreign token attached -> 200',
    crossScan.status === 200 && crossScan.json?.businessName === 'Ember Restaurant',
    `status=${crossScan.status} business=${crossScan.json?.businessName}`);
  token = null;

  // ── Report ─────────────────────────────────────────────────────
  console.log('\n================ SMOKE TEST ================');
  let pass = 0, fail = 0;
  for (const r of results) {
    const tag = r.ok ? 'PASS' : 'FAIL';
    r.ok ? pass++ : fail++;
    console.log(`[${tag}] ${r.name}  (got ${r.status}, want ${r.expect})`);
    if (!r.ok) console.log(`        ${r.body}`);
  }
  console.log(`\n${pass} passed, ${fail} failed`);
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
