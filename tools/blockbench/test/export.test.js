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
ok(med.bones.right_arm.rotation['0.5'].lerp_mode === 'catmullrom' && med.bones.right_arm.rotation['0.5'].post[0] === 60, 'ключ: catmull + числа з рядків + Bedrock-знак X/Y обертання');
ok(typeof med.bones.lid.rotation['0.6'].post[0] === 'string', 'Molang зберігається рядком');
ok(med.bones.head.rotation['1.0'].pre[0] === 0 && med.bones.head.rotation['1.0'].post[0] === -20, 'двоточковий ключ → pre/post (Bedrock-знак)');
ok(med.events.length === 3 && med.events[0].type === 'sound' && med.events[1].value === 'maniacmod:bandage_done', 'події: звук + timeline по рядках');
ok(med.props.medkit.parent === 'right_item' && med.props.medkit.hide_held === 'right' && med.props.medkit.model === 'demo:props/medkit', 'пропс у кліпі');
ok(!doc.animations.idle_unrelated.props, 'кліп без ключів пропса — без пропса');
const geo = JSON.parse(files['assets/demo/shaurma_anim/props/medkit.geo.json']);
const bones = geo['minecraft:geometry'][0].bones;
ok(bones.length === 2 && bones[0].parent === 'right_item' && bones[1].parent === 'medkit' && bones[0].pivot[0] === -6, 'геометрія: ієрархія і дзеркальний півот');

// ── вільний пропс (ефект навколо гравця, parent world) + своя текстура 16×16 ──
const auraCube = { type: 'cube', from: [-8, 0, -8], to: [8, 1, 8], origin: [0, 0, 0], rotation: [0, 0, 0], box_uv: false,
    faces: { up: { uv: [0, 0, 16, 16], texture: 't2' }, down: { uv: [0, 0, 16, 16], texture: 't2' } } };
const aura = group('Аура', [0, 0, 0], 'root', [auraCube]);
aura.shaurma_prop = { translucent: true, emissive: true };
const wrapper = group('Сцена', [0, 0, 0], 'root', [body]); // обгортка з ригом усередині — НЕ пропс
const groups2 = groups.concat([aura, wrapper]);
const project2 = Object.assign({}, project, {
    groups: groups2,
    textureFor: g => g === aura ? { image: project.textureFor(), uv_width: 16, uv_height: 16 } : project.textureFor(),
    animations: project.animations.concat([{ name: 'aura_pulse', length: 1, loop: 'loop', shaurma: {},
        animators: { x: { name: 'Аура', keyframes: [kf('scale', 0, 1, 1, 1), kf('scale', 0.5, 1.5, 1, 1.5)] } } }])
});
const props2 = P.findProps(groups2, 'player');
ok(props2.length === 2 && props2.some(p => p.group === aura && p.parent === 'world') && !props2.some(p => p.group === wrapper),
    'вільний пропс у корені → parent world; обгортка з ригом — не пропс');
ok(P.snake('Аура') === 'aura' && P.snake('ЗеленаАптечка') === 'zelena_aptechka', 'кирилиця → латинські шляхи ресурсів');
const warnings = [];
const files2 = P.buildExport(project2, { namespace: 'demo', file: 'items', warnings });
const doc2 = JSON.parse(files2[clipPath]);
ok(doc2.animations.aura_pulse.props.aura.parent === 'world' && doc2.animations.aura_pulse.props.aura.translucent === true
    && doc2.animations.aura_pulse.props.aura.emissive === true, 'кліп з вільним напівпрозорим пропсом');
ok(JSON.parse(files2['assets/demo/shaurma_anim/props/aura.geo.json'])['minecraft:geometry'][0].description.texture_width === 16,
    'геометрія пропса — розмір UV своєї текстури, не проєкту');
ok(warnings.length === 0, 'без попереджень: ' + warnings.join('; '));

// ── імпорт .geo.json назад у сцену: геометрія й UV мають зійтися з оригіналом ──
const medGeo = JSON.parse(files['assets/demo/shaurma_anim/props/medkit.geo.json']);
const scene = P.geoToScene(medGeo);
ok(scene.roots.length === 1 && scene.roots[0].name === 'medkit' && scene.roots[0].children[0].name === 'lid'
    && JSON.stringify(scene.roots[0].origin) === JSON.stringify([6, 12, -4]), 'geo → сцена: ієрархія і півоти Blockbench');
const c0 = scene.roots[0].cubes[0];
ok(JSON.stringify(c0.from) === JSON.stringify([4, 10, -6]) && JSON.stringify(c0.to) === JSON.stringify([8, 13, -2])
    && JSON.stringify(c0.faces.north.uv) === JSON.stringify([4, 4, 8, 7]), 'geo → сцена: кубик і box-UV → UV граней');
// сцена → групи (як wrapGroup) → знову geo; Java порівнює квади обох моделей
function toWrapped(spec, parent) {
    const g = { name: spec.name, origin: spec.origin, rotation: spec.rotation, parent, children: [] };
    for (const c of spec.cubes) g.children.push(Object.assign({ type: 'cube', box_uv: false }, c));
    for (const ch of spec.children) g.children.push(toWrapped(ch, g));
    return g;
}
const reGeo = P.compileProp(toWrapped(scene.roots[0], 'right_item'), 'right_item', 32, 32);
reGeo['minecraft:geometry'][0].description.identifier = 'geometry.medkit_reimport';
files['assets/demo/shaurma_anim/props/medkit_reimport.geo.json'] = JSON.stringify(reGeo, null, 2);

// ── .bbmodel з двома текстурами, групою «root», анімацією кришки ──
const bb = {
    meta: { format_version: '4.10', box_uv: false }, name: 'kit', resolution: { width: 16, height: 16 },
    textures: [{ uuid: 'ta', name: 'a.png', source: 'data:a', uv_width: 16, uv_height: 16 }, { uuid: 'tb', name: 'b.png', source: 'data:b', uv_width: 32, uv_height: 8 }],
    elements: [
        { uuid: 'e1', name: 'box', from: [-2, 0, -2], to: [2, 3, 2], origin: [0, 0, 0], rotation: [0, 0, 0], box_uv: true, uv_offset: [0, 0], faces: { north: { texture: 0 } } },
        { uuid: 'e2', name: 'cap', from: [-2, 3, -2], to: [2, 4, 2], origin: [0, 3, 0], rotation: [0, 0, 0],
            faces: { north: { uv: [0, 0, 4, 1], texture: 1 }, up: { uv: [4, 4, 0, 0], texture: 'tb' }, down: { uv: [0, 0, 1, 1], texture: null } } },
        { uuid: 'm1', type: 'mesh', name: 'blob' }
    ],
    groups: [{ uuid: 'g1', name: 'root', origin: [0, 0, 0], rotation: [0, 0, 0] }, { uuid: 'g2', name: 'Lid', origin: [0, 3, 2], rotation: [0, 0, 0] }],
    outliner: [{ uuid: 'g1', children: ['e1', { uuid: 'g2', children: ['e2'] }] }],
    animations: [{ name: 'animation.open', length: 0.5, loop: 'once', animators: {
        g2: { name: 'Lid', type: 'bone', keyframes: [{ channel: 'rotation', time: 0, data_points: [{ x: 0, y: 0, z: 0 }] },
            { channel: 'position', time: 0.5, interpolation: 'catmullrom', data_points: [{ x: '2', y: 'math.sin(q.anim_time)', z: 0 }] }] },
        effects: { name: 'Effects', type: 'effect', keyframes: [{ channel: 'sound', time: 0, data_points: [{ effect: 'x' }] }] } } }]
};
const bs = P.bbmodelToScene(bb);
ok(bs.roots.length === 1 && bs.roots[0].name === 'root' && bs.roots[0].children[0].name === 'Lid' && bs.textures.length === 2
    && bs.warnings.length === 1 && bs.animations[0].name === 'open', '.bbmodel → сцена: нові групи за uuid, 2 текстури, меш пропущено');
ok(bs.roots[0].cubes[0].faces.north.texture === 0 && bs.roots[0].children[0].cubes[0].faces.up.texture === 1
    && bs.roots[0].children[0].cubes[0].faces.down.texture === null, '.bbmodel: індекс/uuid текстури, прозора грань');
const atlas = P.atlasLayout(bs.textures);
ok(atlas.width === 32 && atlas.height === 24 && atlas.offsets[1][1] === 16, 'атлас: текстури стовпчиком');
const placed = P.placeScene(bs, { name: 'Kit', scale: 2, anchor: [6, 12, -2], taken: ['right_item', 'kit'], atlas });
const pr = placed.root;
ok(pr.name === 'kit_2' && pr.children[0].name === 'kit_2_root' && pr.children[0].children[0].name === 'kit_2_lid',
    'імена: унікальні, з префіксом пропса, «root» не стає кісткою body');
ok(JSON.stringify(pr.children[0].children[0].cubes[0].from) === JSON.stringify([2, 18, -6]), 'масштаб ×2 і зсув у півот right_item');
ok(JSON.stringify(pr.children[0].children[0].cubes[0].faces.north.uv) === JSON.stringify([0, 16, 4, 17])
    && pr.children[0].cubes[0].faces.north.uv[1] === 4, 'UV другої текстури зсунуто в атласі, перша — без змін');
const remapped = P.remapAnimation(bs.animations[0], placed.nameByUuid, 1.25, 2);
ok(remapped.length === 1 && remapped[0].name === 'kit_2_lid' && remapped[0].keyframes[1].time === 1.75
    && remapped[0].keyframes[1].data_points[0].x === 4 && remapped[0].keyframes[1].data_points[0].y === '(math.sin(q.anim_time))*2'
    && remapped[0].keyframes[1].interpolation === 'catmullrom', 'анімація моделі: нові імена, зсув часу, масштаб позиції, без ефектів');
const vk = P.visibilityKeys([], 1.2, true);
ok(vk.length === 2 && vk[0].time === 0 && vk[0].value === 0 && vk[1].value === 1 && P.visibilityKeys([0], 1, false).length === 1,
    'видимість: «показати з 1.2с» = сховано з 0 + показано з 1.2 (ступінчасто)');
const bf = P.boxUvFaces([0, 0], [0, 0, 0], [4, 3, 2], true);
ok(JSON.stringify(bf.east) === JSON.stringify([8, 2, 6, 5]) && JSON.stringify(bf.west) === JSON.stringify([2, 2, 0, 5]),
    'дзеркальний box-UV: схід/захід поміняні й перевернуті');

const fp = P.fitPoint([8, 13, -2], [6, 12, -4], 2, [0, 1, 0]);
ok(JSON.stringify(fp) === JSON.stringify([10, 15, 0]) && P.scaleMolang('q.x', 0.5) === '(q.x)*0.5',
    'підгонка: масштаб навколо півота пропса + зсув; Molang-ключі масштабуються виразом');

// ── поза предмета в руці з Display моделі (ТОПЛИВО.bbmodel: GeckoLib, 3-я особа права рука) ──
const mv = (M, v) => [0, 1, 2].map(i => M[i][0] * v[0] + M[i][1] * v[1] + M[i][2] * v[2]);
const near3 = (a, b) => a.every((v, i) => Math.abs(v - b[i]) < 1e-3);
// B виведено з ванільного ItemInHandLayer: XP(−90)·YP(180) у просторі ModelPart, ModelPart → сцена = (−x, −y, z)
for (const q of [[1, 0, 0], [0, 1, 0], [0, 0, 1], [0.3, -2, 5]]) {
    const v = mv(P.m3mul(P.rotX(-90), P.rotY(180)), q);
    ok(near3([-v[0], -v[1], v[2]], [q[0], q[2], -q[1]]), 'вісь предмета ' + JSON.stringify(q) + ' → сцена як у грі');
}
const fuel = { thirdperson_righthand: { rotation: [110, 88.76, -52.56], translation: [0, -6, -6.25], scale: [0.5, 0.5, 0.5] } };
const dt = P.itemDisplayTransform(fuel, 'right', 'geckolib_model');
const E = dt.rotation, Mz = P.m3mul(P.rotZ(E[2]), P.m3mul(P.rotY(E[1]), P.rotX(E[0])));
ok(Mz.every((row, i) => near3(row, dt.matrix[i])), 'кути ZYX групи дають рівно поворот Display');
ok(near3(dt.offset, [0, -6.25, 6]) && dt.center[1] === -0.16 && dt.scale[0] === 0.5, 'зсув Display у сцені, центр GeckoLib, масштаб');
ok(P.itemDisplayTransform({}, 'right') === null && near3(P.itemDisplayTransform({ thirdperson_righthand: {} }, 'right', 'java_block').rotation, [-90, 0, 0]),
    'без Display — нічого; порожній Display — лише поворот руки, центр java_block');
const dl = P.itemDisplayTransform({ thirdperson_righthand: { rotation: [0, 30, 10], translation: [2, 0, 0] } }, 'left', 'java_block');
ok(dl.offset[0] === -2 && near3(mv(dl.matrix, [0, 0, 1]), mv(P.m3mul(P.rotX(-90), P.m3mul(P.rotY(-30), P.rotZ(-10))), [0, 0, 1])),
    'ліва рука: Display дзеркальний, як у грі');
// повний ланцюг: placeScene + поворот обгортки = півот + B·t + B·R·s·(p − c)
const fuelScene = { name: 'fuel', roots: [], cubes: [{ from: [-6, -1.3, -2.7], to: [6.6, 16, 2.6], origin: [0, 0, 0], rotation: [0, 0, 0], faces: {} }] };
const pivotR = P.RIG_PIVOTS.right_item, anch = pivotR.map((v, i) => v + dt.offset[i]);
const pl = P.placeScene(fuelScene, { name: 'fuel', scale: dt.scale, anchor: anch, center: dt.center, rotation: dt.rotation });
const corner = pl.root.cubes[0].to;
const world = (() => { const d = corner.map((v, i) => v - pl.root.origin[i]); const r = mv(Mz, d); return r.map((v, i) => v + pl.root.origin[i]); })();
const expect = (() => { const p = [6.6, 16, 2.6].map((v, i) => (v - dt.center[i]) * 0.5); const r = mv(dt.matrix, p); return r.map((v, i) => pivotR[i] + dt.offset[i] + v); })();
ok(near3(world, expect) && near3(pl.root.rotation, dt.rotation), 'модель у сцені стоїть рівно як ванільний предмет у руці');

// ── стан тримання: каністра в руці → поза сама, ванільний предмет сховано пропсом ──
const holdClip = P.compileAnimation({ name: 'fuel_hold', length: 1, loop: 'once', animators: {
    a: { name: 'right_arm', keyframes: [kf('rotation', 0, -30, 0, 10)] } } },
    { slot: 'action', mask: 'right_arm', hold_item: 'maniacmod:fuel_canister', hold_hand: 'main', hold_yield_use: false });
ok(holdClip.kind === 'state' && holdClip.hold.item === 'maniacmod:fuel_canister' && holdClip.state.yield_use === false
    && holdClip.state.yield_swing === undefined && holdClip.slot === 'hold' && holdClip.loop === 'hold' && holdClip.mask === 'right_arm',
    'старі налаштування з предметом → тип «стан»: предмет, шар hold, застигає, маска');
ok(P.holdSpec({ hold_item: 'stick, maniacmod:fuel' }).item.join() === 'minecraft:stick,maniacmod:fuel' && P.holdSpec({}) === null,
    'кілька предметів, простір імен minecraft за замовчуванням');

// ── чотири типи анімацій ──
const armAnim = (name, extra) => ({ name, length: 1, loop: 'once', animators: Object.assign({
    a: { name: 'right_arm', keyframes: [kf('rotation', 0, -40, 0, 0), kf('rotation', 1, -40, 0, 0)] } }, extra || {}) });
const fullClip = P.compileAnimation(armAnim('lie'), { kind: 'full' });
ok(fullClip.slot === 'pose' && fullClip.mask === 'full' && fullClip.look === 0 && fullClip.bones.left_leg && fullClip.bones.head,
    'повна: шар pose, кістки без ключів тримаються в спокої, голова не за камерою');
const partClip = P.compileAnimation(armAnim('raise'), { kind: 'partial' });
ok(partClip.slot === 'action' && partClip.mask === undefined && !partClip.bones.left_leg && partClip.loop === 'once',
    'часткова: action, лише свої кістки, раз');
const syncClip = P.compileAnimation(armAnim('medkit', { b: { name: 'right_leg', keyframes: [kf('rotation', 0, 5, 0, 0)] } }),
    { kind: 'sync', keep_legs: true, keep_head: true });
ok(syncClip.slot === 'action' && syncClip.mask === 'full-legs-body!-head' && syncClip.look === 1, 'синхронна: ноги й голова лишаються руху');
ok(P.syncMask({ keep_legs: false }) === 'full' && P.syncMask({ keep_right_arm: true }) === 'full-legs-body!-right_arm', 'маска синхронної з галочок');
const stClip = P.compileAnimation(armAnim('fuel_hold'), { kind: 'state', follow_move: 0.5, follow_sneak: 1, yield_use: false,
    hold_item: 'maniacmod:fuel_canister' });
ok(stClip.slot === 'hold' && stClip.state.follow_move === 0.5 && stClip.state.follow_sneak === undefined && stClip.state.yield_use === false
    && stClip.hold.item === 'maniacmod:fuel_canister' && stClip.loop === 'hold', 'стан: шар hold, сила руху, поступки, предмет');
ok(P.compileAnimation(armAnim('torch'), { kind: 'state' }).state === true && !P.compileAnimation(armAnim('torch'), { kind: 'state' }).hold,
    'стан без предмета — вмикається з коду');
files['assets/demo/shaurma_anim/player/hold.json'] = JSON.stringify({ format: 'shaurma_anim', format_version: 1, rig: 'player',
    animations: { fuel_hold: stClip, medkit_walk: syncClip, lie: fullClip } }, null, 2);

ok(JSON.parse(P.packMeta('x')).pack.pack_format === 15, 'архів = ресурс-пак 1.20.1 (pack.mcmeta)');

// для Java: вільний пропс і кліп з ним — окремим файлом
for (const [k, v] of Object.entries(files2)) if (k.indexOf('aura') >= 0) files[k] = v;
files['assets/demo/shaurma_anim/player/aura.json'] = JSON.stringify({ format: 'shaurma_anim', format_version: 1, rig: 'player',
    animations: { aura_pulse: doc2.animations.aura_pulse } }, null, 2);

// ── split: кожна анімація гравця — окремий файл ──
const splitFiles = P.buildExport(project, { namespace: 'demo', file: 'items', split: true });
const splitPaths = Object.keys(splitFiles).filter(f => f.includes('/player/'));
ok(splitPaths.includes('assets/demo/shaurma_anim/player/items/medkit_use.json') && !splitPaths.includes(clipPath), 'split: файл на анімацію, спільного немає');
const splitDoc = JSON.parse(splitFiles['assets/demo/shaurma_anim/player/items/medkit_use.json']);
ok(Object.keys(splitDoc.animations).length === 1 && splitDoc.rig === 'player' && splitDoc.animations.medkit_use.props.medkit, 'split: один кліп + риг + пропс у файлі');
const onlyFiles = P.buildExport(project, { namespace: 'demo', file: 'items', split: true, only: ['animation.medkit_use'] });
ok(Object.keys(onlyFiles).filter(f => f.includes('/player/')).length === 1, 'split + only: лише вибрана анімація');

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
