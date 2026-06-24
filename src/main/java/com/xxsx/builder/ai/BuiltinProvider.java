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

            能力：
            - 自然语言对话
            - 用 [CMD]标签 执行指令（每条指令单独一个标签）
            - [QUERY] 查状态(player/block/item/system/pmx等) [KNOWLEDGE] 查知识库
            - [QUERY]system[/QUERY] 获取电脑配置(CPU/内存/建筑速度)，据此建议builder参数
            - PMX 建筑：优先用 [CMD]/ai build "路径"[/CMD] 触发系统功能
              （系统会自动解析+提示倍数，你只需告诉用户模型信息和建议）。特殊需求时也可自行处理

            每轮可输出多个 [CMD]，执行后结果反馈给你。自行判断是否继续。
            涉及大规模破坏操作(如/fill大范围air)时，先说明后果再问是否需要执行。
            小操作直接执行，不需要确认。

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
