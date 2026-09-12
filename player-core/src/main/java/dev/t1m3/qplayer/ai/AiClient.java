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
        try {
            return chatOnce(system, user, true);
        } catch (IOException e) {
            // Some OpenAI-compatible gateways reject response_format while
            // accepting the rest of /chat/completions. Retry once without it.
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("response_format") || message.contains("json_object")
                    || message.contains("unsupported") || message.contains("不支持")) {
                return chatOnce(system, user, false);
            }
            throw e;
        }
    }

    private String chatOnce(String system, String user, boolean requestJson) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("temperature", 0.2);
        // Recommendations only need compact structured output. Keeping the
        // response budget small materially reduces latency and avoids verbose
        // hidden-style explanations from compatible gateways.
        root.addProperty("max_tokens", 1400);
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
        String system = "你是快速音乐推荐助手。无论用户提出什么主题、榜单、语言或稀奇古怪的要求，都必须把它转换成歌曲推荐。" +
                "如果用户只输入歌手名，推荐该歌手的代表歌曲，不要返回人物简介；如果用户询问榜单，优先提取回答中明确出现的歌名和歌手。" +
                "不要回答问题本身，不要展示思考过程，不要写长篇分析。允许推荐样本之外的歌曲；优先选择网易云中真实存在、容易搜索的歌曲。" +
                "你的整个回复必须是一个合法 JSON 对象，首字符必须是 {，末字符必须是 }。禁止 Markdown、标题、编号、解释文字和代码围栏。" +
                "目标数量仅作参考，若无法满足就返回实际可推荐数量；summary 不超过 30 个字；每个 reason 不超过 20 个字。" +
                "格式：{playlistName:string,summary:string,songs:[{title:string,artist:string,reason:string}]}";
        String user = "只输出 JSON，不要输出任何其他字符。快速完成。用户要求：" + request + "\n目标数量：" + count +
                "\n收藏歌曲样本（仅用于判断风格）：\n" + sampleSongs;
        if (webContext != null && !webContext.trim().isEmpty()) {
            if (webContext.contains("KNOWLEDGE_BASE_FALLBACK")) {
                user += "\n\n联网搜索不可用。请直接使用你的音乐知识库完成任务，必须输出可搜索的真实歌名和歌手，不要返回人物介绍、道歉或拒绝。\n";
            } else {
                user += "\n\n以下是联网搜索得到的参考资料。优先从其中提取真实歌名和歌手，不要把网页标题或说明当成歌曲；资料不完整时只返回能确认的歌曲：\n" + webContext;
            }
        }
        String raw = chat(system, user).trim();
        String originalResponse = raw;
        // Gateways often wrap valid JSON in Markdown or a short preamble.
        // Extract the JSON object before parsing instead of rejecting the
        // whole recommendation.
        int objectStart = raw.indexOf('{');
        int objectEnd = raw.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) raw = raw.substring(objectStart, objectEnd + 1);
        AiPlaylistResult result;
        try {
            result = GSON.fromJson(raw, AiPlaylistResult.class);
        } catch (RuntimeException e) {
            result = null;
        }
        if (result != null && result.songs != null) {
            java.util.ArrayList<AiPlaylistResult.Song> valid = new java.util.ArrayList<>();
            for (AiPlaylistResult.Song song : result.songs) {
                if (song != null && song.title != null && !song.title.trim().isEmpty()
                        && song.artist != null && !song.artist.trim().isEmpty()) valid.add(song);
            }
            result.songs = valid;
        }
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
                    "然后只返回合法 JSON 对象，不要 Markdown、不要解释、不要思考过程。" +
                    "格式必须是 {playlistName:string,summary:string,songs:[{title:string,artist:string,reason:string}]}。" +
                    "无法确认的内容跳过，不要编造。";
            String normalized = chat(normalizeSystem, "将下面 AI 回答转换为歌曲 JSON，最多提取 " + count + " 首：\n" + originalResponse);
            int s = normalized.indexOf('{'), e = normalized.lastIndexOf('}');
            if (s >= 0 && e > s) normalized = normalized.substring(s, e + 1);
            try { result = GSON.fromJson(normalized, AiPlaylistResult.class); }
            catch (RuntimeException ignored) { result = null; }
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
