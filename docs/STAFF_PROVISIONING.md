# Initial administrator and staff accounts

Public registration creates borrower accounts only. It never accepts an account role. An administrator provisions Loan Officer and Administrator accounts through the authenticated `POST /api/v1/admin/users` endpoint. New staff accounts must change their temporary password before using role-protected features. The administrator can list staff and change their staff role or suspend/reactivate an account; changes are written to the audit log. At least one active Administrator must remain.

## First administrator

There are no default credentials. For a new local database, set `PESACHAIN_BOOTSTRAP_ADMIN_EMAIL` and `PESACHAIN_BOOTSTRAP_ADMIN_PASSWORD` in the backend process environment before its first startup. Use a unique email and a randomly generated password of at least 12 characters (no more than 72 UTF-8 bytes). Keep the password in a secure local secret store; do not commit it or paste it into logs. The first startup creates one Administrator, requires a password change, and writes an audit record. If an Administrator already exists, startup does not replace or reset that account. Remove both environment variables after provisioning.

If the variables are both absent, startup does not create an account. If only one is supplied or the password is invalid, startup fails rather than silently creating an insecure account. In PowerShell, set environment variables only in the backend process environment (not in a committed file):

```powershell
$env:PESACHAIN_BOOTSTRAP_ADMIN_EMAIL = 'admin@example.test'
$env:PESACHAIN_BOOTSTRAP_ADMIN_PASSWORD = '<unique secret from your password manager>'
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

After the first administrator is created, log in and change the password through `POST /api/v1/auth/change-password` with the current and new passwords. Then remove the environment variables from the shell. Never use a real personal account password for a local academic environment.

## Provisioning API

- `GET /api/v1/admin/users` lists staff account summaries only.
- `POST /api/v1/admin/users` accepts `email`, `password`, and `role` (`LOAN_OFFICER` or `ADMINISTRATOR`). The response indicates `passwordChangeRequired`.
- `PATCH /api/v1/admin/users/{id}` accepts an optional `role` and/or `status` (`ACTIVE` or `SUSPENDED`). Self role/status changes are rejected. Accounts are suspended rather than deleted.

All endpoints require an Administrator session and CSRF protection. Account creation, role changes, suspension/reactivation, bootstrap, and password changes are audited. Role/status audit metadata contains only before/after values, not credentials or personal details. Existing sessions reload current account status and role from PostgreSQL, so suspension or a role change takes effect on the next request.
