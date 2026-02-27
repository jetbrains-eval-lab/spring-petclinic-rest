# OAuth2 and Session Management Documentation

## Overview

This document describes the OAuth2 Google authentication implementation with server-side session management in the Spring PetClinic REST API, implemented according to the requirements in `plans/9_requirements.md`.

## Features

- **Google OAuth2 Authentication**: Secure authentication using Google OAuth2
- **Server-side Session Management**: JDBC-based session storage with Spring Session
- **Session Attributes API**: CRUD operations for user-defined session attributes
- **Role-based Access Control**: Configurable admin roles based on email addresses
- **User Preferences**: Persistent user preferences stored in HttpSession
- **Session Security**: Protection against unauthorized access with OAuth2 re-auth redirect behavior

## Required API Endpoints (6 methods)

### Authentication APIs
- `GET /api/auth/login` - Return authentication status and OAuth2 login URL when unauthenticated
- `POST /api/auth/logout` - Logout and invalidate session

### Session Attribute Management APIs  
- `GET /api/session/user` - Get authenticated user
- `GET /api/session/attributes/{key}` - Get specific session attribute (returns 404 if not found)
- `PUT /api/session/attributes/{key}` - Set/update session attribute
- `DELETE /api/session/attributes/{key}` - Remove session attribute

## Session Attributes Specification

Store in HttpSession:
- `authenticated_user` - OAuth2 user details and roles
- `session_created_time` - Session creation timestamp
- `last_activity_time` - Last activity timestamp
- User-defined attributes including preferences (theme, language, timezone, custom data, etc.)

## Configuration

### Enable OAuth2 Authentication
```properties
# Disable basic auth, enable OAuth2
petclinic.security.enable=false
petclinic.security.oauth2.enable=true
petclinic.security.oauth2.default-login-provider=google

# Google OAuth2 Configuration
spring.security.oauth2.client.registration.google.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_CLIENT_SECRET

# Admin email configuration with role mapping
# Format: email1:ROLE1,ROLE2;email2:ROLE3;email3:ROLE1
petclinic.security.oauth2.admin-emails=admin@petclinic.com:ADMIN,VET_ADMIN,OWNER_ADMIN;manager@petclinic.com:VET_ADMIN,OWNER_ADMIN

# Session timeout for Spring Session
spring.session.timeout=30m
```

### Google OAuth2 Setup
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create OAuth2 credentials
3. Add authorized redirect URI: `http://localhost:9966/petclinic/login/oauth2/code/google`
4. Copy client ID and secret to your configuration

## User Mapping Requirements

Map Google OAuth2 attributes to PetClinic User entity:
- email → username/email
- given_name → first_name
- family_name → last_name
- sub → oauth_id
- picture → picture_url
- provider → oauth_provider

For non-Google providers:
- derive a stable provider-specific user identifier;
- if email is missing, use `provider:providerUserId` as username fallback;
- optional profile fields may be null.

## Role Assignment

- Admin emails (configurable): All necessary roles for system access
- Regular users: no admin roles by default (authenticated access only)
- Configuration via properties: list of admin emails with role mapping format: email1:ROLE1,ROLE2;email2:ROLE3

## API Security Contract (Current Behavior)

- `/api/auth/**` remains publicly accessible
- Protected APIs require authentication
- For unauthenticated access to protected endpoints, system may respond with OAuth2 re-auth redirect (`302 Found`) instead of `401`
- Successful API responses are JSON

### Golden Test Clarifications
- `GET /api/auth/login` (unauthenticated):
  - returns `200 OK`
  - JSON contains `authenticated: false`
  - JSON contains `loginUrl: /oauth2/authorization/{provider}`
- Default provider in `loginUrl` is configured by:
  - `petclinic.security.oauth2.default-login-provider=google`
- Unauthenticated `GET /api/session/user`:
  - returns `302 Found`
  - `Location` points to `/oauth2/authorization/{provider}`

## Additional OAuth2 Providers

To add support for additional OAuth2 providers:

```properties
# GitHub OAuth2 Provider
spring.security.oauth2.client.registration.github.client-id=YOUR_GITHUB_CLIENT_ID
spring.security.oauth2.client.registration.github.client-secret=YOUR_GITHUB_CLIENT_SECRET

# Facebook OAuth2 Provider  
spring.security.oauth2.client.registration.facebook.client-id=YOUR_FACEBOOK_CLIENT_ID
spring.security.oauth2.client.registration.facebook.client-secret=YOUR_FACEBOOK_CLIENT_SECRET
```

## Distributed Session Storage

For production environments with multiple application instances:

### Redis Configuration
```properties
spring.session.store-type=redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=your_redis_password
```

### JDBC Configuration (Current Default)
```properties
spring.session.store-type=jdbc
spring.session.jdbc.initialize-schema=always
```

### MongoDB Configuration
```properties
spring.session.store-type=mongodb
spring.data.mongodb.uri=mongodb://localhost:27017/petclinic_sessions
```

## Testing

The implementation includes comprehensive integration tests covering:
1. OAuth2 login flow and session creation
2. Session persistence across multiple requests
3. Session attributes CRUD operations via API
4. Logout and complete session invalidation
5. Session expiration and re-authentication behavior
6. Unauthorized access to protected endpoints (redirect/re-login via `302 Found`)
7. OAuth2 provider extensibility (at least one non-Google provider, e.g., GitHub)

## Architecture Components

### Core Classes
- **OAuth2SecurityConfig**: Spring Security configuration for OAuth2
- **SessionManagementService**: Service layer for session operations
- **OAuth2UserMappingService**: Maps OAuth2 users to domain users
- **AuthController**: Authentication endpoints (login, logout)
- **SessionController**: Session management endpoints (user, attributes CRUD)

### Session Storage
- **Spring Session JDBC**: Persistent session storage in database
- **HttpSession**: Primary storage for user data and preferences
- **SPRING_SESSION tables**: Automatically created for session persistence

## Security Features

- **302 Re-auth Redirect**: Unauthenticated protected requests are redirected to OAuth2 authorization
- **Session Fixation Protection**: Automatic session regeneration after login
- **CSRF Protection**: Disabled for REST API usage
- **Role-based Access**: Configurable admin roles via email mapping
- **Session Invalidation**: Complete cleanup on logout
