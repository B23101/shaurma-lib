/*
 * Shaurma Animations — плагін Blockbench для рушія анімацій ShaurmaLib.
 *
 * Ідея: анімація гравця — це СЦЕНА. У ній, крім ригу гравця, лежать окремі моделі (пропси):
 * аптечка в правій руці, бинт, що переходить у ліву, шприц адреналіну, 3D-ефект навколо гравця.
 * Вони НЕ предмети слотів — вони існують лише поки грає кліп, і рушій малює їх рівно так, як у Blockbench.
 *
 * Що робить:
 *   • File → Import → «Shaurma: модель у анімацію»: .bbmodel або .geo.json разом з текстурами (кілька
 *     текстур зшиваються в одну), з власною анімацією моделі (кришка відкривається) — прямо в поточну
 *     анімацію. Прив'язка до кістки (права/ліва рука, голова...) або «вільно» (world) — ефекти навколо гравця.
 *   • Пропс = група під кісткою ригу (right_item, left_item, head...), що сама не є кісткою ригу, АБО група
 *     в корені сцени поза ригом (вільний пропс, parent "world"). Підгрупи пропса (кришка, бинт) анімуються
 *     у тому ж кліпі.
 *   • Animation → «Shaurma: передати пропс в іншу кістку»: бинт з аптечки — в ліву руку з поточного кадру
 *     (копія під цільовою кісткою + ступінчасті ключі видимості в обох).
 *   • Animation → «Shaurma: показати / сховати пропс з цього кадру» (масштаб 0, ступінчасто — як у грі).
 *   • File → Export → «Export Shaurma Animation»: кліпи + моделі пропсів + текстури → папка resources
 *     (запам'ятовується в проєкті) або .zip; «Shaurma: швидкий експорт» — повтор без діалогу.
 *   • Animation → «Shaurma: налаштування анімації»: шар, маска («ноги ходять, руки лікують»), згасання,
 *     погляд, перша особа. Зберігається в .bbmodel.
 *   • Group (ПКМ) → «Shaurma: налаштування пропса»: ховати ванільний предмет у руці, світіння, прозорість,
 *     предмет гри замість моделі.
 *   • «Shaurma: створити риг гравця», «Shaurma: скін гравця», стартовий екран → «Shaurma: модель гравця».
 *
 * Конвенції — ті самі, що читає рушій (перевірено проти GeckoLib): значення ключів — як у Blockbench UI,
 * Bedrock-геометрія: півот (−x, y, z), обертання (−x, −y, z), початок кубика (−to.x, from.y, from.z).
 */
(function () {
    'use strict';

    const PLAYER_RIG = ['body', 'head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg', 'right_item', 'left_item'];
    /** Півоти ригу гравця (простір Blockbench) — як Rig.PLAYER у рушії. */
    const RIG_PIVOTS = {
        body: [0, 12, 0], head: [0, 24, 0], torso: [0, 24, 0], right_arm: [5, 22, 0], right_item: [6, 12, -2],
        left_arm: [-5, 22, 0], left_item: [-6, 12, -2], right_leg: [1.9, 12, 0], left_leg: [-1.9, 12, 0]
    };
    const WORLD = 'world';
    const SLOTS = ['locomotion', 'idle', 'hold', 'action', 'gesture', 'pose', 'cinematic'];
    const FACES = ['north', 'east', 'south', 'west', 'up', 'down'];

    // ── Чисті функції конвертації (тестуються в Node: tools/blockbench/test) ─────────────────────

    const TRANSLIT = {
        а: 'a', б: 'b', в: 'v', г: 'h', ґ: 'g', д: 'd', е: 'e', є: 'ie', ж: 'zh', з: 'z', и: 'y', і: 'i', ї: 'i', й: 'i',
        к: 'k', л: 'l', м: 'm', н: 'n', о: 'o', п: 'p', р: 'r', с: 's', т: 't', у: 'u', ф: 'f', х: 'kh', ц: 'ts', ч: 'ch',
        ш: 'sh', щ: 'shch', ь: '', ю: 'iu', я: 'ia', ы: 'y', э: 'e', ё: 'io', ъ: ''
    };

    /** Ім'я → snake_case латиницею (шляхи ресурсів Minecraft: лише a-z0-9_). «Аптечка» → aptechka. */
    function snake(name) {
        return String(name).trim()
            .replace(/[А-ЯҐЄІЇЁЫЭЪа-яґєіїёыэъ]/g, ch => {
                const t = TRANSLIT[ch.toLowerCase()];
                return t === undefined ? '' : (ch === ch.toLowerCase() || !t ? t : t[0].toUpperCase() + t.slice(1));
            })
            .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
            .replace(/[-\s.]+/g, '_')
            .replace(/[^A-Za-z0-9_]/g, '')
            .toLowerCase();
    }

    /** Ті самі псевдоніми, що BoneNames.player у рушії (інакше ім'я пропса може «з'їсти» кістку гравця). */
    const PLAYER_ALIASES = {
        arm_right: 'right_arm', arm_left: 'left_arm', leg_right: 'right_leg', leg_left: 'left_leg',
        item_right: 'right_item', item_left: 'left_item', right_hand: 'right_item', left_hand: 'left_item',
        biped_right_arm: 'right_arm', biped_left_arm: 'left_arm', biped_right_leg: 'right_leg', biped_left_leg: 'left_leg',
        biped_head: 'head', biped_body: 'torso', chest: 'torso', root: 'body'
    };

    function canonicalPlayerBone(name) {
        const s = snake(name);
        return PLAYER_ALIASES[s] || s;
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

    /** Molang-рядок або число з протилежним знаком (як invertMolang у Blockbench). */
    function neg(v) {
        if (typeof v === 'number') return v === 0 ? 0 : -v;
        const s = String(v).trim();
        if (s === '' || s === '0') return 0;
        return s.startsWith('-') && !/[+\-*/]/.test(s.slice(1)) ? s.slice(1) : '-(' + s + ')';
    }

    /**
     * Bedrock-осі: Blockbench зберігає значення «як у редакторі», а Bedrock/GeckoLib і рушій
     * ShaurmaLib чекають X позиції та X/Y обертання з протилежним знаком (те саме робить
     * вбудований експорт Blockbench, keyframe.js → flipArray). Без цього кліпи віддзеркалені:
     * руки й ноги згинаються назад, нахили — в інший бік.
     */
    function flip(arr, channel) {
        if (channel === 'position') return [neg(arr[0]), arr[1], arr[2]];
        if (channel === 'rotation') return [neg(arr[0]), neg(arr[1]), arr[2]];
        return arr;
    }

    /** Ключ Blockbench → ключ Bedrock/ShaurmaLib. */
    function compileKeyframe(kf) {
        const points = kf.data_points || [];
        const out = {};
        const ch = kf.channel;
        if (points.length >= 2) {
            out.pre = flip(vec(points[0]), ch);
            out.post = flip(vec(points[1]), ch);
        } else {
            out.post = flip(vec(points[0]), ch);
        }
        const interp = kf.interpolation || 'linear';
        if (interp !== 'linear') out.lerp_mode = interp;
        if (kf.easing && kf.easing !== 'linear') out.easing = kf.easing;
        return out;
    }

    /** Анімація Blockbench → кліп ShaurmaLib. */
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
        applyKind(clip, bones, settings);
        clip.bones = bones;
        if (events.length) clip.events = events.sort((a, b) => a.time - b.time);
        return clip;
    }

    /**
     * Тип анімації → шар, маска і поведінка з іншими анімаціями:
     *   full    — повна: усе інше вимкнено (кістки без ключів стоять у спокої), шар pose, голова не за камерою;
     *   partial — часткова разова дія (підняти руку): лише свої кістки, шар action;
     *   sync    — синхронна (аптечка на ходу): шар action, рух лишається на вибраних частинах (ноги, голова...);
     *   state   — стан (ліхтарик, каністра): накладається на біг/шифт/idle, поступається удару, шар hold;
     *             з «предметом у руці» вмикається сам;
     *   custom  — шар і маска вручну (як раніше).
     */
    const KINDS = ['full', 'partial', 'sync', 'state', 'custom'];

    function kindOf(s) {
        if (s.kind && KINDS.indexOf(s.kind) >= 0) return s.kind;
        if (s.hold_item) return 'state';
        return s.slot || s.mask ? 'custom' : 'partial';
    }

    function syncMask(s) {
        const keep = [];
        if (s.keep_legs !== false) keep.push('legs', 'body!');
        if (s.keep_torso) keep.push('torso');
        if (s.keep_head) keep.push('head');
        if (s.keep_right_arm) keep.push('right_arm');
        if (s.keep_left_arm) keep.push('left_arm');
        return keep.length ? 'full-' + keep.join('-') : 'full';
    }

    function applyKind(clip, bones, s) {
        const kind = kindOf(s);
        clip.kind = kind;
        if (kind === 'full') {
            clip.slot = s.slot === 'cinematic' ? 'cinematic' : 'pose';
            clip.mask = 'full';
            if (s.look === undefined || s.look === '' || s.look === null) clip.look = 0;
            // «усе інше вимкнено»: кістки ригу без ключів тримаються в спокої, а не віддаються ходьбі
            for (const b of ['body', 'head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']) {
                if (Object.keys(bones).some(n => canonicalPlayerBone(n) === b)) continue;
                bones[b] = { rotation: { '0.0': { post: [0, 0, 0] } }, position: { '0.0': { post: [0, 0, 0] } } };
            }
        } else if (kind === 'partial') {
            clip.slot = s.slot && s.slot !== 'hold' && s.slot !== 'locomotion' ? s.slot : 'action';
            if (!s.mask) delete clip.mask;
        } else if (kind === 'sync') {
            clip.slot = 'action';
            clip.mask = s.mask && s.mask_manual ? s.mask : syncMask(s);
            if (s.keep_head && (s.look === undefined || s.look === '')) clip.look = 1;
        } else if (kind === 'state') {
            clip.slot = 'hold';
            if (!s.mask) delete clip.mask;
            const st = {};
            const n = (k, def) => { const v = Number(s[k]); if (s[k] !== undefined && s[k] !== '' && !isNaN(v) && v !== def) st[k] = round(v); };
            n('follow_move', 0.35); n('follow_sneak', 1); n('follow_idle', 1);
            if (s.yield_swing === false || s.hold_yield_swing === false) st.yield_swing = false;
            if (s.yield_use === false || s.hold_yield_use === false) st.yield_use = false;
            clip.state = Object.keys(st).length ? st : true;
            const hold = holdSpec(s);
            if (hold) clip.hold = hold;
            if (clip.loop === 'once') clip.loop = 'hold'; // стан триває, поки його не вимкнуть
        }
        return clip;
    }

    /** Стан тримання з налаштувань анімації: поки гравець тримає предмет — кліп грає сам (без Java-коду). */
    function holdSpec(s) {
        const items = String(s.hold_item || '').split(/[,\s]+/).map(x => x.trim()).filter(Boolean)
            .map(x => x.indexOf(':') >= 0 ? x : 'minecraft:' + x);
        if (!items.length) return null;
        const h = { item: items.length === 1 ? items[0] : items };
        if (s.hold_hand && s.hold_hand !== 'main') h.hand = s.hold_hand;
        if (s.hold_priority) h.priority = Number(s.hold_priority) || 0;
        return h;
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
            for (const dir of FACES) {
                const f = cube.faces && cube.faces[dir];
                if (!f || f.texture === null || f.texture === false) continue;
                const uv = [round(f.uv[0]), round(f.uv[1])], sz = [round(f.uv[2] - f.uv[0]), round(f.uv[3] - f.uv[1])];
                if (dir === 'up' || dir === 'down') { // так пише Bedrock-кодек Blockbench
                    uv[0] = round(uv[0] + sz[0]); uv[1] = round(uv[1] + sz[1]); sz[0] *= -1; sz[1] *= -1;
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
            if (g.export === false) return;
            const b = { name: g.name, parent: parentName, pivot: [round(-g.origin[0]), round(g.origin[1]), round(g.origin[2])] };
            if (g.rotation && (g.rotation[0] || g.rotation[1] || g.rotation[2])) {
                b.rotation = [round(-g.rotation[0]), round(-g.rotation[1]), round(g.rotation[2])];
            }
            const cubes = (g.children || []).filter(c => (c.type === 'cube' || c.from) && c.export !== false).map(compileCube);
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

    function subtreeHasRig(g, rig) {
        for (const c of (g.children || [])) {
            if (!c.children) continue;
            if (isRigBone(c.name, rig) || subtreeHasRig(c, rig)) return true;
        }
        return false;
    }

    /**
     * Знайти пропси: найвищі не-ригові групи з риговим предком (батько = та кістка) АБО групи в корені
     * сцени без ригу всередині (батько = world: вільна модель навколо гравця).
     */
    function findProps(groups, rig) {
        const props = [];
        for (const g of groups) {
            if (g.export === false || isRigBone(g.name, rig)) continue;
            let p = g.parent, rigParent = null, propParent = false;
            while (p && p !== 'root') {
                if (isRigBone(p.name, rig)) { rigParent = p; break; }
                propParent = true; // між нами і ригом ще не-ригова група — значить ми всередині пропса
                p = p.parent;
            }
            if (propParent || subtreeHasRig(g, rig)) continue;
            props.push({ group: g, parent: rigParent ? canonicalPlayerBone(rigParent.name) : WORLD });
        }
        return props;
    }

    function detectRig(groups) {
        const names = groups.map(g => canonicalPlayerBone(g.name));
        return (names.indexOf('right_arm') >= 0 && names.indexOf('torso') >= 0 && names.indexOf('left_leg') >= 0) ? 'player' : null;
    }

    function groupNames(g, out) {
        out = out || [];
        out.push(g.name);
        for (const c of (g.children || [])) if (c.children) groupNames(c, out);
        return out;
    }

    /**
     * Увесь експорт без файлової системи: повертає {path: content}. {@code opts.warnings} (масив) — сюди
     * пишуться проблеми, які не зупиняють експорт, але дадуть у грі не те, що в Blockbench.
     */
    function buildExport(project, opts) {
        const ns = opts.namespace || 'minecraft';
        const warn = m => { if (opts.warnings) opts.warnings.push(m); };
        const rig = opts.rig || detectRig(project.groups) || ('geo:' + ns + ':' + snake(project.name || 'model'));
        const files = {};
        const doc = { format: 'shaurma_anim', format_version: 1, rig: rig, animations: {} };
        // split: кожна анімація гравця — власний файл player/<тека>/<анімація>.json (id кліпа — ns:анімація, від файлу
        // не залежить). Для geo-ригів id містить ім'я файлу, тому там завжди один файл на проєкт.
        const split = !!opts.split && rig === 'player';
        const folder = snake(opts.file || project.name || 'animations') || 'animations';
        const props = rig === 'player' ? findProps(project.groups, rig) : [];
        const propSpecs = {};

        // імена кісток — ключі треків у кліпі: дві групи з однаковим ім'ям анімуються як одна
        const seen = {};
        for (const g of project.groups) {
            if (g.export === false) continue;
            const k = rig === 'player' ? canonicalPlayerBone(g.name) : g.name;
            if (seen[k]) warn('Дві групи з іменем «' + g.name + '» — у кліпі вони рухатимуться як одна. Перейменуй одну.');
            seen[k] = true;
        }

        for (const p of props) {
            const id = snake(p.group.name);
            const s = p.group.shaurma_prop || {};
            const spec = { parent: p.parent };
            if (s.item) spec.item = s.item;
            else {
                spec.model = ns + ':props/' + id;
                spec.texture = ns + ':textures/shaurma_anim/' + id + '.png';
                let tex = project.textureFor ? project.textureFor(p.group) : null;
                if (typeof tex === 'string') tex = { image: tex };
                if (tex && tex.warning) warn('Пропс «' + p.group.name + '»: ' + tex.warning);
                const tw = (tex && tex.uv_width) || project.texture_width, th = (tex && tex.uv_height) || project.texture_height;
                files['assets/' + ns + '/shaurma_anim/props/' + id + '.geo.json'] =
                    JSON.stringify(compileProp(p.group, p.parent, tw, th), null, 2);
                if (tex && tex.image) files['assets/' + ns + '/textures/shaurma_anim/' + id + '.png'] = { image: tex.image };
                else warn('Пропс «' + p.group.name + '» без текстури — у грі буде фіолетово-чорний.');
            }
            if (s.hide_held && s.hide_held !== 'none') spec.hide_held = s.hide_held;
            if (s.emissive) spec.emissive = true;
            if (s.translucent) spec.translucent = true;
            propSpecs[p.group.name] = spec;
        }
        let count = 0;
        for (const anim of project.animations) {
            if (opts.only && opts.only.indexOf(anim.name) < 0) continue;
            const name = String(anim.name).replace(/^animation\./, '');
            const clip = compileAnimation(anim, anim.shaurma || {});
            // пропс у кліпі — якщо кліп анімує його або його підгрупи, або якщо пропс позначено «у всіх кліпах»
            const used = {};
            for (const p of props) {
                const names = groupNames(p.group);
                const animated = names.some(n => clip.bones[n]);
                const always = (p.group.shaurma_prop || {}).always;
                if (animated || always) used[snake(p.group.name)] = propSpecs[p.group.name];
            }
            if (Object.keys(used).length) clip.props = used;
            if (split) {
                const one = { format: doc.format, format_version: doc.format_version, rig: rig, animations: { [name]: clip } };
                const path = 'assets/' + ns + '/shaurma_anim/player/' + folder + '/' + (snake(name) || 'animation') + '.json';
                if (files[path]) warn('Дві анімації дають один файл «' + path + '» — друга перезаписала першу. Перейменуй одну.');
                files[path] = JSON.stringify(one, null, 2);
            } else {
                doc.animations[name] = clip;
            }
            count++;
        }
        if (!count) warn('У проєкті немає анімацій — експортовано лише моделі пропсів.');
        if (!split) {
            files['assets/' + ns + '/shaurma_anim/' + (rig === 'player' ? 'player/' : '') + folder + '.json'] =
                JSON.stringify(doc, null, 2);
        }
        return files;
    }

    // ── Імпорт моделей у сцену анімації ─────────────────────────────────────────────────────

    /**
     * Box-UV → UV по гранях (розгортка Blockbench; та сама, що GeoMesh/GeckoLib). Розміри — floor,
     * як у Java-рушії. Повертає {dir: [u1, v1, u2, v2]}.
     */
    function boxUvFaces(uvOffset, from, to, mirror) {
        const W = Math.floor(round(Math.abs(to[0] - from[0]))), H = Math.floor(round(Math.abs(to[1] - from[1])));
        const D = Math.floor(round(Math.abs(to[2] - from[2])));
        const list = {
            east: [[0, D], [D, H]], west: [[D + W, D], [D, H]],
            up: [[D + W, D], [-W, -D]], down: [[D + 2 * W, 0], [-W, D]],
            south: [[2 * D + W, D], [W, H]], north: [[D, D], [W, H]]
        };
        if (mirror) {
            for (const k in list) { list[k][0][0] += list[k][1][0]; list[k][1][0] *= -1; }
            const e = list.east; list.east = list.west; list.west = e;
        }
        const u = uvOffset ? uvOffset[0] || 0 : 0, v = uvOffset ? uvOffset[1] || 0 : 0;
        const out = {};
        for (const dir of FACES) {
            const [f, s] = list[dir];
            out[dir] = [u + f[0], v + f[1], u + f[0] + s[0], v + f[1] + s[1]];
        }
        return out;
    }

    function vec3(a, def) {
        if (!Array.isArray(a)) return [def, def, def];
        return [Number(a[0]) || 0, Number(a.length > 1 ? a[1] : a[0]) || 0, Number(a.length > 2 ? a[2] : a[0]) || 0];
    }

    /**
     * Bedrock .geo.json → нейтральна сцена {name, texture_width, texture_height, roots:[група], cubes:[], textures:[]}.
     * Група: {name, origin, rotation, cubes, children}; кубик: {from, to, origin, rotation, inflate, faces:{dir:{uv, texture}}}.
     * Координати — як у Blockbench (обернено до compileCube/compileProp).
     */
    function geoToScene(json, index) {
        const root = typeof json === 'string' ? JSON.parse(json) : json;
        let geos = root['minecraft:geometry'];
        if (!Array.isArray(geos)) {
            geos = Object.keys(root).filter(k => k.startsWith('geometry.')).map(k => {
                const g = root[k];
                return { description: { identifier: k.split(':')[0], texture_width: g.texturewidth, texture_height: g.textureheight }, bones: g.bones };
            });
        }
        if (!geos.length) throw new Error('у файлі немає minecraft:geometry');
        const geo = geos[index || 0];
        const d = geo.description || {};
        const tw = d.texture_width || geo.texturewidth || 16, th = d.texture_height || geo.textureheight || 16;
        const byName = {}, list = [];
        for (const b of (geo.bones || [])) {
            const pv = vec3(b.pivot, 0), rt = vec3(b.rotation, 0);
            const g = { name: b.name, parentName: b.parent || null, origin: [-pv[0], pv[1], pv[2]], rotation: [-rt[0], -rt[1], rt[2]], cubes: [], children: [] };
            for (const c of (b.cubes || [])) g.cubes.push(geoCube(c, b));
            byName[b.name] = g;
            list.push(g);
        }
        const roots = [];
        for (const g of list) {
            const p = g.parentName && byName[g.parentName];
            if (p) p.children.push(g); else roots.push(g);
            delete g.parentName;
        }
        const name = String(d.identifier || 'model').replace(/^geometry\./, '');
        return { name: name, texture_width: tw, texture_height: th, roots: roots, cubes: [], textures: [], animations: [], warnings: [] };
    }

    function geoCube(c, bone) {
        const o = vec3(c.origin, 0), s = vec3(c.size, 0);
        const from = [-(o[0] + s[0]), o[1], o[2]], to = [-o[0], o[1] + s[1], o[2] + s[2]];
        const mirror = c.mirror !== undefined ? !!c.mirror : !!bone.mirror;
        const out = {
            from: from, to: to, inflate: Number(c.inflate !== undefined ? c.inflate : (bone.inflate || 0)) || 0,
            origin: c.pivot ? [-c.pivot[0], c.pivot[1], c.pivot[2]] : [0, 0, 0],
            rotation: c.rotation ? [-c.rotation[0], -c.rotation[1], c.rotation[2]] : [0, 0, 0],
            faces: {}
        };
        if (Array.isArray(c.uv) || c.uv === undefined) {
            const f = boxUvFaces(c.uv || [0, 0], from, to, mirror);
            for (const dir of FACES) out.faces[dir] = { uv: f[dir], texture: 0 };
        } else {
            for (const dir of FACES) {
                const f = c.uv[dir];
                if (!f) { out.faces[dir] = { uv: [0, 0, 0, 0], texture: null }; continue; }
                const uv = vec3(f.uv, 0), sz = f.uv_size ? vec3(f.uv_size, 0) : [0, 0];
                out.faces[dir] = (dir === 'up' || dir === 'down')
                    ? { uv: [uv[0] + sz[0], uv[1] + sz[1], uv[0], uv[1]], texture: 0 }
                    : { uv: [uv[0], uv[1], uv[0] + sz[0], uv[1] + sz[1]], texture: 0 };
            }
        }
        return out;
    }

    /**
     * .bbmodel → та сама нейтральна сцена + текстури (data URL або шлях) і анімації моделі (ключі за uuid груп).
     * Підтримує і старий outliner (групи вбудовані), і новий (масив groups + посилання за uuid).
     */
    function bbmodelToScene(json) {
        const bb = typeof json === 'string' ? JSON.parse(json) : json;
        const res = bb.resolution || {};
        const W = res.width || 16, H = res.height || 16;
        const warnings = [];
        const textures = (bb.textures || []).map(t => ({
            name: t.name || 'texture', uuid: t.uuid, source: t.source || null, path: t.path || null, relative_path: t.relative_path || null,
            uv_width: t.uv_width || W, uv_height: t.uv_height || H
        }));
        const texIndex = ref => {
            if (ref === null || ref === false || ref === undefined) return null;
            if (typeof ref === 'number') return ref < textures.length ? ref : null;
            const i = textures.findIndex(t => t.uuid === ref);
            return i >= 0 ? i : null;
        };
        const boxDefault = !!(bb.meta && bb.meta.box_uv);
        const elements = {};
        let skipped = 0;
        for (const e of (bb.elements || [])) {
            if (e.type && e.type !== 'cube') { skipped++; continue; }
            const box = e.box_uv !== undefined ? !!e.box_uv : boxDefault;
            const cube = {
                name: e.name, from: vec3(e.from, 0), to: vec3(e.to, 0), origin: vec3(e.origin, 0), rotation: vec3(e.rotation, 0),
                inflate: Number(e.inflate) || 0, export: e.export, faces: {}
            };
            const ef = e.faces || {};
            const fromBox = box ? boxUvFaces(e.uv_offset || [0, 0], cube.from, cube.to, !!e.mirror_uv) : null;
            let boxTex = null;
            for (const dir of FACES) if (ef[dir] && texIndex(ef[dir].texture) !== null) { boxTex = texIndex(ef[dir].texture); break; }
            for (const dir of FACES) {
                const f = ef[dir];
                if (box) cube.faces[dir] = { uv: fromBox[dir], texture: f && f.texture === null ? null : boxTex };
                else if (!f) cube.faces[dir] = { uv: [0, 0, 0, 0], texture: null };
                else cube.faces[dir] = { uv: (f.uv || [0, 0, 0, 0]).map(Number), texture: texIndex(f.texture), rotation: f.rotation || 0 };
            }
            elements[e.uuid] = cube;
        }
        if (skipped) warnings.push(skipped + ' меш(ів) пропущено — Bedrock-геометрія підтримує лише кубики.');
        const groupData = {};
        for (const g of (bb.groups || [])) groupData[g.uuid] = g;
        const roots = [], loose = [];
        function node(n, parentList, parentCubes) {
            if (typeof n === 'string') {
                if (elements[n]) parentCubes.push(elements[n]);
                return;
            }
            const data = n.name !== undefined ? n : (groupData[n.uuid] || {});
            const g = {
                name: data.name || 'group', uuid: n.uuid || data.uuid, origin: vec3(data.origin, 0), rotation: vec3(data.rotation, 0),
                export: data.export, cubes: [], children: []
            };
            for (const c of (n.children || [])) node(c, g.children, g.cubes);
            parentList.push(g);
        }
        for (const n of (bb.outliner || [])) node(n, roots, loose);
        const animations = (bb.animations || []).map(a => ({
            name: String(a.name || 'animation').replace(/^animation\./, ''), length: Number(a.length) || 0, loop: a.loop,
            animators: Object.entries(a.animators || {}).map(([uuid, an]) => ({ uuid: uuid, name: an.name, type: an.type, keyframes: an.keyframes || [] }))
        }));
        const name = (bb.model_identifier || bb.name || (bb.geometry_name) || 'model');
        return { name: String(name).replace(/^geometry\./, ''), texture_width: W, texture_height: H, roots: roots, cubes: loose,
            textures: textures, animations: animations, warnings: warnings,
            display: bb.display || null, model_format: (bb.meta && bb.meta.model_format) || null };
    }

    /**
     * pack.mcmeta для архіву: той самий .zip — і ресурс-пак (resourcepacks/), і пакет для папки гри
     * shaurma_anim/ (рушій читає його сам, розпаковувати не треба). 15 — формат ресурсів 1.20.1.
     */
    function packMeta(description) {
        return JSON.stringify({ pack: { pack_format: 15, description: String(description || 'ShaurmaLib animations') } }, null, 2);
    }

    /** Кілька текстур моделі → одна (рушій малює пропс однією текстурою): стовпчиком, у просторі UV. */
    function atlasLayout(textures) {
        let width = 0, height = 0;
        const offsets = [];
        for (const t of textures) {
            offsets.push([0, height]);
            width = Math.max(width, t.uv_width);
            height += t.uv_height;
        }
        return { width: width || 16, height: height || 16, offsets: offsets };
    }

    /**
     * Сцена моделі → готові групи пропса: обгортка з іменем пропса (ручка для переміщення/видимості),
     * унікальні імена (жодне не збігається з кісткою гравця чи іншою групою проєкту), масштаб, зсув
     * у точку прив'язки, UV у просторі атласу.
     *
     * @param o {name, scale, anchor:[x,y,z] куди стане початок моделі, taken: [імена в проєкті],
     *           atlas: atlasLayout|null, uvScale:[fx,fy] (старий Blockbench без UV-розміру текстури)}
     * @return {root, nameByUuid, names}
     */
    function placeScene(scene, o) {
        const sv = Array.isArray(o.scale) ? o.scale.map(x => Number(x) || 1) : [Number(o.scale) || 1, Number(o.scale) || 1, Number(o.scale) || 1];
        const scale = (Math.abs(sv[0]) + Math.abs(sv[1]) + Math.abs(sv[2])) / 3;
        const center = o.center || [0, 0, 0];
        const anchor = o.anchor || [0, 0, 0];
        const taken = new Set((o.taken || []).map(n => canonicalPlayerBone(n)));
        const nameByUuid = {}, names = [];
        const uniq = base => {
            let n = snake(base) || 'part', i = 2;
            const ok = x => !taken.has(canonicalPlayerBone(x)) && PLAYER_RIG.indexOf(canonicalPlayerBone(x)) < 0 && !PLAYER_ALIASES[x];
            let cand = n;
            while (!ok(cand)) cand = n + '_' + (i++);
            taken.add(canonicalPlayerBone(cand));
            names.push(cand);
            return cand;
        };
        const id = uniq(o.name || scene.name || 'prop');
        const P = p => [0, 1, 2].map(i => round(anchor[i] + (p[i] - center[i]) * sv[i]));
        const atlas = o.atlas, uvScale = o.uvScale || [1, 1];
        function cube(c) {
            const faces = {};
            for (const dir of FACES) {
                const f = c.faces && c.faces[dir];
                if (!f || f.texture === null || f.texture === undefined) { faces[dir] = { uv: [0, 0, 0, 0], texture: null }; continue; }
                const off = atlas && atlas.offsets[f.texture] ? atlas.offsets[f.texture] : [0, 0];
                const uv = [f.uv[0] + off[0], f.uv[1] + off[1], f.uv[2] + off[0], f.uv[3] + off[1]];
                faces[dir] = { uv: [round(uv[0] * uvScale[0]), round(uv[1] * uvScale[1]), round(uv[2] * uvScale[0]), round(uv[3] * uvScale[1])], texture: 0 };
                if (f.rotation) faces[dir].rotation = f.rotation;
            }
            const from = P(c.from), to = P(c.to);
            return { name: c.name || 'cube', from: from, to: to, origin: P(c.origin || [0, 0, 0]), rotation: (c.rotation || [0, 0, 0]).slice(),
                inflate: round((c.inflate || 0) * scale), export: c.export, faces: faces };
        }
        function group(g) {
            const prefixed = snake(g.name).startsWith(id) ? g.name : id + '_' + snake(g.name);
            const out = { name: uniq(prefixed), origin: P(g.origin), rotation: (g.rotation || [0, 0, 0]).slice(), export: g.export,
                cubes: (g.cubes || []).map(cube), children: (g.children || []).map(group) };
            if (g.uuid) nameByUuid[g.uuid] = out.name;
            nameByUuid['name:' + g.name] = out.name;
            return out;
        }
        const root = { name: id, origin: anchor.slice(), rotation: (o.rotation || [0, 0, 0]).map(round), cubes: (scene.cubes || []).map(cube), children: (scene.roots || []).map(group) };
        return { root: root, nameByUuid: nameByUuid, names: names };
    }

    // ── Поза предмета в руці (Display моделі) ──

    function m3mul(a, b) {
        const r = [[0, 0, 0], [0, 0, 0], [0, 0, 0]];
        for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) for (let k = 0; k < 3; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }
    function rotX(d) { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [[1, 0, 0], [0, c, -s], [0, s, c]]; }
    function rotY(d) { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [[c, 0, s], [0, 1, 0], [-s, 0, c]]; }
    function rotZ(d) { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [[c, -s, 0], [s, c, 0], [0, 0, 1]]; }

    /** Матриця → кути Blockbench/Bedrock (порядок ZYX: M = Rz·Ry·Rx), градуси. */
    function eulerZYX(m) {
        const sb = Math.max(-1, Math.min(1, -m[2][0]));
        const b = Math.asin(sb);
        let a, c;
        if (Math.abs(Math.cos(b)) > 1e-6) { a = Math.atan2(m[2][1], m[2][2]); c = Math.atan2(m[1][0], m[0][0]); }
        else { a = Math.atan2(-m[1][2], m[1][1]); c = 0; }
        const deg = x => round(x * 180 / Math.PI);
        return [deg(a), deg(b), deg(c)];
    }

    /**
     * Display «у руці від третьої особи» → як поставити модель під right_item/left_item, щоб вона стояла
     * РІВНО як ванільний предмет у руці (ItemInHandLayer → ItemTransform.apply → ItemRenderer):
     *   сцена = півот_кістки + B·t + B·Rxyz(r)·s·(p − c)
     * де B = Rx(−90) (вісь «вгору» предмета дивиться вперед від долоні), t/r/s — translation/rotation/scale
     * з Display (ліва рука — дзеркально, як у грі), c — центр моделі: (8,8,8) для java_block, ≈(0,−0.16,0) для GeckoLib.
     * @return null, якщо Display для руки немає; інакше {offset, rotation, scale, center, matrix}
     */
    function itemDisplayTransform(display, hand, modelFormat) {
        if (!display) return null;
        const left = hand === 'left';
        const d = (left && display.thirdperson_lefthand) || display.thirdperson_righthand || (left ? null : null);
        if (!d) return null;
        const t = vec3(d.translation, 0).map(v => Math.max(-80, Math.min(80, v)));
        const r = vec3(d.rotation, 0);
        const sc = d.scale ? vec3(d.scale, 1).map(v => Math.max(-4, Math.min(4, v))) : [1, 1, 1];
        if (left) { t[0] = -t[0]; r[1] = -r[1]; r[2] = -r[2]; }
        const Q = m3mul(rotX(-90), m3mul(rotX(r[0]), m3mul(rotY(r[1]), rotZ(r[2]))));
        const offset = [round(t[0]), round(t[2]), round(-t[1])]; // B·t, B(x,y,z) = (x, z, −y)
        const java = !modelFormat || /java|block|item|free|generic/.test(String(modelFormat)) && !/gecko|bedrock/.test(String(modelFormat));
        return { offset: offset, rotation: eulerZYX(Q), scale: sc.map(round), center: java ? [8, 8, 8] : [0, -0.16, 0], matrix: Q };
    }

    function scaleMolang(v, k) {
        if (k === 1) return v;
        if (typeof v === 'number') return round(v * k);
        const n = Number(v);
        if (v !== '' && v !== null && v !== undefined && !isNaN(n)) return round(n * k);
        return '(' + v + ')*' + k;
    }

    /**
     * Анімація моделі → ключі для нових груп: перейменування (uuid/ім'я → нове ім'я), зсув у часі,
     * масштаб позицій разом з моделлю. Ефекти (звуки) моделі не переносяться — їх ставлять у кліп гравця.
     * @return [{name, keyframes:[{channel, time, interpolation, easing, data_points, bezier_*}]}]
     */
    function remapAnimation(anim, nameByUuid, timeOffset, scale) {
        const out = [];
        const k = Number(scale) || 1;
        for (const an of (anim.animators || [])) {
            if (an.type === 'effect' || an.uuid === 'effects') continue;
            const name = nameByUuid[an.uuid] || nameByUuid['name:' + an.name];
            if (!name) continue;
            const kfs = (an.keyframes || []).filter(kf => ['rotation', 'position', 'scale'].indexOf(kf.channel) >= 0).map(kf => {
                const r = {
                    channel: kf.channel, time: round((kf.time || 0) + (timeOffset || 0)), interpolation: kf.interpolation || 'linear',
                    data_points: (kf.data_points || []).map(dp => kf.channel === 'position'
                        ? { x: scaleMolang(dp.x, k), y: scaleMolang(dp.y, k), z: scaleMolang(dp.z, k) }
                        : { x: dp.x, y: dp.y, z: dp.z })
                };
                for (const extra of ['easing', 'easingArgs', 'bezier_linked', 'bezier_left_time', 'bezier_left_value', 'bezier_right_time', 'bezier_right_value']) {
                    if (kf[extra] !== undefined) r[extra] = kf[extra];
                }
                return r;
            });
            if (kfs.length) out.push({ name: name, keyframes: kfs });
        }
        return out;
    }

    /** Підгонка пропса: точка → півот + (точка − півот)·масштаб + зсув (усе в пікселях сцени). */
    function fitPoint(p, pivot, scale, offset) {
        const o = offset || [0, 0, 0];
        return [0, 1, 2].map(i => round(pivot[i] + (p[i] - pivot[i]) * scale + (Number(o[i]) || 0)));
    }

    /** Ключі видимості (масштаб 0/1, ступінчасто), як їх ставить «показати/сховати з цього кадру». */
    function visibilityKeys(existingScaleTimes, time, visible) {
        const keys = [];
        const before = existingScaleTimes.some(t => t < time - 1e-3);
        if (!before && time > 1e-3) keys.push({ time: 0, value: visible ? 0 : 1 });
        keys.push({ time: round(time), value: visible ? 1 : 0 });
        return keys;
    }

    const api = { snake, canonicalPlayerBone, compileKeyframe, compileAnimation, compileCube, compileProp, findProps,
        detectRig, buildExport, boxUvFaces, geoToScene, bbmodelToScene, atlasLayout, placeScene, remapAnimation,
        visibilityKeys, packMeta, holdSpec, applyKind, syncMask, kindOf, fitPoint, scaleMolang, itemDisplayTransform, eulerZYX, rotX, rotY, rotZ, m3mul, PLAYER_RIG, RIG_PIVOTS, WORLD };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api; // Node-тести
        return;
    }

    // ── Інтеграція з Blockbench ─────────────────────────────────────────────────────────────

    const disposables = [];
    let autoExportHook = null;
    const hasFs = typeof require !== 'undefined' && typeof process !== 'undefined' && !!(process.versions && process.versions.electron);
    const nodeFs = () => require('fs');
    const nodePath = () => require('path');

    function msg(text, ms) { Blockbench.showQuickMessage(text, ms || 2500); }

    function selectedGroups() {
        if (Group.multi_selected && Group.multi_selected.length) return Group.multi_selected.slice();
        const g = Group.first_selected || Group.selected;
        return g ? [g] : [];
    }

    function rigGroup(name) {
        const want = canonicalPlayerBone(name);
        return Group.all.find(g => canonicalPlayerBone(g.name) === want) || null;
    }

    function rigAncestor(g) {
        let p = g.parent;
        while (p instanceof Group) {
            if (isRigBone(p.name, 'player')) return p;
            p = p.parent;
        }
        return null;
    }

    function allTextureFaces(g, counts) {
        for (const c of g.children) {
            if (c instanceof Group) { allTextureFaces(c, counts); continue; }
            if (!(c instanceof Cube) || c.export === false) continue;
            for (const f of Object.values(c.faces)) if (f.texture) counts[f.texture] = (counts[f.texture] || 0) + 1;
        }
        return counts;
    }

    function uvSize(tex) {
        if (Format.per_texture_uv_size && tex && tex.uv_width) return [tex.uv_width, tex.uv_height];
        return [Project.texture_width, Project.texture_height];
    }

    function projectSnapshot() {
        return {
            name: Project.name || Project.geometry_name || 'model',
            groups: Group.all.map(wrapGroup),
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
            textureFor: w => {
                const g = Group.all.find(x => wrapped.get(x) === w);
                if (!g) return null;
                const counts = allTextureFaces(g, {});
                const uuids = Object.keys(counts).sort((a, b) => counts[b] - counts[a]);
                let tex = uuids.length ? Texture.all.find(t => t.uuid === uuids[0]) : null;
                let warning = uuids.length > 1 ? 'використовує ' + uuids.length + ' текстури — у грі буде лише «' + (tex ? tex.name : '?') +
                    '». Імпортуй модель через «Shaurma: модель у анімацію» — вона зшиває текстури в одну.' : null;
                if (!tex) return null;
                const [w2, h2] = uvSize(tex);
                return { image: tex.getDataURL(), uv_width: w2, uv_height: h2, warning: warning };
            }
        };
    }

    const wrapped = new Map();
    function wrapGroup(g) {
        if (wrapped.has(g)) return wrapped.get(g);
        const w = { name: g.name, origin: g.origin.slice(), rotation: g.rotation.slice(), shaurma_prop: g.shaurma_prop || {}, export: g.export };
        wrapped.set(g, w);
        w.parent = g.parent instanceof Group ? wrapGroup(g.parent) : 'root';
        w.children = g.children.filter(c => c instanceof Group || c instanceof Cube).map(c => c instanceof Group ? wrapGroup(c) : {
            type: 'cube', from: c.from.slice(), to: c.to.slice(), origin: c.origin.slice(), rotation: c.rotation.slice(), export: c.export,
            inflate: c.inflate, box_uv: c.box_uv !== undefined ? c.box_uv : Project.box_uv, uv_offset: c.uv_offset.slice(),
            mirror_uv: c.mirror_uv, faces: Object.fromEntries(Object.entries(c.faces).map(([k, f]) => [k, { uv: f.uv.slice(), texture: f.texture }]))
        });
        return w;
    }

    // ── Експорт ресурсів ──

    /** resources/ поруч зі збереженим .bbmodel (…/src/main/resources), якщо проєкт лежить у моді. */
    function guessResources() {
        if (!hasFs || !Project.save_path) return '';
        const path = nodePath(), fs = nodeFs();
        let dir = path.dirname(Project.save_path);
        for (let i = 0; i < 8 && dir && dir !== path.dirname(dir); i++) {
            for (const cand of [dir, path.join(dir, 'src', 'main', 'resources'), path.join(dir, 'resources')]) {
                if (fs.existsSync(path.join(cand, 'assets'))) return cand;
            }
            dir = path.dirname(dir);
        }
        return '';
    }

    /** Папка гри для «кинув і працює»: run/ дев-середовища поруч із проєктом або .minecraft. */
    function guessGameDir() {
        if (!hasFs) return '';
        const fs = nodeFs(), path = nodePath();
        if (Project.save_path) {
            let dir = path.dirname(Project.save_path);
            for (let i = 0; i < 8 && dir && dir !== path.dirname(dir); i++) {
                for (const cand of [path.join(dir, 'run'), path.join(dir, 'run', 'client')]) {
                    if (fs.existsSync(path.join(cand, 'options.txt')) || fs.existsSync(path.join(cand, 'saves'))) return cand;
                }
                dir = path.dirname(dir);
            }
        }
        const appdata = process.env.APPDATA || (process.env.HOME ? path.join(process.env.HOME, 'Library', 'Application Support') : '');
        for (const cand of [path.join(appdata, '.minecraft'), path.join(appdata, 'minecraft'), path.join(process.env.HOME || '', '.minecraft')]) {
            if (cand && fs.existsSync(cand)) return cand;
        }
        return '';
    }

    function guessNamespace(folder) {
        if (!hasFs || !folder) return '';
        try {
            const dirs = nodeFs().readdirSync(nodePath().join(folder, 'assets')).filter(n => n !== 'minecraft' && !n.startsWith('.'));
            return dirs.length === 1 ? dirs[0] : '';
        } catch (e) { return ''; }
    }

    function exportSettings() {
        const s = Object.assign({}, Project.shaurma_export || {});
        if (!s.folder) s.folder = guessResources();
        if (!s.namespace) s.namespace = guessNamespace(s.folder) || Project.geckolib_modid || 'maniacmod';
        if (!s.file) s.file = snake(Project.name || 'animations');
        if (!s.rig) s.rig = 'auto';
        if (s.split === undefined) s.split = true;
        if (!s.target) s.target = hasFs ? 'folder' : 'zip';
        if (!s.game) s.game = guessGameDir();
        return s;
    }

    function exportDialog() {
        const s = exportSettings();
        new Dialog({
            id: 'shaurma_export', title: 'Export Shaurma Animation',
            width: 560,
            form: {
                namespace: { label: 'Простір імен (modid)', type: 'text', value: s.namespace },
                file: { label: 'Ім\'я файлу кліпів', type: 'text', value: s.file },
                rig: { label: 'Риг', type: 'select', options: { auto: 'Авто', player: 'Гравець (player)', geo: 'Geo-модель' }, value: s.rig },
                target: { label: 'Куди', type: 'select', options: hasFs
                    ? { folder: 'У папку resources мода (assets/… створиться саме)', game: 'Одразу в гру: <гра>/shaurma_anim/ (F3+T — і готово)',
                        zip: 'Архів .zip (не розпаковувати: у <гра>/shaurma_anim/ або resourcepacks/)' }
                    : { zip: 'Архів .zip (не розпаковувати: у <гра>/shaurma_anim/ або resourcepacks/)' }, value: s.target },
                folder: { label: 'Папка resources (де лежить assets/)', type: 'folder', value: s.folder, condition: f => f.target === 'folder' },
                game: { label: 'Папка гри (.minecraft або run/ дев-середовища)', type: 'folder', value: s.game, condition: f => f.target === 'game' },
                split: { label: 'Кожна анімація гравця — окремий файл (…/player/<тека>/<анімація>.json)', type: 'checkbox', value: s.split !== false },
                auto: { label: 'Автоекспорт при збереженні проєкту (Ctrl+S)', type: 'checkbox', value: !!s.auto, condition: f => f.target !== 'zip' },
                only_selected: { label: 'Лише вибрана анімація', type: 'checkbox', value: false }
            },
            onConfirm(form) {
                const next = { namespace: (form.namespace || '').trim(), file: (form.file || '').trim(), rig: form.rig, target: form.target,
                    folder: form.folder || '', game: form.game || '', split: !!form.split, auto: !!form.auto && form.target !== 'zip' };
                Project.shaurma_export = next;
                if (Project.saved !== undefined) Project.saved = false;
                runExport(next, form.only_selected);
            }
        }).show();
    }

    function quickExport() {
        const e = Project.shaurma_export;
        if (!e || (e.target === 'folder' && !e.folder) || (e.target === 'game' && !e.game)) return exportDialog();
        runExport(exportSettings(), false);
    }

    /** Експорт лише вибраної анімації — окремий файл, решту не чіпає (оновити біг, не торкаючись ходьби). */
    function exportSelected() {
        if (!Animation.selected) return msg('Shaurma: вибери анімацію', 2500);
        const e = Project.shaurma_export;
        if (!e || (e.target === 'folder' && !e.folder) || (e.target === 'game' && !e.game)) return exportDialog();
        runExport(Object.assign({}, exportSettings(), { split: true }), true);
    }

    function runExport(s, onlySelected, quiet) {
        wrapped.clear();
        const snap = projectSnapshot();
        const opts = { namespace: s.namespace, file: s.file, warnings: [], split: s.split !== false };
        if (s.rig === 'player') opts.rig = 'player';
        if (s.rig === 'geo') opts.rig = 'geo:' + opts.namespace + ':' + snake(Project.name || 'model');
        if (onlySelected && Animation.selected) opts.only = [Animation.selected.name];
        let files;
        try {
            files = buildExport(snap, opts);
        } catch (e) {
            console.error(e);
            Blockbench.showMessageBox({ title: 'Shaurma: помилка експорту', message: String(e && e.stack || e) });
            return;
        }
        const done = where => report(files, opts.warnings, where, quiet);
        if (s.target === 'folder' && s.folder && hasFs) {
            writeFolder(s.folder, files);
            done(s.folder);
        } else if (s.target === 'game' && s.game && hasFs) {
            // <гра>/shaurma_anim/<файл>/ — своя тека проєкту; старі файли прибираються (перейменований пропс не лишиться)
            const path = nodePath(), fs = nodeFs();
            const dir = path.join(s.game, 'shaurma_anim', snake(s.file || 'animations') || 'animations');
            // повний експорт чистить теку проєкту; експорт однієї анімації дописує лише її файл
            if (!(onlySelected && Animation.selected) && fs.existsSync(dir) && fs.rmSync) fs.rmSync(dir, { recursive: true, force: true });
            files['pack.mcmeta'] = packMeta('ShaurmaLib: ' + (s.file || ''));
            writeFolder(dir, files);
            done(dir);
        } else {
            writeZip(s, files, done);
        }
    }

    function writeFolder(folder, files) {
        const fs = nodeFs(), path = nodePath();
        for (const [rel, content] of Object.entries(files)) {
            const full = path.join(folder, rel);
            fs.mkdirSync(path.dirname(full), { recursive: true });
            if (content && content.image) fs.writeFileSync(full, Buffer.from(content.image.split(',')[1], 'base64'));
            else fs.writeFileSync(full, content, 'utf-8');
        }
    }

    function writeZip(s, files, done) {
        if (typeof JSZip === 'undefined') {
            // зовсім старий Blockbench: кожен файл окремо (структуру тек відновити вручну за іменами)
            for (const [rel, content] of Object.entries(files)) {
                const name = rel.replace(/\//g, '__');
                if (content && content.image) Blockbench.export({ type: 'PNG', extensions: ['png'], name: name, content: content.image, savetype: 'image' });
                else Blockbench.export({ type: 'JSON', extensions: ['json'], name: name.replace(/\.json$/, ''), content: content, savetype: 'text' });
            }
            return done('завантаження');
        }
        const zip = new JSZip();
        zip.file('pack.mcmeta', packMeta('ShaurmaLib: ' + (s.file || '')));
        for (const [rel, content] of Object.entries(files)) {
            if (content && content.image) zip.file(rel, content.image.split(',')[1], { base64: true });
            else zip.file(rel, content);
        }
        zip.generateAsync({ type: 'blob' }).then(blob => {
            Blockbench.export({ type: 'Zip Archive', extensions: ['zip'], name: (s.file || 'shaurma') + '_resources', content: blob, savetype: 'zip' },
                path => done(path || '.zip'));
        });
    }

    function report(files, warnings, where, quiet) {
        const list = Object.keys(files).map(f => '- `' + f + '`').join('\n');
        const warn = warnings.length ? '\n\n**Увага:**\n' + warnings.map(w => '- ' + w).join('\n') : '';
        if (!warnings.length || quiet) msg('Shaurma: записано ' + Object.keys(files).length + ' файл(ів)' + (warnings.length ? ' (є попередження — зроби експорт вручну)' : ''), 2500);
        else Blockbench.showMessageBox({ title: 'Shaurma: експортовано → ' + where, message: list + warn, icon: 'warning' });
    }

    // ── Імпорт моделі в анімацію ──

    function readAsDataURL(file) {
        const ext = file.toLowerCase().endsWith('.jpg') || file.toLowerCase().endsWith('.jpeg') ? 'jpeg' : 'png';
        return 'data:image/' + ext + ';base64,' + nodeFs().readFileSync(file).toString('base64');
    }

    /** Знайти файли текстур: вбудовані в .bbmodel, або поруч з моделлю / в ../textures. */
    function resolveTextures(scene, file, isGeo, cb) {
        const base = file.name.replace(/\.(geo\.json|json|bbmodel)$/i, '').replace(/\.geo$/i, '');
        if (hasFs && file.path) {
            const fs = nodeFs(), path = nodePath();
            const dir = path.dirname(file.path);
            const tryFile = f => { try { return f && fs.existsSync(f) ? readAsDataURL(f) : null; } catch (e) { return null; } };
            if (isGeo) {
                const names = [base + '.png', scene.name + '.png', snake(base) + '.png'];
                const dirs = [dir, path.join(dir, '..', 'textures'), path.join(dir, 'textures'), path.join(dir, '..', 'textures', 'entity'), path.join(dir, '..', 'textures', 'item')];
                for (const d of dirs) for (const n of names) {
                    const data = tryFile(path.join(d, n));
                    if (data) { scene.textures = [{ name: n, source: data, uv_width: scene.texture_width, uv_height: scene.texture_height }]; return cb(); }
                }
            } else {
                for (const t of scene.textures) {
                    if (t.source) continue;
                    t.source = tryFile(t.path) || tryFile(t.relative_path && path.resolve(dir, t.relative_path)) || tryFile(t.path && path.join(dir, path.basename(t.path)));
                }
            }
        }
        const missing = isGeo ? !scene.textures.length : scene.textures.some(t => !t.source);
        if (!missing) return cb();
        Blockbench.showMessageBox({
            title: 'Shaurma: текстура моделі', icon: 'image',
            message: 'Не знайшов текстуру для «' + base + '». Вибери PNG (для .bbmodel з кількома текстурами — по черзі).',
            buttons: ['Вибрати PNG', 'Без текстури']
        }, btn => {
            if (btn !== 0) { scene.textures = scene.textures.filter(t => t.source); return cb(); }
            Blockbench.import({ extensions: ['png'], type: 'PNG текстура', readtype: 'image', multiple: !isGeo }, files => {
                if (isGeo) scene.textures = [{ name: files[0].name, source: files[0].content, uv_width: scene.texture_width, uv_height: scene.texture_height }];
                else {
                    let i = 0;
                    for (const t of scene.textures) if (!t.source && files[i]) t.source = files[i++].content;
                    scene.textures = scene.textures.filter(t => t.source);
                }
                cb();
            });
        });
    }

    function loadImage(src) {
        return new Promise(resolve => {
            const img = new Image();
            img.onload = () => resolve(img);
            img.onerror = () => resolve(null);
            img.src = src;
        });
    }

    /** Усі текстури моделі → одна PNG + розмір UV (зшиті стовпчиком, якщо їх кілька). */
    async function buildAtlas(textures) {
        if (!textures.length) return null;
        const layout = atlasLayout(textures);
        if (textures.length === 1) return { data: textures[0].source, uv_width: textures[0].uv_width, uv_height: textures[0].uv_height, layout: layout };
        const imgs = await Promise.all(textures.map(t => loadImage(t.source)));
        let px = 1;
        textures.forEach((t, i) => { if (imgs[i]) px = Math.max(px, imgs[i].naturalWidth / t.uv_width); });
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(layout.width * px);
        canvas.height = Math.round(layout.height * px);
        const ctx = canvas.getContext('2d');
        ctx.imageSmoothingEnabled = false;
        textures.forEach((t, i) => {
            if (imgs[i]) ctx.drawImage(imgs[i], 0, Math.round(layout.offsets[i][1] * px), Math.round(t.uv_width * px), Math.round(t.uv_height * px));
        });
        return { data: canvas.toDataURL('image/png'), uv_width: layout.width, uv_height: layout.height, layout: layout };
    }

    function rigOptions(includeWorld) {
        const labels = {
            right_item: 'Права рука — предмет (right_item)', left_item: 'Ліва рука — предмет (left_item)',
            right_arm: 'Права рука (right_arm)', left_arm: 'Ліва рука (left_arm)', head: 'Голова (head)', torso: 'Тулуб (torso)',
            body: 'Усе тіло (body)', right_leg: 'Права нога', left_leg: 'Ліва нога'
        };
        const out = {};
        for (const k of Object.keys(labels)) if (rigGroup(k)) out[k] = labels[k];
        if (includeWorld) out[WORLD] = 'Вільно — навколо гравця (ефекти, не за кісткою)';
        return out;
    }

    function importModel() {
        if (Format.single_texture) {
            Blockbench.showMessageBox({ title: 'Shaurma', icon: 'warning', message: 'Формат цього проєкту дозволяє лише одну текстуру, а модель у анімації має власну. ' +
                'Відкрий проєкт у форматі «Shaurma: модель гравця» (оновлений плагін дозволяє кілька текстур) або Bedrock Entity / Generic Model.' });
            return;
        }
        Blockbench.import({ extensions: ['bbmodel', 'json'], type: 'Модель (.bbmodel / .geo.json)', readtype: 'text', multiple: false }, files => {
            const file = files[0];
            let scene, isGeo;
            try {
                const json = JSON.parse(file.content);
                isGeo = !!(json['minecraft:geometry'] || Object.keys(json).some(k => k.startsWith('geometry.')));
                scene = isGeo ? geoToScene(json) : bbmodelToScene(json);
            } catch (e) {
                Blockbench.showMessageBox({ title: 'Shaurma', message: 'Не вдалося прочитати модель: ' + e.message });
                return;
            }
            resolveTextures(scene, file, isGeo, () => importDialog(scene, file));
        });
    }

    function importDialog(scene, file) {
        const sel = selectedGroups()[0];
        const rigOpts = rigOptions(true);
        let parent = sel && isRigBone(sel.name, 'player') ? canonicalPlayerBone(sel.name) : (rigOpts.right_item ? 'right_item' : WORLD);
        const animOpts = { none: '— не вставляти —' };
        scene.animations.forEach((a, i) => { animOpts[String(i)] = a.name + ' (' + round(a.length) + ' с)'; });
        const base = snake(file.name.replace(/\.(geo\.json|json|bbmodel)$/i, '').replace(/\.geo$/i, '')) || snake(scene.name);
        const hasHandDisplay = itemDisplayTransform(scene.display, 'right', scene.model_format);
        const form = {
            name: { label: 'Ім\'я пропса (ім\'я групи й файлів)', type: 'text', value: base },
            parent: { label: 'Прив\'язати до', type: 'select', options: rigOpts, value: parent },
            as_item: { label: 'Як предмет у руці — поза з Display моделі (як ванільний предмет у грі)', type: 'checkbox',
                value: !!hasHandDisplay, condition: x => !!hasHandDisplay && (x.parent === 'right_item' || x.parent === 'left_item') },
            place: { label: 'Початок моделі — у півот цієї кістки', type: 'checkbox', value: true,
                condition: x => !(x.as_item && hasHandDisplay && (x.parent === 'right_item' || x.parent === 'left_item')) },
            scale: { label: 'Масштаб', type: 'number', value: 1, step: 0.05, min: 0.01 },
            offset: { label: 'Додатковий зсув, px', type: 'vector', dimensions: 3, value: [0, 0, 0] },
            hide_held: { label: 'Ховати ванільний предмет у руці', type: 'select', options: { none: 'Ні', right: 'Права', left: 'Ліва', both: 'Обидві' },
                value: parent === 'right_item' ? 'right' : parent === 'left_item' ? 'left' : 'none' },
            translucent: { label: 'Напівпрозора текстура (дим, скло, сяйво)', type: 'checkbox', value: false },
            emissive: { label: 'Світиться (без тіні)', type: 'checkbox', value: false },
            fit: { label: 'Після імпорту — підігнати масштаб/положення наживо', type: 'checkbox', value: true }
        };
        if (scene.animations.length) {
            form.anim = { label: 'Вставити анімацію моделі в ' + (Animation.selected ? '«' + Animation.selected.name + '»' : 'нову анімацію') + ' з поточного кадру',
                type: 'select', options: animOpts, value: scene.animations.length === 1 ? '0' : 'none' };
        }
        if (scene.textures.length > 1) form.atlas_info = { type: 'info', text: 'Текстур: ' + scene.textures.length + ' — зшиваються в одну (рушій малює пропс однією текстурою).' };
        if (scene.warnings.length) form.warn_info = { type: 'info', text: scene.warnings.join(' ') };
        new Dialog({
            id: 'shaurma_import_model', title: 'Shaurma: модель у анімацію — ' + file.name, width: 560, form: form,
            onConfirm(f) { buildImport(scene, f).catch(e => { console.error(e); Blockbench.showMessageBox({ title: 'Shaurma: помилка імпорту', message: String(e && e.stack || e) }); }); }
        }).show();
    }

    async function buildImport(scene, f) {
        const atlas = await buildAtlas(scene.textures);
        const target = f.parent === WORLD ? null : rigGroup(f.parent);
        const pivot = target ? target.origin.slice() : [0, 0, 0];
        const off = f.offset || [0, 0, 0];
        const hand = f.parent === 'right_item' ? 'right' : f.parent === 'left_item' ? 'left' : null;
        const disp = f.as_item && hand ? itemDisplayTransform(scene.display, hand, scene.model_format) : null;
        const userScale = Number(f.scale) || 1;
        let anchor, scale = userScale, center = [0, 0, 0], rotation = [0, 0, 0];
        if (disp) {
            // ванільна поза предмета: півот кістки + зсув Display, поворот Display, масштаб Display × свій
            anchor = [0, 1, 2].map(i => round(pivot[i] + disp.offset[i] + off[i]));
            scale = disp.scale.map(v => v * userScale);
            center = disp.center;
            rotation = disp.rotation;
        } else {
            anchor = f.place ? [pivot[0] + off[0], pivot[1] + off[1], pivot[2] + off[2]] : off.slice();
        }
        let uvScale = [1, 1];
        if (atlas && !Format.per_texture_uv_size) uvScale = [Project.texture_width / atlas.uv_width, Project.texture_height / atlas.uv_height];
        const placed = placeScene(scene, {
            name: f.name, scale: scale, anchor: anchor, center: center, rotation: rotation, taken: Group.all.map(g => g.name),
            atlas: atlas && scene.textures.length > 1 ? atlas.layout : null, uvScale: uvScale
        });

        let anim = Animation.selected;
        const animIdx = f.anim !== undefined && f.anim !== 'none' ? Number(f.anim) : -1;
        const newAnim = animIdx >= 0 && !anim;
        Undo.initEdit({ outliner: true, elements: [], textures: [], animations: anim ? [anim] : [] });

        let tex = null;
        if (atlas) {
            tex = new Texture({ name: placed.root.name + '.png' }).fromDataURL(atlas.data).add(false);
            const setUv = () => { if (Format.per_texture_uv_size) { tex.uv_width = atlas.uv_width; tex.uv_height = atlas.uv_height; } };
            setUv();
            loadImage(atlas.data).then(() => { setUv(); Canvas.updateAllUVs && Canvas.updateAllUVs(); });
        }
        const elements = [];
        function make(spec, parentGroup) {
            const g = new Group({ name: spec.name, origin: spec.origin, rotation: spec.rotation });
            if (spec.export === false) g.export = false;
            (parentGroup ? g.addTo(parentGroup) : g.addTo()).init();
            for (const c of spec.cubes) {
                const faces = {};
                for (const dir of FACES) faces[dir] = { uv: c.faces[dir].uv, texture: c.faces[dir].texture === null || !tex ? null : tex.uuid, rotation: c.faces[dir].rotation || 0 };
                const cube = new Cube({ name: c.name, from: c.from, to: c.to, origin: c.origin, rotation: c.rotation, inflate: c.inflate, box_uv: false, faces: faces });
                if (c.export === false) cube.export = false;
                cube.addTo(g).init();
                cube.box_uv = false;
                elements.push(cube);
            }
            for (const ch of spec.children) make(ch, g);
            return g;
        }
        const root = make(placed.root, target);
        root.shaurma_prop = { hide_held: f.hide_held, emissive: !!f.emissive, translucent: !!f.translucent, always: false, item: '' };

        if (animIdx >= 0) {
            const src = scene.animations[animIdx];
            if (newAnim) {
                anim = new Animation({ name: 'animation.' + placed.root.name, length: src.length, loop: 'once' }).add(false);
                anim.select && anim.select();
            }
            const t0 = Timeline && typeof Timeline.time === 'number' && !newAnim ? Timeline.time : 0;
            const byName = {};
            (function walk(g) { byName[g.name] = g; for (const c of g.children) if (c instanceof Group) walk(c); })(root);
            const keyScale = Array.isArray(scale) ? (Math.abs(scale[0]) + Math.abs(scale[1]) + Math.abs(scale[2])) / 3 : scale;
            for (const a of remapAnimation(src, placed.nameByUuid, t0, keyScale)) {
                const grp = byName[a.name];
                if (!grp) continue;
                const ba = anim.getBoneAnimator(grp);
                for (const kf of a.keyframes) ba.addKeyframe(kf);
            }
            anim.length = Math.max(anim.length || 0, round(t0 + src.length));
        }
        // пропс існує в кліпі, лише якщо кліп його анімує — ключ «видимий з 0» прив'язує його до поточної анімації
        if (anim && !(animIdx >= 0)) setVisibility(anim, root, 0, true);

        Undo.finishEdit('Shaurma: модель у анімацію', { outliner: true, elements: elements, textures: tex ? [tex] : [], animations: anim ? [anim] : [] });
        Canvas.updateAll();
        if (typeof Animator !== 'undefined' && Animator.preview) Animator.preview();
        msg('Shaurma: «' + placed.root.name + '» → ' + (target ? target.name : 'вільно (world)'), 3000);
        if (f.fit) setTimeout(() => fitProp(root), 50);
    }

    // ── Видимість і передача пропса ──

    function setVisibility(anim, group, time, visible) {
        const ba = anim.getBoneAnimator(group);
        for (const k of ba.keyframes.filter(k => k.channel === 'scale' && Math.abs(k.time - time) < 1e-3)) k.remove();
        const times = ba.keyframes.filter(k => k.channel === 'scale').map(k => k.time);
        for (const k of visibilityKeys(times, time, visible)) {
            ba.addKeyframe({ channel: 'scale', time: k.time, interpolation: 'step', data_points: [{ x: k.value, y: k.value, z: k.value }] });
        }
    }

    function visibilityAction(visible) {
        const anim = Animation.selected;
        const groups = selectedGroups();
        if (!anim || !groups.length) return msg('Вибери анімацію і групу пропса', 2000);
        Undo.initEdit({ animations: [anim] });
        for (const g of groups) setVisibility(anim, g, Timeline.time, visible);
        Undo.finishEdit(visible ? 'Shaurma: показати пропс' : 'Shaurma: сховати пропс');
        Animator.preview();
    }

    function shiftTree(g, d) {
        for (let i = 0; i < 3; i++) g.origin[i] += d[i];
        for (const c of g.children) {
            if (c instanceof Group) shiftTree(c, d);
            else if (c instanceof Cube) for (let i = 0; i < 3; i++) { c.from[i] += d[i]; c.to[i] += d[i]; c.origin[i] += d[i]; }
        }
    }

    function renameTree(g, suffix, taken) {
        const uniq = base => {
            let n = base, i = 2;
            while (taken.has(n)) n = base + '_' + (i++);
            taken.add(n);
            return n;
        };
        g.name = uniq(snake(g.name).replace(/\d+$/, '') + '_' + suffix);
        for (const c of g.children) if (c instanceof Group) renameTree(c, suffix, taken);
    }

    function handoffProp() {
        const anim = Animation.selected;
        const g = selectedGroups()[0];
        if (!anim || !g || isRigBone(g.name, 'player')) return msg('Вибери анімацію і групу пропса (не кістку гравця)', 2500);
        const from = rigAncestor(g);
        const opts = rigOptions(true);
        delete opts[from ? canonicalPlayerBone(from.name) : WORLD];
        new Dialog({
            id: 'shaurma_handoff', title: 'Shaurma: передати «' + g.name + '»', width: 520,
            form: {
                info: { type: 'info', text: 'Створює копію пропса під іншою кісткою. Оригінал ховається, копія з\'являється з вказаного часу (ступінчасті ключі масштабу — у грі так само). Положення копії далі підганяй ключами.' },
                target: { label: 'Кому', type: 'select', options: opts, value: opts.left_item ? 'left_item' : Object.keys(opts)[0] },
                time: { label: 'З якого часу, с', type: 'number', value: round(Timeline.time), step: 0.05, min: 0 },
                keep: { label: 'Та сама відстань від кістки, що й в оригіналі', type: 'checkbox', value: true }
            },
            onConfirm(f) {
                const target = f.target === WORLD ? null : rigGroup(f.target);
                Undo.initEdit({ outliner: true, elements: [], animations: [anim] });
                const copy = g.duplicate();
                if (target) copy.addTo(target); else copy.addTo();
                const fromPivot = from ? from.origin : [0, 0, 0];
                const toPivot = target ? target.origin : [0, 0, 0];
                if (f.keep) shiftTree(copy, [toPivot[0] - fromPivot[0], toPivot[1] - fromPivot[1], toPivot[2] - fromPivot[2]]);
                const taken = new Set(Group.all.filter(x => !isInside(x, copy)).map(x => x.name));
                renameTree(copy, f.target === WORLD ? 'world' : f.target.replace(/_item$|_arm$/, ''), taken);
                copy.shaurma_prop = Object.assign({}, g.shaurma_prop || {}, {
                    hide_held: f.target === 'left_item' ? 'left' : f.target === 'right_item' ? 'right' : 'none', always: false
                });
                setVisibility(anim, g, f.time, false);
                setVisibility(anim, copy, f.time, true);
                const elements = [];
                copy.forEachChild && copy.forEachChild(c => elements.push(c), Cube);
                Undo.finishEdit('Shaurma: передати пропс', { outliner: true, elements: elements, animations: [anim] });
                Canvas.updateAll();
                Animator.preview();
                copy.select && copy.select();
            }
        }).show();
    }

    // ── Підгонка пропса наживо: масштаб / зсув / поворот, сцена оновлюється під час введення ──

    /**
     * Діалог з живим переглядом. Усе рахується від знімка на момент відкриття (значення не накопичуються):
     * точка → півот + (точка − півот)·масштаб + зсув. Ключі позиції підгруп масштабуються разом з моделлю,
     * щоб анімація кришки/бинта не «відривалась». Скасувати — модель повертається як була.
     */
    function fitProp(g0) {
        const g = g0 instanceof Group ? g0 : selectedGroups()[0];
        if (!g || isRigBone(g.name, 'player')) return msg('Вибери групу пропса (не кістку гравця)', 2500);
        const nodes = [], cubes = [];
        (function walk(n) {
            nodes.push(n);
            for (const c of n.children) {
                if (c instanceof Group) walk(c);
                else if (c instanceof Cube) { nodes.push(c); cubes.push(c); }
            }
        })(g);
        const snap = new Map(nodes.map(n => [n, {
            origin: n.origin.slice(), rotation: n.rotation ? n.rotation.slice() : null,
            from: n.from ? n.from.slice() : null, to: n.to ? n.to.slice() : null, inflate: n.inflate || 0
        }]));
        const groupSet = new Set(nodes.filter(n => n instanceof Group));
        const keys = [], anims = [];
        for (const a of Animation.all) {
            let touched = false;
            for (const an of Object.values(a.animators)) {
                const grp = an.getGroup ? an.getGroup() : an.group;
                if (!grp || !groupSet.has(grp)) continue;
                for (const kf of an.keyframes) {
                    if (kf.channel !== 'position') continue;
                    keys.push({ kf: kf, points: kf.data_points.map(dp => ({ x: dp.x, y: dp.y, z: dp.z })) });
                    touched = true;
                }
            }
            if (touched) anims.push(a);
        }
        const pivot = g.origin.slice();
        const rot0 = g.rotation.slice();

        function restore() {
            for (const [n, s] of snap) {
                for (let i = 0; i < 3; i++) n.origin[i] = s.origin[i];
                if (s.rotation) for (let i = 0; i < 3; i++) n.rotation[i] = s.rotation[i];
                if (s.from) for (let i = 0; i < 3; i++) { n.from[i] = s.from[i]; n.to[i] = s.to[i]; }
                if (n instanceof Cube) n.inflate = s.inflate;
            }
            for (const k of keys) k.kf.data_points.forEach((dp, i) => Object.assign(dp, k.points[i]));
        }

        function apply(f) {
            restore();
            const s = Number(f.scale) || 1, off = f.offset || [0, 0, 0];
            const P = p => fitPoint(p, pivot, s, off);
            for (const [n, sn] of snap) {
                const o = P(sn.origin);
                for (let i = 0; i < 3; i++) n.origin[i] = o[i];
                if (sn.from) {
                    const a = P(sn.from), b = P(sn.to);
                    for (let i = 0; i < 3; i++) { n.from[i] = a[i]; n.to[i] = b[i]; }
                    n.inflate = round(sn.inflate * s);
                }
            }
            const r = f.rotation || [0, 0, 0];
            for (let i = 0; i < 3; i++) g.rotation[i] = round(rot0[i] + (Number(r[i]) || 0));
            if (f.keys && s !== 1) {
                for (const k of keys) k.kf.data_points.forEach((dp, i) => {
                    dp.x = scaleMolang(k.points[i].x, s); dp.y = scaleMolang(k.points[i].y, s); dp.z = scaleMolang(k.points[i].z, s);
                });
            }
            Canvas.updateAll();
            if (typeof Animator !== 'undefined' && Animator.open && Animator.preview) Animator.preview();
        }

        Undo.initEdit({ outliner: true, elements: cubes, animations: anims });
        let finished = false;
        const cancel = () => {
            if (finished) return;
            finished = true;
            restore();
            if (Undo.cancelEdit) Undo.cancelEdit(); else Undo.finishEdit('Shaurma: підгонка скасована');
            Canvas.updateAll();
            if (typeof Animator !== 'undefined' && Animator.open && Animator.preview) Animator.preview();
        };
        new Dialog({
            id: 'shaurma_fit_prop', title: 'Shaurma: підігнати «' + g.name + '» (наживо)', width: 480,
            form: {
                info: { type: 'info', text: 'Змінюй значення — модель у сцені оновлюється одразу. Масштаб і поворот — навколо півота групи пропса. «Скасувати» повертає як було.' },
                scale: { label: 'Масштаб', type: 'number', value: 1, step: 0.05, min: 0.01 },
                offset: { label: 'Зсув, px', type: 'vector', dimensions: 3, value: [0, 0, 0], step: 0.25 },
                rotation: { label: 'Поворот, °', type: 'vector', dimensions: 3, value: [0, 0, 0], step: 5 },
                keys: { label: 'Масштабувати й ключі позиції підгруп (кришка, бинт)', type: 'checkbox', value: true }
            },
            onFormChange(f) { if (!finished) apply(f); },
            onConfirm(f) {
                if (finished) return;
                finished = true;
                apply(f);
                Undo.finishEdit('Shaurma: підігнати пропс', { outliner: true, elements: cubes, animations: anims });
            },
            onCancel: cancel,
            onClose: cancel
        }).show();
    }

    function isInside(x, root) {
        for (let p = x; p instanceof Group; p = p.parent) if (p === root) return true;
        return false;
    }

    // ── Налаштування ──

    function animationSettings() {
        const a = Animation.selected;
        if (!a) return msg('Вибери анімацію', 1500);
        const s = a.shaurma_settings || {};
        const v = (k, def) => s[k] !== undefined && s[k] !== '' ? s[k] : def;
        const K = k => f => f.kind === k;
        const KS = ks => f => ks.indexOf(f.kind) >= 0;
        new Dialog({
            id: 'shaurma_anim_settings', title: 'Shaurma: ' + a.name, width: 620,
            form: {
                kind: { label: 'Тип анімації', type: 'select', value: kindOf(s), options: {
                    full: 'Повна — усе інше вимкнено, працює лише вона (лежати, катсцена)',
                    partial: 'Часткова — разова дія лише своїх кісток (підняти руку)',
                    sync: 'Синхронна — гравець рухається, але без вибраних частин (аптечка на ходу)',
                    state: 'Стан — триває й змішується з бігом, шифтом, idle, ударом (ліхтарик, каністра)',
                    custom: 'Вручну — шар і маска самостійно'
                } },
                info_full: { type: 'info', condition: K('full'), text: 'Кістки гравця без ключів стоять у спокої (ходьба їх не рухає). Голова не йде за камерою, якщо не змінити «Голова за камерою» нижче.' },
                info_partial: { type: 'info', condition: K('partial'), text: 'Рухаються лише кістки, які мають ключі в цій анімації; решта тіла — ходьба, біг, idle — живе далі. Грає раз.' },
                info_sync: { type: 'info', condition: K('sync'), text: 'Познач частини, які лишаються за РУХОМ гравця. Решту веде ця анімація.' },
                keep_legs: { label: 'Ноги ходять / біжать / присідають', type: 'checkbox', value: s.keep_legs !== false, condition: K('sync') },
                keep_torso: { label: 'Тулуб рухається (нахил на шифті, дихання)', type: 'checkbox', value: !!s.keep_torso, condition: K('sync') },
                keep_head: { label: 'Голова дивиться за камерою', type: 'checkbox', value: !!s.keep_head, condition: K('sync') },
                keep_right_arm: { label: 'Права рука махає при ходьбі', type: 'checkbox', value: !!s.keep_right_arm, condition: K('sync') },
                keep_left_arm: { label: 'Ліва рука махає при ходьбі', type: 'checkbox', value: !!s.keep_left_arm, condition: K('sync') },
                info_state: { type: 'info', condition: K('state'), text: 'Поза накладається на рух: <b>поза + сила × рух</b>. 0 — частина застигла в позі, 1 — повний рух поверх пози. Поки грає дія вищого шару (аптечка, емоція), вона перекриває стан, потім стан повертається.' },
                follow_idle: { label: 'Стоїть: дихання/idle поверх пози (0..1)', type: 'number', value: v('follow_idle', 1), step: 0.05, min: 0, max: 1, condition: K('state') },
                follow_move: { label: 'Іде / біжить: змах поверх пози (0..1)', type: 'number', value: v('follow_move', 0.35), step: 0.05, min: 0, max: 1, condition: K('state') },
                follow_sneak: { label: 'Шифт: нахил поверх пози (0..1)', type: 'number', value: v('follow_sneak', 1), step: 0.05, min: 0, max: 1, condition: K('state') },
                yield_swing: { label: 'Удар — ванільний змах руки (з предметом)', type: 'checkbox', value: v('yield_swing', s.hold_yield_swing) !== false, condition: K('state') },
                yield_use: { label: 'Використання (пити, їсти, лук, щит) — ванільна рука', type: 'checkbox', value: v('yield_use', s.hold_yield_use) !== false, condition: K('state') },
                hold_item: { label: 'Вмикати, коли в руці предмет (ns:item, через кому; порожньо — лише з коду)', type: 'text', value: s.hold_item || '', condition: K('state') },
                hold_hand: { label: 'Рука з предметом', type: 'select', options: { main: 'Основна', off: 'Друга', any: 'Будь-яка' }, value: s.hold_hand || 'main', condition: K('state') },
                hold_priority: { label: 'Пріоритет (з кількох станів грає вищий)', type: 'number', value: s.hold_priority || 0, step: 1, condition: K('state') },
                slot: { label: 'Шар', type: 'select', options: Object.fromEntries(SLOTS.map(x => [x, x])), value: s.slot || 'action', condition: KS(['custom']) },
                mask: { label: 'Маска (upper_body, right_arm+torso*0.5, full-legs); порожньо = кістки з ключами', type: 'text', value: s.mask || '', condition: KS(['custom', 'partial', 'state']) },
                _common: { type: 'info', text: '<b>Загальне</b>' },
                blend_in: { label: 'Згасання на вході, с', type: 'number', value: v('blend_in', 0.15), step: 0.05, min: 0 },
                blend_out: { label: 'Згасання на виході, с', type: 'number', value: v('blend_out', 0.2), step: 0.05, min: 0 },
                look: { label: 'Голова за камерою (0..1)', type: 'number', value: v('look', 1), step: 0.1, min: 0, max: 1 },
                first_person: { label: 'Перша особа', type: 'select', options: { vanilla: 'Ванільні руки', model: 'Анімовані руки + пропси', hide: 'Сховати руки' }, value: s.first_person || 'vanilla' },
                first_person_pitch: { label: 'Руки в 1-й особі слідують нахилу камери (0..1)', type: 'number', value: s.first_person_pitch || 0, step: 0.1, min: 0, max: 1, condition: f => f.first_person === 'model' },
                first_person_offset: { label: 'Руки в 1-й особі: зсув px (x, y, z назад) і нахил°', type: 'vector', dimensions: 4, value: s.first_person_offset || [0, 0, 2.5, 30], condition: f => f.first_person === 'model' },
                camera: { label: 'Камера 1-ї особи йде за головою: позиція (0..1)', type: 'number', value: s.camera || 0, step: 0.1, min: 0, max: 1 },
                camera_rotation: { label: 'Камера повертається за головою (0..1)', type: 'number', value: s.camera_rotation || 0, step: 0.1, min: 0, max: 1 },
                stride: { label: 'Цикл кроку: циклів на крок (для ходьби)', type: 'number', value: s.stride || 1, step: 0.1, min: 0.1, condition: K('custom') },
                additive: { label: 'Адитивно (додається поверх, а не замінює)', type: 'checkbox', value: !!s.additive, condition: K('custom') }
            },
            onConfirm(form) {
                for (const k of Object.keys(form)) if (k.startsWith('info') || k.startsWith('_')) delete form[k];
                if (form.kind !== 'custom') { delete form.slot; delete form.stride; delete form.additive; }
                if (form.kind === 'full' && Number(form.look) === 1 && s.look === undefined) form.look = 0;
                Undo.initEdit({ animations: [a] });
                a.shaurma_settings = form;
                Undo.finishEdit('Shaurma animation settings');
                msg('Shaurma: ' + a.name + ' — ' + form.kind, 1500);
            }
        }).show();
    }

    function propSettings() {
        const g = selectedGroups()[0];
        if (!g) return msg('Вибери групу пропса', 1500);
        const s = g.shaurma_prop || {};
        const from = rigAncestor(g);
        new Dialog({
            id: 'shaurma_prop_settings', title: 'Shaurma prop: ' + g.name,
            form: {
                info: { type: 'info', text: 'Прив\'язка: ' + (from ? from.name : 'вільно (world) — група поза ригом') + '. Щоб змінити — перетягни групу під іншу кістку.' },
                hide_held: { label: 'Ховати ванільний предмет у руці', type: 'select', options: { none: 'Ні', right: 'Права', left: 'Ліва', both: 'Обидві' }, value: s.hide_held || 'none' },
                emissive: { label: 'Світиться (без тіні)', type: 'checkbox', value: !!s.emissive },
                translucent: { label: 'Напівпрозора текстура (дим, скло, сяйво)', type: 'checkbox', value: !!s.translucent },
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
            c.box_uv = true;
            elements.push(c);
            if (name === 'right_arm') new Group({ name: 'right_item', origin: RIG_PIVOTS.right_item }).addTo(g).init();
            if (name === 'left_arm') new Group({ name: 'left_item', origin: RIG_PIVOTS.left_item }).addTo(g).init();
        }
        Project.texture_width = 64;
        Project.texture_height = 64;
        Canvas.updateAll();
        Undo.finishEdit('Shaurma player rig', { outliner: true, elements: elements });
    }

    /** Скін гравця: PNG 64×64 → текстура лише для кубиків ригу (пропси лишаються зі своїми). */
    function playerSkin() {
        Blockbench.import({ extensions: ['png'], type: 'Скін гравця (PNG 64×64)', readtype: 'image' }, files => {
            Undo.initEdit({ textures: [], elements: Cube.all });
            const tex = new Texture({ name: files[0].name }).fromDataURL(files[0].content).add(false);
            if (Format.per_texture_uv_size) { tex.uv_width = 64; tex.uv_height = 64; }
            for (const c of Cube.all) if (c.parent instanceof Group && isRigBone(c.parent.name, 'player')) c.applyTexture(tex, true);
            Undo.finishEdit('Shaurma: скін гравця', { textures: [tex], elements: Cube.all });
            Canvas.updateAll();
        });
    }

    Plugin.register('shaurma_anim', {
        title: 'Shaurma Animations',
        author: 'ShaurmaLib',
        icon: 'directions_run',
        description: 'Анімації гравця з окремими моделями в сцені (аптечка, бинт, шприц, ефекти): імпорт .bbmodel/.geo.json з текстурами, прив\'язка до рук, передача між руками, експорт ресурсів для рушія ShaurmaLib.',
        version: '2.0.0',
        variant: 'both',
        min_version: '4.8.0',
        onload() {
            disposables.push(new Property(Animation, 'object', 'shaurma_settings'));
            disposables.push(new Property(Group, 'object', 'shaurma_prop'));
            if (typeof ModelProject !== 'undefined') disposables.push(new Property(ModelProject, 'object', 'shaurma_export'));
            // автоекспорт: Ctrl+S у Blockbench — і ресурси вже в моді / в грі
            if (Blockbench.on) {
                autoExportHook = () => {
                    const e = Project && Project.shaurma_export;
                    if (!e || !e.auto || e.target === 'zip') return;
                    try { runExport(exportSettings(), false, true); } catch (err) { console.error(err); }
                };
                Blockbench.on('save_project', autoExportHook);
            }
            const A = (id, name, icon, category, click, extra) => {
                const a = new Action(id, Object.assign({ name: name, icon: icon, category: category, click: click }, extra || {}));
                disposables.push(a);
                return a;
            };
            const exp = A('shaurma_export', 'Export Shaurma Animation', 'directions_run', 'file', exportDialog);
            const quick = A('shaurma_quick_export', 'Shaurma: швидкий експорт (як минулого разу)', 'sync', 'file', quickExport,
                { keybind: typeof Keybind !== 'undefined' ? new Keybind({ key: 'e', ctrl: true, alt: true }) : undefined });
            const one = A('shaurma_export_selected', 'Shaurma: експорт вибраної анімації (окремий файл)', 'file_download', 'file', exportSelected,
                { keybind: typeof Keybind !== 'undefined' ? new Keybind({ key: 'e', ctrl: true, alt: true, shift: true }) : undefined });
            const imp = A('shaurma_import_model', 'Shaurma: модель у анімацію (.bbmodel / .geo.json)', 'medical_services', 'file', importModel);
            const show = A('shaurma_prop_show', 'Shaurma: показати пропс з цього кадру', 'visibility', 'animation', () => visibilityAction(true));
            const hide = A('shaurma_prop_hide', 'Shaurma: сховати пропс з цього кадру', 'visibility_off', 'animation', () => visibilityAction(false));
            const fit = A('shaurma_prop_fit', 'Shaurma: підігнати пропс (масштаб, зсув, поворот — наживо)', 'open_with', 'edit', () => fitProp());
            const hand = A('shaurma_prop_handoff', 'Shaurma: передати пропс в іншу кістку', 'swap_horiz', 'animation', handoffProp);
            const set = A('shaurma_anim_settings', 'Shaurma: налаштування анімації', 'tune', 'animation', animationSettings);
            const prop = A('shaurma_prop_settings', 'Shaurma: налаштування пропса', 'build', 'edit', propSettings);
            const rig = A('shaurma_player_rig', 'Shaurma: створити риг гравця', 'accessibility_new', 'edit', createPlayerRig);
            const skin = A('shaurma_player_skin', 'Shaurma: скін гравця (PNG)', 'face', 'edit', playerSkin);

            MenuBar.addAction(exp, 'file.export');
            MenuBar.addAction(quick, 'file.export');
            MenuBar.addAction(one, 'file.export');
            MenuBar.addAction(imp, 'file.import');
            for (const a of ['_', imp, fit, set, show, hide, hand, rig, skin, quick, one]) MenuBar.addAction(a, 'animation');
            MenuBar.addAction(prop, 'edit');
            MenuBar.addAction(fit, 'edit');
            if (Group.prototype.menu) for (const a of ['_', prop, fit, show, hide, hand]) Group.prototype.menu.addAction(a);

            // Пункт у стартовому списку «Створити → Minecraft»: новий проєкт одразу з ригом гравця.
            // Кілька текстур і UV по гранях: скін гравця + власні текстури моделей у сцені анімації.
            if (typeof ModelFormat !== 'undefined') {
                const format = new ModelFormat('shaurma_player', {
                    name: 'Shaurma: модель гравця',
                    description: 'Риг ванільного гравця (64×64, Steve UV) для анімацій рушія ShaurmaLib + окремі моделі в сцені (аптечка, шприц, ефекти) зі своїми текстурами.',
                    icon: 'accessibility_new',
                    category: 'minecraft',
                    target: ['Minecraft: Java Edition', 'ShaurmaLib'],
                    show_on_start_screen: true,
                    box_uv: true,
                    optional_box_uv: true,
                    single_texture: false,
                    per_texture_uv_size: true,
                    bone_rig: true,
                    centered_grid: true,
                    rotate_cubes: true,
                    animation_mode: true,
                    locators: true,
                    new() {
                        if (!newProject(this)) return false;
                        Project.name = 'survivor';
                        createPlayerRig();
                        return true;
                    }
                });
                disposables.push(format);
            }
        },
        onunload() {
            if (autoExportHook && Blockbench.removeListener) Blockbench.removeListener('save_project', autoExportHook);
            for (const d of disposables) if (d && d.delete) d.delete();
            disposables.length = 0;
        }
    });
})();
