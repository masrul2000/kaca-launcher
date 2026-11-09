"""Render ikon kaca orisinal (glass orb) ke PNG + tulis appfilter.xml & drawable.xml."""
import io, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import cairosvg
from icons_map import ICONS

GLYPHS = os.path.join(os.path.dirname(__file__), "node_modules/@material-design-icons/svg/round")
OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
FINAL = 256
SS = 2
S = FINAL * SS
CX, CY, R = S / 2, S * 0.47, S * 0.42


def hex2rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(c, t, k):
    return tuple(int(a + (b - a) * k) for a, b in zip(c, t))


def disc_mask(r=R, cx=CX, cy=CY):
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    return m


def vgrad(top, bottom, y0=0, y1=S):
    """Gradient vertikal RGBA."""
    g = Image.new("RGBA", (1, S))
    for y in range(S):
        k = min(1, max(0, (y - y0) / max(1, (y1 - y0))))
        g.putpixel((0, y), tuple(int(a + (b - a) * k) for a, b in zip(top, bottom)))
    return g.resize((S, S))


def glyph_img(name, size, color="#ffffff"):
    svg = open(os.path.join(GLYPHS, name + ".svg")).read().replace("<svg ", f'<svg fill="{color}" ', 1)
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=size, output_height=size)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def glass(color_hex, glyph=None, base_alpha=196, frost=False, upon_only=False):
    c = hex2rgb(color_hex)
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    disc = disc_mask()

    if not upon_only:
        # bayangan lembut
        sh = Image.new("L", (S, S), 0)
        ImageDraw.Draw(sh).ellipse((CX - R * 0.92, CY - R * 0.86 + R * 0.16, CX + R * 0.92, CY + R * 0.98 + R * 0.16), fill=95)
        sh = sh.filter(ImageFilter.GaussianBlur(S * 0.035))
        canvas.alpha_composite(Image.merge("RGBA", (sh.point(lambda _: 0),) * 3 + (sh,)))

        # badan kaca: gradien diagonal warna, sedikit tembus pandang
        top = mix(c, (255, 255, 255), 0.30 if not frost else 0.55)
        bot = mix(c, (0, 0, 0), 0.18 if not frost else 0.05)
        body = vgrad(top + (base_alpha,), bot + (base_alpha,), CY - R, CY + R)
        body.putalpha(ImageChops.multiply(body.getchannel("A"), disc))
        canvas.alpha_composite(body)

        # cahaya bias di bagian bawah (refraksi)
        glow = Image.new("L", (S, S), 0)
        ImageDraw.Draw(glow).ellipse((CX - R * 0.85, CY + R * 0.25, CX + R * 0.85, CY + R * 1.25), fill=110)
        glow = glow.filter(ImageFilter.GaussianBlur(S * 0.04))
        glow = ImageChops.multiply(glow, disc)
        light = mix(c, (255, 255, 255), 0.65)
        canvas.alpha_composite(Image.merge("RGBA", tuple(Image.new("L", (S, S), v) for v in light) + (glow,)))

        # glyph putih + bayangan
        if glyph:
            gs = int(R * 1.0)
            g = glyph_img(glyph, gs)
            gx, gy = int(CX - gs / 2), int(CY - gs / 2)
            shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
            a = g.getchannel("A").point(lambda v: int(v * 0.32))
            sh_layer = Image.new("RGBA", g.size, (0, 0, 0, 0)); sh_layer.putalpha(a)
            shadow.paste(sh_layer, (gx, gy + int(S * 0.012)), sh_layer)
            shadow = shadow.filter(ImageFilter.GaussianBlur(S * 0.012))
            canvas.alpha_composite(shadow)
            # gradien putih tipis di glyph
            gl = Image.new("RGBA", (gs, gs), (255, 255, 255, 255))
            gl.putalpha(g.getchannel("A"))
            canvas.alpha_composite(gl, (gx, gy))

    # kilau atas (specular)
    spec = vgrad((255, 255, 255, 95), (255, 255, 255, 0), CY - R, CY - R * 0.1)
    sm = Image.new("L", (S, S), 0)
    ImageDraw.Draw(sm).ellipse((CX - R * 0.78, CY - R * 0.97, CX + R * 0.78, CY + R * 0.05), fill=255)
    sm = sm.filter(ImageFilter.GaussianBlur(S * 0.03))
    spec.putalpha(ImageChops.multiply(spec.getchannel("A"), ImageChops.multiply(sm, disc)))
    canvas.alpha_composite(spec)

    # garis tepi berkilau
    inner = disc_mask(R - S * 0.008)
    ring = ImageChops.subtract(disc, inner)
    rim = vgrad((255, 255, 255, 200), (255, 255, 255, 90), CY - R, CY + R)
    rim.putalpha(ImageChops.multiply(rim.getchannel("A"), ring))
    canvas.alpha_composite(rim)

    return canvas.resize((FINAL, FINAL), Image.LANCZOS)


def main():
    draw_dir = os.path.join(OUT, "drawable-nodpi")
    os.makedirs(draw_dir, exist_ok=True)
    for name, gl, color, _ in ICONS:
        glass(color, gl).save(os.path.join(draw_dir, f"{name}.png"), optimize=True)
    # untuk aplikasi yang belum punya ikon: piringan kaca netral + kilau di atasnya
    glass("#A9B4C8", None, base_alpha=170, frost=True).save(os.path.join(draw_dir, "iconback.png"), optimize=True)
    glass("#ffffff", None, upon_only=True).save(os.path.join(draw_dir, "iconupon.png"), optimize=True)

    xml_dir = os.path.join(OUT, "xml")
    os.makedirs(xml_dir, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>",
             '    <iconback img1="iconback" />', '    <iconupon img1="iconupon" />', '    <scale factor="0.56" />']
    for name, _, _, comps in ICONS:
        for comp in comps:
            if "/" not in comp:
                comp = f"{comp}/{comp}"
            lines.append(f'    <item component="ComponentInfo{{{comp}}}" drawable="{name}" />')
    lines.append("</resources>")
    open(os.path.join(xml_dir, "appfilter.xml"), "w").write("\n".join(lines) + "\n")

    d = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>", '    <version>1</version>', '    <category title="Semua" />']
    for name, *_ in sorted(ICONS):
        d.append(f'    <item drawable="{name}" />')
    d.append("</resources>")
    open(os.path.join(xml_dir, "drawable.xml"), "w").write("\n".join(d) + "\n")
    print("ok", len(ICONS))


if __name__ == "__main__":
    main()
