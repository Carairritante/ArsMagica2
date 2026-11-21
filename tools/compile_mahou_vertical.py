"""Bake approved Blockbench cubes/UVs; never edit their source models.

Usage: python tools/compile_mahou_vertical.py --models <approved-model-directory>
The renderer consumes the baked meshes; Blockbench/JSON parsing is not needed in-game.
"""
from pathlib import Path
import argparse, base64, hashlib, json, math, struct

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'src/main/resources/assets/arsmagica2'
VARIANTS = ['none', 'arcane', 'water', 'fire', 'earth', 'air', 'lightning',
            'ice', 'nature', 'life', 'ender', 'lightning_lunar', 'blizzard',
            'firestorm', 'starfall', 'storm_lightning']


def rotate(point, origin, angles):
    x, y, z = [point[i] - origin[i] for i in range(3)]
    # Blockbench/Three.js Euler XYZ: column vectors see Z, then Y, then X.
    ax, ay, az = map(math.radians, angles)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    return [(v + o) / 16 for v, o in zip((x, y, z), origin)]


def bake(model):
    groups, ancestry = {}, {}
    def walk(node, parents):
        if isinstance(node, str):
            ancestry[node] = parents
            return
        assert not any(node.get('rotation', [0, 0, 0])), 'Rotated groups need hierarchical baking'
        groups[node['uuid']] = node
        for child in node.get('children', []):
            walk(child, parents + [node['uuid']])
    for node in model['outliner']:
        walk(node, [])
    animators = model['animations'][0]['animators']
    mesh, counts, skipped = bytearray(), [0, 0, 0], 0
    for element in model['elements']:
        parents = ancestry[element['uuid']]
        if any(groups[g]['name'] == 'caster_reference' for g in parents):
            skipped += 1
            continue
        phase = next(int(groups[g]['name'][5]) for g in parents if groups[g]['name'].startswith('phase'))
        keys = [k for g in parents for k in animators.get(g, {}).get('keyframes', []) if k['channel'] == 'scale']
        end = min(k['time'] for k in keys if float(k['data_points'][0]['x']) > 0)
        start = max(k['time'] for k in keys if float(k['data_points'][0]['x']) == 0 and k['time'] < end)
        mesh.extend(struct.pack('>Bff', phase, start, end))
        counts[phase - 1] += 1
        x0, y0, z0 = element['from']; x1, y1, z1 = element['to']
        # Outward winding; the inner walls remain visible from inside the rings.
        faces = {
            'north': [(x1,y1,z0),(x1,y0,z0),(x0,y0,z0),(x0,y1,z0)],
            'south': [(x0,y1,z1),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1)],
            'east': [(x1,y1,z1),(x1,y0,z1),(x1,y0,z0),(x1,y1,z0)],
            'west': [(x0,y1,z0),(x0,y0,z0),(x0,y0,z1),(x0,y1,z1)],
            'up': [(x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0)],
            'down': [(x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)],
        }
        for name, vertices in faces.items():
            face = element['faces'][name]
            assert face['texture'] == 0 and not face.get('rotation', 0)
            u0, v0, u1, v1 = [v / 512 for v in face['uv']]
            for pos, uv in zip(vertices, [(u0,v0),(u0,v1),(u1,v1),(u1,v0)]):
                values = rotate(pos, element.get('origin', [0,0,0]), element.get('rotation', [0,0,0])) + list(uv)
                assert all(math.isfinite(v) for v in values)
                mesh.extend(struct.pack('>5f', *values))
    assert counts == [64, 308, 524] and skipped == 7, (counts, skipped)
    return struct.pack('>4sii', b'MHV1', 1, sum(counts)) + mesh


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--models', type=Path, required=True)
    args = parser.parse_args()
    report = {}
    for variant in VARIANTS:
        name = 'firestorm_mahou_vertical_3d.bbmodel' if variant == 'firestorm' else f'mahou_vertical_{variant}.bbmodel'
        source = args.models / name
        data = source.read_bytes(); model = json.loads(data)
        assert model['resolution'] == {'width': 512, 'height': 512}
        atlas = base64.b64decode(model['textures'][0]['source'].split(',')[1])
        assert atlas[:8] == b'\x89PNG\r\n\x1a\n' and struct.unpack('>II', atlas[16:24]) == (512,512)
        mesh = bake(model)
        for relative, payload in [(f'models/mahou_vertical/{variant}.mesh', mesh),
                                  (f'textures/mahou_vertical/{variant}.png', atlas)]:
            path = DEST / relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(payload)
        report[variant] = {'source': name, 'source_sha256': hashlib.sha256(data).hexdigest(),
                           'mesh_sha256': hashlib.sha256(mesh).hexdigest(),
                           'atlas_sha256': hashlib.sha256(atlas).hexdigest(),
                           'phase_elements': [64,308,524], 'reference_elements_excluded': 7}
    (DEST / 'models/mahou_vertical/manifest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Baked 16 approved variants; 896 cubes each; reference mannequins excluded.')


if __name__ == '__main__':
    main()
