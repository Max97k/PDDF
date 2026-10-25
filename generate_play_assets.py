import os
from PIL import Image, ImageDraw, ImageFont

ss_dir = r'c:\Users\b\PDDF\app\src\test\screenshots'
out_dirs = [
    r'c:\Users\b\PDDF\assets\google_play',
    r'C:\Users\b\.gemini\antigravity\brain\ebc2175b-c121-4e30-91ec-2ba7f36d0885'
]

font_tag = ImageFont.truetype(r'C:\Windows\Fonts\segoeuib.ttf', 24)
font_h1 = ImageFont.truetype(r'C:\Windows\Fonts\segoeuib.ttf', 54)
font_sub = ImageFont.truetype(r'C:\Windows\Fonts\segoeui.ttf', 30)

W, H = 1080, 1920  # Exact 9:16 aspect ratio

cards = [
    {
        'src': 'real_ss_1_empty.png',
        'out_base': 'screenshot_1_main_empty_state',
        'tag': '100% OFFLINE & PRIVATE',
        'title': 'Unlock Encrypted PDFs',
        'sub': 'Zero cloud uploads. Open directly from any app.',
        'bg_top': '#0B192C',
        'accent': '#9CCAFF',
        'pill_bg': '#00497D'
    },
    {
        'src': 'real_ss_2_batch_selection.png',
        'out_base': 'screenshot_2_batch_selection',
        'tag': 'HIGH-SPEED BATCH ENGINE',
        'title': 'Batch Decrypt & ZIP',
        'sub': 'Unlock multiple PDFs at once & inspect metadata.',
        'bg_top': '#0D1F36',
        'accent': '#9CCAFF',
        'pill_bg': '#1B5E91'
    },
    {
        'src': 'real_ss_3_success_preview.png',
        'out_base': 'screenshot_3_decrypted_success',
        'tag': 'BUILT-IN SECURE VIEWER',
        'title': 'Instant Preview & Share',
        'sub': 'Read in-app, re-protect, or auto-shred temp cache.',
        'bg_top': '#0B192C',
        'accent': '#D1E4FF',
        'pill_bg': '#00497D'
    },
    {
        'src': 'real_ss_4_light_theme.png',
        'out_base': 'screenshot_4_light_theme',
        'tag': 'MATERIAL DESIGN 3 & VAULT',
        'title': 'Adaptive DayNight UI',
        'sub': 'Light, Dark & AMOLED themes with Biometric Vault.',
        'bg_top': '#102A45',
        'accent': '#9CCAFF',
        'pill_bg': '#1B5E91'
    }
]

for c in cards:
    canvas = Image.new('RGBA', (W, H), c['bg_top'])
    d = ImageDraw.Draw(canvas)

    # Decorative background glow circles
    d.ellipse([W//2 - 620, -280, W//2 + 620, 960], fill='#132F52')
    d.ellipse([W//2 - 460, 420, W//2 + 460, 1340], fill='#173A65')

    # Top tag pill
    tag_bbox = d.textbbox((0, 0), c['tag'], font=font_tag)
    tw = (tag_bbox[2] - tag_bbox[0]) + 48
    th = 48
    tx = (W - tw) // 2
    ty = 86
    d.rounded_rectangle([tx, ty, tx + tw, ty + th], radius=24, fill=c['pill_bg'], outline=c['accent'], width=2)
    d.text((tx + 24, ty + 9), c['tag'], fill=c['accent'], font=font_tag)

    # Headline
    h_bbox = d.textbbox((0, 0), c['title'], font=font_h1)
    hw = h_bbox[2] - h_bbox[0]
    d.text(((W - hw) // 2, 156), c['title'], fill='#FFFFFF', font=font_h1)

    # Subtitle
    s_bbox = d.textbbox((0, 0), c['sub'], font=font_sub)
    sw = s_bbox[2] - s_bbox[0]
    d.text(((W - sw) // 2, 234), c['sub'], fill='#D1E4FF', font=font_sub)

    # Load raw screenshot (1080 x 2400) and scale to fit phone frame (w=780, h=1733)
    raw = Image.open(os.path.join(ss_dir, c['src'])).convert('RGBA')
    pw = 780
    ph = int(raw.height * (pw / raw.width))
    raw_resized = raw.resize((pw, ph), Image.Resampling.LANCZOS)

    px = (W - pw) // 2
    py = 315

    # Phone outer bezel (#263850 outline + #080C10 body)
    d.rounded_rectangle([px - 18, py - 18, px + pw + 18, py + ph + 18], radius=56, fill='#0A0E14', outline='#4A698C', width=4)

    # Mask screenshot with rounded corners
    mask = Image.new('L', (pw, ph), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, pw, ph], radius=42, fill=255)
    canvas.paste(raw_resized, (px, py), mask)

    # Speaker / camera punch-hole detail
    d.ellipse([W//2 - 12, py + 18, W//2 + 12, py + 42], fill='#1A222C')

    final_rgb = canvas.convert('RGB')
    base_name = c['out_base']
    for od in out_dirs:
        png_path = os.path.join(od, base_name + '.png')
        jpg_path = os.path.join(od, base_name + '.jpg')
        final_rgb.save(png_path, 'PNG', optimize=True)
        final_rgb.save(jpg_path, 'JPEG', quality=95)
    print(f'Saved {base_name}: {final_rgb.size}')
