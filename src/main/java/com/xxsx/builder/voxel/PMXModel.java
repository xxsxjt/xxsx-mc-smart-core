package com.xxsx.builder.voxel;

import org.joml.Vector3f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/** PMX 模型数据容器 */
public class PMXModel {
    public final List<Vector3f> vertices = new ArrayList<>();
    public final List<Vector3f> normals = new ArrayList<>();
    public final List<Vector2f> uvs = new ArrayList<>();
    /** 每个三角形：int[3] 顶点索引 */
    public final List<int[]> faces = new ArrayList<>();
    /** 每个三角形的材质索引（对应 materials 列表） */
    public final List<Integer> faceMaterials = new ArrayList<>();
    public final List<PMXMaterial> materials = new ArrayList<>();

    /** 材质定义 */
    public static class PMXMaterial {
        public final String name;
        public final float[] diffuse;     // RGBA
        public final float[] ambient;     // RGB
        public final float[] specular;    // RGB
        public final float specularPower;
        public final float[] edgeColor;   // RGBA
        public final float edgeSize;
        public final int vertexCount;     // 此材质使用的顶点数
        public final String texturePath;

        public PMXMaterial(String name, float[] diffuse, float[] ambient, float[] specular,
                           float specularPower, float[] edgeColor, float edgeSize,
                           int vertexCount, String texturePath) {
            this.name = name; this.diffuse = diffuse; this.ambient = ambient;
            this.specular = specular; this.specularPower = specularPower;
            this.edgeColor = edgeColor; this.edgeSize = edgeSize;
            this.vertexCount = vertexCount; this.texturePath = texturePath;
        }

        /** 获取漫反射颜色（三通道，用于体素着色） */
        public float[] getDiffuseRGB() {
            return new float[]{diffuse[0], diffuse[1], diffuse[2]};
        }
    }

    /** PMX 文件所在目录（用于查找纹理） */
    public String textureBaseDir = null;

    public int getFaceCount() { return faces.size(); }
    public int getVertexCount() { return vertices.size(); }
}
