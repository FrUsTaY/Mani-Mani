import os
from PIL import Image, ImageDraw

def create_icons():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    res_dir = os.path.join(base_dir, 'app', 'src', 'main', 'res')
    
    icon_path = os.path.join(base_dir, 'icon.png')
    push_path = os.path.join(base_dir, 'push-icon.png')
    
    im_icon = Image.open(icon_path).convert('RGBA')
    im_push = Image.open(push_path).convert('RGBA')
    
    # 1. Prepare Squircle Icon with transparent corners
    w, h = im_icon.size
    mask_squircle = Image.new('L', (w, h), 0)
    draw_sq = ImageDraw.Draw(mask_squircle)
    draw_sq.rounded_rectangle([0, 0, w - 1, h - 1], radius=int(w * 0.22), fill=255)
    
    im_icon_transparent_corners = im_icon.copy()
    im_icon_transparent_corners.putalpha(mask_squircle)
    
    # Prepare Circular Icon with transparent outside
    mask_circle = Image.new('L', (w, h), 0)
    draw_circ = ImageDraw.Draw(mask_circle)
    # Circle with slight 2% inset for clean anti-aliasing
    margin = int(w * 0.02)
    draw_circ.ellipse([margin, margin, w - 1 - margin, h - 1 - margin], fill=255)
    
    im_icon_round = im_icon.copy()
    im_icon_round.putalpha(mask_circle)
    
    # 2. Generate mipmap densities for legacy launchers (ic_launcher.webp & ic_launcher_round.webp)
    mipmap_densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }
    
    for folder, size in mipmap_densities.items():
        out_folder = os.path.join(res_dir, folder)
        os.makedirs(out_folder, exist_ok=True)
        
        # ic_launcher.webp
        sq_resized = im_icon_transparent_corners.resize((size, size), Image.Resampling.LANCZOS)
        sq_resized.save(os.path.join(out_folder, 'ic_launcher.webp'), 'WEBP', quality=100)
        
        # ic_launcher_round.webp
        round_resized = im_icon_round.resize((size, size), Image.Resampling.LANCZOS)
        round_resized.save(os.path.join(out_folder, 'ic_launcher_round.webp'), 'WEBP', quality=100)
        print(f"Generated {folder}: {size}x{size} webp")

    # 3. Generate Push Notification Icons (ic_notification_mono.png)
    # Standard status bar notification icon sizes: 24, 36, 48, 72, 96 dp/px
    drawable_densities = {
        'drawable-mdpi': 24,
        'drawable-hdpi': 36,
        'drawable-xhdpi': 48,
        'drawable-xxhdpi': 72,
        'drawable-xxxhdpi': 96
    }
    
    # Remove old vector xml if present
    old_xml = os.path.join(res_dir, 'drawable', 'ic_notification_mono.xml')
    if os.path.exists(old_xml):
        os.remove(old_xml)
        print("Removed old drawable/ic_notification_mono.xml")
        
    for folder, size in drawable_densities.items():
        out_folder = os.path.join(res_dir, folder)
        os.makedirs(out_folder, exist_ok=True)
        push_resized = im_push.resize((size, size), Image.Resampling.LANCZOS)
        push_resized.save(os.path.join(out_folder, 'ic_notification_mono.png'), 'PNG')
        print(f"Generated {folder}/ic_notification_mono.png: {size}x{size}")

    # Also save fallback in drawable/
    im_push.resize((96, 96), Image.Resampling.LANCZOS).save(
        os.path.join(res_dir, 'drawable', 'ic_notification_mono.png'), 'PNG'
    )
    print("Generated drawable/ic_notification_mono.png (96x96)")

    # 4. Generate Adaptive Icon Foreground (ic_launcher_foreground.png)
    # Adaptive icon canvas is 108x108 dp.
    # mdpi: 108, hdpi: 162, xhdpi: 216, xxhdpi: 324, xxxhdpi: 432
    # The safe zone is center 66% - 72%. Scale icon to 72% so it fits safely.
    adaptive_densities = {
        'drawable-mdpi': 108,
        'drawable-hdpi': 162,
        'drawable-xhdpi': 216,
        'drawable-xxhdpi': 324,
        'drawable-xxxhdpi': 432
    }
    
    # Remove old vector foreground xml
    old_fg_xml = os.path.join(res_dir, 'drawable', 'ic_launcher_foreground.xml')
    if os.path.exists(old_fg_xml):
        os.remove(old_fg_xml)
        print("Removed old drawable/ic_launcher_foreground.xml")

    for folder, canvas_size in adaptive_densities.items():
        out_folder = os.path.join(res_dir, folder)
        os.makedirs(out_folder, exist_ok=True)
        
        fg_canvas = Image.new('RGBA', (canvas_size, canvas_size), (0, 0, 0, 0))
        # Inner logo size (~72% of canvas)
        inner_size = int(canvas_size * 0.72)
        inner_logo = im_icon_transparent_corners.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
        offset = (canvas_size - inner_size) // 2
        fg_canvas.paste(inner_logo, (offset, offset), inner_logo)
        
        fg_canvas.save(os.path.join(out_folder, 'ic_launcher_foreground.png'), 'PNG')
        print(f"Generated {folder}/ic_launcher_foreground.png: {canvas_size}x{canvas_size}")

    # Fallback in drawable/
    fg_canvas.save(os.path.join(res_dir, 'drawable', 'ic_launcher_foreground.png'), 'PNG')

    # Also update ic_launcher_background.xml
    bg_xml = os.path.join(res_dir, 'drawable', 'ic_launcher_background.xml')
    with open(bg_xml, 'w', encoding='utf-8') as f:
        f.write('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#041A16"
        android:pathData="M 0,0 h 108 v 108 h -108 z" />
</vector>
''')
    print("Updated drawable/ic_launcher_background.xml with matching #041A16")

if __name__ == '__main__':
    create_icons()
