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
- Themed apps use the clay icons (live clock and calendar date). Other apps get their own icon on a dark tile.
- Drag: long-press an icon and move it. Drop on another icon's centre to make a folder, between icons to reorder, on the top bar to remove / app info / uninstall. Hold at a screen edge to change page. Long-press without moving gives the menu.
- Folders: tap to open, rename at the top, long-press an app inside to move it out.
- Widgets: long-press empty space > Add widget. Use the dots button on a widget to resize, move or remove it.
- Swipe down on the home screen to open notifications.
- To go back to your old launcher: Settings > Apps > Default apps > Home app.

## Apply it in another launcher (optional)
Install Nova Launcher, set it as default, then Settings > Look and feel > Icon style > Icon theme > Voltage.

## Notes
- Icons only change for apps listed in `android/app/src/main/res/xml/appfilter.xml`. HiOS app IDs differ between versions. To add one, copy a line and change the component.
- Live icons on the phone: the **clock** ticks in real time and the **calendar** shows today's date (standard icon-pack features; the launcher must support them, e.g. Nova for the calendar, Lawnchair for both). Other icons stay still; their idle motion is in the web preview and `animated/`. Open/close transitions belong to the launcher (Nova > Look and feel > Animation speed).
- Deploy `web/` on Netlify: drag the folder in, or connect the repo (settings come from `netlify.toml`).
