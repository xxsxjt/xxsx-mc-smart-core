package com.xxsx.builder.voxel;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** OBJ + MTL 文本解析器。三角化四边形面，提取材质颜色。 */
public class OBJParser {

    public OBJModel parse(Path objPath) throws IOException {
        OBJModel m = new OBJModel();
        Map<String, float[]> mtlColors = new HashMap<>();
        float[] currentColor = {0.8f, 0.8f, 0.8f};
        int currentMat = 0;

        // 先读 MTL
        List<String> lines = Files.readAllLines(objPath);
        Path mtlPath = null;
        for (String l : lines) {
            if (l.startsWith("mtllib ")) {
                String mtlName = l.substring(7).trim();
                mtlPath = objPath.resolveSibling(mtlName);
                break;
            }
        }
        if (mtlPath != null && Files.exists(mtlPath)) {
            String matName = "";
            for (String l : Files.readAllLines(mtlPath)) {
                if (l.startsWith("newmtl ")) matName = l.substring(7).trim();
                else if (l.startsWith("Kd ") && !matName.isEmpty()) {
                    String[] p = l.substring(3).trim().split("\\s+");
                    if (p.length >= 3) {
                        mtlColors.put(matName, new float[]{
                            Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])});
                    }
                }
            }
        }

        // 默认材质
        m.materials.add(new PMXModel.PMXMaterial("default", new float[]{0.8f,0.8f,0.8f,1},
            new float[]{0,0,0}, new float[]{0,0,0}, 0, new float[]{0,0,0,1}, 0, 0, ""));

        // 解析 OBJ
        for (String l : lines) {
            l = l.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;

            String[] parts = l.split("\\s+");
            try {
                switch (parts[0]) {
                    case "v" -> m.vertices.add(new org.joml.Vector3f(
                        Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])));
                    case "vn" -> m.normals.add(new org.joml.Vector3f(
                        Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])));
                    case "vt" -> m.uvs.add(new org.joml.Vector2f(
                        Float.parseFloat(parts[1]), Float.parseFloat(parts[2])));
                    case "usemtl" -> {
                        String name = parts[1];
                        float[] col = mtlColors.get(name);
                        if (col != null) {
                            m.materials.add(new PMXModel.PMXMaterial(name,
                                new float[]{col[0],col[1],col[2],1},
                                new float[]{0,0,0}, new float[]{0,0,0}, 0,
                                new float[]{0,0,0,1}, 0, 0, ""));
                            currentMat = m.materials.size() - 1;
                            currentColor = col;
                        }
                    }
                    case "f" -> {
                        int[] vi = new int[parts.length - 1];
                        int[] ti = new int[parts.length - 1];
                        for (int i = 1; i < parts.length; i++) {
                            String[] f = parts[i].split("/");
                            vi[i-1] = Integer.parseInt(f[0]) - 1; // 1-based -> 0-based
                            ti[i-1] = f.length > 1 && !f[1].isEmpty() ? Integer.parseInt(f[1]) - 1 : 0;
                        }
                        // 三角化：扇形分割
                        for (int i = 1; i < vi.length - 1; i++) {
                            m.faces.add(new int[]{vi[0], vi[i], vi[i+1]});
                            m.faceMaterials.add(currentMat);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 确保至少有一个材质
        if (m.materials.isEmpty())
            m.materials.add(new PMXModel.PMXMaterial("default",
                new float[]{0.8f,0.8f,0.8f,1}, new float[]{0,0,0}, new float[]{0,0,0}, 0,
                new float[]{0,0,0,1}, 0, 0, ""));

        return m;
    }
}
