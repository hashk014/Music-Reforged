"""Package the tested JAR and complete corresponding sources without local server data."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent.parent
VERSION = '1.0.0'
NAME = f'Music-Reforged-{VERSION}'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def archive(path, entries):
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as output:
        for source, name in sorted(entries, key=lambda item: item[1]):
            entry = zipfile.ZipInfo(name, date_time=(2026, 10, 11, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            output.writestr(entry, source.read_bytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', default='docs/matrix-results.json', help='Successful runtime report identifying the built JAR')
    args = parser.parse_args()
    jar = ROOT / 'target' / f'{NAME}.jar'
    report_path = ROOT / args.report
    report = json.loads(report_path.read_text(encoding='utf-8'))
    if not jar.exists():
        raise SystemExit('Build first: ./build.ps1 or mvn clean verify')
    if digest(jar) != report['jarSha256']:
        raise SystemExit('The built JAR differs from the tested matrix. Run the matrix before packaging.')
    if not report['results'] or any(row['status'] != 'passed' for row in report['results']):
        raise SystemExit('The compatibility matrix contains an incomplete or failed test.')
    destination = ROOT / 'dist'
    destination.mkdir(exist_ok=True)
    shipped = destination / jar.name
    shutil.copyfile(jar, shipped)
    sources = [(ROOT / name, name) for name in
               ('pom.xml', 'build.ps1', 'README.md', 'LICENSE', 'NOTICE', '.gitignore')]
    for directory in ('src', 'tools', 'docs', 'licenses', '.github'):
        for source in (ROOT / directory).rglob('*'):
            if source.is_file() and '__pycache__' not in source.parts and source.suffix != '.pyc':
                sources.append((source, source.relative_to(ROOT).as_posix()))
    source_zip = destination / f'{NAME}-sources.zip'
    archive(source_zip, [(source, f'{NAME}/{name}') for source, name in sources])
    distribution = destination / f'{NAME}.zip'
    entries = [(shipped, shipped.name)]
    for name in ('README.md', 'LICENSE', 'NOTICE', 'docs/COMPATIBILITY.md',
                 'docs/matrix-results.json', 'docs/migration-results.json',
                 'docs/server-builds.json', 'docs/admin-import.jpg'):
        entries.append((ROOT / name, name))
    entries.extend((source, source.relative_to(ROOT).as_posix())
                   for source in (ROOT / 'licenses').rglob('*') if source.is_file())
    if report_path != ROOT / 'docs/matrix-results.json':
        entries.append((report_path, report_path.relative_to(ROOT).as_posix()))
    archive(distribution, entries)
    artifacts = (shipped, source_zip, distribution)
    (destination / 'SHA256SUMS.txt').write_text(
        ''.join(f'{digest(path)}  {path.name}\n' for path in artifacts), encoding='utf-8')
    for artifact in artifacts:
        print(f'{artifact}: {artifact.stat().st_size:,} bytes, SHA256 {digest(artifact)}')


if __name__ == '__main__':
    main()
