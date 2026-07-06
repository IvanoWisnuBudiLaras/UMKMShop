# Walkthrough: Senior QA Audit & Test Coverage Optimization

## Overview
As part of the Senior QA audit, I have refactored the core data layer of UMKMShop to support isolated testing and significantly increased the test coverage for critical security and money-related features (Priority 1). A dedicated Supabase sandbox project (`UMKMShop-Test-QA`) was established for RLS validation.

## Key Accomplishments

### 1. Dependency Injection Refactor
- Refactored **all core repositories** (`Auth`, `Order`, `Product`, `Chat`, `Profile`, `Shipping`, `Inbox`, `PushToken`) to accept `SupabaseClient` and `CoroutineDispatcher` via constructor.
- This eliminated reliance on global singletons like `SupabaseClientProvider`, enabling reliable unit testing.

### 2. High Coverage for Authentication
- Achieved **90.2% instruction coverage** for the `data.auth` package.
- Implemented `AuthRepositoryTest.kt` which validates:
    - Session restoration and current user retrieval.
    - Sign-up and Login flows including edge cases like verification states.
    - Secure logout and push token cleanup.

### 3. Data Boundary & RLS Validation
- **Row-Level Security**: Verified policies for `orders` and `chat_rooms` in the test sandbox, ensuring data isolation between users.
- **Repository Validation**: Integrated logic checks in repositories (e.g., `require(input.sellerId == currentUserId())`) to act as a secondary barrier alongside RLS.

### 4. Progress on Other Packages
- **`data.shipping`**: 60% coverage (Village search & shipping estimation).
- **`data.order`**: 49% package coverage.
- **`data.notification`**: 30% coverage.
- **Priority 2 Repositories**: Initial tests implemented for `Product`, `Chat`, and `Profile`.

## Verification Summary

### Automated Tests
- **Package Coverage**: Verified using JaCoCo reports.
- **Unit Tests**: Ran `app:jacocoTestReport` across all refactored packages. Total 43 unit tests passing.

### Manual Security Validation
- Verified RLS policies via direct SQL execution in `UMKMShop-Test-QA`:
    ```sql
    -- Confirmed that this fails if seller_id != current user
    INSERT INTO orders (seller_id, ...) VALUES ('other_user_id', ...);
    ```

## Technical Challenges & Recommendations
- **SDK Mocking**: Supabase's use of inline extensions for Postgrest DSL makes deep line coverage in unit tests difficult.
- **Recommendation**: To reach >90% in repositories like `OrderRepository`, future audits should utilize `Ktor MockEngine` to simulate real JSON responses from Supabase, bypassing the need to mock complex SDK internals.
