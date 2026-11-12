// Тест плагіна без Blockbench: мок-проєкт → buildExport → файли на диск (їх потім читає Java-рушій).
// Запуск: node tools/blockbench/test/export.test.js <вихідна_папка>
'use strict';
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const P = require('../shaurma_anim.js');

let passed = 0;
function ok(cond, name) { assert.ok(cond, name); passed++; console.log('PASS ' + name); }

// ── мок ригу гравця (як у player-animation_model.bbmodel) + пропс аптечки під правою рукою ──
function group(name, origin, parent, children = []) {
    const g = { name, origin, rotation: [0, 0, 0], parent, children, shaurma_prop: {} };
    children.forEach(c => { if (c.children) c.parent = g; });
    return g;
}
const cube = (from, to, uv) => ({ type: 'cube', from, to, origin: [0, 0, 0], rotation: [0, 0, 0], box_uv: true, uv_offset: uv, faces: {} });

const lid = group('lid', [6, 13, -4], null, [cube([4, 13, -6], [8, 14, -2], [0, 8])]);
const medkit = group('medkit', [6, 12, -4], null, [cube([4, 10, -6], [8, 13, -2], [0, 0]), lid]);
lid.parent = medkit;
medkit.shaurma_prop = { hide_held: 'right' };
const rightItem = group('right_item', [6, 12, -2], null, [medkit]);
medkit.parent = rightItem;
const rightArm = group('right_arm', [5, 22, 0], null, [cube([4, 12, -2], [8, 24, 2], [40, 16]), rightItem]);
rightItem.parent = rightArm;
const body = group('body', [0, 12, 0], 'root', [rightArm, group('head', [0, 24, 0], null), group('torso', [0, 24, 0], null),
    group('left_arm', [-5, 22, 0], null), group('right_leg', [1.9, 12, 0], null), group('left_leg', [-1.9, 12, 0], null)]);
body.children.forEach(c => c.parent = body);
const groups = [];
(function walk(g) { groups.push(g); g.children.forEach(c => { if (c.children) walk(c); }); })(body);

const kf = (channel, time, x, y, z, extra = {}) => Object.assign({ channel, time, interpolation: 'linear', data_points: [{ x, y, z }] }, extra);
const project = {
    name: 'Medkit Demo',
    groups,
    texture_width: 32, texture_height: 32,
    textureFor: () => 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=',
    animations: [{
        name: 'animation.medkit_use', length: 2, loop: 'once',
        shaurma: { slot: 'action', mask: 'upper_body', blend_in: 0.1, blend_out: 0.25, look: 0.6, first_person: 'model', additive: false },
        animators: {
            a1: { name: 'right_arm', keyframes: [kf('rotation', 0, '0', '0', '0'), kf('rotation', 0.5, '-60', '-20', '0', { interpolation: 'catmullrom' }),
                                                kf('rotation', 2, 0, 0, 0)] },
            a2: { name: 'lid', keyframes: [kf('rotation', 0, 0, 0, 0), kf('rotation', 0.6, '-110 + math.sin(q.anim_time*90)', 0, 0)] },
            a3: { name: 'head', keyframes: [{ channel: 'rotation', time: 1, interpolation: 'step', data_points: [{ x: 0, y: 0, z: 0 }, { x: 20, y: 0, z: 0 }] }] },
            fx: { name: 'Effects', type: 'effect', keyframes: [
                { channel: 'sound', time: 0.45, data_points: [{ effect: 'maniacmod:medkit_open', file: '' }] },
                { channel: 'timeline', time: 1.0, data_points: [{ script: 'maniacmod:bandage_done;\nother_tag' }] }] }
        }
    }, {
        name: 'idle_unrelated', length: 1, loop: 'loop', shaurma: {},
        animators: { b1: { name: 'head', keyframes: [kf('rotation', 0, 0, 0, 0)] } }
    }]
};

// ── перевірки чистих функцій ──
ok(P.snake('RightArm') === 'right_arm' && P.canonicalPlayerBone('rightItem') === 'right_item', 'імена кісток → snake/канон');
ok(P.detectRig(groups) === 'player', 'риг гравця розпізнано');
const props = P.findProps(groups, 'player');
ok(props.length === 1 && props[0].group.name === 'medkit' && props[0].parent === 'right_item', 'пропс: лише корінь (medkit), батько right_item');

const c = P.compileCube({ from: [4, 10, -6], to: [8, 13, -2], origin: [0, 0, 0], rotation: [0, 0, 0], box_uv: true, uv_offset: [0, 0] });
ok(JSON.stringify(c.origin) === JSON.stringify([-8, 10, -6]) && c.size[0] === 4, 'кубик: Bedrock-початок (−to.x, from.y, from.z)');
const pf = P.compileCube({ from: [0, 0, 0], to: [2, 2, 2], origin: [0, 0, 0], rotation: [0, 0, 0], box_uv: false,
    faces: { up: { uv: [0, 0, 2, 2], texture: 0 }, north: { uv: [2, 2, 4, 4], texture: 0 }, south: { uv: [0, 0, 1, 1], texture: null } } });
ok(pf.uv.up.uv[0] === 2 && pf.uv.up.uv_size[0] === -2 && pf.uv.north.uv_size[1] === 2 && !pf.uv.south, 'per-face UV: up/down перевернуто, без текстури — пропуск');

const files = P.buildExport(project, { namespace: 'demo', file: 'items' });
const clipPath = 'assets/demo/shaurma_anim/player/items.json';
ok(Object.keys(files).includes(clipPath), 'файл кліпів: ' + clipPath);
ok(Object.keys(files).includes('assets/demo/shaurma_anim/props/medkit.geo.json'), 'файл геометрії пропса');
ok(files['assets/demo/textures/shaurma_anim/medkit.png'] && files['assets/demo/textures/shaurma_anim/medkit.png'].image, 'текстура пропса');
const doc = JSON.parse(files[clipPath]);
const med = doc.animations.medkit_use;
ok(doc.format === 'shaurma_anim' && doc.rig === 'player' && med && !doc.animations['animation.medkit_use'], 'формат, риг, префікс animation. прибрано');
ok(med.animation_length === 2 && med.length === 2 && med.loop === 'once', 'довжина в обох ключах (Blockbench може імпортувати назад)');
ok(med.mask === 'upper_body' && med.first_person === 'model' && med.look === 0.6 && med.additive === undefined, 'налаштування анімації');
ok(med.bones.right_arm.rotation['0.5'].lerp_mode === 'catmullrom' && med.bones.right_arm.rotation['0.5'].post[0] === -60, 'ключ: catmull + числа з рядків');
ok(typeof med.bones.lid.rotation['0.6'].post[0] === 'string', 'Molang зберігається рядком');
ok(med.bones.head.rotation['1.0'].pre[0] === 0 && med.bones.head.rotation['1.0'].post[0] === 20, 'двоточковий ключ → pre/post');
ok(med.events.length === 3 && med.events[0].type === 'sound' && med.events[1].value === 'maniacmod:bandage_done', 'події: звук + timeline по рядках');
ok(med.props.medkit.parent === 'right_item' && med.props.medkit.hide_held === 'right' && med.props.medkit.model === 'demo:props/medkit', 'пропс у кліпі');
ok(!doc.animations.idle_unrelated.props, 'кліп без ключів пропса — без пропса');
const geo = JSON.parse(files['assets/demo/shaurma_anim/props/medkit.geo.json']);
const bones = geo['minecraft:geometry'][0].bones;
ok(bones.length === 2 && bones[0].parent === 'right_item' && bones[1].parent === 'medkit' && bones[0].pivot[0] === -6, 'геометрія: ієрархія і дзеркальний півот');

// ── запис для Java-перевірки ──
const out = process.argv[2];
if (out) {
    for (const [rel, content] of Object.entries(files)) {
        const full = path.join(out, rel);
        fs.mkdirSync(path.dirname(full), { recursive: true });
        if (content.image) fs.writeFileSync(full, Buffer.from(content.image.split(',')[1], 'base64'));
        else fs.writeFileSync(full, content);
    }
    console.log('written to ' + out);
}
console.log('\n' + passed + ' passed, 0 failed');
