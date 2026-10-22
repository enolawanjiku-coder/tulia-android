# Tulia Blueprint

A WhatsApp-style chat app for Android. Native Android in Java + Firebase, built with opencode on Ubuntu using command-line tools (no Android Studio), tested on a real phone.

## 1. Product scope

Tulia v1 is a fast, reliable 1:1 and group messenger with media, push notifications and basic privacy. Calls, channels, communities and AI come after the core is solid.

## 2. WhatsApp feature inventory and Tulia plan

Phase key: **P1** = MVP, **P2** = core polish, **P3** = advanced, **Skip** = not worth it for now.

### Accounts and profile

| Feature | Phase | Notes |
| --- | --- | --- |
| Email / Google sign-in | P1 | Works in Expo Go |
| Phone number + OTP login | P3 | `Firebase Auth phone verification (native SDK); register your SHA-1 and SHA-256 in the console` |
| Profile name, photo, About text | P1 | Firebase Storage for photo |
| Contact discovery (match phone book) | P3 | Needs phone auth plus a Cloud Function to match hashed numbers |
| Username search | P1 | Replaces contact discovery until phone auth exists |
| Block / report user | P2 | `blocked[]` on user doc |
| Multi-device / linked devices | Skip | Very hard with Firebase |

### Messaging

| Feature | Phase | Notes |
| --- | --- | --- |
| Real-time text 1:1 | P1 | Firestore `onSnapshot` |
| Sent / delivered / read ticks | P1 | `status` field plus `readBy` map |
| Typing indicator | P2 | Short-lived field on chat doc |
| Online / last seen | P2 | Realtime Database presence is cheaper than Firestore |
| Reply to a message (quote) | P2 | `replyTo` field |
| Emoji reactions | P2 | `reactions` map |
| Edit message (time-limited) | P2 | `editedAt` field |
| Delete for me / delete for everyone | P2 | Per-user hidden list plus tombstone |
| Forward message | P2 | Copy into another chat |
| Star / save messages | P3 | `users/{uid}/starred` |
| Pin message in chat | P3 | `pinned` on chat doc |
| In-chat search | P3 | Client-side first, Algolia / Typesense later |
| Drafts shown in chat list | P2 | Local storage |
| Mentions (@user) in groups | P2 | `mentions[]` |
| Disappearing messages | P3 | TTL field plus Cloud Function cleanup |
| View-once media | P3 | Delete after first open |
| Schedule message | P3 | Cloud Tasks / scheduled function |
| Message translation | P3 | Translation API |

### Media and attachments

| Feature | Phase | Notes |
| --- | --- | --- |
| Send photos | P1 | `Android Photo Picker`, compress before upload |
| Send video | P2 | Compress, thumbnail |
| Voice notes | P2 | `MediaRecorder to record, ExoPlayer (Media3) to play`, waveform optional |
| Documents / PDFs | P2 | `Storage Access Framework (OpenDocument)` |
| Location share | P3 | `FusedLocationProviderClient` |
| Contact card share | P3 |  |
| Camera capture | P2 |  |
| Stickers / GIFs | P3 | GIF provider API |
| Built-in document scanner | Skip | Later idea |

### Chat list and organisation

| Feature | Phase | Notes |
| --- | --- | --- |
| Chat list sorted by latest | P1 | `lastMessageAt` |
| Unread badge | P1 | Per-user `unread` counter |
| Pin chats (up to 3) | P2 |  |
| Archive chats | P2 |  |
| Mute chats | P2 | Respect in push function |
| Mark as unread | P3 |  |
| Chat filters (All / Unread / Groups) | P2 |  |
| Lists / favourites | P3 |  |
| Locked chats (secret code) | P3 | Biometrics via `androidx.biometric` |

### Groups

| Feature | Phase | Notes |
| --- | --- | --- |
| Create group, name, icon | P2 |  |
| Add / remove members | P2 |  |
| Admin roles | P2 | `admins[]` |
| Group description | P2 |  |
| Invite link | P3 |  |
| Admin-only messaging | P3 |  |
| Group message history for new members | P3 |  |
| Polls | P3 |  |
| Events in groups | P3 |  |
| Communities | Skip | Large scope; revisit after groups |

### Status, channels and broadcasts

| Feature | Phase | Notes |
| --- | --- | --- |
| Status / stories (24h) | P3 | Storage plus TTL cleanup |
| Channels (one-to-many) | P3 | Public follow model |
| Broadcast lists | P3 |  |

### Calls

| Feature | Phase | Notes |
| --- | --- | --- |
| Voice and video calls | P3 | Use the LiveKit Android SDK (self-hostable); ringing needs FCM high-priority plus a call UI |
| Group calls | Skip | After 1:1 calls work |
| Screen sharing | Skip |  |

### Notifications

| Feature | Phase | Notes |
| --- | --- | --- |
| Push for new messages | P1.5 | Cloud Function on message create, sends via FCM |
| Reply from notification | P3 | Needs native config |
| Per-chat mute and custom tones | P3 |  |

### Privacy and security

| Feature | Phase | Notes |
| --- | --- | --- |
| Firestore security rules | P1 | Non-negotiable |
| App lock (biometric) | P2 |  |
| Privacy settings (last seen, photo, About visibility) | P2 | Per-field audience setting |
| Read receipts on/off | P2 |  |
| End-to-end encryption | Skip | Not practical with this stack. Use transport and at-rest encryption, say so honestly in the privacy policy |
| Two-step verification | P3 |  |

### Settings and extras

| Feature | Phase | Notes |
| --- | --- | --- |
| Dark / light theme | P1 |  |
| Chat wallpaper | P3 |  |
| Font size | P3 |  |
| Storage usage / media auto-download | P3 |  |
| Chat backup and export | P3 |  |
| Delete account | P2 | Required by Play Store |
| Meta-AI-style assistant | Skip | Optional later, via Claude API through a Cloud Function (never from the app) |
| Business tools (catalog, payments) | Skip | Separate product |

## 3. Tech stack

- **Client:** Native Android, Java 17, Material 3, AndroidX (Navigation, ViewModel, LiveData, RecyclerView), Glide for images
- **State:** ViewModel + LiveData for UI state; Firestore snapshot listeners for server data
- **Backend:** Firebase Auth, Firestore, Storage, Cloud Functions, FCM, Realtime Database (presence)
- **Builds:** Gradle from the command line (no Android Studio), installed to your phone over adb; GitHub Actions as a cloud fallback
- **Calls (later):** LiveKit
- **Monitoring:** Crashlytics or Sentry, Firebase Analytics

## Setup (Ubuntu, no Android Studio)

The command-line SDK is a few GB lighter than Android Studio and enough for Gradle builds.

```bash
sudo apt update && sudo apt install -y openjdk-17-jdk unzip adb
# download "Command line tools only" for Linux from developer.android.com/studio
mkdir -p ~/android-sdk/cmdline-tools
unzip commandlinetools-linux-*.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
echo 'export ANDROID_HOME=$HOME/android-sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc
source ~/.bashrc
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

1. Install Gradle once (SDKMAN: `curl -s https://get.sdkman.io | bash`, then `sdk install gradle`), run `gradle wrapper`, and use `./gradlew` from then on.
2. On your phone: turn on Developer options, then USB debugging (or Wireless debugging).
3. Build and install: `./gradlew installDebug`
4. Firebase console: add an Android app with package `com.tulia.app`, put `google-services.json` in `app/`, and add the SHA-1 and SHA-256 from `./gradlew signingReport`.

## 4. Data model (Firestore)

```
users/{uid}
  name, username, photoURL, about, pushTokens[], blocked[], settings{}, createdAt

chats/{chatId}
  type: "direct" | "group"
  members[], admins[], name, photoURL
  lastMessage{ text, senderId, type, at }
  lastMessageAt
  unread{ uid: count }
  typing{ uid: timestamp }
  pinnedBy[], mutedBy[], archivedBy[]

chats/{chatId}/messages/{msgId}
  senderId, type (text|image|video|audio|file|location)
  text, mediaURL, thumbURL, duration, size
  replyTo, reactions{ uid: emoji }, mentions[]
  createdAt, editedAt, deletedFor[], deletedForAll
  deliveredTo[], readBy[]

usernames/{username}  -> uid   (enforces unique usernames)
```

Direct chat id: `[uidA, uidB].sort().join("_")`.

Realtime Database for presence: `status/{uid} = { online, lastSeen }` with `onDisconnect()`.

Storage paths: `avatars/{uid}`, `chats/{chatId}/{msgId}/{file}`.

## 5. Cloud Functions

1. `onMessageCreate`: update `lastMessage`, `lastMessageAt`, increment `unread` for other members, send push (skip muted and blocked)
2. `onUserCreate`: create profile doc
3. `onUserDelete`: clean up user data
4. `cleanupExpired` (scheduled): disappearing messages and expired statuses
5. `createGroup`, `addMember`, `removeMember`: validate admin rights server-side
6. `matchContacts` (later): hashed phone number lookup

## 6. Security rules (principles)

- Only chat members can read or write a chat and its messages.
- Users can only write their own `users/{uid}` doc.
- Messages: `senderId` must equal `request.auth.uid`; no editing others' messages.
- Group membership and admin changes only through Cloud Functions.
- Storage: size and content-type limits; read only for chat members.
- Add Firebase App Check before public release.

## 7. Screens and navigation

```
com.tulia.app/
  ui/auth/       WelcomeFragment, LoginFragment, SignupFragment, ProfileSetupFragment
  ui/main/       MainActivity (bottom nav) -> ChatsFragment, StatusFragment, CallsFragment, SettingsFragment
  ui/chat/       ChatFragment, ChatInfoFragment, MediaViewerFragment
  ui/contacts/   NewChatFragment, NewGroupFragment, ProfileFragment
  ui/settings/   Account, Privacy, Notifications, Appearance, Storage
  data/          AuthRepo, UserRepo, ChatRepo, models
  service/       TuliaMessagingService (FCM)
```

## 8. Build roadmap

**Phase 0: Foundation.** Project, Firebase config, theme, auth-gated navigation, debug APK installing on your phone over adb.

**Phase 1: MVP.** Email / Google login, profile, user search, 1:1 real-time chat, chat list, unread counts, ticks, image sending, security rules, push notifications.

**Phase 2: Core polish.** Typing and presence, replies, reactions, edit / delete, voice notes, documents, video, groups with admins, pin / archive / mute, app lock, privacy settings, delete account.

**Phase 3: Advanced.** Phone OTP, contact discovery, status, voice and video calls, disappearing messages, view-once, polls, channels, search, backup.

**Release checklist.** App icon and splash, Play Store listing, privacy policy, data-deletion flow, App Check, Crashlytics, test on low-end Android, signed AAB via Gradle.

## 9. Risks and trade-offs

- **Firestore cost:** every message listener read costs money. Paginate (load 30 at a time), never listen to whole collections.
- **Offline:** enable Firestore persistence; show pending state for unsent messages.
- **Phone auth and calls** work natively with no extra build tricks, but need your SHA-1 and SHA-256 fingerprints registered in Firebase. Java is fully supported, though most new Android docs and samples are Kotlin, so expect to translate.
- **No E2EE** with this stack. Be upfront about it.
- **Spam and abuse:** rate-limit message sends, add report and block early.

## 10. opencode prompt sequence

Feed one prompt at a time and test on your phone before moving on.

1. Scaffold: "Native Android app in Java (no Kotlin), Gradle Groovy DSL, package com.tulia.app, minSdk 26, Material 3, ViewBinding, single-activity with the Navigation component, Firebase BoM + google-services plugin. Light/dark theme, green accent. Must build with `./gradlew assembleDebug` from the command line."
2. Auth: "Email/password and Google sign-in, persisted session, create `users/{uid}` on signup, profile setup screen with photo upload."
3. Users: "User search by username, unique username claim via `usernames/{username}` in a transaction."
4. Chat core: "ChatFragment with RecyclerView + ListAdapter/DiffUtil, Firestore snapshot listener, 30-message pagination, optimistic send; chat list with last message and unread count."
5. Rules: "Write Firestore and Storage security rules for the data model in this blueprint, plus emulator tests."
6. Receipts and media: "Delivered / read ticks, image sending with compression, Glide for display."
7. Push: "Cloud Function `onMessageCreate` sending FCM (mute and block aware), `TuliaMessagingService`, notification channel, POST\_NOTIFICATIONS permission for Android 13+."
8. Polish: typing, presence, replies, reactions, edit / delete.
9. Groups: group creation and admin functions.
10. Advanced: voice notes, status, phone auth, calls with LiveKit.
