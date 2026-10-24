import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

import { StorefrontComponent } from './storefront';
import { PublicTableMenuResponse } from './storefront_model';

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
});
