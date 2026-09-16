from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app' / 'src' / 'main' / 'assets'
errors = []

def check(cond, msg):
    if not cond:
        errors.append(msg)

def load_assignment(name, prefix):
    text = (ASSETS / name).read_text(encoding='utf-8').strip()
    check(text.startswith(prefix), f'{name}: unexpected assignment prefix')
    if not text.startswith(prefix):
        return {}
    raw = text[len(prefix):].strip()
    if raw.endswith(';'):
        raw = raw[:-1]
    try:
        return json.loads(raw)
    except Exception as exc:
        errors.append(f'{name}: JSON parse failed: {exc}')
        return {}

core = load_assignment('data_core.js', 'window.APP_PARTS=')
wj = load_assignment('data_wujiang.js', 'window.APP_PARTS.locWujiang=')
fr = load_assignment('data_furong.js', 'window.APP_PARTS.locFurong=')
build = (ASSETS / 'data_build.js').read_text(encoding='utf-8')
ref = (ASSETS / 'reference_gallery.js').read_text(encoding='utf-8')
index = (ASSETS / 'index.html').read_text(encoding='utf-8')

check(core.get('version') == '1.1.7', 'data_core.js version must be 1.1.7')
check("window.STORAGE_KEY='shot_planner_app_v112'" in build, 'main storage key changed unexpectedly')
check("const MAIN_KEY=window.STORAGE_KEY||'shot_planner_app_v112'" in ref, 'reference backup must use main app storage key')
check("referenceGalleryVersion:'1.1.7'" in ref, 'reference gallery backup version is not 1.1.7')
check('direct.click()' in ref and 'projectBtn.click()' in ref, 'reference gallery location navigation does not delegate to main app buttons')
check('referenceImages:refs()' in ref, 'backup does not include reference images')
check('localStorage.setItem(MAIN_KEY,JSON.stringify(data))' in ref, 'restore does not write main app state')
check('reference_gallery.js' in index, 'reference gallery script missing from index')

storyboards = core.get('storyboards', [])
check(len(storyboards) == 2, f'expected 2 storyboards, found {len(storyboards)}')
expected_images = {'wujiang_storyboard.jpg', 'furong_storyboard.jpg'}
actual_images = {x.get('image') for x in storyboards}
check(actual_images == expected_images, f'storyboard assets mismatch: {actual_images}')
for img in expected_images:
    p = ASSETS / img
    check(p.exists(), f'missing storyboard asset: {img}')
    if p.exists():
        check(p.stat().st_size > 10_000, f'storyboard asset suspiciously small: {img}')
check('.webp' not in (ASSETS / 'data_core.js').read_text(encoding='utf-8'), 'stale nonexistent storyboard .webp reference remains')

for loc, expected_count, prefix in [(wj, 28, 'wj_'), (fr, 29, 'fr_')]:
    shots = loc.get('shots', [])
    name = loc.get('name', prefix)
    check(len(shots) == expected_count, f'{name}: expected {expected_count} shots, found {len(shots)}')
    ids = [s.get('id') for s in shots]
    check(len(ids) == len(set(ids)), f'{name}: duplicate shot ids')
    check(all(str(i).startswith(prefix) for i in ids), f'{name}: invalid shot id prefix')
    check(all(s.get('status') in {'pending', 'done', 'retake'} for s in shots), f'{name}: invalid shot status')
    check(all(s.get('priority') in {1, 2, 3} for s in shots), f'{name}: invalid shot priority')
    titles = [s.get('title') for s in shots]
    check(len(titles) == len(set(titles)), f'{name}: duplicate shot titles')

all_titles = {s.get('title') for s in wj.get('shots', []) + fr.get('shots', [])}
meta = (ASSETS / 'data_meta.js').read_text(encoding='utf-8')
# Verify representative critical references used by rough-cut/cover plans.
for title in ['夜景 Hero Shot', '蓝调核心全景', '瀑布夜景正面 Hero', '沿河漫步人物视角', '人物与瀑布尺度关系']:
    check(title in all_titles, f'missing critical referenced shot: {title}')
    check(title in meta, f'data_meta.js no longer references expected shot: {title}')

if errors:
    print('QA CHECK FAILED')
    for e in errors:
        print(' -', e)
    sys.exit(1)

print('QA CHECK PASSED')
print(f" - 乌江寨 shots: {len(wj.get('shots', []))}")
print(f" - 芙蓉镇 shots: {len(fr.get('shots', []))}")
print(f" - storyboards: {len(storyboards)}")
print(" - backup/restore key consistency: OK")
print(" - location navigation delegation: OK")
