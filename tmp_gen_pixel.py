import os

grid = [
    "                                ",
    "                                ",
    "                                ",
    "                                ",
    "                                ",
    "          ........              ",
    "        ..@@@@@@@@..            ",
    "      ..@@@@@@@@@@@@..          ",
    "     .@@@@@@@@@@@@@@@@.         ",
    "     .@@@@@@@@........          ",
    "    .@@@@@@@..        .         ",
    "    .@@@@@@.           .        ",
    "   .@@@@@@@. L         .        ",
    "   .@@@@@@@.           .        ",
    "   .@@@@@@@.     ~     .        ",
    "  ..@@@@@@@.           .        ",
    "  .@@@@@@@@.           ..       ",
    "  .@@@@@@@@.           ..       ",
    "  ..@@@@@@@@...........         ",
    "    ........    ~               ",
    "                                ",
    "        L                       ",
    "                                ",
    "                                ",
    "                                ",
    "                                ",
    "                                ",
    "                                ",
    "                                ",
    "                                ",
    "                                ",
    "                                "
]

scale = 9
viewport = 32 * scale

def build_path(char_match):
    path_data = []
    for y, row in enumerate(grid):
        for x, char in enumerate(row):
            if char == char_match:
                sx = x * scale
                sy = y * scale
                path_data.append(f"M{sx},{sy} h{scale} v{scale} h-{scale} Z")
    return " ".join(path_data)

outline_path = build_path('.')
fill_path = build_path('@')
leaf_path = build_path('L')
wind_path = build_path('~')

xml = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="288dp"
    android:height="288dp"
    android:viewportWidth="{viewport}"
    android:viewportHeight="{viewport}">

    <group
        android:name="entire_logo"
        android:pivotX="{viewport/2}"
        android:pivotY="{viewport/2}">
        
        <group android:name="cloud_group">
            <path
                android:name="cloud_fill"
                android:fillColor="#33FFFFFF"
                android:pathData="{fill_path}" />
            <path
                android:name="cloud_outline"
                android:fillColor="#FFFFFF"
                android:pathData="{outline_path}" />
        </group>

        <group android:name="leaf_group">
            <path
                android:name="leaves"
                android:fillColor="#4CAF50"
                android:pathData="{leaf_path}" />
        </group>

        <group android:name="wind_group">
            <path
                android:name="wind"
                android:fillColor="#03DAC6"
                android:pathData="{wind_path}" />
        </group>
    </group>
</vector>
"""

with open(r"c:\Users\wwwaj\AndroidStudioProjects\AQI\app\src\main\res\drawable\ic_pixel_cloud.xml", "w") as f:
    f.write(xml)

print("Generated ic_pixel_cloud.xml successfully.")
