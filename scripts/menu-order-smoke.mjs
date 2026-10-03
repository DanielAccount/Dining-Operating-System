// Verifies menu item display ordering: insert, move, category change, delete.
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
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
}

const stamp = Date.now().toString().slice(-6);

const catB_unused = null; void catB_unused;

const mk = (categoryId, name, displayOrder) => req('POST', '/api/menu-items', {
  categoryId, localizedNames: { en: name }, basePrice: 1, ...(displayOrder !== undefined ? { displayOrder } : {}),
});

const inCat = async (categoryId) =>
  (await req('GET', `/api/menu-items/category/${categoryId}`)).json.map(i => `${i.localizedNames.en}:${i.displayOrder}`);

(async () => {
  const login = await req('POST', '/api/auth/login', { email: 'ember.owner@example.com', password: 'Password123!' });
  token = login.json?.accessToken;
  if (!token) { console.log('login failed', login.text.slice(0,200)); process.exit(1); }

  const catA = (await req('POST', '/api/categories', { localizedNames: { en: `OrdA${stamp}` } })).json;
  const catB = (await req('POST', '/api/categories', { localizedNames: { en: `OrdB${stamp}` } })).json;

  // 1. create with no position -> appended
  await mk(catA.id, 'A1');
  await mk(catA.id, 'A2');
  await mk(catA.id, 'A3');
  check('3 creates with no displayOrder append in order 1,2,3',
    JSON.stringify(await inCat(catA.id)) === JSON.stringify(['A1:1', 'A2:2', 'A3:3']),
    JSON.stringify(await inCat(catA.id)));

  // 2. insert at position 1 pushes the rest down
  await mk(catA.id, 'A0', 1);
  check('insert at position 1 shifts the others down',
    JSON.stringify(await inCat(catA.id)) === JSON.stringify(['A0:1', 'A1:2', 'A2:3', 'A3:4']),
    JSON.stringify(await inCat(catA.id)));

  // 3. insert in the middle
  await mk(catA.id, 'A15', 3);
  check('insert at middle position 3 lands between A1 and A2',
    JSON.stringify(await inCat(catA.id)) === JSON.stringify(['A0:1', 'A1:2', 'A15:3', 'A2:4', 'A3:5']),
    JSON.stringify(await inCat(catA.id)));

  // 4. out-of-range clamps to the end
  const big = (await mk(catA.id, 'ABig', 999)).json;
  check('insert at 999 clamps to last position',
    big.displayOrder === 6, `got ${big.displayOrder}`);

  // 5. move an existing item to the top
  const a2 = (await req('GET', `/api/menu-items/category/${catA.id}`)).json.find(i => i.localizedNames.en === 'A2');
  await req('PUT', `/api/menu-items/${a2.id}`, {
    categoryId: catA.id, localizedNames: { en: 'A2' }, basePrice: 1, displayOrder: 1,
  });
  check('move existing item to position 1',
    JSON.stringify(await inCat(catA.id)) === JSON.stringify(['A2:1', 'A0:2', 'A1:3', 'A15:4', 'A3:5', 'ABig:6']),
    JSON.stringify(await inCat(catA.id)));

  // 6. move to the end
  const a0 = (await req('GET', `/api/menu-items/category/${catA.id}`)).json.find(i => i.localizedNames.en === 'A0');
  await req('PUT', `/api/menu-items/${a0.id}`, {
    categoryId: catA.id, localizedNames: { en: 'A0' }, basePrice: 1, displayOrder: 6,
  });
  check('move existing item to the last position',
    JSON.stringify(await inCat(catA.id)) === JSON.stringify(['A2:1', 'A1:2', 'A15:3', 'A3:4', 'ABig:5', 'A0:6']),
    JSON.stringify(await inCat(catA.id)));

  // 7. update WITHOUT displayOrder must not move anything (compare positions,
  //    not names - the item is being renamed in the same request)
  const before = JSON.stringify((await inCat(catA.id)).map(s => s.split(':')[1]));
  const a1 = (await req('GET', `/api/menu-items/category/${catA.id}`)).json.find(i => i.localizedNames.en === 'A1');
  const renamed = (await req('PUT', `/api/menu-items/${a1.id}`, {
    categoryId: catA.id, localizedNames: { en: 'A1-renamed' }, basePrice: 2,
  })).json;
  const afterOrders = JSON.stringify((await inCat(catA.id)).map(s => s.split(':')[1]));
  check('omitting displayOrder on update leaves position untouched',
    afterOrders === before && renamed.displayOrder === 2,
    `before=${before} after=${afterOrders} got=${renamed.displayOrder}`);

  // 8. move to another category: old compacted, new appended
  const a15 = (await req('GET', `/api/menu-items/category/${catA.id}`)).json.find(i => i.localizedNames.en === 'A15');
  await mk(catB.id, 'B1');
  await mk(catB.id, 'B2');
  const moved = (await req('PUT', `/api/menu-items/${a15.id}`, {
    categoryId: catB.id, localizedNames: { en: 'A15' }, basePrice: 1,
  })).json;
  check('move to another category appends to the new one',
    moved.displayOrder === 3, `got ${moved.displayOrder}`);
  check('the category it left is compacted with no gap',
    JSON.stringify(await inCat(catA.id)) === JSON.stringify(['A2:1', 'A1-renamed:2', 'A3:3', 'ABig:4', 'A0:5']),
    JSON.stringify(await inCat(catA.id)));
  check('the new category has no duplicate positions',
    JSON.stringify(await inCat(catB.id)) === JSON.stringify(['B1:1', 'B2:2', 'A15:3']),
    JSON.stringify(await inCat(catB.id)));

  // 9. move to an EMPTY category
  const a3 = (await req('GET', `/api/menu-items/category/${catA.id}`)).json.find(i => i.localizedNames.en === 'A3');
  const catC = (await req('POST', '/api/categories', { localizedNames: { en: `OrdC${stamp}` } })).json;
  const intoEmpty = (await req('PUT', `/api/menu-items/${a3.id}`, {
    categoryId: catC.id, localizedNames: { en: 'A3' }, basePrice: 1, displayOrder: 5,
  })).json;
  check('move into an empty category lands at position 1, not 5',
    intoEmpty.displayOrder === 1, `got ${intoEmpty.displayOrder}`);

  // 10. delete compacts the remaining positions
  const catD = (await req('POST', '/api/categories', { localizedNames: { en: `OrdD${stamp}` } })).json;
  const d1 = (await mk(catD.id, 'D1')).json, d2 = (await mk(catD.id, 'D2')).json, d3 = (await mk(catD.id, 'D3')).json;
  await req('DELETE', `/api/menu-items/${d1.id}`);
  check('deleting the first item compacts the rest',
    JSON.stringify(await inCat(catD.id)) === JSON.stringify(['D2:1', 'D3:2']),
    JSON.stringify(await inCat(catD.id)));

  const d4 = (await mk(catD.id, 'D4')).json;
  await req('DELETE', `/api/menu-items/${d2.id}`);
  check('deleting a middle item compacts the rest',
    JSON.stringify(await inCat(catD.id)) === JSON.stringify(['D3:1', 'D4:2']),
    JSON.stringify(await inCat(catD.id)));
  void d3; void d4;

  // 11. full list is ordered by (category.displayOrder, item.displayOrder).
  //     The ordering key is the category's displayOrder, NOT its id.
  const all = (await req('GET', '/api/menu-items')).json;
  const monotonic = (() => {
    let prevCatOrder = -1, prevItemOrder = 0, ok = true;
    for (const i of all) {
      const c = i.category.displayOrder;
      if (c < prevCatOrder) { ok = false; break; }
      if (c === prevCatOrder && i.displayOrder < prevItemOrder) { ok = false; break; }
      if (c !== prevCatOrder) prevItemOrder = 0;
      prevCatOrder = c; prevItemOrder = i.displayOrder;
    }
    return ok;
  })();
  check('GET /api/menu-items is ordered by category then position', monotonic,
    all.map(i => `cat${i.category.displayOrder}#${i.displayOrder}`).join(' '));

  // 12. displayOrder = 0 is rejected
  const zero = await req('POST', '/api/menu-items', {
    categoryId: catD.id, localizedNames: { en: 'Zero' }, basePrice: 1, displayOrder: 0,
  });
  check('displayOrder 0 is rejected with 400', zero.status === 400, `HTTP ${zero.status}`);

  // cleanup
  for (const c of [catA, catB, catC, catD]) {
    const items = (await req('GET', `/api/menu-items/category/${c.id}`)).json;
    for (const i of items) await req('DELETE', `/api/menu-items/${i.id}`);
    await req('DELETE', `/api/categories/${c.id}`);
  }

  let pass = 0, fail = 0;
  console.log('=========== MENU ITEM ORDERING ===========');
  for (const r of results) {
    r.ok ? pass++ : fail++;
    console.log(`[${r.ok ? 'PASS' : 'FAIL'}] ${r.name}`);
    if (!r.ok) console.log(`        ${r.detail}`);
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
