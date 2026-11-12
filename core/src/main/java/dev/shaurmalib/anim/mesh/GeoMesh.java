package dev.shaurmalib.anim.mesh;

import dev.shaurmalib.anim.io.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bedrock-геометрія ({@code .geo.json}, формати 1.8 і 1.12+), розібрана і «запечена» в квади.
 *
 * Конвенції — РІВНО як у GeckoLib 4 ({@code BakedModelFactory}), бо саме з ними Blockbench-моделі
 * виглядають у грі так само, як у редакторі: півот і початок кубика дзеркаляться по X,
 * обертання кісток — {@code (-x, -y, z)}, box-UV та per-face UV з тими ж розгортками.
 * Простір результату — простір сцени Blockbench (Y вгору), одиниці — блоки (піксель/16).
 *
 * Чиста Java: адаптер версії лише множить готові вершини на свій PoseStack.
 */
public final class GeoMesh {

    public final int textureWidth, textureHeight;
    public final List<MeshBone> bones = new ArrayList<>();
    public final List<MeshBone> roots = new ArrayList<>();
    private final Map<String, MeshBone> byName = new HashMap<>();

    public static final class MeshBone {
        public final String name;
        public final String parentName;
        public MeshBone parent;
        public final List<MeshBone> children = new ArrayList<>();
        /** Півот у просторі сцени, блоки. */
        public final float pivotX, pivotY, pivotZ;
        /** Початкове обертання, радіани (вже з конвенцією -x,-y,z). */
        public final float rotX, rotY, rotZ;
        public final List<MeshCube> cubes = new ArrayList<>();

        MeshBone(String name, String parentName, float px, float py, float pz, float rx, float ry, float rz) {
            this.name = name;
            this.parentName = parentName;
            this.pivotX = px; this.pivotY = py; this.pivotZ = pz;
            this.rotX = rx; this.rotY = ry; this.rotZ = rz;
        }
    }

    public static final class MeshCube {
        public final float pivotX, pivotY, pivotZ, rotX, rotY, rotZ;
        public final List<Quad> quads = new ArrayList<>();

        MeshCube(float px, float py, float pz, float rx, float ry, float rz) {
            pivotX = px; pivotY = py; pivotZ = pz;
            rotX = rx; rotY = ry; rotZ = rz;
        }
    }

    /** 4 вершини (x,y,z блоки), UV (0..1), нормаль. */
    public static final class Quad {
        public final float[] pos = new float[12];
        public final float[] uv = new float[8];
        public final float nx, ny, nz;

        Quad(float[][] verts, float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
            for (int i = 0; i < 4; i++) {
                pos[i * 3] = verts[i][0];
                pos[i * 3 + 1] = verts[i][1];
                pos[i * 3 + 2] = verts[i][2];
            }
            uv[0] = u0; uv[1] = v0;
            uv[2] = u1; uv[3] = v0;
            uv[4] = u1; uv[5] = v1;
            uv[6] = u0; uv[7] = v1;
            this.nx = nx; this.ny = ny; this.nz = nz;
        }
    }

    private GeoMesh(int tw, int th) {
        this.textureWidth = tw <= 0 ? 16 : tw;
        this.textureHeight = th <= 0 ? 16 : th;
    }

    public MeshBone bone(String name) { return byName.get(name); }

    // ── Парсинг ──────────────────────────────────────────────────────────

    public static GeoMesh parse(String json) {
        Map<String, Object> root = Json.parseObject(json);
        Map<String, Object> geo = null;
        List<Object> list = Json.list(root.get("minecraft:geometry"));
        if (list != null && !list.isEmpty()) {
            geo = Json.obj(list.get(0));
        } else {
            for (Map.Entry<String, Object> e : root.entrySet()) {
                if (e.getKey().startsWith("geometry.")) { geo = Json.obj(e.getValue()); break; }
            }
        }
        if (geo == null) throw new IllegalArgumentException("немає minecraft:geometry");
        Map<String, Object> desc = Json.obj(geo.get("description"));
        Map<String, Object> d = desc != null ? desc : geo;
        GeoMesh mesh = new GeoMesh((int) Json.num(first(d, "texture_width", "texturewidth"), 16),
            (int) Json.num(first(d, "texture_height", "textureheight"), 16));

        List<Object> bones = Json.list(geo.get("bones"));
        if (bones == null) return mesh;
        for (Object bo : bones) {
            Map<String, Object> b = Json.obj(bo);
            if (b == null) continue;
            float[] pivot = vec(b.get("pivot"), 0);
            float[] rot = vec(b.get("rotation"), 0);
            MeshBone bone = new MeshBone(Json.str(b.get("name"), "bone"), Json.str(b.get("parent"), null),
                -pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f,
                (float) Math.toRadians(-rot[0]), (float) Math.toRadians(-rot[1]), (float) Math.toRadians(rot[2]));
            boolean boneMirror = Json.bool(b.get("mirror"), false);
            float boneInflate = (float) Json.num(b.get("inflate"), 0);
            List<Object> cubes = Json.list(b.get("cubes"));
            if (cubes != null) {
                for (Object co : cubes) {
                    Map<String, Object> c = Json.obj(co);
                    if (c != null) bone.cubes.add(cube(c, mesh, boneMirror, boneInflate));
                }
            }
            mesh.bones.add(bone);
            mesh.byName.put(bone.name, bone);
        }
        for (MeshBone b : mesh.bones) {
            MeshBone p = b.parentName == null ? null : mesh.byName.get(b.parentName);
            if (p == null) mesh.roots.add(b);
            else { b.parent = p; p.children.add(b); }
        }
        return mesh;
    }

    private static MeshCube cube(Map<String, Object> c, GeoMesh mesh, boolean boneMirror, float boneInflate) {
        float[] o = vec(c.get("origin"), 0);
        float[] s = vec(c.get("size"), 0);
        float[] cp = vec(c.get("pivot"), 0);
        float[] cr = vec(c.get("rotation"), 0);
        boolean mirror = c.containsKey("mirror") ? Json.bool(c.get("mirror"), false) : boneMirror;
        float inflate = (float) (c.containsKey("inflate") ? Json.num(c.get("inflate"), 0) : boneInflate) / 16f;

        MeshCube cube = new MeshCube(-cp[0] / 16f, cp[1] / 16f, cp[2] / 16f,
            (float) Math.toRadians(-cr[0]), (float) Math.toRadians(-cr[1]), (float) Math.toRadians(cr[2]));

        float ox = -(o[0] + s[0]) / 16f, oy = o[1] / 16f, oz = o[2] / 16f;
        float sx = s[0] / 16f, sy = s[1] / 16f, sz = s[2] / 16f;
        float x0 = ox - inflate, y0 = oy - inflate, z0 = oz - inflate;
        float x1 = ox + sx + inflate, y1 = oy + sy + inflate, z1 = oz + sz + inflate;
        // назви як у GeckoLib VertexSet
        float[] blb = { x0, y0, z0 }, brb = { x0, y0, z1 }, tlb = { x0, y1, z0 }, trb = { x0, y1, z1 };
        float[] tlf = { x1, y1, z0 }, trf = { x1, y1, z1 }, blf = { x1, y0, z0 }, brf = { x1, y0, z1 };
        float[][] west = { trb, tlb, blb, brb }, east = { tlf, trf, brf, blf };
        float[][] north = { tlb, tlf, blf, blb }, south = { trf, trb, brb, brf };
        float[][] up = { trb, trf, tlf, tlb }, down = { blb, blf, brf, brb };

        Object uvRaw = c.get("uv");
        Map<String, Object> faceUv = Json.obj(uvRaw);
        String[] dirs = { "west", "east", "north", "south", "up", "down" };
        float[][] normals = { { -1, 0, 0 }, { 1, 0, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { 0, 1, 0 }, { 0, -1, 0 } };
        for (int d = 0; d < 6; d++) {
            String dir = dirs[d];
            float[][] verts;
            boolean boxUv = faceUv == null;
            switch (dir) {
                case "west" -> verts = mirror ? east : west;
                case "east" -> verts = mirror ? west : east;
                case "north" -> verts = north;
                case "south" -> verts = south;
                case "up" -> verts = mirror && !boxUv ? down : up;
                default -> verts = mirror && !boxUv ? up : down;
            }
            float u, v, us, vs;
            if (boxUv) {
                float[] uv = vec(uvRaw, 0);
                float fx = (float) Math.floor(s[0]), fy = (float) Math.floor(s[1]), fz = (float) Math.floor(s[2]);
                switch (dir) {
                    case "west" -> { u = uv[0] + fz + fx; v = uv[1] + fz; us = fz; vs = fy; }
                    case "east" -> { u = uv[0]; v = uv[1] + fz; us = fz; vs = fy; }
                    case "north" -> { u = uv[0] + fz; v = uv[1] + fz; us = fx; vs = fy; }
                    case "south" -> { u = uv[0] + fz + fx + fz; v = uv[1] + fz; us = fx; vs = fy; }
                    case "up" -> { u = uv[0] + fz; v = uv[1]; us = fx; vs = fz; }
                    default -> { u = uv[0] + fz + fx; v = uv[1] + fz; us = fx; vs = -fz; }
                }
            } else {
                Map<String, Object> f = Json.obj(faceUv.get(dir));
                if (f == null) continue;
                float[] fuv = vec(f.get("uv"), 0);
                float[] fsz = vec(f.get("uv_size"), 0);
                u = fuv[0]; v = fuv[1]; us = fsz[0]; vs = fsz[1];
            }
            float uW = (u + us) / mesh.textureWidth, vH = (v + vs) / mesh.textureHeight;
            float uu = u / mesh.textureWidth, vv = v / mesh.textureHeight;
            float nx = normals[d][0], ny = normals[d][1], nz = normals[d][2];
            float u0, u1;
            if (!mirror) { u0 = uW; u1 = uu; } else { u0 = uu; u1 = uW; nx = -nx; }
            float[][] copy = { verts[0].clone(), verts[1].clone(), verts[2].clone(), verts[3].clone() };
            cube.quads.add(new Quad(copy, u0, vv, u1, vH, nx, ny, nz));
        }
        return cube;
    }

    private static float[] vec(Object o, float def) {
        float[] r = { def, def, def };
        List<Object> l = Json.list(o);
        if (l != null) for (int i = 0; i < Math.min(3, l.size()); i++) r[i] = (float) Json.num(l.get(i), def);
        return r;
    }

    private static Object first(Map<String, Object> m, String... keys) {
        for (String k : keys) if (m.containsKey(k)) return m.get(k);
        return null;
    }
}
