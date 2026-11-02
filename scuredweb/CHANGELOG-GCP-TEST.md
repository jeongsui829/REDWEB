# REDWEB GCP Test Changes

Generated: 2026-07-24 KST

## Deployment-critical changes

- Replaced packaged JDBC credentials with JNDI-first configuration at `java:comp/env/jdbc/RedWebDB`.
- Kept direct JDBC only as an explicit local-development fallback through `REDWEB_DB_*` or `redweb.db.*` values.
- Marked Oracle JDBC as `provided`; Tomcat must load the driver from its shared `lib` directory.
- Added startup database validation and a session-free `/health` endpoint that returns only `UP` or `DOWN`.
- Added external upload-root configuration through `REDWEB_UPLOAD_ROOT` or `redweb.upload.root`.
- Added Tomcat Context, systemd, Oracle readiness, Nginx, and trusted-proxy examples under `deploy/gcp-test/conf`.

## Account and administration changes

- Revalidated the current DB account on protected requests and invalidated sessions for suspended, withdrawn, deleted, or demoted users.
- Kept expired-suspension release atomic and preserved indefinite suspensions.
- Rotated session IDs after login and administrator secondary verification.
- Recorded `LAST_LOGIN_AT` and `RW_USER_AUTH_LOG` in one transaction.
- Prevented self-suspension, suspension of the final active administrator, and suspension of the final active super administrator.
- Connected important administrator actions to `RW_ADMIN_ACTION_LOG` and distinguished action failure from audit failure.
- Added confirmation handling to administrator forms and loaded the existing common JavaScript on those pages.

## Data integrity and files

- Added post-commit physical-file cleanup for normal/admin post deletion and complete user deletion.
- Added normalized upload-root containment checks for attachment and profile cleanup.
- Distinguished successful attachment compensation from partial compensation failure.
- Completed user-reference cleanup using the actual Oracle FK set, including reactions on other users' posts.
- Removed production runtime sample/local fallbacks and separated DB failure from a normal empty result.
- Kept `BoardDAO.insertPost()` byte-for-byte unchanged from `scuredweb(9)`.

## Validation and UI

- Aligned server-side limits and JSP `maxlength` values for posts, comments, messages, notices, profiles, and registration fields.
- Rejected invalid board codes and malformed numeric IDs without silently truncating or rendering another resource.
- Kept board/home content rails non-sticky, limited mobile filters to two rows, and retained the collapsible mobile board menu.
- Restricted unauthenticated online-user responses to counts; names are available only to authenticated sessions.
- Added reusable Oracle, compensation, restart, functional, administrator, scroll, and packaging verification scripts.

## Removed runtime-only fallbacks

- `src/main/java/com/scuredweb/common/util/SampleDataFactory.java`
- `src/main/java/com/scuredweb/admin/service/AdminOpsService.java`

These classes were unused or represented local/sample behavior that must not participate in an external test deployment.
