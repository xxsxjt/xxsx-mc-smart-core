package com.xxsx.builder.voxel;

import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.*;

/** OBJ 模型数据，与 PMXModel 兼容 */
public class OBJModel {
    public final List<Vector3f> vertices = new ArrayList<>();
    public final List<Vector3f> normals = new ArrayList<>();
    public final List<Vector2f> uvs = new ArrayList<>();
    public final List<int[]> faces = new ArrayList<>();      // 三角形 [v0,v1,v2]
    public final List<Integer> faceMaterials = new ArrayList<>();
    public final List<PMXModel.PMXMaterial> materials = new ArrayList<>();
    public final List<int[]> faceNormals = new ArrayList<>(); // 面法线索引冗余

    public int getFaceCount() { return faces.size(); }
    public int getVertexCount() { return vertices.size(); }
}
