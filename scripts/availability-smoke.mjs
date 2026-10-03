// Availability semantics: the three levels and the customer-facing result.
//
//   item.isActive   tenant-wide   is the dish on the menu at all?
//   isAvailable     per branch    does THIS branch offer it?
//   isInStock       per branch    does THIS branch have it today?
//
// Expected: retired or not-offered -> hidden; sold out -> visible, soldOut=true.

const BASE = process.env.BASE ?? 'http://localhost:8080';
let token = null;
const H = () => ({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' });

async function req(method, path, body) {
  const res = await fetch(BASE + path, { method, headers: H(), body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json = null; try { json = JSON.parse(text); } catch {}
  return { status: res.status, json, text };
}

const results = [];
const check = (name, ok, detail = '') => results.push({ name, ok, detail });

const stamp = Date.now().toString().slice(-6);
const created = { cats: [], items: [], groups: [] };

(async () => {
  const login = await req('POST', '/api/auth/login', { email: 'ember.owner@example.com', password: 'Password123!' });
  token = login.json?.accessToken;
  if (!token) { console.log('login failed', login.text.slice(0, 200)); process.exit(1); }

  const branches = (await req('GET', '/api/branches')).json;
  const [branchA, branchB] = branches;

  const cat = (await req('POST', '/api/categories', { localizedNames: { en: `Avail${stamp}` } })).json;
  created.cats.push(cat.id);

  // Two options: one available, one not, to check option filtering.
  const mg = (await req('POST', '/api/modifier-groups', {
    localizedNames: { en: `Opt${stamp}` }, isRequired: false, minSelect: 0, maxSelect: 1,
    options: [
      { localizedNames: { en: 'Yes' }, extraPrice: 1, isAvailable: true },
      { localizedNames: { en: 'No' }, extraPrice: 2, isAvailable: false },
    ],
  })).json;
  created.groups.push(mg.id);

  const mk = async (name) => (await req('POST', '/api/menu-items', {
    categoryId: cat.id, localizedNames: { en: name }, basePrice: 5,
    modifierGroups: [{ modifierGroupId: mg.id, displayOrder: 0, isActive: true, requiredOverride: false }],
  })).json;
  for (const n of ['Plain', 'SoldOut', 'NotOffered', 'Retired', 'RetiredAndSoldOut']) {
    created.items.push((await mk(n)).id);
  }

  const byName = async (name) => (await req('GET', `/api/menu-items/category/${cat.id}`)).json
    .find(i => i.localizedNames.en === name);

  const setBranch = (branchId, menuItemId, patch) =>
    req('PUT', `/api/branches/${branchId}/menu-items/${menuItemId}`, { priceOverride: null, isAvailable: true, ...patch });

  // ── Customer menu helpers ──
  const menuFor = async (branchId) => (await req('GET', `/api/branches/${branchId}/customer-menu`)).json;
  const findItem = (menu, name) => menu.flatMap(c => c.items).find(i => i.localizedNames.en === name);

  // ── 1. defaults: everything orderable, no overrides ──
  const plain = await byName('Plain');
  const m1 = await menuFor(branchA.id);
  const plainView = findItem(m1, 'Plain');
  check('no overrides -> item visible and orderable (soldOut=false)',
    !!plainView && plainView.soldOut === false, JSON.stringify(plainView));

  // ── 2. sold out at branch A: visible + soldOut, but NOT at branch B ──
  await setBranch(branchA.id, plain.id, { isInStock: false });
  const mA = await menuFor(branchA.id);
  const mB = await menuFor(branchB.id);
  const atA = findItem(mA, 'Plain');
  const atB = findItem(mB, 'Plain');
  check('sold out at branch A -> visible with soldOut=true',
    !!atA && atA.soldOut === true, JSON.stringify(atA));
  check('stock is per branch: branch B is unaffected',
    !!atB && atB.soldOut === false, JSON.stringify(atB));

  // ── 3. not offered at branch A: hidden entirely ──
  const notOffered = await byName('NotOffered');
  await setBranch(branchA.id, notOffered.id, { isAvailable: false });
  const mA2 = await menuFor(branchA.id);
  check('not offered at branch A -> hidden from the customer menu',
    findItem(mA2, 'NotOffered') === undefined, JSON.stringify(findItem(mA2, 'NotOffered')));
  check('not offered at branch A -> still visible at branch B',
    !!findItem(mB, 'NotOffered'), 'missing at B');

  // ── 4. sold out AND not offered -> hidden, not "sold out" ──
  const both = await byName('RetiredAndSoldOut');
  await setBranch(branchA.id, both.id, { isAvailable: false, isInStock: false });
  const mA3 = await menuFor(branchA.id);
  check('not offered + sold out -> hidden, never shown as soldOut',
    findItem(mA3, 'RetiredAndSoldOut') === undefined, 'item leaked into the menu');

  // ── 5. retired tenant-wide -> hidden at every branch ──
  const retired = await byName('Retired');
  await setBranch(branchA.id, retired.id, { isInStock: false });
  await req('PUT', `/api/menu-items/${retired.id}`, {
    categoryId: cat.id, localizedNames: { en: 'Retired' }, basePrice: 5, isActive: false,
  });
  const mA4 = await menuFor(branchA.id);
  const mB4 = await menuFor(branchB.id);
  check('retired item is hidden at every branch',
    findItem(mA4, 'Retired') === undefined && findItem(mB4, 'Retired') === undefined,
    `A=${!!findItem(mA4, 'Retired')} B=${!!findItem(mB4, 'Retired')}`);

  // ── 6. retiring must not change a sibling's position or hide others ──
  const stillThere = findItem(mA4, 'Plain');
  check('retiring one item leaves the rest of the menu intact',
    !!stillThere && !!findItem(mA4, 'SoldOut') && !!findItem(mA4, 'NotOffered') === false,
    `plain=${!!stillThere} soldOutItem=${!!findItem(mA4, 'SoldOut')}`);

  // ── 7. unavailable modifier options are filtered out ──
  const plainWithMods = findItem(mA, 'Plain');
  const grp = plainWithMods?.modifierGroups?.[0];
  check('unavailable modifier options are removed from the customer menu',
    grp && grp.options.length === 1 && grp.options[0].localizedNames.en === 'Yes',
    JSON.stringify(grp));

  // ── 8. branch grid reports all three levels + resolved isOrderable ──
  const grid = (await req('GET', `/api/branches/${branchA.id}/menu-items`)).json;
  const gSold = grid.find(r => r.menuItemId === plain.id);
  check('branch grid: sold-out item reports isOrderable=false',
    gSold && gSold.isInStock === false && gSold.isAvailable === true && gSold.isOrderable === false,
    JSON.stringify(gSold));
  const gHidden = grid.find(r => r.menuItemId === notOffered.id);
  check('branch grid: not-offered item reports isOrderable=false',
    gHidden && gHidden.isAvailable === false && gHidden.isOrderable === false, JSON.stringify(gHidden));
  check('branch grid still shows unavailable items (it is the editor, not the customer menu)',
    !!gHidden, 'unavailable row missing from the grid');

  // ── 9. toggling retire in the UI must not disturb display order ──
  const ordersBefore = (await req('GET', `/api/menu-items/category/${cat.id}`)).json
    .map(i => `${i.localizedNames.en}:${i.displayOrder}`);
  const retiredRow = (await req('GET', `/api/menu-items/category/${cat.id}`)).json
    .find(i => i.localizedNames.en === 'Retired');
  await req('PUT', `/api/menu-items/${retiredRow.id}`, {
    categoryId: cat.id, localizedNames: { en: 'Retired' }, basePrice: 5, isActive: true,
    // note: no displayOrder - exactly what the card toggle sends
  });
  const ordersAfter = (await req('GET', `/api/menu-items/category/${cat.id}`)).json
    .map(i => `${i.localizedNames.en}:${i.displayOrder}`);
  check('re-enabling an item via a displayOrder-less PUT does not renumber',
    JSON.stringify(ordersBefore) === JSON.stringify(ordersAfter),
    `before=${JSON.stringify(ordersBefore)} after=${JSON.stringify(ordersAfter)}`);

  // ── 10. reset clears the branch row back to orderable defaults ──
  await req('DELETE', `/api/branches/${branchA.id}/menu-items/${plain.id}`);
  const afterReset = findItem(await menuFor(branchA.id), 'Plain');
  check('resetting a branch row restores orderable defaults',
    afterReset && afterReset.soldOut === false, JSON.stringify(afterReset));

  // ── 11. retired item is absent from the management list? No - owners must see it ──
  await req('PUT', `/api/menu-items/${retired.id}`, {
    categoryId: cat.id, localizedNames: { en: 'Retired' }, basePrice: 5, isActive: false,
  });
  const mgmtList = (await req('GET', `/api/menu-items/category/${cat.id}`)).json;
  check('management list still includes retired items (owners need to find them to restore)',
    !!mgmtList.find(i => i.localizedNames.en === 'Retired' && i.isActive === false),
    'retired item vanished from the management list');

  // cleanup
  for (const id of created.items) await req('DELETE', `/api/menu-items/${id}`);
  for (const id of created.groups) await req('DELETE', `/api/modifier-groups/${id}`);
  for (const id of created.cats) await req('DELETE', `/api/categories/${id}`);

  let pass = 0, fail = 0;
  console.log('=========== AVAILABILITY SEMANTICS ===========');
  for (const r of results) {
    r.ok ? pass++ : fail++;
    console.log(`[${r.ok ? 'PASS' : 'FAIL'}] ${r.name}`);
    if (!r.ok) console.log(`        ${r.detail}`);
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
