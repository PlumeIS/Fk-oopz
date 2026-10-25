"""Validate actual DEX names/signatures (JADX aliases are not runtime names)."""
import hashlib
import struct
import sys
import zipfile


def uleb(data, offset):
    value = shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, offset
        shift += 7


def read_dex(data):
    def table(offset):
        return struct.unpack_from('<II', data, offset)

    strings = []
    size, offset = table(56)
    for i in range(size):
        start = struct.unpack_from('<I', data, offset + 4 * i)[0]
        _, start = uleb(data, start)
        strings.append(data[start:data.index(0, start)].decode('utf-8', 'replace'))
    size, offset = table(64)
    types = [strings[struct.unpack_from('<I', data, offset + 4 * i)[0]] for i in range(size)]
    protos = []
    size, offset = table(72)
    for i in range(size):
        _, ret, args = struct.unpack_from('<III', data, offset + 12 * i)
        count = struct.unpack_from('<I', data, args)[0] if args else 0
        params = [types[struct.unpack_from('<H', data, args + 4 + 2 * j)[0]] for j in range(count)]
        protos.append('(' + ''.join(params) + ')' + types[ret])
    fields = []
    size, offset = table(80)
    for i in range(size):
        owner, typ, name = struct.unpack_from('<HHI', data, offset + 8 * i)
        fields.append((types[owner], strings[name], types[typ]))
    methods = []
    size, offset = table(88)
    for i in range(size):
        owner, proto, name = struct.unpack_from('<HHI', data, offset + 8 * i)
        methods.append((types[owner], strings[name], protos[proto]))
    defined_fields, defined_methods = set(), set()
    size, offset = table(96)
    for i in range(size):
        start = struct.unpack_from('<I', data, offset + 32 * i + 24)[0]
        if not start:
            continue
        counts = []
        for _ in range(4):
            value, start = uleb(data, start)
            counts.append(value)
        for group, count in enumerate(counts):
            index = 0
            for _ in range(count):
                diff, start = uleb(data, start)
                index += diff
                _, start = uleb(data, start)  # access flags
                if group < 2:
                    defined_fields.add(fields[index])
                else:
                    _, start = uleb(data, start)  # code offset
                    defined_methods.add(methods[index])
    return defined_fields, defined_methods


path = sys.argv[1] if len(sys.argv) > 1 else 'oopz-1.1.10.apk'
with open(path, 'rb') as source:
    digest = hashlib.file_digest(source, 'sha256').hexdigest()
assert digest == 'c7286974d65598937a96d11199f52c00acf304cf38671858a122b573dace8445', digest
fields, methods = set(), set()
with zipfile.ZipFile(path) as apk:
    for name in apk.namelist():
        if name.startswith('classes') and name.endswith('.dex'):
            f, m = read_dex(apk.read(name))
            fields.update(f)
            methods.update(m)
plugin = 'Lcom/example/flutter_cjadsdk_plugin/FlutterCjadsdkPlugin;'
for signature in [
    (plugin, 'loadSplashAd', '(ZLjava/lang/String;)V'),
    (plugin, 'showSplashAd', '()V'),
    (plugin, 'destorySplash', '()V'),
    (plugin, 'onMethodCall', '(Lq6/h;Lq6/i$d;)V'),
    (plugin, 'onDetachedFromEngine', '(Lm6/a$b;)V'),
    ('Lq6/i;', 'c', '(Ljava/lang/String;Ljava/lang/Object;)V'),
    ('Lq6/i$d;', 'a', '(Ljava/lang/Object;)V'),
    ('Lcom/weilaishanhai/oopz/MyApplication;', 'onCreate', '()V'),
    ('Lcj/mobile/CJSplash;', 'loadAd', '(Landroid/content/Context;Ljava/lang/String;IILcj/mobile/listener/CJSplashListener;)V'),
    ('Lcj/mobile/CJSplash;', 'showAd', '(Landroid/app/Activity;Landroid/view/ViewGroup;)V'),
    ('Lcj/mobile/CJSplash;', 'destroy', '()V'),
    ('Lcj/mobile/CJSplash;', 'isValid', '()Z'),
    ('Lcj/mobile/listener/CJSplashListener;', 'onLoad', '()V'),
    ('Lcj/mobile/listener/CJSplashListener;', 'onShow', '()V'),
    ('Lcj/mobile/listener/CJSplashListener;', 'onClose', '()V'),
]:
    assert signature in methods, signature
for signature in [(plugin, 'channel', 'Lq6/i;'), ('Lq6/h;', 'a', 'Ljava/lang/String;'),
                  (plugin, 'splashAd', 'Lcj/mobile/CJSplash;'),
                  ('Lcj/mobile/CJSplash;', 'listener', 'Lcj/mobile/listener/CJSplashListener;')]:
    assert signature in fields, signature
print('PASS: sample SHA-256 and all 19 runtime hook/field signatures')
