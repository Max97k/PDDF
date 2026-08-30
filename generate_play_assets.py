import os
from PIL import Image

output_dir = r'c:\Users\b\PDDF\assets\google_play'
os.makedirs(output_dir, exist_ok=True)

brain_dir = r'C:\Users\b\.gemini\antigravity\brain\bcd2f8a1-222e-4240-ad0a-100d4b073a02'

# 1. App Icon: 512 x 512 px PNG (under 1MB, sRGB)
icon_path = os.path.join(brain_dir, 'app_icon_raw_1788057121638.jpg')
if os.path.exists(icon_path):
    img = Image.open(icon_path).convert('RGB')
    img = img.resize((512, 512), Image.Resampling.LANCZOS)
    target = os.path.join(output_dir, 'app_icon_512.png')
    img.save(target, 'PNG', optimize=True)
    print(f'Saved App Icon: {target} ({os.path.getsize(target)/1024:.1f} KB, size={img.size})')

# 2. Feature Graphic: 1024 x 500 px PNG (under 15MB)
fg_path = os.path.join(brain_dir, 'feature_graphic_raw_1788057135602.jpg')
if os.path.exists(fg_path):
    img = Image.open(fg_path).convert('RGB')
    img = img.resize((1024, 500), Image.Resampling.LANCZOS)
    target = os.path.join(output_dir, 'feature_graphic_1024x500.png')
    img.save(target, 'PNG', optimize=True)
    print(f'Saved Feature Graphic: {target} ({os.path.getsize(target)/1024:.1f} KB, size={img.size})')

# 3. Screenshots (4 Phone screenshots): 1080 x 1920 px (9:16 aspect ratio, under 8MB)
screenshots = [
    ('screenshot_1_raw_1788057149296.jpg', 'screenshot_1_batch_decryption.png'),
    ('screenshot_2_raw_1788057183824.jpg', 'screenshot_2_password_vault.png'),
    ('screenshot_3_raw_1788057199082.jpg', 'screenshot_3_output_options.png'),
    ('screenshot_4_raw_1788057216659.jpg', 'screenshot_4_offline_security.png')
]

for raw_name, out_name in screenshots:
    p = os.path.join(brain_dir, raw_name)
    if os.path.exists(p):
        img = Image.open(p).convert('RGB')
        img = img.resize((1080, 1920), Image.Resampling.LANCZOS)
        target = os.path.join(output_dir, out_name)
        img.save(target, 'PNG', optimize=True)
        print(f'Saved Screenshot: {target} ({os.path.getsize(target)/1024:.1f} KB, size={img.size})')

print('All assets processed successfully!')
