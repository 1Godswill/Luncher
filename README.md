# FEG Launcher (Voltage icon pack and launcher) for Tecno Camon 18i

Clay-style 3D object icons for 15 system and Google apps, plus a preview site with iPhone-style open/close animation, swipeable pages and jiggle mode.

## Folders
- `android/` Android Studio project (icon pack APK)
- `web/` static preview site (Netlify)
- `animated/` animated WebP version of each icon (for widgets such as KWGT, or sharing)
- `.github/workflows/build.yml` builds the APK in the cloud

## Get the APK without a PC
1. Upload this whole folder to a new GitHub repo.
2. Open the Actions tab, run "Build APK".
3. Download `FEG-Launcher-APK` from the finished run, unzip it, install `app-debug.apk`.
   The build signs it with an auto-made debug key.

## Voltage is now a launcher (v2.0)
The APK is a standalone home screen, no Nova needed. After installing, press Home, choose **Voltage**, then **Always**.
- Swipe left/right for pages, swipe up for the app drawer (with search), dock at the bottom.
- Long-press an icon: remove, app info, uninstall. In the drawer: add to home. Long-press empty space: wallpaper.
- Themed apps use the clay icons (live clock and calendar date). Other apps get a generated 3D clay look (rounded body, shading, thick slab, shadow).
- Drag: long-press an icon and move it. Drop on another icon's centre to make a folder, between icons to reorder, on the top bar to remove / app info / uninstall. Hold at a screen edge to change page. Long-press without moving gives the menu.
- Folders: tap to open, rename at the top, long-press an app inside to move it out.
- Widgets: long-press empty space > Add widget. Long-press a widget and let go, then drag its handles to resize (or tap the red X to remove). The dots button has more options.
- Swipe down on the home screen to open notifications.
- A faint "feg-tech" watermark sits at the bottom left of the home screen.
- To go back to your old launcher: Settings > Apps > Default apps > Home app.

## What's new in v3.8
- **Badge counters now draw in front of the icon** (they were hidden behind icons that have the 3D shadow effect on).
- **Renamed FEG Launcher** with the new logo as app icon and favicon. The name shown in the app, settings and messages is now FEG Launcher. See `FEG-PLAY-SETUP.md` (run `python3 tools/patch_manifest.py`) to apply the name and icon to the manifest.
- **FEG-PLAY music player on the home screen.** A built-in player that sits on your home screen like a widget. It is added once, at the top of the first page, the first time v3.8 starts. Remove it like any widget (drag to the top bar, or the three-dot menu), and add it back from long-press empty space > Add widget > **FEG-PLAY - music player** (first in the list).
  - One row high = small player: album art, song, previous, play/pause, next, thin progress line. Two rows or more = full widget: art, song and artist, draggable progress bar with times, shuffle, previous, play/pause, next, repeat. Resize it like any widget (long-press, let go, drag the handles).
  - Tap the art or song name to open the full FEG-PLAY screen: **Playing**, **Songs**, **Albums**, **Artists**, **Favourites**, **Queue**, with search, Play all and Shuffle, a mini player under the lists, sleep timer (15 min to 1 h 30), volume slider, favourite heart. Long-press a song (or tap its three dots): Play next, Add to queue, favourite, remove from queue.
  - Plays the music already on the phone (MP3, M4A, FLAC, WAV, OGG and anything else Android can play). Shuffle and repeat (off / all / one) are remembered, and it picks up the last song where you stopped.
  - Keeps playing when you leave Voltage, with a notification (previous / play-pause / next) and lock-screen controls. Pauses when headphones are unplugged or another app takes the sound (calls, videos), and resumes after short interruptions. Headset buttons work.
  - First tap on play asks for permission to read your music (and to show the notification on Android 13+).
- **Needs a one-time manifest edit** (name, icon, permissions and the playback service). See `FEG-PLAY-SETUP.md`, or run `python3 tools/patch_manifest.py`.
- Source: `FegEngine.java` (library, queue, playback, focus, lock-screen session), `FegPlayService.java` (notification and background playback), `FegPlayerView.java` (home widget), `FegPlayerScreen.java` (full screen), `FegUi.java` (icons, seek bar, art placeholder). The home screen treats FEG-PLAY as a widget with the special id `FEG_WID` in `MainActivity.java`.

## What's new in v3.7
- **Resize and remove widgets by dragging.** Long-press a widget and let go: a white frame appears with handles on the right edge, the bottom edge and the corner. Drag a handle and the widget snaps to whole grid cells (it follows your finger live). Tap the red X on the frame to remove it, or long-press and drag the widget to the top bar (the whole bar is Remove for widgets and folders). Tap anywhere else, or press Back, to close the frame. Long-press now works even on widgets whose content is clickable (clocks, lists, music players).
- **Endless scrolling.** Swipe past the last home page and you land on the first; swipe back from the first and you land on the last. The paged app drawer (Horizontal pages) loops too. Dragging an icon to the screen edge keeps turning pages around. Turn it off in Voltage settings > Home screen layout > Endless scrolling. Notes: while a page wraps, a widget on the page that is coming into view is shown as a picture of itself, and with "wallpaper moves with pages" on, the wallpaper jumps back at the wrap.
- **No two apps share an icon.** A clay icon from the pack is used by one app only (the exact match wins, e.g. the real phone dialer keeps the Phone icon). Any other app that the pack would have given the same icon (a second Messages app, a second Gmail-type app, a cloned app...) gets its own icon built from its real logo in your chosen style. If two apps still end up looking the same or nearly the same, the later one gets a small coloured letter tag in the corner, a different colour for each, so you can always tell them apart.

## Settings (v3.8)
Long-press empty space > **Voltage settings**.
- **Wallpaper**: two built-in wallpapers (Crimson cross is applied on first launch), pick from Gallery, **Crop and position** (drag and pinch), **Blur** slider, and **Live / 3D** (opens the phone's live wallpaper picker).
- **Icon style**: 3D (clay), Round, Square, Squircle, Glass. Themed clay icons sit on the tile; other apps are cut into the shape.
- **Icon size**: 70% to 130%, applies to home, dock, folders and the app drawer.

- **Icon labels**: show or hide app names on home, text size 9 to 15. The drawer and folder windows always show names.
- **Home screen layout**: 3 to 6 columns, 4 to 7 rows, and **Endless scrolling** on/off. Icons and widgets reflow.
- **App drawer**: on/off, 3 to 6 columns, scroll style (vertical, vertical with fast scroller, horizontal pages). Off puts every app on the home screen and swipe up opens search only.
- **App search**: swipe up and type. Matches apps, phone settings (Wi-Fi, Bluetooth, display and more) and, if you allow it, contacts.
- **Widgets**: Add widget button, show or hide the three-dot menu on widgets, lock widgets, corner radius 0 to 32, spacing 0 to 12. Resize by dragging the handles that appear when you long-press a widget and let go (or use the three-dot menu: Resize by dragging, or Resize with buttons). Arrange by long-pressing a widget and dragging it to another spot or page; drop it on the top bar to delete, or tap the red X on its frame.
- **Dock**: show or hide, 3 to 6 icons (icons shrink to fit), background opacity, corner radius. **Choose dock apps** lists the slots; or long-press a dock icon (Replace, Remove, info, uninstall), or long-press any app on home or in the drawer and pick **Add to dock**.
- **Gestures**: Swipe up (default app drawer), Swipe down (default notifications), Double-tap empty space, Pinch in, Pinch out. Each can be Nothing, App drawer, Search, Notifications, Quick settings, Voltage settings, Home menu or Open an app. Swipe sensitivity 1 to 5. Swipes that start on a widget go to the widget.
- **Notification badges**: Dots, Numbers or Hidden; on or off for dock icons and folders (a folder shows the total of its apps). Needs **Allow notification access** (Voltage badges). On Android 13+ a sideloaded app may need Settings > Apps > Voltage > three dots > Allow restricted settings first. Counts skip ongoing notifications (music, calls) and group summaries.
- **Animation effects**: app opening (Zoom, Fade, Slide up, None), coming back home (Zoom, Fade, Slide down, None), page scrolling (Slide, Cube, Zoom, Fade), folder opening (Pop, Fade, Slide up, None), and an animation speed of 50 to 200%.
- **Folder style**: Glass, Transparent, 3D or Rounded, for both the home tile and the folder window, plus folder size 70 to 130%.
- **Theme**: Dark, Light, Auto (follows the phone) or Custom (pick an accent colour and a background). Colours the settings screen, app drawer and folder windows; home names stay white over the wallpaper.
- **3D effects**: icon shadows, a depth slider (shadow height, parallax travel, cube tilt), tilt parallax (icons shift against the wallpaper as you tilt the phone), wallpaper that moves with the pages, and floating icons.
- **Screen lock**: glossy 3D numbers float around at random. Pick the unlock rule: smallest to biggest, biggest to smallest, odd numbers only, even numbers only, odd or even smallest first, or random each time. The lock screen shows no instructions unless you switch on "Show the rule" (random always shows it). Wrong tap starts over. Choose 4 to 9 numbers and the speed, lock when the screen turns off, preview, or lock now. A small white dot at the bottom switches to the phone's own PIN, pattern or password. It protects Voltage only; keep the phone's own lock on. App lock uses the same rule.
- **Hide apps**: pick apps to hide from the drawer (and optionally from search). Long-press an app in the drawer and choose Hide app. Unhide from the same list.
- **App lock**: choose apps that ask for the number puzzle before opening from Voltage. Ask every time, after 1 min or after 5 min.
- **Backup and restore**: save everything (layout, folders, dock, icon style and size, all settings) to a JSON file you choose, restore from it, or reset to defaults. Wallpaper pictures are not included.
- **Performance and battery**: Balanced, Battery saver or Smooth; reduce animations; auto battery saver at 20% or when the phone's power saver is on; free memory when Voltage is in the background. Battery saver turns off page effects, floating icons, tilt parallax and most animations.

Source: `SettingsPanel.java` (screen; add new cards in `build()`), `BadgeService.java` (notification counts), `LockView.java` (number lock), `Theme.java` (colours), `IconStyler.java` (icon shapes), `WallpaperHelper.java` and `CropView.java` (wallpaper). Default wallpapers are in `res/drawable-nodpi/wallpaper_cross_*.jpg`.

## Apply it in another launcher (optional)
Install Nova Launcher, set it as default, then Settings > Look and feel > Icon style > Icon theme > Voltage.

## Notes
- Icons only change for apps listed in `android/app/src/main/res/xml/appfilter.xml`. HiOS app IDs differ between versions. To add one, copy a line and change the component.
- Live icons on the phone: the **clock** ticks in real time and the **calendar** shows today's date (standard icon-pack features; the launcher must support them, e.g. Nova for the calendar, Lawnchair for both). Other icons stay still; their idle motion is in the web preview and `animated/`. Open/close transitions belong to the launcher (Nova > Look and feel > Animation speed).
- Deploy `web/` on Netlify: drag the folder in, or connect the repo (settings come from `netlify.toml`).
