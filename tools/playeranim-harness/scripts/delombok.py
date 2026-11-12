#!/usr/bin/env python3
"""Мінімальний delombok для coreLib PlayerAnimator: Lombok недоступний у пісочниці без Maven Central.
Обробляє лише ті анотації, що реально трапляються в coreLib (@Getter/@Setter/@Accessors на полях)."""
import re, sys, glob

def cap(n): return n[0].upper() + n[1:]

def delombok(s):
    lines = s.split('\n'); out = []; i = 0
    while i < len(lines):
        l = lines[i]
        if re.match(r'\s*import lombok', l): i += 1; continue
        if re.match(r'\s*@(Getter|Setter|Accessors)\b', l):
            ann = []
            while re.match(r'\s*@', lines[i]): ann.append(lines[i].strip()); i += 1
            field = lines[i]
            m = re.match(r'\s*((?:(?:public|private|protected|final|static)\s+)*)([\w<>\[\], ?]+?)\s+(\w+)\s*(=.*)?;', field)
            assert m, field
            typ, name = m.group(2), m.group(3)
            chain = any('chain = true' in a for a in ann)
            for k in [a for a in ann if not re.match(r'@(Getter|Setter|Accessors)', a)]: out.append('    ' + k)
            out.append(field)
            if any(a.startswith('@Getter') for a in ann):
                g = ('is' + cap(name[2:] if re.match(r'is[A-Z]', name) else name)) if typ == 'boolean' else 'get' + cap(name)
                out.append(f'    public {typ} {g}() {{ return this.{name}; }}')
            if any(a.startswith('@Setter') for a in ann):
                if chain:
                    cls = re.findall(r'public class (\w+)', s)[0]
                    out.append(f'    public {cls} set{cap(name)}({typ} v) {{ this.{name}=v; return this; }}')
                else:
                    out.append(f'    public void set{cap(name)}({typ} v) {{ this.{name}=v; }}')
            i += 1; continue
        out.append(l); i += 1
    return '\n'.join(out)

FPC = '''package dev.kosmx.playerAnim.api.firstPerson;
public class FirstPersonConfiguration {
    boolean showRightArm = false, showLeftArm = false, showRightItem = true, showLeftItem = true;
    public FirstPersonConfiguration() {}
    public FirstPersonConfiguration(boolean a, boolean b, boolean c, boolean d){showRightArm=a;showLeftArm=b;showRightItem=c;showLeftItem=d;}
    public boolean isShowRightArm(){return showRightArm;} public boolean isShowLeftArm(){return showLeftArm;}
    public boolean isShowRightItem(){return showRightItem;} public boolean isShowLeftItem(){return showLeftItem;}
    public FirstPersonConfiguration setShowRightArm(boolean v){showRightArm=v;return this;}
    public FirstPersonConfiguration setShowLeftArm(boolean v){showLeftArm=v;return this;}
    public FirstPersonConfiguration setShowRightItem(boolean v){showRightItem=v;return this;}
    public FirstPersonConfiguration setShowLeftItem(boolean v){showLeftItem=v;return this;}
}
'''
root = sys.argv[1]
open(root + '/dev/kosmx/playerAnim/api/firstPerson/FirstPersonConfiguration.java', 'w').write(FPC)
for p in glob.glob(root + '/**/*.java', recursive=True):
    s = open(p).read()
    if 'lombok' in s:
        open(p, 'w').write(delombok(s))
