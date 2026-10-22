# Tulia Design Spec

Red and white chat app for Android (Java, Material 3 components, Firebase).
"Tulia" means "settle down, be calm" in Swahili. The design should feel calm, warm and unhurried, never loud.

## 1. The one rule

**Red is your voice. White is everyone else, and the space around it.**

- Red appears only on things that are *yours* or *actionable*: your sent bubbles, the send button, the main button, unread counts, the active tab.
- Everything else is white or soft blush. No red backgrounds on screens, no red headers, no red decoration.
- The memorable moment is one thing: your sent bubbles carry a red gradient that is fixed to the screen, so the colour shifts from bright to deep as messages scroll up and down.

## 2. Colours

### Light theme

| Token | Name | Hex | Use |
|---|---|---|---|
| `tulia_red` | Hibiscus | `#D62839` | Primary buttons, active icons, links, focus ring |
| `tulia_red_light` | Poppy | `#DC2F42` | Gradient start (top of screen) |
| `tulia_red_deep` | Ember | `#A8111E` | Gradient end (bottom of screen), pressed state |
| `tulia_blush` | Blush | `#FFE9EC` | Chips, reaction pills, selected rows, search field |
| `tulia_blush_line` | Petal | `#F3D9DD` | 1dp borders, dividers |
| `tulia_bg` | Milk | `#FFF9F8` | Chat background, screen background |
| `tulia_surface` | White | `#FFFFFF` | Received bubbles, cards, top bar, sheets |
| `tulia_ink` | Ink | `#2B1B1E` | Primary text |
| `tulia_dust` | Dust | `#7E6B6F` | Secondary text, timestamps |
| `tulia_on_red` | Chalk | `#FFFFFF` | Text and icons on red |

### Dark theme

| Token | Hex | Use |
|---|---|---|
| `bg` | `#140C0D` | Screen background |
| `surface` | `#1F1416` | Top bar, sheets, received bubbles |
| `surface_high` | `#2A1B1E` | Input field, chips |
| `line` | `#3A262A` | Dividers |
| `red` | `#FF5A6B` | Primary buttons, active icons (lifted for contrast) |
| `gradient` | `#D62839` to `#8F0F1B` | Sent bubbles (slightly deeper, easier on the eyes) |
| `ink` | `#FBEFF0` | Primary text |
| `dust` | `#B59DA1` | Secondary text |

### Gradients

| Name | Direction | Colours | Where |
|---|---|---|---|
| **Voice** | Vertical, anchored to the screen (top of screen to bottom of screen) | `#DC2F42` to `#A8111E` | Sent bubbles |
| **Tulia button** | 135 degrees | `#E5384B` to `#C21F2F` | FAB, send button, primary button (icon or large text only) |
| **Dawn** | Vertical, 160dp tall | `#FFE9EC` to `#FFFFFF` | Behind the top bar on the chat list; fades out as you scroll |
| **Status ring** | Sweep (angular) | `#D62839`, `#FF8A96`, `#D62839` | Ring around avatars with unseen status |

Rules:
- No gradients on text, cards, or full screens.
- White text on the Voice gradient passes 4.5:1 contrast at both ends. Do not lighten the start colour.
- Semantic colours: errors use Ember with an icon and a sentence, never red alone. Warnings use amber `#B7791F` with an icon.

## 3. Typography

Bundle both fonts in `res/font/` (free, OFL licence, from Google Fonts).

| Role | Font | Weight | Size / line height |
|---|---|---|---|
| App name, screen titles | Bricolage Grotesque | 700 | 24sp / 30sp |
| Section titles, contact names | Bricolage Grotesque | 600 | 17sp / 22sp |
| Message text, body | Figtree | 400 | 16sp / 22sp |
| Chat list preview | Figtree | 400 | 14sp / 19sp |
| Buttons, tabs | Figtree | 600 | 14sp / 20sp |
| Timestamps, ticks, captions | Figtree | 500 | 11sp / 14sp |

- Sentence case everywhere. No all-caps labels.
- Message text: left-aligned, max bubble width 78% of the screen.
- Emoji-only messages (1 to 3) render at 40sp with no bubble.

## 4. Shape, space and depth

- **Spacing grid:** 4dp base. Screen padding 16dp. Between messages from the same sender 2dp, between different senders 10dp.
- **Bubbles:** 20dp corners; the corner next to the sender's side on the *last* bubble of a group is 6dp (the "tail"). Sent bubbles are right, received are left.
- **Received bubble:** White fill, 1dp Petal border, no shadow.
- **Sent bubble:** Voice gradient, no border, no shadow.
- **Sheets and dialogs:** 28dp top corners, white, scrim `#2B1B1E` at 40%.
- **Search field, chips, badges:** Fully rounded (pill).
- **Avatars:** Circles. List 52dp, chat header 40dp, group member 44dp.
- **Elevation:** Almost flat. Top bar 0dp with a Petal divider appearing on scroll. Only the FAB and the open reaction bar cast a shadow, tinted `#D62839` at 25%.
- **Icons:** Rounded outline style, 24dp, 2dp stroke. Active icon is filled and red.

## 5. Components

### Chat list
- White top bar with "Tulia" in the display font; search and menu icons in Ink. Dawn gradient sits behind it.
- Row: avatar 52dp, name (600), last message in Dust (one line, ellipsis), time right-aligned in Dust.
- Unread: time turns red, a red pill badge with white count appears under it; the message preview turns Ink.
- Pinned chats show a small pin icon in Dust. Muted chats show a slashed bell.
- Row pressed: Blush fill. Row selected (long-press): Blush fill plus red check on the avatar.
- Empty state: "No chats yet. Tap the pencil to start one." with a simple red-outline bubble illustration.

### Bottom navigation
- 4 tabs: Chats, Status, Calls, Settings. White bar, Petal top divider.
- Active tab: red filled icon, Blush pill behind it, label in Ink. Inactive: Dust outline icon.

### FAB (new chat)
- 56dp circle, Tulia button gradient, white pencil icon, red-tinted shadow.
- Hides while scrolling down, returns on scroll up.

### Chat screen
- **Header:** White, back arrow, avatar 40dp, name (600), status line under it: "online" in red, "typing..." in red, or "last seen today 14:20" in Dust.
- **Background:** Milk by default. Optional wallpapers in settings: Plain, Blush wash, Dusk (dark).
- **Date chips:** Sticky white pill with Petal border, Dust text.
- **Sent bubble:** Voice gradient, Chalk text, timestamp and ticks bottom-right inside the bubble in white 70%.
- **Received bubble:** White, Ink text, timestamp in Dust.
- **Reply quote inside a bubble:** 3dp left bar (white on sent, red on received), quote text at 13sp, 80% opacity.
- **Reactions:** Small pill under the bubble, Blush fill, overlapping the bubble edge by 6dp.
- **Ticks:** One tick = sent, two ticks = delivered (white 60%), two ticks = read (white 100%, tiny pop animation once).
- **Voice note:** Play button in a white circle, waveform in white (sent) or red (received).
- **Image bubble:** 20dp corners, 2dp white inner padding, caption below.

### Message input bar
- White bar. Inside it: attach icon, a Blush pill text field (grows to 5 lines), camera icon.
- Focused field: 2dp red outline.
- Mic button (red gradient circle) swaps to a send arrow when text is present, with a 150ms scale-and-rotate transition.
- Hold mic to record: field is replaced with a red pulsing dot, timer, and "Slide to cancel".

### Status and avatars
- Unseen status: gradient sweep ring, 2.5dp, 3dp white gap. Seen: Petal ring.
- Online: 10dp red dot with a 2dp white ring, bottom-right of the avatar.
- No profile photo: Blush circle with initials in Ember.

### Buttons
- **Primary:** Hibiscus fill, Chalk text, 48dp tall, pill shape.
- **Secondary:** White fill, 1.5dp red outline, red text.
- **Text button:** Red text, no fill.
- **Destructive (delete, leave group):** Secondary style in Ember, always behind a confirm dialog that names what will be lost.

### Settings
- Grouped white cards on Milk, Petal dividers, icons in Ink, switches use red thumb-on / Blush track-on.

## 6. Motion and behaviour

| Interaction | Behaviour | Duration |
|---|---|---|
| Send message | Bubble slides up 8dp and fades in; list scrolls to bottom | 160ms |
| Receive message | Fades in; if you are scrolled up, show a mini FAB "2 new" instead of jumping | 160ms |
| Open chat | Shared-element avatar and name from the list to the header | 300ms |
| Screen transitions | Slide 24dp horizontally plus fade | 220ms |
| Long-press bubble | Bubble scales to 0.97, light haptic, reaction bar and menu appear above it | 120ms |
| Swipe to reply | Bubble follows the finger; at 56dp a reply icon pops in with a haptic tick and the bubble springs back | 200ms |
| Typing indicator | 3 red dots that gently breathe (scale 0.8 to 1.0, 1.6s loop, staggered). Never bounce fast. | 1600ms loop |
| Mic to send swap | Scale and rotate crossfade | 150ms |
| Delete message | Bubble collapses vertically and fades; undo snackbar for 5 seconds | 200ms |
| New unread badge | Scales in from 0.6 with a slight overshoot | 180ms |

- **Easing:** decelerate `cubic-bezier(0.05, 0.7, 0.1, 1)` for entering, accelerate `cubic-bezier(0.3, 0, 0.8, 0.15)` for exiting.
- **Idle:** Nothing animates on its own except the typing dots, the recording dot and the status ring when new.
- **Reduced motion:** If the system setting is on, replace slides and scales with plain fades.
- **Haptics:** Light on long-press, send, and swipe-reply threshold. Nothing else.
- **Offline:** A slim Blush banner at the top, "Waiting for network. Messages will send when you're back." Unsent messages show a clock icon instead of ticks.
- **Failed send:** Red-outlined bubble with a retry icon; tap to retry, long-press to delete.

## 7. Dark mode

- Follows the system by default, with a manual override (System / Light / Dark).
- Sent bubbles keep the Voice gradient (`#D62839` to `#8F0F1B`). Received bubbles use `surface`.
- Primary red lifts to `#FF5A6B` so it stays readable on dark surfaces.
- No pure black. Backgrounds are a warm near-black.

## 8. Accessibility

- Text contrast 4.5:1 minimum (body) and 3:1 (large text and icons).
- Touch targets 48dp minimum.
- Every icon button has a `contentDescription`.
- Visible focus ring: 2dp red outline for keyboard and switch access.
- Support system font scaling up to 200%; bubbles wrap, nothing truncates in the middle of a message.
- Never use colour alone to show state: unread has a count, errors have an icon and text, online has text in the header.

## 9. Android implementation notes (Java)

### `res/values/colors.xml`
```xml
<resources>
    <color name="tulia_red">#D62839</color>
    <color name="tulia_red_light">#DC2F42</color>
    <color name="tulia_red_deep">#A8111E</color>
    <color name="tulia_blush">#FFE9EC</color>
    <color name="tulia_blush_line">#F3D9DD</color>
    <color name="tulia_bg">#FFF9F8</color>
    <color name="tulia_surface">#FFFFFF</color>
    <color name="tulia_ink">#2B1B1E</color>
    <color name="tulia_dust">#7E6B6F</color>
    <color name="tulia_on_red">#FFFFFF</color>
</resources>
```
Put the dark values in `res/values-night/colors.xml` using the same names.

### Theme
- Parent: `Theme.Material3.DayNight.NoActionBar`.
- Map `colorPrimary` to `tulia_red`, `colorOnPrimary` to `tulia_on_red`, `colorSurface` to `tulia_surface`, `android:colorBackground` to `tulia_bg`, `colorOutline` to `tulia_blush_line`, `colorSecondaryContainer` to `tulia_blush`.
- Set `fontFamily` to Figtree, and use Bricolage Grotesque in `textAppearanceHeadline*`.

### Screen-anchored sent-bubble gradient
Use one custom view for all sent bubbles. The gradient is created once for the full screen height and shifted by the bubble's position on screen at draw time.

```java
public class SentBubbleLayout extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private final int[] loc = new int[2];
    private final LinearGradient shader;
    private float[] radii; // 8 values: tl, tr, br, bl (x,y each)

    public SentBubbleLayout(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        shader = new LinearGradient(0, 0, 0, screenH,
            getContext().getColor(R.color.tulia_red_light),
            getContext().getColor(R.color.tulia_red_deep),
            Shader.TileMode.CLAMP);
        paint.setShader(shader);
    }

    public void setTail(boolean lastInGroup) {
        float big = dp(20), small = dp(6);
        radii = new float[]{big,big, big,big, lastInGroup ? small : big, lastInGroup ? small : big, big,big};
        rebuildPath();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        rebuildPath();
    }

    private void rebuildPath() {
        if (radii == null) return;
        path.reset();
        path.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), radii, Path.Direction.CW);
    }

    @Override protected void onDraw(Canvas canvas) {
        getLocationOnScreen(loc);
        matrix.setTranslate(0, -loc[1]);
        shader.setLocalMatrix(matrix);
        canvas.drawPath(path, paint);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
```
In the chat `RecyclerView`, add an `OnScrollListener` that calls `invalidate()` on visible sent bubbles so the colour updates while scrolling.

### Other
- Fonts: download Bricolage Grotesque and Figtree from Google Fonts into `res/font/`.
- Icons: Material Symbols Rounded (outlined, weight 500) as vector drawables.
- Ripples: Blush at 40% opacity on white surfaces, white at 20% on red.
- Animations: `ViewPropertyAnimator` for small moves, `MaterialSharedAxis` and `MaterialContainerTransform` for screen changes.

## 10. Voice and copy

- Plain verbs, sentence case, short. "Send", "Add to group", "Delete for everyone". No jokes in error messages.
- Errors say what happened and what to do: "Message not sent. Check your connection and tap to retry."
- Empty screens invite one action: "No chats yet. Tap the pencil to start one."
- One name per action everywhere: if the button says "Delete for everyone", the confirmation says "Delete for everyone?" and the toast says "Deleted for everyone".

## 11. Prompt for opencode

> Apply TULIA_DESIGN.md to the Tulia Android app (Java, Material 3). Step 1 only: add `colors.xml` (light and night), the Material 3 theme mapping, Figtree and Bricolage Grotesque fonts, the type scale, and shape styles (20dp bubbles, pill chips, 28dp sheets). Then build `SentBubbleLayout` with the screen-anchored gradient and a `ReceivedBubble` style, and show both in a test activity with 20 sample messages. Don't touch Firebase code.
