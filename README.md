# Voltage icon pack for Tecno Camon 18i

Clay-style 3D object icons for 15 system and Google apps, plus a preview site with iPhone-style open/close animation, swipeable pages and jiggle mode.

## Folders
- `android/` Android Studio project (icon pack APK)
- `web/` static preview site (Netlify)
- `animated/` animated WebP version of each icon (for widgets such as KWGT, or sharing)
- `.github/workflows/build.yml` builds the APK in the cloud

## Get the APK without a PC
1. Upload this whole folder to a new GitHub repo.
2. Open the Actions tab, run "Build APK".
3. Download `Voltage-Icon-Pack-APK` from the finished run, unzip it, install `app-debug.apk`.
   The build signs it with an auto-made debug key.

## Voltage is now a launcher (v2.0)
The APK is a standalone home screen, no Nova needed. After installing, press Home, choose **Voltage**, then **Always**.
- Swipe left/right for pages, swipe up for the app drawer (with search), dock at the bottom.
- Long-press an icon: remove, app info, uninstall. In the drawer: add to home. Long-press empty space: wallpaper.
- Themed apps use the clay icons (live clock and calendar date). Other apps get a generated 3D clay look (rounded body, shading, thick slab, shadow).
- Drag: long-press an icon and move it. Drop on another icon's centre to make a folder, between icons to reorder, on the top bar to remove / app info / uninstall. Hold at a screen edge to change page. Long-press without moving gives the menu.
- Folders: tap to open, rename at the top, long-press an app inside to move it out.
- Widgets: long-press empty space > Add widget. Use the dots button on a widget to resize, move or remove it.
- Swipe down on the home screen to open notifications.
- To go back to your old launcher: Settings > Apps > Default apps > Home app.

## Settings (v3.5)
Long-press empty space > **Voltage settings**.
- **Wallpaper**: two built-in wallpapers (Crimson cross is applied on first launch), pick from Gallery, **Crop and position** (drag and pinch), **Blur** slider, and **Live / 3D** (opens the phone's live wallpaper picker).
- **Icon style**: 3D (clay), Round, Square, Squircle, Glass. Themed clay icons sit on the tile; other apps are cut into the shape.
- **Icon size**: 70% to 130%, applies to home, dock, folders and the app drawer.

- **Icon labels**: show or hide app names on home, text size 9 to 15. The drawer and folder windows always show names.
- **Home screen layout**: 3 to 6 columns, 4 to 7 rows. Icons and widgets reflow.
- **App drawer**: on/off, 3 to 6 columns, scroll style (vertical, vertical with fast scroller, horizontal pages). Off puts every app on the home screen and swipe up opens search only.
- **App search**: swipe up and type. Matches apps, phone settings (Wi-Fi, Bluetooth, display and more) and, if you allow it, contacts.
- **Widgets**: Add widget button, show or hide the three-dot menu on widgets, lock widgets, corner radius 0 to 32, spacing 0 to 12. Resize from the three-dot menu (width and height steppers). Arrange by long-pressing a widget and dragging it to another spot or page; drop it on Remove to delete. Long-press without moving opens the menu.
- **Dock**: show or hide, 3 to 6 icons (icons shrink to fit), background opacity, corner radius. **Choose dock apps** lists the slots; or long-press a dock icon (Replace, Remove, info, uninstall), or long-press any app on home or in the drawer and pick **Add to dock**.
- **Gestures**: Swipe up (default app drawer), Swipe down (default notifications), Double-tap empty space, Pinch in, Pinch out. Each can be Nothing, App drawer, Search, Notifications, Quick settings, Voltage settings, Home menu or Open an app. Swipe sensitivity 1 to 5. Swipes that start on a widget go to the widget.
- **Notification badges**: Dots, Numbers or Hidden; on or off for dock icons and folders (a folder shows the total of its apps). Needs **Allow notification access** (Voltage badges). On Android 13+ a sideloaded app may need Settings > Apps > Voltage > three dots > Allow restricted settings first. Counts skip ongoing notifications (music, calls) and group summaries.
- **Animation effects**: app opening (Zoom, Fade, Slide up, None), coming back home (Zoom, Fade, Slide down, None), page scrolling (Slide, Cube, Zoom, Fade), folder opening (Pop, Fade, Slide up, None), and an animation speed of 50 to 200%.
- **Folder style**: Glass, Transparent, 3D or Rounded, for both the home tile and the folder window, plus folder size 70 to 130%.
- **Theme**: Dark, Light, Auto (follows the phone) or Custom (pick an accent colour and a background). Colours the settings screen, app drawer and folder windows; home names stay white over the wallpaper.
- **3D effects**: icon shadows, a depth slider (shadow height, parallax travel, cube tilt), tilt parallax (icons shift against the wallpaper as you tilt the phone), wallpaper that moves with the pages, and floating icons.
- **Screen lock**: glossy 3D numbers float around at random. Tap them smallest to biggest; the biggest unlocks. Wrong tap starts over. Choose 4 to 9 numbers and the speed, lock when the screen turns off, preview, or lock now. It protects Voltage only; keep the phone's own lock on.
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
