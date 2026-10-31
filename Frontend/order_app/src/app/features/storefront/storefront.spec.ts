import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

import { StorefrontComponent } from './storefront';
import { PublicTableMenuResponse } from './storefront_model';
import { CartService } from './cart.service';

/**
 * The two states a scanned QR code can land in, rendered truthfully.
 *
 * The live-slug test is the whole Phase 3 promise in one assertion set:
 * business name, table number, and an availability-filtered item on screen.
 * The dead-slug test pins the not-found copy, which is a privacy contract
 * as much as UX: a dead sticker must never reveal which business, branch
 * or table it belonged to.
 */
describe('StorefrontComponent', () => {
  let httpMock: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<StorefrontComponent>>;

  // jsdom does not implement <dialog>.showModal()/close(); the Phase 4
  // sheets need both so the submit -> close -> re-attach flow can run.
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal ??= function () {};
    HTMLDialogElement.prototype.close ??= function () {};
  });

  const seededMenu: PublicTableMenuResponse = {
    businessName: 'Ember Restaurant',
    address: 'Mandalay',
    branchName: 'Ember Mandalay',
    tableNumber: 'T5',
    categories: [
      {
        id: 1,
        localizedNames: { en: 'Mains', mm: 'အဓိကစား' },
        items: [
          {
            id: 9,
            localizedNames: { en: 'Shan Noodles', mm: 'ရှမ်းခေါက်ဆွဲ' },
            localizedDescriptions: { en: 'With chicken and peanuts' },
            imageUrl: null,
            price: 3500,
            soldOut: false,
            modifierGroups: [],
          },
          {
            id: 10,
            localizedNames: { en: 'Mohinga' },
            localizedDescriptions: null,
            imageUrl: null,
            price: 2000,
            soldOut: true,
            modifierGroups: [],
          },
        ],
      },
    ],
  };

  function setup(slug: string | null): void {
    TestBed.configureTestingModule({
      imports: [StorefrontComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => slug } } },
        },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StorefrontComponent);
    // ngOnInit issues the public menu request, so it has to run first.
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('renders whose menu, which table, and the items for a live slug', () => {
    setup('Gkr2ybHlxj');

    httpMock
      .expectOne((req) => req.url === '/api/public/tables/Gkr2ybHlxj/menu', 'public menu request')
      .flush(seededMenu);

    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Ember Restaurant');
    expect(text).toContain('Ember Mandalay · Table T5');
    expect(text).toContain('Shan Noodles');
    expect(text).toContain('With chicken and peanuts');
    // English is picked first from the localized maps.
    expect(text).toContain('Mains');
    // A sold-out item stays visible but is marked honestly.
    expect(text).toContain('Mohinga');
    expect(text).toContain('Sold out');
  });

  it('shows the generic not-found state for a dead slug — and nothing about who it was', () => {
    setup('zzDeadCode');

    httpMock
      .expectOne((req) => req.url === '/api/public/tables/zzDeadCode/menu')
      .flush({ message: 'Menu not found' }, { status: 404, statusText: 'Not Found' });

    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Menu not found');
    expect(text).toContain('ask our staff');
    // The privacy contract: no tenant, branch or table names from the dead code.
    expect(text).not.toContain('Ember');
    expect(text).not.toContain('Brew');
  });

  it('distinguishes a network failure from a dead code, with a retry button', () => {
    setup('Gkr2ybHlxj');

    // status 0 = request never reached the server.
    httpMock
      .expectOne((req) => req.url === '/api/public/tables/Gkr2ybHlxj/menu')
      .flush(null, { status: 0, statusText: 'Offline' });

    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain("Can't reach the menu");
    expect(text).toContain('Try again');
    expect(text).not.toContain('Menu not found');
  });

  // ── Phase 4: the ordering flow ───────────────────────────────────

  const placedOrder = {
    id: 55,
    status: 'PENDING',
    orderType: 'DINE_IN',
    source: 'QR_CUSTOMER',
    tableNumber: 'T5',
    sessionCode: 'SESS-CODE-1',
    customerNote: null,
    customerPhoneNumber: null,
    invoiceNumber: null,
    subtotal: 3500,
    taxAmount: 0,
    discountAmount: 0,
    serviceCharge: 0,
    finalAmount: 3500,
    createdAt: '2026-10-28T10:00:00Z',
    items: [],
  };

  /** Loads the menu for `orderslug`, ready for cart interactions. */
  function setupOrderable(): InstanceType<typeof StorefrontComponent> {
    setup('orderslug');
    httpMock
      .expectOne((req) => req.url === '/api/public/tables/orderslug/menu')
      .flush(seededMenu);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('submits the cart with an idempotency key, then re-attaches by the returned session code', () => {
    const component = setupOrderable();
    const cart = fixture.debugElement.injector.get(CartService);

    cart.add(seededMenu.categories[0].items[0], []);   // Shan Noodles x1
    cart.add(seededMenu.categories[0].items[0], []);   // merges: quantity 2
    fixture.detectChanges();

    component.submitCart('birthday, one candle');

    const post = httpMock.expectOne(
      (req) => req.method === 'POST' && req.url === '/api/public/tables/orderslug/orders',
      'order submission');
    const body = post.request.body as {
      idempotencyKey: string;
      customerNote?: string;
      items: { menuItemId: number; quantity: number }[];
    };

    // The payload contract: a client-generated key, server-known ids,
    // no amounts anywhere.
    expect(body.idempotencyKey).toMatch(/^[\w-]{8,}$/);
    expect(body.customerNote).toBe('birthday, one candle');
    expect(body.items).toEqual([{ menuItemId: 9, quantity: 2, modifiers: undefined }]);

    // Placed: the response's sessionCode lands in slug-scoped storage and
    // the orders list is fetched with it.
    post.flush(placedOrder);

    const refetch = httpMock.expectOne(
      (req) => req.url === '/api/public/tables/orderslug/orders'
          && req.params.get('sessionCode') === 'SESS-CODE-1',
      'orders re-attach fetch');
    refetch.flush([placedOrder]);
    fixture.detectChanges();

    expect(localStorage.getItem('dos.session.orderslug')).toBe('SESS-CODE-1');
    expect(cart.lines().length).toBe(0);           // fresh cart after success
    expect(component.orders().length).toBe(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Your orders');
  });

  it('a sold-out rejection shows the kitchen\'s message and keeps the cart for editing', () => {
    const component = setupOrderable();
    const cart = fixture.debugElement.injector.get(CartService);
    cart.add(seededMenu.categories[0].items[0], []);
    fixture.detectChanges();

    component.submitCart('');

    const post = httpMock.expectOne(
      (req) => req.method === 'POST' && req.url === '/api/public/tables/orderslug/orders');
    post.flush(
      { message: "Your order was not placed: Shan Noodles is sold out right now. Please update your cart and try again." },
      { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(component.submitError()).toContain('sold out');
    expect(cart.lines().length).toBe(1);          // kept for the customer to fix
    expect(localStorage.getItem('dos.session.orderslug')).toBeNull();
  });
});
