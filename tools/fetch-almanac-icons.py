"""Fetch the attributed SVG sets used by the almanac renderers (build-time only)."""
import hashlib
import html
import json
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
target = root / 'artwork' / 'almanac'
target.mkdir(parents=True, exist_ok=True)
def get(url):
    # Use the host trust store through curl (Python's bundled roots may be stale).
    return subprocess.run(['curl.exe', '--fail', '--silent', '--show-error', '--location',
                           '--max-time', '45', '--user-agent', 'CalendarMods/0.1 (SVG asset import)', url],
                          check=True, capture_output=True).stdout

animals = {'rat': 'Rat.svg', 'ox': 'Ox.svg', 'tiger': 'Tiger.svg', 'rabbit': 'Rabbit.svg',
           'dragon': 'Dragon_2.svg', 'snake': 'Snake.svg', 'horse': 'Horse.svg', 'goat': 'Goat.svg',
           'monkey': 'Monkey_2.svg', 'rooster': 'Rooster.svg', 'dog': 'Dog_2.svg', 'pig': 'Boar.svg'}
manifest = []
for key, filename in animals.items():
    source = 'https://commons.wikimedia.org/wiki/File:' + filename
    page = get(source).decode('utf-8')
    media = html.unescape(re.search(r'class="fullMedia".*?href="([^"]+)"', page, re.S).group(1))
    share_alike = key in {'dragon', 'monkey', 'rooster', 'dog', 'pig'}
    assert ('creativecommons.org/licenses/by-sa/3.0' if share_alike else 'public domain') in page.lower(), f'Check license: {source}'
    data = get(media)
    (target / (key + '.svg')).write_bytes(data)
    manifest.append(dict(file=key+'.svg', source=source, original=media,
                         license=('CC BY-SA 3.0, Rev' if key == 'dragon' else 'CC BY-SA 3.0, Angelus') if share_alike else 'Public domain (see file page)', sha256=hashlib.sha256(data).hexdigest()))
    print(key, len(data), flush=True)

signs = ['aries','taurus','gemini','cancer','leo','virgo','libra','scorpio','sagittarius','capricorn','aquarius','pisces']
# Pin a released tag so later imports do not silently replace artwork.
base = 'https://raw.githubusercontent.com/tabler/tabler-icons/v3.46.0/'
for sign in signs:
    url = base + 'icons/outline/zodiac-' + sign + '.svg'
    data = get(url)
    (target / ('zodiac-' + sign + '.svg')).write_bytes(data)
    manifest.append(dict(file='zodiac-'+sign+'.svg', source=url, license='MIT', sha256=hashlib.sha256(data).hexdigest()))
    print(sign, len(data), flush=True)
(root / 'app/src/main/assets/notices/tabler-icons.txt').write_bytes(get(base + 'LICENSE'))
(target / 'sources.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
