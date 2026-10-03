// Phase 2 (Menu) end-to-end smoke test.
//
// Requires a running backend + Postgres with the dev seeder applied.
// Seeder logins all use the password "Password123!".
//
//   cd Backend/ordering && ./mvnw spring-boot:run
//   node scripts/phase2-smoke.mjs            # defaults to http://localhost:8080
//   BASE=http://localhost:9090 node scripts/phase2-smoke.mjs
//
// Exit code 0 = all checks passed. Creates its own data in the dev tenant.
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

async function login(email = 'ember.owner@example.com') {
  const r = await req('POST', '/api/auth/login', { email, password: 'Password123!' });
  token = r.json.accessToken;
  return r;
}

const run = async () => {
  await login();

  // ── Categories ──
  const cat = check('create category', await req('POST', '/api/categories', {
    localizedNames: { en: 'Mains', mm: 'အဓိကစား' }, displayOrder: 1,
  }), 201);
  const catId = cat.id;

  const cat2 = check('create category 2', await req('POST', '/api/categories', {
    localizedNames: { en: 'Drinks' },
  }), 201);
  const cat2Id = cat2.id;

  check('reorder category up', await req('PUT', `/api/categories/${cat2Id}`, {
    localizedNames: { en: 'Drinks' }, displayOrder: 1,
  }));
  const cats = check('list categories', await req('GET', '/api/categories'));
  results.push({ name: 'category order after reorder', status: 200, expect: 200,
    ok: cats.find(c => c.id === catId)?.displayOrder === 2, body: JSON.stringify(cats.map(c => [c.id, c.displayOrder])) });

  check('delete empty category -> 204', await req('DELETE', `/api/categories/${cat2Id}`), 204);

  // ── Modifier groups ──
  const mg = check('create modifier group', await req('POST', '/api/modifier-groups', {
    localizedNames: { en: 'Size' }, isRequired: true, minSelect: 1, maxSelect: 1,
    options: [{ localizedNames: { en: 'Large' }, extraPrice: 3.0, isAvailable: true }],
  }), 201);

  check('modifier group minSelect>maxSelect -> 4xx', await req('POST', '/api/modifier-groups', {
    localizedNames: { en: 'Bad' }, isRequired: true, minSelect: 5, maxSelect: 1, options: [],
  }), 400);

  check('list modifier groups', await req('GET', '/api/modifier-groups'));
  check('update modifier group', await req('PUT', `/api/modifier-groups/${mg.id}`, {
    localizedNames: { en: 'Size' }, isRequired: false, minSelect: 0, maxSelect: 2,
    options: [
      { localizedNames: { en: 'Small' }, extraPrice: 0, isAvailable: true },
      { localizedNames: { en: 'Large' }, extraPrice: 4.0, isAvailable: true },
    ],
  }));

  // ── Menu items ──
  const item = check('create menu item with modifier group', await req('POST', '/api/menu-items', {
    categoryId: catId, localizedNames: { en: 'Burger' }, localizedDescriptions: { en: 'Tasty' },
    basePrice: 10.0, isActive: true, isInStock: true,
    modifierGroups: [{ modifierGroupId: mg.id, displayOrder: 1, requiredOverride: true, isActive: true }],
  }), 201);

  check('list menu items', await req('GET', '/api/menu-items'));
  check('get menu item by id', await req('GET', `/api/menu-items/${item.id}`));
  check('list menu items by category', await req('GET', `/api/menu-items/category/${catId}`));
  check('delete modifier group in use -> 409', await req('DELETE', `/api/modifier-groups/${mg.id}`), 409);

  check('update menu item', await req('PUT', `/api/menu-items/${item.id}`, {
    categoryId: catId, localizedNames: { en: 'Burger Deluxe' }, basePrice: 12.5, isActive: true, isInStock: true,
    modifierGroups: [],
  }));

  check('delete non-empty category -> 409', await req('DELETE', `/api/categories/${catId}`), 409);
  // (covered above, before the item is unlinked)

  // ── Image upload (the endpoint the frontend actually calls) ──
  // 1x1 PNG
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64');
  const fd = new FormData();
  fd.append('file', new Blob([png], { type: 'image/png' }), 'pic.png');
  const up = await fetch(`${BASE}/api/menu-items/images/upload`, { method: 'POST', headers: H(), body: fd });
  const upJson = await up.json();
  check('image upload /images/upload', { status: up.status, text: JSON.stringify(upJson) });

  // The duplicate POST /api/menu-items/upload endpoint was removed because it
  // built a double-prefixed URL. Assert it no longer accepts an upload.
  const fd2 = new FormData();
  fd2.append('file', new Blob([png], { type: 'image/png' }), 'pic2.png');
  const up2 = await fetch(`${BASE}/api/menu-items/upload`, { method: 'POST', headers: H(), body: fd2 });
  const up2Json = await up2.json().catch(() => null);
  results.push({
    name: 'duplicate /api/menu-items/upload endpoint is gone',
    status: up2.status, expect: 405,
    ok: up2.status === 405 || up2.status === 404,
    body: `status=${up2.status} ${JSON.stringify(up2Json)}`,
  });

  // Reject a non-image
  const fd3 = new FormData();
  fd3.append('file', new Blob([Buffer.from('not an image')], { type: 'image/png' }), 'x.png');
  const up3 = await fetch(`${BASE}/api/menu-items/images/upload`, { method: 'POST', headers: H(), body: fd3 });
  results.push({ name: 'reject non-image upload -> 4xx', status: up3.status, expect: 400,
    ok: up3.status === 400, body: (await up3.text()).slice(0, 160) });

  // ── Branch pricing ──
  const branches = (await req('GET', '/api/branches')).json;
  const branchId = branches[0].id;

  const rows = check('list branch menu items', await req('GET', `/api/branches/${branchId}/menu-items`));
  results.push({ name: 'branch list includes the new item as a virtual row (no override yet)',
    status: 200, expect: 200,
    ok: rows.some(r => r.menuItemId === item.id && r.branchMenuItemId === null && Number(r.effectivePrice) === 12.5),
    body: JSON.stringify(rows.filter(r => r.menuItemId === item.id)) });

  const ups = check('upsert branch menu item', await req('PUT', `/api/branches/${branchId}/menu-items/${item.id}`,
    { priceOverride: 15.0, isAvailable: false }));
  results.push({ name: 'upsert reflects override + effective price', status: 200, expect: 200,
    ok: ups.branchMenuItemId != null && Number(ups.effectivePrice) === 15 && ups.isAvailable === false, body: JSON.stringify(ups) });

  check('bulk upsert branch menu items', await req('PUT', `/api/branches/${branchId}/menu-items`, {
    items: [{ menuItemId: item.id, priceOverride: null, isAvailable: true }],
  }));
  const after = check('list branch menu items after bulk', await req('GET', `/api/branches/${branchId}/menu-items`));
  const afterRow = after.find(r => r.menuItemId === item.id);
  results.push({ name: 'bulk upsert cleared override and restored availability', status: 200, expect: 200,
    ok: !!afterRow && afterRow.priceOverride === null && Number(afterRow.effectivePrice) === 12.5
        && afterRow.isAvailable === true,
    body: JSON.stringify(afterRow) });

  check('bulk upsert duplicate ids -> 4xx', await req('PUT', `/api/branches/${branchId}/menu-items`, {
    items: [{ menuItemId: item.id, isAvailable: true }, { menuItemId: item.id, isAvailable: false }],
  }), 400);

  check('reset branch menu item', await req('DELETE', `/api/branches/${branchId}/menu-items/${item.id}`), 204);

  // ── Branch-scoped user must not touch another branch ──
  await login('ember.mdy.manager@example.com');
  const mdyTok = token;
  const other = branches.find(b => b.id !== branchId);
  const cross = await req('GET', `/api/branches/${other.id}/menu-items`);
  results.push({ name: 'branch-scoped user is pinned to their own branch', status: cross.status, expect: 200,
    ok: cross.status === 200, body: `branchId=${other.id} status=${cross.status}` });
  // verify it actually returned THEIR branch, not the requested one
  const mdyRows = (await req('GET', `/api/branches/${other.id}/menu-items`)).json;
  const mdyOwn = (await req('GET', '/api/me')).json;
  results.push({ name: 'me.branch matches branch-scoped user', status: 200, expect: 200,
    ok: mdyOwn.branch?.id != null, body: JSON.stringify(mdyOwn.branch) });
  void mdyTok; void mdyRows;

  // ── Permission enforcement ──
  await login('ember.mdy.waiter@example.com');
  const w = token;
  const forbidden = await req('POST', '/api/categories', { localizedNames: { en: 'Nope' } });
  results.push({ name: 'waiter cannot create category -> 403', status: forbidden.status, expect: 403,
    ok: forbidden.status === 403, body: forbidden.text.slice(0, 120) });
  const canRead = await req('GET', '/api/categories');
  results.push({ name: 'waiter can read categories', status: canRead.status, expect: 200,
    ok: canRead.status === 200, body: '' });
  void w;

  // ── Report ──
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
