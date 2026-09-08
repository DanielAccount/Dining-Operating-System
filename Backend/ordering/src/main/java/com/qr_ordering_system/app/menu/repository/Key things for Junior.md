### Key things to understand as a junior dev

1. Tenant + soft-delete are invisible here. @TenantId and @SoftDelete in your base classes inject those predicates
   automatically. This is better than writing them by hand — it's impossible to forget. (Your UserRepository bypasses
   this with a native query; that's fine for login since there's no tenant context yet.)
2. Optional<T> for single lookups, List<T> for collections — never return null from a repo.
3. left join fetch queries exist because MenuItem → MenuItemModifierGroup → ModifierGroup would otherwise be 3+ queries
   per item (N+1 problem). One query = predictable performance.
4. existsBy... methods generate select case when count(*) > 0... — cheap way to enforce uniqueness/conflict rules in
   the service layer (Step 4) before relying on the DB unique index, so you return a friendly 409 instead of an ugly
    "500".
5. One thing to watch: findAllByModifierGroupId etc. rely on @TenantId filtering — fine in a web request with a
   logged-in user, but in the seeder or async jobs you must set the tenant context first.