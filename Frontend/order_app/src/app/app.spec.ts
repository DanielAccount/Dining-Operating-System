import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a router outlet, so the shell has no markup of its own', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    // The shell used to assert a "Hello, order_app" heading from the Angular
    // starter that this app replaced with <router-outlet>, so the test had been
    // failing and nobody ran it. It now asserts what the shell actually is.
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });
});
