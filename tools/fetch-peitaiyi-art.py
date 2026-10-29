"""Fetch the public artwork explicitly requested by the user; keep origin and hashes.
Run from repository root. No webpage JavaScript or remote calendar rules are executed.
"""
import hashlib
import json
import pathlib
import re
import subprocess
from concurrent.futures import ThreadPoolExecutor

root = pathlib.Path(__file__).resolve().parents[1]
script_file = root / 'build/pro-inspect/peitaiyi.js'
if not script_file.exists():
    script_file.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['curl.exe', '--fail', '--silent', '--show-error', '--location',
                    'https://www.peitaiyi.com/chinese-almanac/script.js?v=20260608task2', '-o', str(script_file)], check=True)
script = script_file.read_text(encoding='utf-8-sig')
paths = sorted(set(re.findall(r'\./assets/(?:zodiac|festivals_tinted)/[^"\s]+\.png(?:\?[^"\s]+)?', script)))
terms = re.findall(r'name: "([^"]+)", month: \d+, day: \d+, artKey: "([^"]+)"', script)
paths += [f'./assets/solar_terms_tinted/{key}.png' for name, key in terms]
output = root / 'app/src/main/assets/peitaiyi'
output.mkdir(parents=True, exist_ok=True)

def fetch(path):
    if '/zodiac/' not in path:
        path = path.replace('.png', '.webp')
    relative = path.replace('./assets/', '').split('?')[0]
    target = output / relative
    assert target.resolve().is_relative_to(output.resolve()), 'Asset path escapes output directory'
    target.parent.mkdir(parents=True, exist_ok=True)
    url = 'https://www.peitaiyi.com/chinese-almanac/' + path[2:]
    if not target.exists():
        subprocess.run(['curl.exe', '--fail', '--silent', '--show-error', '--location', '--retry', '2', url, '-o', str(target)], check=True)
    data = target.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n' or (data[:4] == b'RIFF' and data[8:12] == b'WEBP'), relative
    if target.suffix == '.webp':
        target.with_suffix('.png').unlink(missing_ok=True)
    return {'asset': relative, 'url': url, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}

with ThreadPoolExecutor(max_workers=4) as pool:
    entries = list(pool.map(fetch, paths))
manifest = {'source': 'https://www.peitaiyi.com/chinese-almanac/',
            'rights': 'Source site artwork, copied at user request. No open-source license asserted; original rights retained.',
            'assets': entries}
(output / 'sources.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'{len(entries)} images, {sum(item["bytes"] for item in entries):,} bytes')
