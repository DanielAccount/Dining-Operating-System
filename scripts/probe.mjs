// Probes specific suspected defects. Read-only probes where possible.
const BASE = process.env.BASE ?? 'http://localhost:8080';
let token = null;
const H = () => (token ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' });

async function req(method, path, body, extraHeaders = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: { ...H(), ...extraHeaders },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null; try { json = JSON.parse(text); } catch {}
  return { status: res.status, json, text };
}

const out = [];
const say = (n, ok, d) => out.push(`${ok ? 'OK  ' : 'BUG '} ${n}${d ? '\n       ' + d : ''}`);

const r = await req('POST', '/api/auth/login', { email: 'ember.owner@example.com', password: 'Password123!' });
token = r.json.accessToken;

const cat = (await req('POST', '/api/categories', { localizedNames: { en: 'Probe' } })).json;

// ── PROBE 1: create modifier group with minSelect omitted ──
const p1 = await req('POST', '/api/modifier-groups', {
  localizedNames: { en: 'NoMinSelect' }, isRequired: false, maxSelect: 1, options: [],
});
say('P1 create modifier group WITHOUT minSelect (minSelect is @Min but not @NotNull)',
  p1.status === 400, `HTTP ${p1.status} ${p1.text.slice(0, 200)}`);

// same, but isRequired true
const p1b = await req('POST', '/api/modifier-groups', {
  localizedNames: { en: 'NoMinSelect2' }, isRequired: true, maxSelect: 1, options: [],
});
say('P1b same with isRequired=true', p1b.status === 400, `HTTP ${p1b.status} ${p1b.text.slice(0, 200)}`);

// ── PROBE 2: do modifier option IDs survive an update? ──
const mg0 = await req('POST', '/api/modifier-groups', {
  localizedNames: { en: 'IdStability' }, isRequired: false, minSelect: 0, maxSelect: 2,
  options: [
    { localizedNames: { en: 'A' }, extraPrice: 1, isAvailable: true },
    { localizedNames: { en: 'B' }, extraPrice: 2, isAvailable: true },
  ],
});
if (!mg0.json?.options) {
  console.log(`could not create probe modifier group: HTTP ${mg0.status} ${mg0.text.slice(0,200)}`);
  process.exit(1);
}
const mg = mg0.json;
const idsBefore = mg.options.map(o => o.id).sort();
const mgUpd = (await req('PUT', `/api/modifier-groups/${mg.id}`, {
  localizedNames: { en: 'IdStability' }, isRequired: false, minSelect: 0, maxSelect: 2,
  options: [
    { id: idsBefore[0], localizedNames: { en: 'A' }, extraPrice: 1, isAvailable: true },
    { id: idsBefore[1], localizedNames: { en: 'B' }, extraPrice: 2, isAvailable: true },
  ],
})).json;
const idsAfter = (mgUpd.options || []).map(o => o.id).sort();
say('P2 modifier option IDs survive an update (client sends them back)',
  JSON.stringify(idsBefore) === JSON.stringify(idsAfter),
  `before=${JSON.stringify(idsBefore)} after=${JSON.stringify(idsAfter)}`);

// NOTE: the old P3 ("is an out-of-stock item still offered?") is gone.
// Item-level isInStock was removed in V10 - stock is now a per-branch fact on
// branch_menu_items, and the full rule (retired / not offered / sold out) is
// covered by scripts/availability-smoke.mjs. A probe here would only have
// reported a vacuous PASS once the field disappeared from MenuItemResponse.

const item = (await req('POST', '/api/menu-items', {
  categoryId: cat.id, localizedNames: { en: 'SoldOut' }, basePrice: 5, isActive: true,
})).json;

const branches = (await req('GET', '/api/branches')).json;

// ── PROBE 4: CORS must allow the frontend origin and refuse everyone else ──
// app.cors.allowed-origins is unset locally, so CorsConfig falls back to
// loopback-only on purpose: a wildcard would let any site issue authenticated
// cross-origin requests against a token in localStorage. A production
// deployment sets APP_CORS_ALLOWED_ORIGINS to its real origin.
const preflight = async (origin) => {
  const res = await fetch(`${BASE}/api/categories`, {
    method: 'OPTIONS',
    headers: {
      Origin: origin,
      'Access-Control-Request-Method': 'GET',
      'Access-Control-Request-Headers': 'authorization',
    },
  });
  return { status: res.status, allow: res.headers.get('access-control-allow-origin') };
};

const foreign = await preflight('https://app.example.com');
const local = await preflight('http://localhost:4200');

say('P4a CORS refuses an unlisted origin', foreign.status === 403 && !foreign.allow,
  `HTTP ${foreign.status}, Access-Control-Allow-Origin=${foreign.allow}`);
say('P4b CORS allows the Angular dev server (loopback)', local.status === 200 && !!local.allow,
  `HTTP ${local.status}, Access-Control-Allow-Origin=${local.allow}`);

// ── PROBE 5: does deleting a menu item leave orphan branch overrides? ──
await req('PUT', `/api/branches/${branches[0].id}/menu-items/${item.id}`, { priceOverride: 99, isAvailable: true });
await req('DELETE', `/api/menu-items/${item.id}`);
const afterDel = (await req('GET', `/api/branches/${branches[0].id}/menu-items`)).json;
say('P5 branch overrides for a deleted menu item are cleaned up',
  !afterDel.some(x => x.menuItemId === item.id), `grid rows now: ${afterDel.length}`);

// ── PROBE 6: soft-delete growth — is reset() reversible/idempotent? ──
const item2 = (await req('POST', '/api/menu-items', {
  categoryId: cat.id, localizedNames: { en: 'ResetTwice' }, basePrice: 7,
})).json;
const r1 = await req('PUT', `/api/branches/${branches[0].id}/menu-items/${item2.id}`, { priceOverride: 8, isAvailable: true });
const d1 = await req('DELETE', `/api/branches/${branches[0].id}/menu-items/${item2.id}`);
const d2 = await req('DELETE', `/api/branches/${branches[0].id}/menu-items/${item2.id}`);
say('P6 reset is idempotent: a second reset is still 204', d2.status === 204,
  `first=${d1.status} second=${d2.status} ${d2.text.slice(0, 120)}`);

const r2 = await req('PUT', `/api/branches/${branches[0].id}/menu-items/${item2.id}`, { priceOverride: 9, isAvailable: true });
say('P6b re-upserting after reset works (no unique-index clash from soft-deleted row)',
  r2.status === 200, `HTTP ${r2.status} ${r2.text.slice(0, 160)}`);

// ── PROBE 7: does a menu item have any ordering field? ──
say('P7 MenuItemResponse exposes a display/ordering field',
  'displayOrder' in item2 || 'sortOrder' in item2 || 'position' in item2,
  `fields: ${JSON.stringify(Object.keys(item2))}`);

// ── PROBE 8: modifier group isActive writable? ──
const mgAct = await req('PUT', `/api/modifier-groups/${mg.id}`, {
  localizedNames: { en: 'IdStability' }, isRequired: false, minSelect: 0, maxSelect: 2,
  isActive: false, options: [],
});
say('P8 modifier group isActive can be set to false via the API',
  mgAct.json?.isActive === false, `HTTP ${mgAct.status} isActive=${mgAct.json?.isActive}`);

// ── PROBE 9: 500 handler must not leak internals ──
const bad = await req('GET', '/api/menu-items/abc');
say('P9 non-numeric id yields a clean 400, not a stack trace',
  bad.status === 400 && !bad.text.includes('Exception'), `HTTP ${bad.status} ${bad.text.slice(0, 140)}`);

// ── PROBE 10: wrong Content-Type should be 415, not 500 ──
const ct = await fetch(`${BASE}/api/categories`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'text/plain' }, body: 'hello' });
const ctText = await ct.text();
say('P10 unsupported Content-Type returns 415 (not an opaque 500)', ct.status === 415,
  `HTTP ${ct.status} ${ctText.slice(0, 120)}`);

console.log(out.join('\n'));
