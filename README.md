[this modrinth page](https://modrinth.com/mod/elytrapitchhelper) (modrinth[dot]com/mod/elytrapitchhelper) and [github releases](https://github.com/asphyxiamywife/ElytraPitchHelper/releases) are the only officially maintained places to download

While gliding, it shows subtle guide lines at +40° and –40° pitch, so you don’t have to rely on the cluttered F3 debug screen. This makes it easier to stay focused on smooth and efficient flight. The indicators appear only when you’re wearing Elytra and are close to these pitch angles, keeping your screen clean the rest of the time.

![output](https://github.com/user-attachments/assets/5586de9c-9d75-4815-b7e1-e0d6782f2cb6)


Starting with 1.1.0, configuration options availlable:

<details>
<summary>targetUpMinecraft (float, degrees)</summary>

What: Desired pitch target when gliding “nose up.”

Default: -40.0 (Minecraft uses negative for up)

Change when: You prefer a gentler/steeper climb target.

Suggested range: -60.0 to -10.0
</details>


<details>
<summary>targetDownMinecraft (float, degrees)</summary>

What: Desired pitch target when gliding “nose down.”

Default: 40.0 (positive is down)

Change when: You prefer faster dives or milder descents.

Suggested range: 10.0 to 60.0
</details>


<details>
<summary>toleranceDegrees (float, degrees)</summary>

What: Angle window around a target where the guide fades in. Smaller = stricter “on target” cue.

Default: 6.0

Change when: The guide feels too sensitive or too forgiving.

Suggested range: 2.0 to 12.0
</details>


<details>
<summary>maxOffsetPixels (int, px)</summary>

What: Maximum distance the guide line can move from the screen center.

Default: 42

Change when: On high/low resolutions the guide travels too far or too little.

Suggested range: 24 to 80
</details>


<details>
<summary>offsetPerDegree (float, px/deg)</summary>

What: Sensitivity mapping; how many pixels the guide moves per degree of pitch 

difference.

Default: 2.0

Change when: You want finer/coarser motion of the guides.

Suggested range: 0.8 to 3.5
</details>


<details>
<summary>lineColorRgb (int, RGB 0xRRGGBB)</summary>

What: Color of the horizontal guide line.

Default: 16777215 (white, 0xFFFFFF)

Change when: You want better contrast with your resource pack or sky.

Tips: Use hex in the GUI (e.g., FFFFFF, 00FF00) or decimal in json.
</details>


<details>
<summary>centerTickColorRgb (int, RGB 0xRRGGBB)</summary>

What: Color of the small center tick marker.

Default: 16744703 (magenta-ish, 0xFF80FF)

Change when: You want the center tick to pop or match your HUD theme.
</details>

1.1.2-lite, added:
<details>
<summary>showOnlyWithFirework (bool, true/false)</summary>

What: Show guide lines only when firework present in hand.

Default: false

Change when: You fly with fireworks and want minimal setup.
</details>

