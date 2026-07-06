# Flow Coverage - UMKMShop Business Logic

This report tracks the validation of official business flows defined in `PRD-v2.md` (§5.2 - §5.6).
Validation is performed against a **Real Local Supabase Stack** using pgTAP and Kotlin integration tests.

| Diagram/Flow in PRD | Node yang diuji | Node yang TIDAK bisa diuji otomatis | Status |
| :--- | :--- | :--- | :--- |
| **§5.2: Auth & Profile** | Registration trigger, Profile Creation, RLS updates | Email verification (SMTP Mocked) | ✅ Lolos (pgTAP) |
| **§5.3: Catalog & Search** | Trigram search, Category filter, Price filter | - | ✅ Lolos (pgTAP) |
| **§5.4: Chat & Notif** | Message trigger, PGMQ enqueue, Room update | FCM actual delivery (Mocked) | ✅ Lolos (pgTAP) |
| **§5.5: Order & Payment** | Order creation, Status update trigger, Inbox sync | Xendit actual callback (MockEngine) | ✅ Lolos (pgTAP) |
| **§5.6: Review System** | Rating validation, Aggregate trigger | - | ✅ Lolos (pgTAP) |

## Validation Methodology
- **Real RLS**: Tested using pgTAP `set_config` with JWT claims.
- **Triggers**: Verified via pgTAP `results_eq` after direct inserts.
- **External APIs**: Mocked at the HTTP level using Ktor `MockEngine` in Kotlin tests.
- **FCM**: Mocked using MockK as there is no reliable local emulator for push delivery.
- **Cron Jobs**: Logic tested by manual execution of SQL scripts in the local stack.

## Technical Findings (Local Stack Integration)
1. **Trigger Atomicity**: Confirmed that `auth.users` -> `profiles` and `messages` -> `pgmq` triggers are atomic and transactional.
2. **RLS Accuracy**: Verified that `auth.uid()` based policies correctly filter rows for unauthorized users without throwing errors (returns empty set).
3. **Rating Aggregation**: Verified that concurrent reviews are correctly aggregated into `profiles.rating_avg`.
