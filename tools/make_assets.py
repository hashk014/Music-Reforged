"""Generate the original hash014 demo loops; ffmpeg encodes mono Ogg Vorbis.
The 15 legacy demo files are kept separately with upstream attribution.
"""
import array, json, math, pathlib, shutil, subprocess, tempfile, wave, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'
RATE = 22050
NOTES = {'reforged': [60, 64, 67, 72, 71, 67, 64, 62],
         'chill': [57, 60, 64, 67, 64, 60, 55, 59],
         'arcade': [72, 76, 79, 84, 79, 76, 74, 71]}

def generate():
    ffmpeg = shutil.which('ffmpeg')
    if not ffmpeg: raise SystemExit('ffmpeg required to regenerate authored OGG files')
    for name, notes in NOTES.items():
        pcm = array.array('h')
        for i in range(16 * RATE):
            t = i / RATE
            beat = int(t * 2)
            phase = (t * 2) % 1
            note = notes[beat % len(notes)]
            freq = 440 * 2 ** ((note - 69) / 12)
            envelope = min(1, phase / .04) * math.exp(-3 * phase)
            melody = math.sin(2 * math.pi * freq * t)
            bass = math.sin(2 * math.pi * (freq / 4) * t) * .4
            if name == 'arcade': melody = .7 * math.sin(2 * math.pi * freq * t) + .2 * math.sin(6 * math.pi * freq * t)
            # Quiet beginning/end prevents a click at the loop boundary.
            fade = min(1, t / .03, (16 - t) / .06)
            pcm.append(int(9000 * (melody + bass) * envelope * fade))
        output = RES / 'audio/musicreforged' / (name + '.ogg')
        output.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as td:
            wav = pathlib.Path(td) / 'sound.wav'
            with wave.open(str(wav), 'wb') as w:
                w.setparams((1, 2, RATE, len(pcm), 'NONE', 'not compressed')); w.writeframes(pcm.tobytes())
            subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '3', str(output)], check=True)
    source = ROOT / 'upstream/resources/resourcepack/assets/minecraft/sounds'
    if source.exists():
        dest = RES / 'audio/minecraft'; dest.mkdir(parents=True, exist_ok=True)
        for f in source.glob('*.ogg'): shutil.copy2(f, dest / f.name)
    bundled = sorted(p.relative_to(RES / 'audio').as_posix() for p in (RES / 'audio').rglob('*.ogg'))
    (RES / 'bundled-sounds.json').write_text(json.dumps(bundled, indent=2), encoding='utf-8')
    formats = {'1.8.8': [1, 0], '1.9.4': [2, 0], '1.10.2': [2, 0], '1.11.2': [3, 0],
               '1.12': [3, 0], '1.12.1': [3, 0], '1.12.2': [3, 0],
               '1.13': [4, 0], '1.13.1': [4, 0], '1.13.2': [4, 0]}
    existing = RES / 'pack-formats.json'
    if existing.exists(): formats.update(json.loads(existing.read_text(encoding='utf-8')))
    cache = ROOT.parent / 'PixelPrinter Reforged/test-servers'
    for folder in cache.iterdir() if cache.exists() else []:
        jars = list(folder.glob('versions/**/*.jar')) + list(folder.glob('cache/patched*.jar'))
        for jar in jars:
            with zipfile.ZipFile(jar) as z:
                if 'version.json' not in z.namelist(): continue
                data = json.loads(z.read('version.json')); p = data['pack_version']
                formats[folder.name] = [p, 0] if isinstance(p, int) else [p.get('resource_major', p.get('resource')), p.get('resource_minor', 0)]
    formats = {k:v for k,v in formats.items() if '/' not in k}
    (RES / 'pack-formats.json').write_text(json.dumps(formats, indent=2, sort_keys=True), encoding='utf-8')
    print('Generated', len(bundled), 'audio assets and', len(formats), 'pack format fallbacks')

if __name__ == '__main__': generate()
