P=$PWD/files/rootfs/root/.dsh/profiles/web
mkdir -p $P/../../../tmp $P/.plugin-manager/logs
rm -rf $P/node_modules/dsh-mobile-bridge-plugin
mkdir -p $P/node_modules/dsh-mobile-bridge-plugin
tar -xzf /data/local/tmp/dsh-plugin.tgz -C $P/node_modules/dsh-mobile-bridge-plugin || $PWD/files/rootfs/bin/tar -xzf /data/local/tmp/dsh-plugin.tgz -C $P/node_modules/dsh-mobile-bridge-plugin
ls $P/node_modules/dsh-mobile-bridge-plugin | head -5
python3 - <<'PY'
import json
path = '/root/.dsh/profiles/web/package.json'
p = '/data/user/0/com.dsh.mobile/files/rootfs' + path
with open(p) as f: pkg = json.load(f)
pkg.setdefault('dependencies', {})['dsh-mobile-bridge-plugin'] = 'file:local'
dsh = pkg.setdefault('dsh', {}).setdefault('profile', {})
b = dsh.setdefault('bundles', [])
if 'dsh-mobile-bridge-plugin' not in b: b.append('dsh-mobile-bridge-plugin')
with open(p, 'w') as f: json.dump(pkg, f, indent=2)
print('deps:', list(pkg['dependencies']))
print('bundles:', b)
PY