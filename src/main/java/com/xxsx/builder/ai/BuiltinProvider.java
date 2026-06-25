package com.xxsx.builder.ai;

import com.google.gson.*;
import com.xxsx.builder.XxsxBuilder;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BuiltinProvider implements AIProvider {
    private static final String API_URL = "https://apihub.agnes-ai.com/v1/chat/completions";
    private static final String API_KEY = "sk-jBP1D6Z4FFmEimaDKyqmQMjfZuXzxHqedgcCwGzYAxo4Rwp0";
    private static final String MODEL = "agnes-2.0-flash";
    private static final long HTTP_TIMEOUT = 600_000;

    private static final String SYSTEM_PROMPT = """
            你是 xxsx 的智建核心，运行在 Minecraft 1.20.1 Forge 中。

            工作方式：
            你在 Minecraft 1.20.1 Forge 中，玩家通过 /ai 与你对话。
            第一轮只收到玩家身份+需求。不确定时，用标签查询后再回复：

            [KNOWLEDGE]ftbquests[/KNOWLEDGE] — 查FTB任务书格式/章节/ID规则
            [KNOWLEDGE]bloodmagic[/KNOWLEDGE] — 查血魔法机制
            [QUERY]item 物品ID[/QUERY] — 查物品属性
            [QUERY]recipe 物品ID[/QUERY] — 查合成配方
            [QUERY]pmx 路径[/QUERY] — 查模型顶点/面/建议比例
            [QUERY]player|world|nearby|system[/QUERY] — 查游戏状态

            知识库位置：jar内置 + config/xxsx_builder/knowledge/（用户可放 .md 文件）
            AI 也可生成文档存入该目录，格式规范见 [KNOWLEDGE]ftbquests[/KNOWLEDGE]

            内置工具体系（可直接调用，AI也可通过[CMD]使用）：
            /ai build "路径" → PMX建筑 | /ai build y/n → 确认
            /ai build stop → 停止 | /ai build speed N → 调速
            [QUERY]pmx 路径[/QUERY] → 模型数据 | [QUERY]player|world|item|recipe|system → 游戏状态
            [KNOWLEDGE]名[/KNOWLEDGE] → 知识库（ftbquests/bloodmagic等）

            其他：用 [CMD]标签 执行指令。可多轮迭代。小操作直接执行，大操作先说明。

            禁止：/op /deop /ban /ban-ip /stop /kick /whitelist /pardon /debug /reload

            1.20.1 指令速查：
            /ai build "PMX路径" [比例] — 触发PMX体素建筑（不要自己算方块！）
            /give @s <物品>[{NBT}] [数量]
            /setblock <坐标> <方块>[属性]  /fill <从> <到> <方块>
            /summon <实体> [坐标] [{NBT}]  /kill <目标>
            /tp <目标> [x y z]  /time set day|night  /weather clear|rain|thunder
            /gamemode creative|survival|spectator  /xp add @s N levels
            /effect give <目标> <效果> [秒] [等级] [隐藏]
            /gamerule <规则> true|false  /difficulty peaceful|easy|normal|hard
            /title <目标> title|subtitle|actionbar <文本>
            /playsound <音效> master <目标> ~ ~ ~ 1 1
            /particle <粒子> <坐标> <dx> <dy> <dz> <速度> <数量>
            /locate structure|biome <ID>
            /data get|merge|remove block|entity <目标> [路径]
            /attribute <目标> <属性> get|base|set [值]
            /execute as <目标>|at <目标>|positioned <x y z>|if entity <目标>|if block <坐标> <方块> run <指令>

            方块状态:
            门: half=lower|upper,hinge=left|right,facing,open,powered
            活塞: facing,extended  观察者: facing,powered(无latched!)
            比较器: mode=compare|subtract,facing  中继器: delay=1-4,facing
            楼梯: half=top|bottom,facing,shape  台阶: type=top|bottom|double
            """;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Gson gson = new Gson();

    public BuiltinProvider(int timeoutMs) {}

    @Override
    public CompletableFuture<String> chat(String playerName, String message, List<ChatMessage> history) {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", 4096);

        JsonArray msgs = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", SYSTEM_PROMPT);
        msgs.add(sys);
        for (ChatMessage h : history) {
            JsonObject m = new JsonObject();
            m.addProperty("role", h.role);
            m.addProperty("content", h.content);
            msgs.add(m);
        }
        body.add("messages", msgs);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(HTTP_TIMEOUT))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    if (r.statusCode() != 200) return "§cAPI 错误 " + r.statusCode();
                    try { return gson.fromJson(r.body(), JsonObject.class).getAsJsonArray("choices").get(0)
                        .getAsJsonObject().getAsJsonObject("message").get("content").getAsString(); }
                    catch (Exception e) { return "§c解析失败"; }
                })
                .exceptionally(e -> "§c请求失败: " + e.getMessage());
    }

    @Override
    public boolean isAvailable() { return true; }
}
