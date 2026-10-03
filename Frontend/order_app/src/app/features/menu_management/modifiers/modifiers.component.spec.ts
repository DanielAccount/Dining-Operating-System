import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { TestRequest } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { ModifiersComponent } from './modifiers.component';
import { ModifierGroup, ModifierGroupCreateRequest } from '../models/modifiers_models';

/**
 * The regression that mattered most in this screen.
 *
 * The edit form used to rebuild its option rows without their ids, so saving a
 * modifier group sent every option as "new". The backend then created fresh
 * rows and deleted the originals: option ids changed under the customer's feet,
 * and any order line referencing one (Phase 4) would be orphaned. The backend
 * was already written to defend against exactly this, so the bug was entirely
 * in the form - and invisible, because the save returned 200 and the screen
 * looked right.
 *
 * These tests assert the payload, which is the only place the bug lived.
 */
describe('ModifiersComponent - option identity on save', () => {
  let httpMock: HttpTestingController;

  // jsdom does not implement <dialog>.showModal(); stub it so the component's
  // open/close paths run.
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal ??= function () {};
    HTMLDialogElement.prototype.close ??= function () {};
  });

  const existingGroup: ModifierGroup = {
    id: 42,
    localizedNames: { en: 'Spiciness', mm: 'အစပ်အဆာ' },
    isRequired: true,
    minSelect: 1,
    maxSelect: 2,
    isActive: true,
    options: [
      { id: 100, localizedNames: { en: 'Mild' }, extraPrice: 0, isAvailable: true },
      { id: 101, localizedNames: { en: 'Hot' }, extraPrice: 500, isAvailable: false },
    ],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  function setup(): ModifiersComponent {
    TestBed.configureTestingModule({
      imports: [ModifiersComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ModifiersComponent);
    // ngOnInit issues the initial list request, so it has to run first.
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  /** Flushes the list request ngOnInit makes. */
  function flushInitialList(groups: ModifierGroup[] = []): void {
    httpMock.expectOne((r) => r.url.endsWith('/api/modifier-groups') && r.method === 'GET')
      .flush(groups);
  }

  function editAndSave(component: ModifiersComponent, group: ModifierGroup): ModifierGroupCreateRequest {
    component.openEditModal(group);

    const control = (index: number, name: string) =>
      component.optionsArray.at(index).get(name)!;

    // Rename one option and re-price it, exactly as a user would.
    control(0, 'englishName').setValue('Extra Hot');
    control(0, 'extraPrice').setValue(750);
    // Option 2 stays untouched and unavailable.
    control(1, 'isAvailable').setValue(false);

    component.saveModifier();

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/modifier-groups/42') && r.method === 'PUT');
    const payload = req.request.body as ModifierGroupCreateRequest;
    completeSave(req);
    return payload;
  }

  afterEach(() => httpMock.verify());

  it('sends the existing option ids back so the rows are updated in place', () => {
    const component = setup();
    flushInitialList();

    const payload = editAndSave(component, existingGroup);

    expect(payload.options.map((o) => o.id)).toEqual([100, 101]);
  });

  it('keeps an option marked unavailable unavailable', () => {
    const component = setup();
    flushInitialList();

    const payload = editAndSave(component, existingGroup);

    expect(payload.options[1].isAvailable).toBe(false);
  });

  it('sends no id for a newly added option, so only that one is created', () => {
    const component = setup();
    flushInitialList();

    component.openEditModal(existingGroup);
    component.addOptionRow();
    component.optionsArray.at(2).get('englishName')!.setValue('Volcanic');
    component.optionsArray.at(2).get('extraPrice')!.setValue(1000);

    component.saveModifier();

    const req = httpMock.expectOne((r) => r.method === 'PUT');
    const payload = req.request.body as ModifierGroupCreateRequest;
    completeSave(req);

    expect(payload.options.map((o) => o.id)).toEqual([100, 101, undefined]);
  });

  it('never sends isActive, so saving cannot silently un-retire a group', () => {
    const component = setup();
    flushInitialList();

    const payload = editAndSave(component, { ...existingGroup, isActive: false });

    // null on the request means "leave it alone" to ModifierGroupService.
    expect(payload.isActive).toBeUndefined();
  });

  it('omits a blank translation instead of sending an empty string', () => {
    const component = setup();
    flushInitialList();

    component.openEditModal(existingGroup);
    component.optionsArray.at(0).get('myanmarName')!.setValue('');
    component.saveModifier();

    const req = httpMock.expectOne((r) => r.method === 'PUT');
    const payload = req.request.body as ModifierGroupCreateRequest;
    completeSave(req);

    expect(payload.options[0].localizedNames).toEqual({ en: 'Mild' });
  });

  /** Completes a save: flush the PUT, then the list reload the component fires. */
  function completeSave(request: TestRequest): void {
    request.flush(existingGroup);
    httpMock.expectOne((r) => r.method === 'GET').flush([]);
  }
});
