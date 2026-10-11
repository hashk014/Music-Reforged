"""Prepare isolated server binary caches. Never use an existing world's folder."""
import argparse, concurrent.futures, hashlib, json, os, pathlib, shutil, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
UA = {'User-Agent': 'MusicReforged/1.0 (https://github.com/ZombieStriker/Music)'}

def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120).read()

def prepare(version, cache=None):
    folder = ROOT / 'test-servers' / version
    folder.mkdir(parents=True, exist_ok=True)
    if cache:
        source = pathlib.Path(cache) / version
        if (source / 'server.jar').exists():
            for name in ('server.jar', 'download.json'):
                if (source / name).exists() and not (folder / name).exists(): shutil.copy2(source / name, folder / name)
            # Jar binaries are copied. No world, plugin state, or server config is reused.
            for name in ('cache', 'versions', 'libraries'):
                if (source / name).is_dir():
                    for binary in (source / name).rglob('*.jar'):
                        dest = folder / binary.relative_to(source)
                        if not dest.exists(): dest.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(binary, dest)
    if (folder / 'server.jar').exists() and (folder / 'download.json').exists():
        build = json.loads((folder / 'download.json').read_text())
        if hashlib.sha256((folder / 'server.jar').read_bytes()).hexdigest() == build['downloads']['server:default']['checksums']['sha256']: return version + ' checksum verified'
    builds = json.loads(fetch('https://fill.papermc.io/v3/projects/paper/versions/' + version + '/builds'))
    build = next((b for b in builds if b['channel'] == 'STABLE'), builds[0])
    d = build['downloads']['server:default']; data = fetch(d['url'])
    if hashlib.sha256(data).hexdigest() != d['checksums']['sha256']: raise RuntimeError('Checksum mismatch')
    (folder / 'server.jar').write_bytes(data); (folder / 'download.json').write_text(json.dumps(build, indent=2))
    return version + ' downloaded build ' + str(build['id'])

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('versions', nargs='*'); parser.add_argument('--all', action='store_true'); parser.add_argument('--cache'); a = parser.parse_args()
    versions = a.versions
    if a.all:
        project = json.loads(fetch('https://fill.papermc.io/v3/projects/paper'))
        versions = [v for group in project['versions'].values() for v in group if '-' not in v and (1,8,8) <= tuple(map(int,v.split('.'))) <= (26,3)]
        (ROOT / 'docs').mkdir(exist_ok=True); (ROOT / 'docs/paper-versions.json').write_text(json.dumps(project, indent=2))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as ex:
        fs = {ex.submit(prepare, v, a.cache): v for v in versions}
        for f in concurrent.futures.as_completed(fs):
            try: print(f.result(), flush=True)
            except Exception as e: print(fs[f], 'FAILED', str(e), flush=True); raise
