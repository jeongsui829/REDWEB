# REDWEB GCP Test Verification

Generated: 2026-07-24 KST

## Final verdict

**Conditionally ready for a restricted external test.** The current source and WAR passed local Windows, JDK 25, Tomcat 11.0.24, and Oracle XE integration checks. Ubuntu 24.04, Oracle Free `FREEPDB1`, systemd, GCP IAM/IAP, Nginx, and public HTTPS were not available and must be checked on the VM before access is shared.

## Build and verifier results

- `mvn clean package`: `BUILD SUCCESS`; Java 25.0.2 LTS; 82 main source files compiled.
- Maven test result: no test sources and `No tests to run`.
- Latest WAR build: 2026-07-24 13:25 KST. Final artifact hashes are recorded in the completion report.
- `node --check`: all 10 application and verification JavaScript files passed.
- `AdminFilterVerifier`: passed missing DB user, ADMIN+SUSPENDED, SUPER_ADMIN+WITHDRAWN, ADMIN+ACTIVE, database exception, and ADMIN-to-MEMBER cases. Each database-backed case asserted one lookup.
- `OnlineUserTrackerVerifier`: passed.
- WAR inspection: no Servlet API, OJDBC, development seed SQL, `.git`, `target`, or populated JDBC properties.
- Packaging inspection: all three artifacts passed credential-assignment and forbidden-path checks; the GCP ZIP contains no seed, result, log, screenshot, `target`, `dist`, or `.git` entry. All 43 webapp source files matched the WAR.

## Final WAR smoke

The runtime evidence below was produced before the v12 packaging and missing-admin session-only change. v12 was rebuilt and passed its Java verifiers, but Tomcat and Oracle were not restarted for this packaging-only verification.

The earlier `target/final-war-smoke.log` was a failed configuration attempt, not success evidence. The local Tomcat had no `conf/Catalina/localhost/scuredweb.xml`; the web resource reference therefore resolved to an unconfigured DataSource and logged `class ''`, URL `null`, `No suitable driver`, and `DBConfig.validateConfiguration` failure. OJDBC itself was present in Tomcat shared `lib`.

The final recheck used a newly copied Tomcat directory, installed a complete local JNDI Context outside the source tree, kept OJDBC in shared `lib`, passed no `REDWEB_DB_*` values to Tomcat, and deployed the newly built WAR.

- Deployment descriptor completed and Tomcat started.
- `/scuredweb/health`: `200`, `{"status":"UP"}`.
- `/scuredweb/login` and `/scuredweb/register`: `200`.
- Anonymous `/scuredweb/admin/dashboard`: `302` to `/scuredweb/login`.
- Authenticated MEMBER `/scuredweb/admin/dashboard`: `403`.
- Login POST: `302`, final `/home` GET `200`; protected board-write GET `200`.
- Login cookie on local HTTP: `HttpOnly`, `SameSite=Lax`, and no `Secure`, as required for the IAP HTTP mode.
- Plain post POST: `302`, detail GET `200`, post ID `136`, direct DB probe `POST_COUNT=1`.
- Attachment post ID `137`; download returned `200`.
- Result: `deploy/gcp-test/results/final-war-smoke-success.json`, `success=true`.
- The old null-JNDI, no-driver, and configuration-failure messages were absent from the successful log.
- Tomcat shutdown still reported Oracle JDBC background-thread cleanup warnings. This is a residual issue, not hidden by the smoke result.

## Windows Oracle XE evidence

These results are from local Oracle XE `XEPDB1`, not Oracle Free `FREEPDB1` and not GCP.

- Final smoke sequences before: attachment `44`, post `136`, user `1094`.
- Final smoke sequences after: attachment `46`, post `138`, user `1095`.
- The final smoke created and directly queried `RW_USER`, `RW_POST`, and `RW_ATTACHMENT` data; post and attachment cleanup plus account withdrawal completed.
- Invalid phone registration returned to registration and `USER_COUNT=0`; registration without a phone persisted `PHONE_IS_NULL=1`.
- Earlier additive sequence repair advanced only unsafe sequences and a second run changed nothing.
- `AuthSuspensionVerifier`, `SocialFailureVerifier`, `ReportResolutionVerifier`, and `HardDeleteVerifier` passed, including forced failure and rollback assertions.
- Actual `RW_USER` foreign-key metadata was queried before hard-delete verification.

## Restart and compensation evidence

- Before restart, post `133` and one attachment row existed, download returned `200`, and one physical file existed.
- After restart, detail and download returned `200`; the same DB row and file remained. Exact cleanup then produced `POST_COUNT=0` and removed the file.
- Forced attachment DB failure with successful compensation left `POST_COUNT=0` and no new file.
- Forced compensation-delete failure showed the administrator-check warning and retained the expected partial row until exact cleanup.

## Administrator and UI evidence

- A real local administrator account completed login and secondary verification before the current AdminFilter-only change.
- Dashboard, users, posts, reports, and notices returned `200` at 360, 768, 1280, and 1440 by 900.
- Direct administrator SQL bootstrap and the browser result are recorded in `admin-bootstrap-windows-xe.json` and `admin-ui-windows-xe.json`.
- Playwright checked 34 page/viewport combinations. Maximum horizontal overflow, content-rail sticky/fixed count, overlap count, console errors, and failed assets were all zero.
- Mobile header heights were 144px at 360 and 146px at 768; board filters stayed within two rows.
- `/favicon.svg` returned `200 image/svg+xml`; `/favicon.ico` and an unknown route returned accurate `404` responses.
- Online-user browser verification passed login/register GET, Guest heartbeat, authenticated heartbeat, same-session tab, anonymous-name suppression, and logout scenarios.

## Not verified here

- Ubuntu 24.04 and Oracle Free `FREEPDB1` schema/application flow.
- `systemd-analyze verify`, healthy/unhealthy Oracle readiness blocking, and readiness re-execution on a real systemd host.
- Actual participant-account IAP tunnel to 8080 and denied tunnels to 22, 1521, and 5500.
- IAP firewall reachability to a Connector bound to `VM_PRIVATE_IP`.
- Nginx TLS certificate issuance, HTTPS `Secure` cookie behavior, Basic Auth, and IP allowlist.
- The current AdminFilter change was verified by its Java verifier and MEMBER HTTP 403 path; suspended/withdrawn administrator sessions were not recreated through the browser against Oracle.

## Cleanup

All IDs created by the final functional smoke were deleted through post deletion and account withdrawal. Generated runtime directories and logs remain only under `target` and are excluded from both source ZIPs.
