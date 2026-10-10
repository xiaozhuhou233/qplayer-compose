package dev.t1m3.qplayer.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** OpenAI-compatible chat client; works with DeepSeek, OpenAI, Qwen gateways and custom APIs. */
public final class AiClient {
    private static final Gson GSON = new Gson();
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;

    public AiClient(String baseUrl, String apiKey, String model, int timeoutMs) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null ? "" : model;
        this.timeoutMs = Math.max(5_000, timeoutMs);
    }

    public String chat(String system, String user) throws IOException {
        return chat(system, user, 0.2d);
    }

    /**
     * One turn at a caller-chosen {@code temperature}. The playlist path uses this to run
     * <b>warm</b> ({@link AiReference#temperature}) — the user asked for the generated songs to have
     * randomness (「ai 算法生成的歌曲要有随机性」), and a greedy 0.2 answers the same request with the
     * same list every time. Every other caller keeps {@link #chat(String, String)}, which is the old
     * 0.2: the transition chooser must answer the same way twice for the same pair.
     */
    public String chat(String system, String user, double temperature) throws IOException {
        try {
            return chatOnce(system, user, true, temperature);
        } catch (IOException e) {
            // Some OpenAI-compatible gateways reject response_format while
            // accepting the rest of /chat/completions. Retry once without it.
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("response_format") || message.contains("json_object")
                    || message.contains("unsupported") || message.contains("不支持")) {
                return chatOnce(system, user, false, temperature);
            }
            throw e;
        }
    }

    /**
     * One turn with no {@code response_format} hint at all, for prompts whose
     * answer is not a JSON document — a single word or line, say. Worth its own
     * entry point rather than a call to {@link #chat}: several providers (DeepSeek
     * among them) reject {@code response_format: json_object} outright unless the
     * prompt happens to contain the word "json", which {@link #chat} can only
     * discover by paying for the rejected request first. Nothing about the rest of
     * the request differs — same model, same temperature, same timeout, same
     * endpoint and key.
     */
    public String chatPlain(String system, String user) throws IOException {
        return chatOnce(system, user, false, 0.2d);
    }

    /**
     * The playlist JSON, tolerant of both the compact pair-array shape the prompt now
     * asks for ({@code {"name":…,"songs":[["title","artist"]]}}) and the older object
     * shape — a model may answer in either, and the older shape costs nothing to accept.
     */
    private static AiPlaylistResult parseResult(String raw) {
        AiPlaylistResult result = new AiPlaylistResult();
        if (raw == null) return result;
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return result;
        try {
            JsonObject obj = JsonParser.parseString(raw.substring(start, end + 1)).getAsJsonObject();
            com.google.gson.JsonElement name = obj.has("name") ? obj.get("name") : obj.get("playlistName");
            if (name != null && name.isJsonPrimitive()) result.playlistName = name.getAsString();
            if (obj.has("summary") && obj.get("summary").isJsonPrimitive()) {
                result.summary = obj.get("summary").getAsString();
            }
            if (!obj.has("songs") || !obj.get("songs").isJsonArray()) return result;
            for (com.google.gson.JsonElement el : obj.getAsJsonArray("songs")) {
                if (el == null || el.isJsonNull()) continue;
                AiPlaylistResult.Song song = new AiPlaylistResult.Song();
                if (el.isJsonArray()) {
                    com.google.gson.JsonArray pair = el.getAsJsonArray();
                    if (pair.size() > 0 && pair.get(0).isJsonPrimitive()) song.title = pair.get(0).getAsString();
                    if (pair.size() > 1 && pair.get(1).isJsonPrimitive()) song.artist = pair.get(1).getAsString();
                } else if (el.isJsonObject()) {
                    com.google.gson.JsonObject o = el.getAsJsonObject();
                    // 键名宽容：英文、中文、以及常见的变体都认（多认一种只会多出结果）。
                    song.title = firstPrimitive(o, "title", "歌名", "name", "song");
                    song.artist = firstPrimitive(o, "artist", "歌手", "singer", "artists", "by");
                    song.reason = firstPrimitive(o, "reason", "理由", "note");
                } else if (el.isJsonPrimitive()) {
                    // 一条字符串（"歌名 - 歌手" 之类）：尽力拆开，不完整的按歌名收下。
                    String text = el.getAsString().trim();
                    for (String sep : new String[] { " - ", " – ", " — ", "—", "–", "-", "|", "·" }) {
                        int at = text.indexOf(sep);
                        if (at > 0 && at + sep.length() < text.length()) {
                            song.title = text.substring(0, at).trim();
                            song.artist = text.substring(at + sep.length()).trim();
                            break;
                        }
                    }
                    if (song.title.isEmpty()) song.title = text;
                }
                if (!song.title.trim().isEmpty() && !song.artist.trim().isEmpty()) result.songs.add(song);
            }
        } catch (RuntimeException ignored) { }
        return result;
    }

    /** The first of {@code keys} that the object carries as a string. */
    private static String firstPrimitive(com.google.gson.JsonObject o, String... keys) {
        for (String key : keys) {
            com.google.gson.JsonElement el = o.get(key);
            if (el != null && el.isJsonPrimitive()) return el.getAsString().trim();
        }
        return "";
    }

    /** A small plain-text answer, without paying the playlist's full output budget. */
    public String chatPlain(String system, String user, int maxTokens) throws IOException {
        return chatOnce(system, user, false, 0.7d, Math.max(32, Math.min(8192, maxTokens)));
    }

    private String chatOnce(String system, String user, boolean requestJson) throws IOException {
        return chatOnce(system, user, requestJson, 0.2d);
    }

    private String chatOnce(String system, String user, boolean requestJson, double temperature)
            throws IOException {
        return chatOnce(system, user, requestJson, temperature, 8192);
    }

    private String chatOnce(String system, String user, boolean requestJson, double temperature,
                            int maxTokens) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("temperature", Math.max(0d, Math.min(2d, temperature)));
        // Recommendations only need compact structured output. Keeping the
        // response budget small materially reduces latency and avoids verbose
        // hidden-style explanations from compatible gateways.
        // One compact item is roughly 25-35 tokens.  A fixed 1400-token cap
        // truncates larger requests and leaves an invalid JSON document.
        // ⚠️ 这里只放应用一直以来的字段（model / temperature / max_tokens /
        // response_format / messages）。不要再加 reasoning_effort 之类的硬性限制：
        // 真实的兼容网关可能因此完全拿不到结果（2026-10-07 的教训）。
        root.addProperty("max_tokens", maxTokens);
        // Ask OpenAI-compatible providers to enforce a JSON object response.
        // Providers that do not support this field are handled by the caller's
        // Markdown fallback parser.
        if (requestJson) {
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            root.add("response_format", responseFormat);
        }
        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        JsonObject s = new JsonObject(); s.addProperty("role", "system"); s.addProperty("content", system); messages.add(s);
        JsonObject u = new JsonObject(); u.addProperty("role", "user"); u.addProperty("content", user); messages.add(u);
        root.add("messages", messages);
        byte[] body = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + "/chat/completions").openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(timeoutMs); c.setReadTimeout(timeoutMs);
        c.setRequestProperty("Content-Type", "application/json");
        if (!apiKey.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + apiKey);
        try (OutputStream out = c.getOutputStream()) { out.write(body); }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String response = read(in);
        if (code < 200 || code >= 300) {
            String message = response;
            try {
                JsonObject error = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("error");
                if (error != null && error.has("message")) message = error.get("message").getAsString();
            } catch (RuntimeException ignored) { }
            throw new IOException("AI 请求失败（HTTP " + code + "）：" + message);
        }
        JsonObject parsed = JsonParser.parseString(response).getAsJsonObject();
        return parsed.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    public AiPlaylistResult generatePlaylist(String sampleSongs, String request, int count) throws IOException {
        return generatePlaylist(sampleSongs, request, count, "");
    }

    public AiPlaylistResult generatePlaylist(String sampleSongs, String request, int count, String webContext) throws IOException {
        return generatePlaylist(sampleSongs, request, count, webContext, "");
    }

    /**
     * The same, with <b>the reference block</b> the controller composed handed in: my listening
     * history, the list that is playing (a continuation only) and this run's 切入角度 — see
     * {@link AiReference#block}.
     *
     * <p>Round 34 introduced this parameter as the 「老歌」 anchor of a continuation; round 35 widened
     * it to every generation, because the user asked for the history to be consulted as well
     * (「推荐歌曲除了当红歌还需要我的历史记录参考」) and for picks beyond the current hits
     * (「参考也要推荐某些歌手的新歌或者稍微小众点的歌曲」). The request itself is also sent warm here
     * ({@link AiReference#temperature}) — 「ai 算法生成的歌曲要有随机性」 — which is why this path does
     * not use the plain {@link #chat(String, String)} with its greedy 0.2.
     *
     * @param references the block {@link AiReference#block} built, or empty for a bare request
     */
    public AiPlaylistResult generatePlaylist(String sampleSongs, String request, int count,
                                             String webContext, String references) throws IOException {
        boolean previousListProvided = references != null && references.contains("上一张歌单");
        String normalizedRequest = request == null ? "" : request
                .replaceAll("(?i)r\\s*(?:and|&)\\s*b", "R&B")
                .replaceAll("(?i)rhythm\\s*and\\s*blues", "R&B");
        // 2026-10-07 提速重写（「大幅度优化 AI 推荐歌曲的速度，更改提示词，但不要改变要求」）：
        // 选曲要求逐条保留（R&B 规则 / 语种自由 / 四项选取 / 随机性 / 老歌+新歌 / 不拒绝短描述 /
        // 歌手名请求 / 只输出 JSON），提速来自更小的输出：单行紧凑 JSON、reason 一律空串、
        // 恰好目标数量（不再鼓励多给 —— 多给一首就多一次网易云搜索），措辞去掉重复后更短。
        // 2026-10-09：用户直接给出了提示词规格（「现在改提示词」），逐字采用 —— 角色定义与
        // 5 条规则就是权威文本。它要的 {"name":…,"songs":[["歌名","歌手"]]} 正是解析器已经在读
        // 的形态，所以这只是一次提示词改写，**没有**改动请求本身（不加字段、不压预算 —— 见那条禁则）。
        // 2026-10-10：用户再次直接给出提示词文本（「直接说结果：…更改提示词」），逐字采用。
        // 只把他当分隔符用的「……」落成句号、把连续空格收拢；没有增删或改写任何要求。
        String system = "你是专业 DJ，负责按用户要求推荐歌曲，语种没有硬性要求，选曲必须同时包含："
                + "参考历史记录，歌手新歌，几首小众。"
                + "每次生成要有随机性。"
                + "输出（越快越好）：不要任何思考过程、解释或前言，直接输出最终 JSON。"
                + "格式固定为 {\"name\":\"歌单名(不超过10个字)\",\"songs\":[[\"歌名\",\"歌手\"],...]}；"
                + "每首歌只有歌名和歌手两个值。"
                + "单行、无空格、无换行、无 Markdown。"
                + "songs 数组必须达到目标数量。";
        // 「上一张歌单」延续只在续播时出现（用户没在提示词里写它，但这是 AI DJ 续播的已有行为，
        // 所以保留这一行；它只在真有上一张歌单时追加）。
        if (previousListProvided) {
            system += "\n另附上一张歌单：新歌单要保持同样的风格，并在其中保留一部分老歌，其余为新歌。";
        }
        if (webContext != null && webContext.contains("KNOWLEDGE_BASE_ONLY")) {
            system += "当前已开启强制使用知识库：无论用户提出什么问题，都禁止联网、禁止调用搜索工具、禁止要求搜索资料，只能使用你已有的模型知识完成推荐。";
        }
        // 用户回合只放「输入」：请求本身、数量、参考块。规则已在 system 里，不重复。
        String user = "Request: " + normalizedRequest +
                "\nCount: " + count + "\n"
                + "Taste sample (style reference only):\n" + sampleSongs;
        if (references != null && !references.trim().isEmpty()) {
            user += "\n\n" + references.trim();
        }
        if (webContext != null && !webContext.trim().isEmpty()) {
            if (webContext.contains("KNOWLEDGE_BASE_FALLBACK") || webContext.contains("KNOWLEDGE_BASE_ONLY")) {
                user += "\n\n联网搜索不可用。请直接使用你的音乐知识库完成任务，必须输出可搜索的真实歌名和歌手，不要返回人物介绍、道歉或拒绝。\n";
            } else {
                user += "\n\n以下是联网搜索得到的参考资料。优先从其中提取真实歌名和歌手，不要把网页标题或说明当成歌曲；资料不完整时只返回能确认的歌曲：\n" + webContext;
            }
        }
        // Warm, and a fresh draw for every attempt: 「ai 算法生成的歌曲要有随机性」. A retry after an
        // unusable answer therefore comes back with different songs rather than the same ones again.
        // ⚠️ 不要给这个请求加硬性限制（用户 2026-10-07：「你这个版本ai根本无法返回任何结果，
        // 不要给ai加硬性限制」）。第二轮试过的 reasoning_effort=low 与「按数量收紧的
        // max_tokens」都会让真实网关拿不到结果，已全部撤掉；这里保持应用一直以来的请求形态：
        // 默认温度、完整 8192 预算、不带任何额外字段。提速只能靠提示词本身。
        String raw = chat(system, user,
                AiReference.temperature(java.util.concurrent.ThreadLocalRandom.current())).trim();
        String originalResponse = raw;
        // Gateways often wrap valid JSON in Markdown or a short preamble.
        // Extract the JSON object before parsing instead of rejecting the
        // whole recommendation.
        int objectStart = raw.indexOf('{');
        int objectEnd = raw.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) raw = raw.substring(objectStart, objectEnd + 1);
        AiPlaylistResult result = parseResult(raw);
        if (result.songs.isEmpty()) result = null;
        // Be tolerant of providers that return fewer/more items than requested.
        // The resolver will use every valid item instead of discarding an
        // otherwise useful recommendation set.
        if (result == null || result.songs == null || result.songs.isEmpty()) {
            // Do not spend another request when the model already returned a
            // readable ranked list. This is common for chart questions.
            result = parseRankedMarkdown(originalResponse);
        }
        if (result == null || result.songs == null || result.songs.isEmpty()) {
            // Second pass: complex chart/trend questions are often answered as
            // prose. Ask the model to normalize that prose into song objects.
            String normalizeSystem = "你是歌曲信息提取器。只从输入文本中提取真实歌曲的歌名和歌手，" +
                    "只返回一个 JSON 对象，不要 Markdown、不要解释、不要思考过程。" +
                    "格式固定为 {\"name\":\"\",\"songs\":[[\"歌名\",\"歌手\"]]}。" +
                    "无法确认的内容跳过，不要编造。";
            String normalized = chat(normalizeSystem, "将下面 AI 回答转换为歌曲 JSON，最多提取 " + count + " 首：\n" + originalResponse);
            int s = normalized.indexOf('{'), e = normalized.lastIndexOf('}');
            if (s >= 0 && e > s) normalized = normalized.substring(s, e + 1);
            result = parseResult(normalized);
            if (result.songs.isEmpty()) result = null;
            if (result == null || result.songs == null || result.songs.isEmpty()) {
                // The second pass can also ignore JSON mode. Reuse the same
                // local extractor instead of reporting a false empty result.
                result = parseRankedMarkdown(normalized);
            }
        }
        if (result == null || result.songs == null || result.songs.isEmpty())
            throw new IOException("AI 未能提取出歌曲，请换一种描述");
        return result;
    }

    /** Fallback for models that answer chart requests with a readable Markdown
     * ranking instead of the requested JSON, e.g. `1. **Artist** - *Title*`. */
    private static AiPlaylistResult parseRankedMarkdown(String raw) {
        AiPlaylistResult result = new AiPlaylistResult();
        if (raw == null || raw.trim().isEmpty()) return result;
        Pattern p = Pattern.compile("^\\s*(?:\\d+[.)、:]|[-•])\\s+(.+?)\\s+[-–—:]\\s+(.+?)\\s*$");
        for (String line : raw.split("\\r?\\n")) {
            Matcher m = p.matcher(line);
            if (!m.find()) continue;
            AiPlaylistResult.Song song = new AiPlaylistResult.Song();
            song.artist = cleanSongPart(m.group(1), false);
            song.title = cleanSongPart(m.group(2), true);
            if (!song.artist.isEmpty() && !song.title.isEmpty()) {
                song.reason = "榜单推荐";
                result.songs.add(song);
            }
        }
        result.summary = "从 AI 返回的榜单中提取歌曲";
        return result;
    }

    private static String cleanSongPart(String value, boolean title) {
        if (value == null) return "";
        String s = value.replace("**", "").replace("__", "").replace("*", "")
                .replaceAll("\\[([^]]+)\\]\\([^)]*\\)", "$1")
                .trim();
        if (title) {
            s = s.replaceFirst("\\s*[（(](?=.*(?:连续|第\\d+|周|榜|排名|位|week|weeks))[^）)]*[）)]\\s*$", "");
        }
        return s.trim();
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n; while ((n = input.read(b)) >= 0) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
