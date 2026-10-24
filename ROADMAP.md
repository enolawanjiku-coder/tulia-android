# Tulia Roadmap to Best-in-Class

Status: v0.2.12 (build 14). This roadmap extends the original blueprint (`Tulia Blueprint.md`)
with what is actually built vs missing, in build order. Update it as milestones complete.

## Where we are

Done and working:

- Email + Google sign-in, profile setup and edit, username search
- 1:1 real-time chat: text and images, typing indicator, replies, reactions,
  edit / delete, swipe actions
- Chat list: unread counts, pin / mute / archive
- Dark / light theme, Firestore rules, in-app updater, release pipeline
- Media API on Cloudflare Workers + R2 (JPEG only, 8MB cap, member-checked)

Known gaps found in code review (2026-10-24):

- No real push notifications (no FCM service in the manifest; notifications only
  fire from the Firestore listener while the app process is alive)
- No read receipts (sent/delivered only; no `readBy` handling)
- No presence (online / last seen)
- No groups, voice notes, video, documents, block/report, forward, drafts
- Status and Calls tabs are placeholders

## Milestone 1 — Trust and reliability (P2 finish) — code complete 2026-10-24

Goal: the app never silently misses a message, and the core chat feels complete.

1. **Real FCM push notifications.** ✅ DONE (code). Cloudflare Worker `POST /notify`:
   sender's client calls it after a successful Firestore write; the worker verifies
   the Firebase ID token and chat membership, loads member docs (FCM tokens, mute
   list, blocks) with a service account, and sends via FCM HTTP v1. App side:
   `TuliaMessagingService`, `PushTokenRegistry` (writes `pushTokens/{uid}`, a
   write-only-to-owner collection), token registration on auth, mute/block respected
   server-side, dedup against the in-process `MessageNotifier`.
   **Still needed from you (console work):**
   - Create a Firebase service account (Role: Firebase Cloud Messaging API Admin +
     Cloud Datastore User) and set its JSON as a Worker secret:
     `cd cloudflare && npx wrangler secret put FCM_SA` (paste the whole JSON)
   - Publish `firestore.rules` (adds `pushTokens` + `reports` + blocked-sender check)
   - `cd cloudflare && npx wrangler deploy` (already deployed 2026-10-24; re-run after rule/secret changes is not needed — rules deploy from the console or `firebase deploy --only firestore:rules`)
2. **Read receipts.** ✅ Already existed — per-user `lastRead` timestamps drive
   blue ticks (`TICK_READ`); no work was needed.
3. **Presence.** ✅ DONE (code). Realtime DB `status/{uid}` with `onDisconnect()`
   re-armed on every reconnect; foreground tracking in `TuliaApp`; chat header shows
   typing > online > last seen.
   **Still needed from you:** create a Realtime Database instance in the Firebase
   console (locked mode), then publish `database.rules.json` to it.
4. **Block and report.** ✅ DONE (code). Chat overflow menu (Block/Unblock/Report),
   `users/{uid}.blocked`, `reports` collection, blocked users filtered from search,
   and Firestore rules now reject messages in direct chats when the recipient has
   blocked the sender.

## Milestone 2 — Media richness — voice notes DONE 2026-10-24

1. **Voice notes.** ✅ DONE (code + worker deployed). Hold the mic to record
   (AAC/M4A, 64kbps), slide left to cancel, release to send; worker accepts
   `audio/mp4` + `audio/aac` up to 25MB with magic-byte sniffing; bubbles render
   play/pause + waveform + duration with in-place MediaPlayer playback; chat list
   and notifications show "Voice note".
2. **Video.** TODO — extend worker table (`video/mp4`, size cap, thumbnail),
   Media3/ExoPlayer or intent-based playback.
3. **Documents** TODO — Storage Access Framework picker, file bubble, open intent;
   extend worker allowlist.

## Milestone 3 — Groups

Create group (name, photo, members), group info screen, add/remove members,
admin roles (`admins[]` already in the data model), member-only rules already
shaped for it. Sender name shown in group bubbles.

## Milestone 4 — Release-grade hardening

- Firebase App Check (Play Integrity)
- Crashlytics
- Delete account flow + data cleanup (Play Store requirement)
- Privacy policy page
- Biometric app lock (`androidx.biometric`)
- Privacy settings: last seen / photo / about visibility per audience
- Performance pass on a low-end device profile

## Milestone 5 — Differentiators

- Status stories (24h, gradient ring, fills the placeholder tab)
- Disappearing messages and view-once media
- Scheduled messages and quiet hours — lean into the "tulia = settle down, be
  calm" identity: calm messaging instead of engagement-bait
- Calls (LiveKit) — biggest effort, last
- Later: phone OTP, contact discovery, translation, channels

## Working agreement

- One milestone at a time, build and test on a real phone before moving on
  (same rule as the blueprint's prompt sequence)
- Keep the worker the single non-Firebase backend surface
- Update this file's checkmarks as items ship
