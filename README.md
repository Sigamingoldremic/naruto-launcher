# Naruto Launcher (stage 1)
Open this folder in Android Studio, let it sync, then Run on your phone.
Needs PPSSPP (free or Gold) installed. On first PLAY, allow "all files access".

Edit `PspIso.ALLOWED` with your game's DISC_ID (shown under the PLAY button after you pick the ISO).
Check key names in `Emulator.iniText()` against PPSSPP's Config.cpp.
Stage 2: replace the Intent in `Emulator.launch()` with the embedded PPSSPP fork + libadrenotools.
