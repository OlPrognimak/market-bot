# JWT, OIDC/OAuth2, and Okta Security Concept

## Goal

Add OIDC/OAuth2 login with Okta without breaking the existing username/password and application JWT security.

The recommended first version is **hybrid authentication**:

- Existing local login remains available.
- Existing application JWT remains the token used by frontend API calls and WebSocket connections.
- Okta is added as an external login provider.
- After successful Okta authentication, the backend provisions or links a local `AppUserEntity` and issues the same application JWT format used today.

This keeps the frontend and existing authorization model stable while adding Okta login.

## Current Security State

The application currently uses custom stateless JWT authentication.

Relevant classes:

- `SecurityConfiguration`
- `JwtAuthenticationFilter`
- `JwtService`
- `AppSecurityProperties`
- `AuthController`
- `AppUserEntity`
- `AppUserDetailsService`

Current flow:

1. User calls `/api/auth/login` with username and password.
2. Backend validates credentials against local users.
3. `JwtService` creates a signed HS256 application JWT.
4. Frontend sends the token as `Authorization: Bearer ...`.
5. `JwtAuthenticationFilter` validates the token and loads the local user.
6. WebSocket currently also supports `access_token` as query parameter.

This model should stay as the compatibility baseline.

## Recommended Target Architecture

### Phase 1: Hybrid Local JWT + Okta Login

This is the safest implementation for the current application.

Flow:

1. User clicks **Login with Okta** in the frontend.
2. Frontend redirects to backend endpoint, for example `/oauth2/authorization/okta`.
3. Spring Security redirects the browser to Okta.
4. Okta authenticates the user.
5. Okta redirects back to the backend OAuth2 callback.
6. Backend reads OIDC claims such as `sub`, `email`, `name`, and optionally `groups`.
7. Backend finds or creates a local user.
8. Backend maps Okta groups to local roles.
9. Backend issues the existing application JWT.
10. Frontend stores and uses this application JWT exactly as today.

Result:

- The API still trusts only the application JWT.
- Okta is used for login identity proof.
- Existing authorization code changes are limited.
- Existing WebSocket token behavior can remain unchanged.

### Phase 2: Optional Okta JWT Resource Server

Later the backend can also accept Okta access tokens directly by enabling Spring Security Resource Server JWT validation.

This is a larger change because:

- Frontend must store/send Okta access tokens.
- Backend must map Okta token claims to local authorities.
- Local user lookup/linking must happen for each external token or be cached.
- WebSocket authentication must accept Okta tokens too.

This is not recommended as the first step.

### Phase 3: Optional Removal of Local Password Login

Only after Okta login is stable, local password login can be disabled or restricted to emergency admin access.

For this project, keep local admin login as a fallback.

## Okta Account and Okta Configuration

An Okta organization/account is required. For development, use an Okta developer/integrator account.

In Okta:

1. Create an OIDC application integration.
2. Prefer **Web Application** for backend-handled Authorization Code flow.
3. Enable Authorization Code flow. Use PKCE if available.
4. Configure redirect URI:

```text
http://localhost:8080/login/oauth2/code/okta
```

If the backend runs under a different host/port, this URI must match that backend base URL exactly.

5. Configure sign-out redirect URI if logout is integrated later:

```text
http://localhost:3000/login
```

6. Assign users and groups to the application.
7. Add a groups claim if role mapping should be based on Okta groups.

Recommended Okta groups:

- `market-bot-admin`
- `market-bot-user`

Recommended role mapping:

| Okta group | Local role |
| --- | --- |
| `market-bot-admin` | `ADMIN` |
| `market-bot-user` | `USER` |

Okta issuer example:

```text
https://dev-12345678.okta.com/oauth2/default
```

The exact issuer must come from the Okta authorization server used by the app.

## Maven Dependencies

Add OAuth2 client support for Okta login:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

Add resource server support only if the API will validate Okta access tokens directly:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

For Phase 1, `spring-boot-starter-oauth2-client` is enough.

## `application.yml` Concept

### Hybrid Okta Login

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          okta:
            provider: okta
            client-id: ${OKTA_CLIENT_ID:}
            client-secret: ${OKTA_CLIENT_SECRET:}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - openid
              - profile
              - email
              - groups
        provider:
          okta:
            issuer-uri: ${OKTA_ISSUER_URI:}
```

Application-specific configuration:

```yaml
app:
  security:
    auth-mode: ${APP_SECURITY_AUTH_MODE:LOCAL_JWT}
    jwt-secret: ${APP_SECURITY_JWT_SECRET:change-me}
    jwt-expiration-minutes: ${APP_SECURITY_JWT_EXPIRATION_MINUTES:720}
    oidc:
      enabled: ${APP_SECURITY_OIDC_ENABLED:false}
      provider: okta
      auto-provision-users: ${APP_SECURITY_OIDC_AUTO_PROVISION_USERS:true}
      default-role: ${APP_SECURITY_OIDC_DEFAULT_ROLE:USER}
      admin-groups: ${APP_SECURITY_OIDC_ADMIN_GROUPS:market-bot-admin}
      user-groups: ${APP_SECURITY_OIDC_USER_GROUPS:market-bot-user}
      frontend-success-uri: ${APP_SECURITY_OIDC_FRONTEND_SUCCESS_URI:http://localhost:3000/auth/oidc/callback}
      frontend-failure-uri: ${APP_SECURITY_OIDC_FRONTEND_FAILURE_URI:http://localhost:3000/login?error=oidc}
```

Suggested `auth-mode` values:

| Value | Meaning |
| --- | --- |
| `LOCAL_JWT` | Current behavior only |
| `HYBRID` | Local JWT login plus Okta login |
| `OIDC_ONLY` | Okta login only, local emergency admin can remain configurable |

### Resource Server Mode

Use this only for a later direct Okta-token API model:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OKTA_ISSUER_URI:}
```

## Credentials

Required environment variables:

```shell
OKTA_CLIENT_ID=...
OKTA_CLIENT_SECRET=...
OKTA_ISSUER_URI=https://dev-12345678.okta.com/oauth2/default
APP_SECURITY_OIDC_ENABLED=true
APP_SECURITY_AUTH_MODE=HYBRID
```

Rules:

- Do not commit Okta client secrets.
- Do not store Okta client secrets in plain text in PostgreSQL.
- In the first version, keep Okta secrets in environment variables or IntelliJ run configuration private environment variables.
- If secrets are later managed in the System Settings page, add encryption at rest first.
- For a pure SPA PKCE flow, no client secret is allowed in the frontend. That would require Resource Server mode and a larger frontend change.

## Java Backend Changes

### Security Properties

Extend `AppSecurityProperties` with:

- `authMode`
- `oidc.enabled`
- `oidc.provider`
- `oidc.autoProvisionUsers`
- `oidc.defaultRole`
- `oidc.adminGroups`
- `oidc.userGroups`
- `oidc.frontendSuccessUri`
- `oidc.frontendFailureUri`

### User Entity

Extend `AppUserEntity` with external identity fields:

- `authProvider`, for example `LOCAL` or `OKTA`
- `externalSubject`, Okta `sub`
- `lastLoginAt`

Recommended database constraints:

- Unique local username remains.
- Unique `(auth_provider, external_subject)` where `external_subject` is not null.
- Email should not be the only hard identity key because emails can change.

For users created from Okta:

- `username` can be generated from email or Okta login.
- `passwordHash` should either be nullable for external users or contain an unusable generated value.
- `enabled` should follow local application rules.

### OIDC Provisioning Service

Add a service, for example `OidcUserProvisioningService`.

Responsibilities:

- Read OIDC claims.
- Find local user by `(OKTA, sub)`.
- If not found, optionally link by verified email.
- If still not found and auto-provision is enabled, create a local user.
- Map Okta groups to local `UserRole`.
- Update display name, email, and last login timestamp.

Important:

- Linking by email should be conservative.
- If an existing local user has the same email, link only when email is verified or admin approval is configured.

### OAuth2 Success Handler

Add a custom success handler for Okta login.

Responsibilities:

- Receive the authenticated `OidcUser`.
- Call `OidcUserProvisioningService`.
- Create application JWT with existing `JwtService`.
- Return the token to the frontend.

Safer token handoff:

1. Backend creates a short-lived one-time login code.
2. Backend redirects to frontend:

```text
http://localhost:3000/auth/oidc/callback?code=one-time-code
```

3. Frontend calls backend:

```text
POST /api/auth/oidc/exchange
```

4. Backend returns the application JWT.

Avoid putting the final application JWT directly into the query string. If a direct redirect is used temporarily, use URL fragment instead of query parameter, but the one-time exchange code is cleaner.

### Auth Controller

Add endpoints:

- `GET /api/auth/oidc/providers`
- `POST /api/auth/oidc/exchange`
- Optional `POST /api/auth/oidc/logout`

Existing endpoints remain:

- `POST /api/auth/login`
- `POST /api/auth/signup`
- `GET /api/auth/me`

### Security Configuration

In `SecurityConfiguration`:

- Keep CSRF disabled for stateless API unless cookie-based auth is introduced later.
- Keep CORS configuration.
- Permit OAuth2 endpoints:

```text
/oauth2/**
/login/oauth2/**
/api/auth/oidc/**
```

- Enable OAuth2 login when OIDC is enabled:

```java
http.oauth2Login(oauth2 -> oauth2
        .successHandler(oktaAuthenticationSuccessHandler)
        .failureHandler(oktaAuthenticationFailureHandler)
);
```

- Keep `JwtAuthenticationFilter` for application JWT validation.
- Ensure the custom JWT filter does not block OAuth2 callback endpoints.

If Resource Server mode is later added:

- Configure `oauth2ResourceServer().jwt()`.
- Add `JwtAuthenticationConverter` for Okta group-to-role mapping.
- Decide precedence when both application JWT and Okta JWT are accepted.

### WebSocket Authentication

Current WebSocket authentication relies on the application JWT query token.

In Phase 1 no change is required because Okta users receive the same application JWT after login.

If direct Okta tokens are accepted later, WebSocket authentication must validate Okta JWTs too.

## Frontend Changes

Add login UI:

- Existing username/password login remains.
- Add **Login with Okta** button.
- Button navigates to backend OAuth2 authorization URL:

```text
http://localhost:8080/oauth2/authorization/okta
```

Add callback page:

```text
/auth/oidc/callback
```

Callback behavior:

1. Read one-time code from URL.
2. Call `/api/auth/oidc/exchange`.
3. Store returned application JWT using the existing token storage logic.
4. Call `/api/auth/me`.
5. Redirect to the main page.

No broad frontend API refactor is required in Phase 1.

## Database Changes

Recommended migration:

```sql
alter table app_user
    add column auth_provider varchar(32) not null default 'LOCAL',
    add column external_subject varchar(255),
    add column last_login_at timestamp with time zone;

create unique index ux_app_user_external_identity
    on app_user (auth_provider, external_subject)
    where external_subject is not null;
```

Optional audit table:

```sql
create table user_auth_event (
    id bigserial primary key,
    user_id bigint not null,
    provider varchar(32) not null,
    event_type varchar(64) not null,
    created timestamp with time zone not null,
    details jsonb
);
```

The audit table is useful for login troubleshooting but not mandatory for the first version.

## Logout Concept

There are two sessions/tokens:

- Local application JWT in frontend storage.
- Okta browser session.

Minimal logout:

- Remove local application JWT from frontend storage.
- Redirect to login page.

Full logout:

- Remove local application JWT.
- Redirect to Okta logout endpoint.
- Return to frontend login page.

Full logout can be added after login is working.

## SSL/TLS Concept

### Why TLS Is Required

OIDC/OAuth2 redirects, application JWTs, Okta authorization codes, cookies, and user credentials must be protected in transport.

For local development, HTTP can still work technically, but it hides production problems:

- Redirect URIs differ between local and production.
- Browser security behavior differs for secure cookies and mixed content.
- OAuth2 providers often require HTTPS for non-localhost redirect URIs.
- Tokens can be exposed if traffic is not encrypted.

Target state:

- Local development can use `https://localhost:8443`.
- Production should use a real CA certificate.
- HTTP should redirect to HTTPS in production.
- OAuth2 redirect URIs in Okta must match the HTTPS URL exactly.

### Recommended TLS Options

| Environment | Certificate option | Recommendation |
| --- | --- | --- |
| Local development | Self-signed certificate | Acceptable for quick testing, browser warning expected |
| Local development | Local trusted CA, for example `mkcert` | Better because browser trusts the certificate after installing the local CA |
| Private/internal network | Internal company CA | Good if all clients trust the internal CA |
| Public production | Let's Encrypt | Recommended free public CA |
| Public production | Commercial CA | Only needed if company policy requires it |

For this project:

- Use self-signed or local trusted CA for development.
- Use Let's Encrypt for public deployment if the app has a real domain name.
- Do not use self-signed certificates in public production.

### Spring Boot HTTPS With Java KeyStore

Spring Boot can enable HTTPS with `server.ssl.*` properties.

Example `application-local-ssl.yml`:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: ${APP_SSL_KEY_STORE:classpath:ssl/market-bot-local.p12}
    key-store-type: PKCS12
    key-store-password: ${APP_SSL_KEY_STORE_PASSWORD:changeit}
    key-alias: ${APP_SSL_KEY_ALIAS:market-bot-local}
```

Generate a self-signed local certificate:

```shell
keytool -genkeypair \
  -alias market-bot-local \
  -keyalg RSA \
  -keysize 3072 \
  -validity 365 \
  -storetype PKCS12 \
  -keystore market-bot-local.p12 \
  -storepass changeit \
  -dname "CN=localhost, OU=Development, O=Market Bot, L=Local, ST=Local, C=DE" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"
```

Recommended file handling:

- Store local development certificates outside Git, for example `./certs/market-bot-local.p12`.
- Add `certs/*.p12`, `certs/*.jks`, `certs/*.key`, and `certs/*.crt` to `.gitignore`.
- Pass certificate location and password through environment variables.
- Do not commit private keys or keystore passwords.

If the certificate is stored outside the classpath:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: ${APP_SSL_KEY_STORE:file:./certs/market-bot-local.p12}
    key-store-type: PKCS12
    key-store-password: ${APP_SSL_KEY_STORE_PASSWORD}
    key-alias: ${APP_SSL_KEY_ALIAS:market-bot-local}
```

### Spring Boot HTTPS With PEM Files

Spring Boot can also use PEM certificate and key files.

Example:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    certificate: ${APP_SSL_CERTIFICATE:file:./certs/localhost.crt}
    certificate-private-key: ${APP_SSL_PRIVATE_KEY:file:./certs/localhost.key}
```

Use PEM files when certificates come directly from reverse proxy tooling or ACME tooling.

Use Java KeyStore or PKCS12 when operating directly in the JVM is easier.

### Spring Boot SSL Bundles

For a cleaner future configuration, Spring Boot SSL bundles can group keystore or PEM settings under `spring.ssl.bundle` and reference them from `server.ssl.bundle`.

Example:

```yaml
spring:
  ssl:
    bundle:
      jks:
        market-bot:
          key:
            alias: market-bot-local
          keystore:
            location: ${APP_SSL_KEY_STORE:file:./certs/market-bot-local.p12}
            password: ${APP_SSL_KEY_STORE_PASSWORD}
            type: PKCS12

server:
  port: 8443
  ssl:
    bundle: market-bot
```

This is useful if the app later needs different TLS material for server connections and outbound client trust.

### SecurityConfiguration Changes For HTTPS

TLS is mostly configured through Spring Boot server settings, not through `SecurityConfiguration`.

Still, production security should add channel enforcement:

```java
http.requiresChannel(channel -> channel
        .anyRequest().requiresSecure()
);
```

Use this only for profiles where HTTPS is actually enabled. If enabled while the app only runs on HTTP, local requests will fail or redirect incorrectly.

Recommended profile behavior:

| Profile | HTTPS behavior |
| --- | --- |
| `local` | HTTP allowed by default |
| `local-ssl` | HTTPS enabled, secure channel can be tested |
| `prod` | HTTPS required |

For production behind a reverse proxy, the app must also correctly process forwarded headers:

```yaml
server:
  forward-headers-strategy: framework
```

Without this, Spring Boot may think the original request is HTTP even when the browser used HTTPS through a proxy. That can break OAuth2 redirect URI generation.

### HTTP To HTTPS Redirect

Spring Boot property-based SSL usually exposes only the HTTPS connector. If both HTTP and HTTPS ports are needed, one connector must be added programmatically.

Recommended production setup:

- Let a reverse proxy such as nginx, Caddy, Traefik, or a cloud load balancer terminate TLS.
- Redirect HTTP to HTTPS at the proxy.
- Run Spring Boot internally on HTTP or HTTPS depending on deployment policy.

If Spring Boot terminates TLS itself and must also expose HTTP redirect, add a Tomcat connector programmatically. This is optional and should be done only if there is no reverse proxy.

### Okta Redirect URI Changes With HTTPS

When HTTPS is enabled locally, add this redirect URI in Okta:

```text
https://localhost:8443/login/oauth2/code/okta
```

Frontend success URI should also use HTTPS if the frontend is served over HTTPS:

```yaml
app:
  security:
    oidc:
      frontend-success-uri: ${APP_SECURITY_OIDC_FRONTEND_SUCCESS_URI:https://localhost:3000/auth/oidc/callback}
      frontend-failure-uri: ${APP_SECURITY_OIDC_FRONTEND_FAILURE_URI:https://localhost:3000/login?error=oidc}
```

Important:

- Okta redirect URIs must match exactly.
- `http://localhost` may work for development, but non-localhost production URLs should use HTTPS.
- Browser mixed-content rules can block frontend calls if the frontend is HTTPS but the backend API is HTTP.

### Free CA Option: Let's Encrypt

Let's Encrypt is the recommended free public CA for production when the app has a real DNS name.

Requirements:

- Public domain name, for example `market-bot.example.com`.
- Ability to prove domain control through HTTP, TLS, or DNS challenge.
- ACME client such as Certbot, Caddy, Traefik, nginx ACME integration, or another ACME-compatible client.
- Automated renewal because Let's Encrypt certificates are short-lived.

Recommended production topology:

```text
Browser
  -> HTTPS 443
Reverse proxy with Let's Encrypt certificate
  -> HTTP 8080 or HTTPS 8443
Spring Boot market-bot
```

Advantages:

- Certificates are publicly trusted.
- No browser warning.
- Renewal can be automated.
- Spring Boot does not need direct access to private CA automation if TLS terminates at the proxy.

If Spring Boot terminates TLS directly, the ACME client must write certificates to a location readable by the application, and the application or server must reload/restart after renewal.

### Certificate and Secret Storage

Rules:

- Private keys must not be committed to Git.
- Keystore passwords must not be committed to Git.
- Use environment variables or a secrets manager for certificate passwords.
- Public certificates can be less sensitive, but keep certificate/private-key pairs together only in protected local directories.
- For Docker, mount certificates as read-only volumes.

Example Docker-style paths:

```yaml
server:
  ssl:
    key-store: ${APP_SSL_KEY_STORE:file:/run/secrets/market-bot.p12}
    key-store-password: ${APP_SSL_KEY_STORE_PASSWORD}
```

### TLS Implementation Plan

1. Add `.gitignore` rules for local certificate files.
2. Add optional `application-local-ssl.yml`.
3. Generate local PKCS12 self-signed certificate or use local trusted CA tooling.
4. Run backend on `https://localhost:8443`.
5. Add matching Okta localhost HTTPS redirect URI.
6. Test `/api/auth/login`, `/api/auth/me`, WebSocket, and Okta callback over HTTPS.
7. For production, choose reverse proxy TLS termination with Let's Encrypt.
8. Add `server.forward-headers-strategy=framework` for proxy deployments.
9. Enable secure-channel requirement only in production or SSL profile.

## Risks and Decisions

| Topic | Risk | Decision |
| --- | --- | --- |
| Token in URL | Query parameters can leak through logs/history | Use one-time exchange code |
| Group claims | Okta does not always include groups by default | Configure groups claim in Okta |
| Email linking | Email can change or collide | Prefer Okta `sub`; link by email only conservatively |
| Client secret storage | Plain DB storage is unsafe | Use environment variables first |
| WebSocket auth | Existing WS expects application JWT | Keep application JWT in Phase 1 |
| Local admin access | Okta outage can block access | Keep emergency local admin login |
| CORS/redirect URI | OAuth2 redirect URI must match exactly | Configure local/dev/prod URIs explicitly |
| TLS certificate storage | Private keys can leak through Git or logs | Keep keys outside Git and pass secrets via environment |
| Reverse proxy TLS | App can generate wrong OAuth2 redirect URI if forwarded headers are ignored | Configure forwarded headers in production |

## Implementation Plan

1. Add `spring-boot-starter-oauth2-client`.
2. Add OIDC properties to `AppSecurityProperties`.
3. Add database fields for external identity.
4. Add OIDC user provisioning/linking service.
5. Add OAuth2 success/failure handlers.
6. Update `SecurityConfiguration` for OAuth2 login endpoints.
7. Add `/api/auth/oidc/exchange`.
8. Add frontend Okta login button and callback page.
9. Test local JWT login still works.
10. Test Okta login creates/links user and returns application JWT.
11. Test role mapping from Okta groups.
12. Test WebSocket still authenticates with the returned application JWT.

## Recommended First Version

Implement only Hybrid mode:

- Local JWT login remains.
- Okta OIDC login is added.
- Backend issues existing application JWT after Okta success.
- Okta secrets are configured through environment variables.
- User role comes from Okta groups, with configured default role fallback.
- Local admin user remains available.

Do not implement direct Okta access-token API authorization in the first version unless there is a specific requirement to remove the application JWT.

## References

- Spring Security OAuth2 Login core configuration: https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html
- Spring Security OAuth2 Resource Server JWT: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Okta Spring Boot redirect login guide: https://developer.okta.com/docs/guides/sign-into-web-app-redirect/spring-boot/main/
- Okta groups claim guide: https://developer.okta.com/docs/guides/customize-tokens-groups-claim/main/
- Spring Boot SSL configuration: https://docs.spring.io/spring-boot/reference/features/ssl.html
- Spring Boot embedded web server SSL: https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.configure-ssl
- Let's Encrypt getting started: https://letsencrypt.org/getting-started/
