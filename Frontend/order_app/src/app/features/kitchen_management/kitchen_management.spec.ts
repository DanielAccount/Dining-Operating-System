import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../core/auth/auth.service';
import { BranchContextService } from '../../core/branch/branch-context.service';
import { KitchenManagement } from './kitchen_management';
import { KitchenItem } from './kitchen.models';

/**
 * The Phase 5 kitchen board's two most important promises:
 * an accepted line is visible with its production instructions, and a chef
 * can advance that line through the API-backed action button.
 */
describe('KitchenManagement', () => {
  let httpMock: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<KitchenManagement>>;

  const line: KitchenItem = {
    id: 41,
    orderId: 17,
    invoiceNumber: 'INV-000017',
    tableNumber: 'T4',
    orderType: 'DINE_IN',
    source: 'QR_CUSTOMER',
    customerNote: 'No onions',
    orderCreatedAt: '2026-11-05T06:00:00Z',
    itemNameSnapshot: { en: 'Burger' },
    quantity: 2,
    itemStatus: 'PENDING',
    orderStatus: 'ACCEPTED',
    modifiers: [{ id: 8, name: 'Extra cheese' }],
    itemCreatedAt: '2026-11-05T06:00:00Z',
    readyAt: null,
    servedAt: null,
  };

  function setup(canUpdate = true): void {
    TestBed.configureTestingModule({
      imports: [KitchenManagement],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: BranchContextService,
          useValue: {
            branches: signal([{ id: 1, name: 'Main Kitchen', status: 'ACTIVE' }]),
            selectedBranchId: signal<number | null>(1),
            isTenantWide: signal(false),
            load: () => undefined,
            select: () => undefined,
          },
        },
        {
          provide: AuthService,
          useValue: {
            hasPermission: (permission: string) =>
              canUpdate && permission === 'KDS_UPDATE_ITEM_STATUS',
          },
        },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(KitchenManagement);
    fixture.detectChanges();
  }

  afterEach(() => {
    fixture.destroy();
    httpMock.verify();
  });

  it('shows an accepted ticket with its item, quantity, note and modifiers', () => {
    setup();

    const request = httpMock.expectOne('/api/branches/1/kds');
    expect(request.request.method).toBe('GET');
    request.flush([line]);

    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Kitchen Display');
    expect(text).toContain('INV-000017');
    expect(text).toContain('T4');
    expect(text).toContain('Burger');
    expect(text).toContain('2×');
    expect(text).toContain('Extra cheese');
    expect(text).toContain('No onions');
    expect(text).toContain('Start cooking');
  });

  it('posts the preparing action and refreshes the board afterwards', () => {
    setup();

    httpMock.expectOne('/api/branches/1/kds').flush([line]);
    fixture.detectChanges();

    const startButton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((button) => button.textContent?.includes('Start cooking'));

    expect(startButton).toBeDefined();
    startButton!.click();
    fixture.detectChanges();

    const action = httpMock.expectOne(
      '/api/branches/1/kds/items/41/preparing',
    );
    expect(action.request.method).toBe('POST');
    action.flush({ ...line, itemStatus: 'PREPARING', orderStatus: 'PREPARING' });

    // The action response updates the line immediately, then the component
    // reconciles with the server.
    httpMock.expectOne('/api/branches/1/kds').flush([]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain(
      'Start cooking',
    );
  });

  it('keeps the board read-only for a user who can view but not update it', () => {
    setup(false);

    httpMock.expectOne('/api/branches/1/kds').flush([line]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Burger');
    expect(text).not.toContain('Start cooking');
  });
});
