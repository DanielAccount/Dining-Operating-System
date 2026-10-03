import { HttpClient } from '@angular/common/http';
import { computed, effect, inject, Injectable, signal } from '@angular/core';

import { environment } from '../../../environment/environment';
import { AuthService } from '../auth/auth.service';
import { BranchSummary } from './branch.model';
import { extractErrorMessage } from '../../shared/http/error-message';

const STORAGE_KEY = 'dining_active_branch_id';

/**
 * Which branch the app is currently working in.
 *
 * The backend has two kinds of account and they cannot be treated the same way:
 *
 *  - branch-scoped staff (manager, cashier, waiter, chef) carry a branchId in
 *    their JWT. The server ignores any branchId in the URL for them and acts on
 *    their own branch, so the UI must not pretend they can switch.
 *  - tenant-wide users (owner) carry no branch and must name one explicitly.
 *
 * The sidebar picker used to be hardcoded markup - two fictional branches,
 * wired to nothing - which showed the wrong branch for every real tenant. This
 * service is the single source of truth: the picker writes to it, and the
 * screens that operate on a branch (branch pricing today, tables and reports
 * later) read from it rather than keeping their own copy.
 */
@Injectable({ providedIn: 'root' })
export class BranchContextService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly branchesState = signal<BranchSummary[]>([]);
  private readonly selectedState = signal<number | null>(null);

  /** Load attempted. Prevents a second request from every screen that mounts. */
  private loaded = false;
  private readonly loadingState = signal(false);
  readonly loading = this.loadingState.asReadonly();

  readonly branches = this.branchesState.asReadonly();
  readonly selectedBranchId = this.selectedState.asReadonly();

  /** Set when the branch list could not be loaded, so the picker can say why. */
  loadStateError: string | null = null;

  /** True for an owner: no branch in their token, so they choose one. */
  readonly isTenantWide = computed(() => this.auth.currentUser()?.branch == null);

  readonly selectedBranch = computed(
    () => this.branchesState().find((b) => b.id === this.selectedState()) ?? null,
  );

  /**
   * True only when there is a genuine choice to offer.
   *
   * A tenant-wide user who holds BRANCH_VIEW and whose tenant actually has two
   * or more branches. Everything else gets a plain label instead of a picker:
   *  - branch-scoped staff are pinned to the branch in their token, so a
   *    dropdown would be a lie the server ignores anyway;
   *  - a single-branch tenant has nothing to switch to, and a one-option
   *    <select> still reads as a dropdown to the user.
   */
  readonly hasBranchChoice = computed(
    () => this.isTenantWide()
      && this.auth.hasPermission('BRANCH_VIEW')
      && this.branchesState().length > 1,
  );

  /**
   * The branch list is fetched once per signed-in user, not once per page.
   *
   * Without this the one-shot `loaded` flag survived a logout: the service is a
   * root singleton, so logging in as tenant B after tenant A in the same tab
   * kept tenant A's branches on screen ("Ember owner sees Brew Cafe Main").
   *
   * Watching the user (rather than having AuthService call reset) also covers
   * every entry path for free: fresh login, logout then a different account,
   * and restoreSession() after a refresh. Injecting this service into
   * AuthService instead would be a construction-time DI cycle, since this
   * service already injects AuthService.
   */
  private readonly userChange = effect(() => {
    const user = this.auth.currentUser(); // tracked: the effect re-runs on change

    // Read before reset(): the remembered selection has to survive a page
    // refresh (where this effect fires for the same user) but must never be
    // handed to a different account. Branch ids are globally unique and load()
    // re-validates the id against the new user's list on arrival, so carrying
    // it across a login is safe.
    const remembered = this.readStored();

    this.reset();
    this.rememberedId = remembered;

    if (user) this.load();
  });

  /** Stored id considered by the in-flight load; see {@link userChange}. */
  private rememberedId: number | null = null;

  /**
   * Drops every branch fact this service is holding. The stored selection is
   * left alone deliberately - it belongs to the browser, not to a session, and
   * load() validates it against whichever user's branches actually arrive.
   */
  reset(): void {
    this.branchesState.set([]);
    this.selectedState.set(null);
    this.loadingState.set(false);
    this.loadStateError = null;
    this.loaded = false;
    this.rememberedId = null;
  }

  /**
   * Loads the branch list once per session. Safe to call from every screen -
   * subsequent calls are ignored until {@link reload} is used.
   *
   * Normally the {@link userChange} effect drives this; screens may still call
   * it, since repeated calls are ignored. If the session has not been restored
   * yet this returns without marking itself loaded, and the effect retries as
   * soon as the user arrives.
   */
  load(): void {
    if (this.loaded) return;

    this.loadStateError = null;

    const user = this.auth.currentUser();
    if (!user) return; // session not restored yet; the user effect retries

    if (user.branch) {
      // Locked to their own branch - no list to fetch, no choice to offer.
      this.branchesState.set([
        { id: user.branch.id, name: user.branch.name, status: user.branch.status },
      ]);
      this.selectedState.set(user.branch.id);
      this.loaded = true;
      return;
    }

    if (!this.auth.hasPermission('BRANCH_VIEW')) {
      // A tenant-wide account without BRANCH_VIEW cannot list branches. Leave the
      // picker empty rather than firing a request that is guaranteed to 403.
      this.loaded = true;
      return;
    }

    this.loadingState.set(true);
    this.http.get<BranchSummary[]>(`${environment.apiUrl}/branches`).subscribe({
      next: (data) => {
        this.branchesState.set(data);

        const stored = this.rememberedId ?? this.readStored();
        const valid = data.some((b) => b.id === stored);
        this.selectedState.set(valid ? stored : (data[0]?.id ?? null));
        this.persist(this.selectedState());

        this.loadingState.set(false);
        this.loaded = true;
      },
      error: (err) => {
        console.error('Failed to load branches', err);
        this.loadStateError = extractErrorMessage(err, 'Failed to load branches.');
        this.loadingState.set(false);
        this.loaded = true;
      },
    });
  }

  /**
   * Selects the active branch. Ignored for branch-scoped staff, whose token
   * decides the branch regardless of what the UI asks for.
   */  select(branchId: number | null): void {
    const userBranch = this.auth.currentUser()?.branch;
    if (userBranch) {
      this.selectedState.set(userBranch.id);
      return;
    }

    if (branchId !== null && !this.branchesState().some((b) => b.id === branchId)) {
      return; // not one of ours
    }

    this.selectedState.set(branchId);
    this.persist(branchId);
  }

  private readStored(): number | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw === null ? NaN : Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private persist(branchId: number | null): void {
    if (branchId === null) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, String(branchId));
    }
  }
}
