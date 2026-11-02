# RedWeb DB Outline

## Core domains

- Member and auth: `RW_USER`, `RW_USER_AUTH_LOG`, `RW_NOTIFICATION`
- Boards and content: `RW_BOARD`, `RW_CATEGORY`, `RW_POST`, `RW_COMMENT`, `RW_TAG`, `RW_POST_TAG`, `RW_ATTACHMENT`
- Operations and logs: `RW_REPORT`, `RW_NOTICE`, `RW_ACCESS_LOG`, `RW_ERROR_LOG`, `RW_RECENT_VIEW`
- Admin and analytics: `RW_ADMIN_ROLE`, `RW_ADMIN_USER_ROLE`, `RW_ADMIN_ACTION_LOG`, `RW_DAILY_STATS`

## Initial deployment order

1. Run `redweb-schema.sql`
2. Create Oracle user and grant table/sequence privileges if needed
3. Run `redweb-seed.sql`
4. Update `src/main/resources/properties/jdbc.properties` with the local Oracle connection

## Notes for later secure version

- `PASSWORD_HASH` is intentionally left as a generic field name so later versions can swap in stronger password storage without changing most of the schema.
- Access and error logs are separated to make pre-hardening and post-hardening comparisons easier.
- Role and admin role mappings are split so operational privilege separation can be tightened later.
