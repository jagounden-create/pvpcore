#!/usr/bin/env python3
"""
Draws every menu and hologram, writes the Nexo glyph configs, and zips it all up laid out like
the plugins/Nexo folder, so the zip can be unpacked straight into it.

Run:  python3 package.py      (needs Pillow: pip install pillow)
"""
import os
import zipfile

import generate
import holograms

ROOT = os.path.dirname(os.path.abspath(__file__))
ZIP_NAME = "nexo-gui-pack.zip"


def menus_yaml(sizes):
    lines = [
        "# Menu backgrounds. The image starts %d px above the menu (for the sign):" % generate.TOP,
        "# ascent = 13 + %d, height = the image's height, so it shows at 1:1 scale." % generate.TOP,
        "# Title of each menu:  <white><shift:-8><glyph:ID>",
        "",
    ]
    for name, size in sizes.items():
        lines += [f"{name}:", f"  texture: menus/{name}", f"  ascent: {size['ascent']}", f"  height: {size['height']}", ""]
    return "\n".join(lines)


def holograms_yaml(sizes):
    lines = [
        "# Hologram logos. ascent = height, so the logo sits on top of its hologram line.",
        "# Lower height (keep ascent equal to it) for a smaller logo, or scale the hologram.",
        "# Hologram line:  %nexo_ID%   (needs PlaceholderAPI)",
        "",
    ]
    for name, (w, h) in sizes.items():
        lines += [f"{name}:", f"  texture: holograms/{name}", f"  ascent: {h}", f"  height: {h}", ""]
    return "\n".join(lines)


def main():
    menu_sizes = generate.main()
    holo_sizes = holograms.main()
    os.makedirs(os.path.join(ROOT, "nexo"), exist_ok=True)
    configs = {
        "menus_glyphs.yml": menus_yaml(menu_sizes),
        "holograms_glyphs.yml": holograms_yaml(holo_sizes),
    }
    for name, text in configs.items():
        with open(os.path.join(ROOT, "nexo", name), "w") as f:
            f.write(text)

    with zipfile.ZipFile(os.path.join(ROOT, ZIP_NAME), "w", zipfile.ZIP_DEFLATED) as z:
        for name in menu_sizes:
            z.write(os.path.join(ROOT, "textures", name + ".png"), f"pack/assets/minecraft/textures/menus/{name}.png")
        for name in holo_sizes:
            z.write(os.path.join(ROOT, "holograms", name + ".png"), f"pack/assets/minecraft/textures/holograms/{name}.png")
        for name in configs:
            z.write(os.path.join(ROOT, "nexo", name), f"glyphs/{name}")
        z.write(os.path.join(ROOT, "README.txt"), "README.txt")
        for name in ("all_menus.png", "all_menus_gray.png", "all_holograms.png"):
            z.write(os.path.join(ROOT, "previews", name), f"previews/{name}")

    print(f"{len(menu_sizes)} menus, {len(holo_sizes)} holograms -> {ZIP_NAME}")


if __name__ == "__main__":
    main()
