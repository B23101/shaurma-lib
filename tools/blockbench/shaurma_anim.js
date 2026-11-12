/*
 * Shaurma Animations — плагін Blockbench для рушія анімацій ShaurmaLib.
 *
 * Що робить:
 *   • File → Export → «Shaurma Animation»: усі (або вибрані) анімації проєкту → assets/<ns>/shaurma_anim/<файл>.json
 *     у власному форматі ShaurmaLib (надмножина Bedrock: Blockbench сам може імпортувати файл назад).
 *   • Пропси: група, що лежить ПІД кісткою ригу (right_arm, head...) і сама не є кісткою ригу, — це пропс
 *     (аптечка, ножиці, шприц). Її геометрія експортується в shaurma_anim/props/<ім'я>.geo.json, текстура —
 *     у textures/shaurma_anim/<ім'я>.png, а анімація її підгруп (кришка, бинт) — у той самий кліп.
 *     Сховати пропс у кліпі = масштаб 0 у ключі (або видимість у налаштуваннях пропса).
 *   • Animation → «Shaurma: налаштування анімації»: шар, маска (які кістки чіпає — «ноги ходять, руки лікують»),
 *     згасання, погляд, перша особа. Зберігається в .bbmodel.
 *   • Group (ПКМ) → «Shaurma: налаштування пропса»: ховати ванільний предмет у руці, світіння, предмет гри.
 *   • Animation → «Shaurma: створити риг гравця»: кістки й куби ванільного гравця (64×64, Steve UV).
 *
 * Конвенції — ті самі, що читає рушій (перевірено проти GeckoLib): значення ключів — як у Blockbench UI,
 * Bedrock-геометрія: півот (−x, y, z), обертання (−x, −y, z), початок кубика (−to.x, from.y, from.z).
 */
(function () {
    'use strict';

    const PLAYER_RIG = ['body', 'head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg', 'right_item', 'left_item'];
    const SLOTS = ['locomotion', 'idle', 'hold', 'action', 'gesture', 'pose', 'cinematic'];

    // ── Чисті функції конвертації (тестуються в Node: tools/blockbench/test) ─────────────────────

    function snake(name) {
        return String(name).trim()
            .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
            .replace(/[-\s.]+/g, '_')
            .toLowerCase();
    }

    function canonicalPlayerBone(name) {
        const s = snake(name);
        const alias = { arm_right: 'right_arm', arm_left: 'left_arm', leg_right: 'right_leg', leg_left: 'left_leg',
            item_right: 'right_item', item_left: 'left_item', right_hand: 'right_item', left_hand: 'left_item', root: 'body' };
        return alias[s] || s;
    }

    function isRigBone(name, rig) {
        return rig === 'player' ? PLAYER_RIG.indexOf(canonicalPlayerBone(name)) >= 0 : false;
    }

    function num(v) {
        if (typeof v === 'number') return round(v);
        const n = Number(v);
        if (v !== '' && v !== null && v !== undefined && !isNaN(n)) return round(n);
        return String(v); // Molang
    }

    function round(n) { return Math.round(n * 10000) / 10000; }

    function vec(dp) {
        if (!dp) return [0, 0, 0];
        return [num(dp.x === undefined ? 0 : dp.x), num(dp.y === undefined ? 0 : dp.y), num(dp.z === undefined ? 0 : dp.z)];
    }

    function timeKey(t) {
        const r = Math.round(t * 10000) / 10000;
        return String(r % 1 === 0 ? r.toFixed(1) : r);
    }

    /** Ключ Blockbench → ключ Bedrock/ShaurmaLib. */
    function compileKeyframe(kf) {
        const points = kf.data_points || [];
        const out = {};
        if (points.length >= 2) {
            out.pre = vec(points[0]);
            out.post = vec(points[1]);
        } else {
            out.post = vec(points[0]);
        }
        const interp = kf.interpolation || 'linear';
        if (interp !== 'linear') out.lerp_mode = interp;
        if (kf.easing && kf.easing !== 'linear') out.easing = kf.easing;
        return out;
    }

    /**
     * Анімація → кліп. {@code anim} — об'єкт у формі Blockbench Animation; {@code boneInfo(name)} повертає
     * {isRig, isProp, propRoot, keep} для групи.
     */
    function compileAnimation(anim, settings) {
        settings = settings || {};
        const clip = {
            loop: anim.loop || 'once',
            length: round(anim.length || 0),
            animation_length: round(anim.length || 0)
        };
        const meta = ['slot', 'mask', 'blend_in', 'blend_out', 'look', 'first_person', 'first_person_pitch', 'first_person_offset', 'camera', 'camera_rotation', 'stride', 'speed', 'additive'];
        for (const k of meta) {
            const v = settings[k];
            if (v === undefined || v === null || v === '' || (typeof v === 'number' && isNaN(v))) continue;
            if (k === 'additive' && !v) continue;
            if (k === 'first_person' && v === 'vanilla') continue;
            clip[k] = v;
        }
        const bones = {};
        const events = [];
        for (const animator of Object.values(anim.animators || {})) {
            const kfs = animator.keyframes || [];
            if (!kfs.length) continue;
            if (animator.type === 'effect' || animator.name === 'Effects' || animator.uuid === 'effects') {
                for (const kf of kfs) events.push(...compileEffect(kf));
                continue;
            }
            const bone = {};
            for (const ch of ['rotation', 'position', 'scale']) {
                const list = kfs.filter(k => k.channel === ch).sort((a, b) => a.time - b.time);
                if (!list.length) continue;
                const obj = {};
                for (const kf of list) obj[timeKey(kf.time)] = compileKeyframe(kf);
                bone[ch] = obj;
            }
            if (Object.keys(bone).length) bones[animator.name] = bone;
        }
        clip.bones = bones;
        if (events.length) clip.events = events.sort((a, b) => a.time - b.time);
        return clip;
    }

    function compileEffect(kf) {
        const out = [];
        for (const dp of (kf.data_points || [])) {
            if (kf.channel === 'sound' && dp.effect) out.push({ time: round(kf.time), type: 'sound', value: String(dp.effect) });
            else if (kf.channel === 'particle' && dp.effect) {
                const e = { time: round(kf.time), type: 'particle', value: String(dp.effect) };
                if (dp.locator) e.locator = dp.locator;
                out.push(e);
            } else if (kf.channel === 'timeline' && dp.script) {
                for (const line of String(dp.script).split(/[;\n]/)) {
                    const v = line.trim();
                    if (v) out.push({ time: round(kf.time), type: 'custom', value: v });
                }
            }
        }
        return out;
    }

    /** Кубик Blockbench → кубик Bedrock (конвенції GeckoLib/Bedrock, див. GeoMesh у ядрі). */
    function compileCube(cube) {
        const from = cube.from, to = cube.to;
        const size = [round(to[0] - from[0]), round(to[1] - from[1]), round(to[2] - from[2])];
        const c = { origin: [round(-to[0]), round(from[1]), round(from[2])], size: size };
        if (cube.inflate) c.inflate = cube.inflate;
        if (cube.rotation && (cube.rotation[0] || cube.rotation[1] || cube.rotation[2])) {
            c.pivot = [round(-cube.origin[0]), round(cube.origin[1]), round(cube.origin[2])];
            c.rotation = [round(-cube.rotation[0]), round(-cube.rotation[1]), round(cube.rotation[2])];
        }
        if (cube.box_uv) {
            c.uv = [cube.uv_offset[0], cube.uv_offset[1]];
            if (cube.mirror_uv) c.mirror = true;
        } else {
            c.uv = {};
            for (const dir of ['north', 'east', 'south', 'west', 'up', 'down']) {
                const f = cube.faces && cube.faces[dir];
                if (!f || f.texture === null || f.texture === false) continue;
                const uv = [f.uv[0], f.uv[1]], sz = [round(f.uv[2] - f.uv[0]), round(f.uv[3] - f.uv[1])];
                if (dir === 'up' || dir === 'down') { // так пише Bedrock-кодек Blockbench
                    uv[0] += sz[0]; uv[1] += sz[1]; sz[0] *= -1; sz[1] *= -1;
                }
                c.uv[dir] = { uv: uv, uv_size: sz };
            }
        }
        return c;
    }

    /** Піддерево групи → Bedrock-геометрія пропса (координати абсолютні, як у сцені). */
    function compileProp(rootGroup, parentRigBone, texW, texH) {
        const bones = [];
        (function walk(g, parentName) {
            const b = { name: g.name, parent: parentName, pivot: [round(-g.origin[0]), round(g.origin[1]), round(g.origin[2])] };
            if (g.rotation && (g.rotation[0] || g.rotation[1] || g.rotation[2])) {
                b.rotation = [round(-g.rotation[0]), round(-g.rotation[1]), round(g.rotation[2])];
            }
            const cubes = (g.children || []).filter(c => c.type === 'cube' || c.from).map(compileCube);
            if (cubes.length) b.cubes = cubes;
            bones.push(b);
            for (const ch of (g.children || [])) if (ch.type === 'group' || ch.children) walk(ch, g.name);
        })(rootGroup, parentRigBone);
        return {
            format_version: '1.12.0',
            'minecraft:geometry': [{
                description: { identifier: 'geometry.' + snake(rootGroup.name), texture_width: texW || 16, texture_height: texH || 16 },
                bones: bones
            }]
        };
    }

    /** Знайти пропси: найвищі не-ригові групи з риговим предком. */
    function findProps(groups, rig) {
        const props = [];
        for (const g of groups) {
            if (isRigBone(g.name, rig)) continue;
            let p = g.parent, rigParent = null, propParent = false;
            while (p && p !== 'root') {
                if (isRigBone(p.name, rig)) { rigParent = p; break; }
                propParent = true; // між нами і ригом ще не-ригова група — значить ми всередині пропса
                p = p.parent;
            }
            if (rigParent && !propParent) props.push({ group: g, parent: canonicalPlayerBone(rigParent.name) });
        }
        return props;
    }

    function detectRig(groups) {
        const names = groups.map(g => canonicalPlayerBone(g.name));
        return (names.indexOf('right_arm') >= 0 && names.indexOf('torso') >= 0 && names.indexOf('left_leg') >= 0) ? 'player' : null;
    }

    /** Увесь експорт без файлової системи: повертає {path: content}. */
    function buildExport(project, opts) {
        const ns = opts.namespace || 'minecraft';
        const rig = opts.rig || detectRig(project.groups) || ('geo:' + ns + ':' + snake(project.name || 'model'));
        const files = {};
        const doc = { format: 'shaurma_anim', format_version: 1, rig: rig, animations: {} };
        const props = rig === 'player' ? findProps(project.groups, rig) : [];
        const propSpecs = {};
        for (const p of props) {
            const id = snake(p.group.name);
            const s = p.group.shaurma_prop || {};
            const spec = { parent: p.parent };
            if (s.item) spec.item = s.item;
            else {
                spec.model = ns + ':props/' + id;
                spec.texture = ns + ':textures/shaurma_anim/' + id + '.png';
                files['assets/' + ns + '/shaurma_anim/props/' + id + '.geo.json'] =
                    JSON.stringify(compileProp(p.group, p.parent, project.texture_width, project.texture_height), null, 2);
                if (project.textureFor) {
                    const tex = project.textureFor(p.group);
                    if (tex) files['assets/' + ns + '/textures/shaurma_anim/' + id + '.png'] = { image: tex };
                }
            }
            if (s.hide_held && s.hide_held !== 'none') spec.hide_held = s.hide_held;
            if (s.emissive) spec.emissive = true;
            propSpecs[p.group.name] = spec;
        }
        for (const anim of project.animations) {
            if (opts.only && opts.only.indexOf(anim.name) < 0) continue;
            const name = String(anim.name).replace(/^animation\./, '');
            const clip = compileAnimation(anim, anim.shaurma || {});
            // пропс у кліпі — якщо кліп анімує його або його підгрупи, або якщо пропс позначено «у всіх кліпах»
            const used = {};
            for (const p of props) {
                const names = [];
                (function walk(g) { names.push(g.name); for (const c of (g.children || [])) if (c.children) walk(c); })(p.group);
                const animated = names.some(n => clip.bones[n]);
                const always = (p.group.shaurma_prop || {}).always;
                if (animated || always) used[snake(p.group.name)] = propSpecs[p.group.name];
            }
            if (Object.keys(used).length) clip.props = used;
            doc.animations[name] = clip;
        }
        files['assets/' + ns + '/shaurma_anim/' + (rig === 'player' ? 'player/' : '') + snake(opts.file || project.name || 'animations') + '.json'] =
            JSON.stringify(doc, null, 2);
        return files;
    }

    const api = { snake, canonicalPlayerBone, compileKeyframe, compileAnimation, compileCube, compileProp, findProps,
        detectRig, buildExport, PLAYER_RIG };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api; // Node-тести
        return;
    }

    // ── Інтеграція з Blockbench ─────────────────────────────────────────────────────────────

    const disposables = [];

    function projectSnapshot() {
        const groups = Group.all.map(g => g);
        return {
            name: Project.name || Project.geometry_name || 'model',
            groups: groups.map(wrapGroup),
            animations: Animation.all.map(a => ({
                name: a.name, length: a.length, loop: a.loop,
                shaurma: a.shaurma_settings || {},
                animators: Object.fromEntries(Object.entries(a.animators).map(([k, an]) => [k, {
                    name: an.name, uuid: k, type: an.type || (an instanceof EffectAnimator ? 'effect' : 'bone'),
                    keyframes: an.keyframes.map(kf => ({
                        channel: kf.channel, time: kf.time, interpolation: kf.interpolation, easing: kf.easing,
                        data_points: kf.data_points.map(dp => Object.assign({}, dp))
                    }))
                }]))
            })),
            texture_width: Project.texture_width, texture_height: Project.texture_height,
            textureFor: g => {
                const cube = g.children.find(c => c instanceof Cube);
                let tex = null;
                if (cube) {
                    for (const f of Object.values(cube.faces)) if (f.texture) { tex = Texture.all.find(t => t.uuid === f.texture); break; }
                }
                tex = tex || Texture.getDefault();
                return tex ? tex.getDataURL() : null;
            }
        };
    }

    const wrapped = new Map();
    function wrapGroup(g) {
        if (wrapped.has(g)) return wrapped.get(g);
        const w = { name: g.name, origin: g.origin.slice(), rotation: g.rotation.slice(), shaurma_prop: g.shaurma_prop || {} };
        wrapped.set(g, w);
        w.parent = g.parent instanceof Group ? wrapGroup(g.parent) : 'root';
        w.children = g.children.map(c => c instanceof Group ? wrapGroup(c) : {
            type: 'cube', from: c.from.slice(), to: c.to.slice(), origin: c.origin.slice(), rotation: c.rotation.slice(),
            inflate: c.inflate, box_uv: c.box_uv !== undefined ? c.box_uv : Project.box_uv, uv_offset: c.uv_offset.slice(),
            mirror_uv: c.mirror_uv, faces: Object.fromEntries(Object.entries(c.faces).map(([k, f]) => [k, { uv: f.uv.slice(), texture: f.texture }]))
        });
        return w;
    }

    function exportAll() {
        wrapped.clear();
        const snap = projectSnapshot();
        new Dialog({
            id: 'shaurma_export', title: 'Export Shaurma Animation',
            form: {
                namespace: { label: 'Простір імен (modid)', type: 'text', value: Project.geckolib_modid || 'maniacmod' },
                file: { label: 'Ім\'я файлу кліпів', type: 'text', value: snake(Project.name || 'animations') },
                rig: { label: 'Риг', type: 'select', options: { auto: 'Авто', player: 'Гравець (player)', geo: 'Geo-модель' }, value: 'auto' },
                folder: { label: 'Папка resources (де лежить assets/)', type: 'folder', value: '' },
                only_selected: { label: 'Лише вибрана анімація', type: 'checkbox', value: false }
            },
            onConfirm(form) {
                const opts = { namespace: form.namespace.trim(), file: form.file.trim() };
                if (form.rig === 'player') opts.rig = 'player';
                if (form.rig === 'geo') opts.rig = 'geo:' + opts.namespace + ':' + snake(Project.name || 'model');
                if (form.only_selected && Animation.selected) opts.only = [Animation.selected.name];
                const files = buildExport(snap, opts);
                writeFiles(form.folder, files);
            }
        }).show();
    }

    function writeFiles(folder, files) {
        const entries = Object.entries(files);
        if (folder && typeof require !== 'undefined') {
            const fs = require('fs'), path = require('path');
            for (const [rel, content] of entries) {
                const full = path.join(folder, rel);
                fs.mkdirSync(path.dirname(full), { recursive: true });
                if (content && content.image) {
                    fs.writeFileSync(full, Buffer.from(content.image.split(',')[1], 'base64'));
                } else {
                    fs.writeFileSync(full, content, 'utf-8');
                }
            }
            Blockbench.showQuickMessage('Shaurma: записано ' + entries.length + ' файл(ів)', 2500);
            return;
        }
        // Веб-версія: кожен файл окремим завантаженням
        for (const [rel, content] of entries) {
            const name = rel.split('/').pop();
            if (content && content.image) Blockbench.export({ type: 'PNG', extensions: ['png'], name: name, content: content.image, savetype: 'image' });
            else Blockbench.export({ type: 'JSON', extensions: ['json'], name: name.replace(/\.json$/, ''), content: content, savetype: 'text' });
        }
    }

    function animationSettings() {
        const a = Animation.selected;
        if (!a) return Blockbench.showQuickMessage('Вибери анімацію', 1500);
        const s = a.shaurma_settings || {};
        new Dialog({
            id: 'shaurma_anim_settings', title: 'Shaurma: ' + a.name,
            form: {
                slot: { label: 'Шар', type: 'select', options: Object.fromEntries(SLOTS.map(x => [x, x])), value: s.slot || 'action' },
                mask: { label: 'Маска (upper_body, right_arm+torso*0.5, full-legs)', type: 'text', value: s.mask || '' },
                blend_in: { label: 'Згасання на вході, с', type: 'number', value: s.blend_in !== undefined ? s.blend_in : 0.15, step: 0.05, min: 0 },
                blend_out: { label: 'Згасання на виході, с', type: 'number', value: s.blend_out !== undefined ? s.blend_out : 0.2, step: 0.05, min: 0 },
                look: { label: 'Голова за камерою (0..1)', type: 'number', value: s.look !== undefined ? s.look : 1, step: 0.1, min: 0, max: 1 },
                first_person: { label: 'Перша особа', type: 'select', options: { vanilla: 'Ванільні руки', model: 'Анімовані руки + пропси', hide: 'Сховати руки' }, value: s.first_person || 'vanilla' },
                first_person_pitch: { label: 'Руки в 1-й особі слідують нахилу камери (0..1)', type: 'number', value: s.first_person_pitch || 0, step: 0.1, min: 0, max: 1 },
                first_person_offset: { label: 'Руки в 1-й особі: зсув px (x, y, z назад) і нахил°', type: 'vector', dimensions: 4, value: s.first_person_offset || [0, 0, 2.5, 30] },
                camera: { label: 'Камера 1-ї особи йде за головою: позиція (0..1)', type: 'number', value: s.camera || 0, step: 0.1, min: 0, max: 1 },
                camera_rotation: { label: 'Камера повертається за головою (0..1)', type: 'number', value: s.camera_rotation || 0, step: 0.1, min: 0, max: 1 },
                stride: { label: 'Цикл кроку: циклів на крок (для ходьби)', type: 'number', value: s.stride || 1, step: 0.1, min: 0.1 },
                additive: { label: 'Адитивно (додається поверх, а не замінює)', type: 'checkbox', value: !!s.additive }
            },
            onConfirm(form) {
                Undo.initEdit({ animations: [a] });
                a.shaurma_settings = form;
                Undo.finishEdit('Shaurma animation settings');
            }
        }).show();
    }

    function propSettings() {
        const g = Group.selected;
        if (!g) return Blockbench.showQuickMessage('Вибери групу пропса', 1500);
        const s = g.shaurma_prop || {};
        new Dialog({
            id: 'shaurma_prop_settings', title: 'Shaurma prop: ' + g.name,
            form: {
                hide_held: { label: 'Ховати ванільний предмет у руці', type: 'select', options: { none: 'Ні', right: 'Права', left: 'Ліва', both: 'Обидві' }, value: s.hide_held || 'none' },
                emissive: { label: 'Світиться (без тіні)', type: 'checkbox', value: !!s.emissive },
                always: { label: 'У всіх кліпах (навіть без ключів)', type: 'checkbox', value: !!s.always },
                item: { label: 'Замість моделі — предмет гри (ns:item), порожньо = модель', type: 'text', value: s.item || '' }
            },
            onConfirm(form) {
                Undo.initEdit({ outliner: true });
                g.shaurma_prop = form;
                Undo.finishEdit('Shaurma prop settings');
            }
        }).show();
    }

    /** Риг ванільного гравця: ті самі півоти, що в рушії (Rig.PLAYER) і в ванільній моделі. */
    function createPlayerRig() {
        Undo.initEdit({ outliner: true, elements: [] });
        const body = new Group({ name: 'body', origin: [0, 12, 0] }).init();
        const parts = [
            ['head', [0, 24, 0], [[-4, 24, -4], [4, 32, 4], [0, 0]]],
            ['torso', [0, 24, 0], [[-4, 12, -2], [4, 24, 2], [16, 16]]],
            ['right_arm', [5, 22, 0], [[4, 12, -2], [8, 24, 2], [40, 16]]],
            ['left_arm', [-5, 22, 0], [[-8, 12, -2], [-4, 24, 2], [32, 48]]],
            ['right_leg', [1.9, 12, 0], [[-0.1, 0, -2], [3.9, 12, 2], [0, 16]]],
            ['left_leg', [-1.9, 12, 0], [[-3.9, 0, -2], [0.1, 12, 2], [16, 48]]]
        ];
        const elements = [];
        for (const [name, origin, cube] of parts) {
            const g = new Group({ name: name, origin: origin }).addTo(body).init();
            const c = new Cube({ name: name, from: cube[0], to: cube[1], uv_offset: cube[2], box_uv: true }).addTo(g).init();
            elements.push(c);
            if (name === 'right_arm') new Group({ name: 'right_item', origin: [6, 12, -2] }).addTo(g).init();
            if (name === 'left_arm') new Group({ name: 'left_item', origin: [-6, 12, -2] }).addTo(g).init();
        }
        Project.texture_width = 64;
        Project.texture_height = 64;
        Canvas.updateAll();
        Undo.finishEdit('Shaurma player rig', { outliner: true, elements: elements });
    }

    Plugin.register('shaurma_anim', {
        title: 'Shaurma Animations',
        author: 'ShaurmaLib',
        icon: 'directions_run',
        description: 'Експорт анімацій тіла, пропсів і масок для рушія анімацій ShaurmaLib (Minecraft, будь-який лоадер).',
        version: '1.0.0',
        variant: 'both',
        min_version: '4.8.0',
        onload() {
            disposables.push(new Property(Animation, 'object', 'shaurma_settings'));
            disposables.push(new Property(Group, 'object', 'shaurma_prop'));
            const exp = new Action('shaurma_export', { name: 'Export Shaurma Animation', icon: 'directions_run', category: 'file', click: exportAll });
            const set = new Action('shaurma_anim_settings', { name: 'Shaurma: налаштування анімації', icon: 'tune', category: 'animation', click: animationSettings });
            const prop = new Action('shaurma_prop_settings', { name: 'Shaurma: налаштування пропса', icon: 'medical_services', category: 'edit', click: propSettings });
            const rig = new Action('shaurma_player_rig', { name: 'Shaurma: створити риг гравця', icon: 'accessibility_new', category: 'edit', click: createPlayerRig });
            MenuBar.addAction(exp, 'file.export');
            MenuBar.addAction(set, 'animation');
            MenuBar.addAction(rig, 'animation');
            if (Group.prototype.menu) Group.prototype.menu.addAction(prop);
            MenuBar.addAction(prop, 'edit');
            disposables.push(exp, set, prop, rig);
        },
        onunload() {
            for (const d of disposables) if (d && d.delete) d.delete();
            disposables.length = 0;
        }
    });
})();
