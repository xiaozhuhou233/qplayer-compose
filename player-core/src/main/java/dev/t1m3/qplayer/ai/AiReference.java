package dev.t1m3.qplayer.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The AI DJ's <b>randomness</b> and its <b>references</b> (round 35).
 *
 * <p>The user, after living with the first version: 「ai 算法生成的歌曲要有随机性，也不用非要是中英文穿插，
 * 另外给听歌历史记录加入 ai 参考范围，推荐歌曲除了当红歌还需要我的历史记录参考，参考也要推荐某些歌手的
 * 新歌或者稍微小众点的歌曲」. Three things follow, and they are all decided here rather than left to the
 * model:
 *
 * <ul>
 *   <li><b>Randomness.</b> A generation is not reproducible twice: the history it is shown is a
 *   <em>random sample</em> of the account's listening history ({@link #sample}), the instruction it is
 *   given carries a random 切入角度 ({@link #angle}), and the request is sent warm
 *   ({@link #temperature}) instead of at the greedy 0.2 the transition chooser needs. Same request,
 *   two different lists — which is the point.</li>
 *   <li><b>The listening history is a reference for every generation</b>, not only for the
 *   long-press taste shortcut: {@link #block} writes it into the prompt beside the list that is
 *   playing.</li>
 *   <li><b>Hot songs are not the whole answer.</b> The angles and the prompt clauses ask for the
 *   user's own history <em>and</em> for new releases by the artists in it, and for a few
 *   less-mainstream picks.</li>
 * </ul>
 *
 * <p>Everything here is a pure function of its inputs and the {@link Random} it is handed, so the
 * policy can be tested without a network, a model or a controller — see {@code AiReferenceTest}.
 */
public final class AiReference {

    /** How many history rows are shown to the model, drawn at random from up to a hundred. Twenty,
     *  the same order of magnitude as the liked-song sample the long-press shortcut already used: big
     *  enough to carry the taste, small enough not to crowd out the request itself. */
    public static final int HISTORY_SAMPLE = 20;

    /** How many rows of the list that is playing are shown — the 「老歌」 anchor of a continuation. */
    public static final int PREVIOUS_LIST_CAP = 20;

    /** The warm end of the request's temperature: this is the range the playlist is generated in.
     *  The transition chooser keeps the client's own 0.2 (it must answer the same way twice for the
     *  same pair), so the two features do not share one number any more. */
    public static final double TEMPERATURE_MIN = 0.75;
    public static final double TEMPERATURE_MAX = 0.95;

    /**
     * The 切入角度 offered to the model, one drawn per generation. Written as instructions rather than
     * as topics on purpose: each one moves the model off "the current top hits for this style", which
     * is what made two generations of the same request come back nearly identical.
     */
    private static final String[] ANGLES = {
        "从我历史里挑一位我常听的歌手，找一首他/她最近发的新歌。",
        "找几首风格相似但更小众、榜单上看不到的名字。",
        "从我以前听过的东西里，找同年代同风格但我没听过的。",
        "不要按热度排，按我自己的口味顺序来挑。",
        "挑一两首最近发行、还很少有人听的。",
        "从我历史里出现过的歌手往外延伸一位，风格要接得上。",
        "这张歌单里放两三首冷门佳作，其余按我的口味来。",
        "用我历史里听得最多的那种情绪做主线，歌可以更旧或更新。",
    };

    private AiReference() {}

    /**
     * A random sample of {@code pool}, in random order — never a prefix of it.
     *
     * <p>The random draw is the load-bearing part: the same account's history, drawn twice, gives two
     * different prompts, and that is what makes two generations of the same request differ. Rows are
     * never repeated inside one sample.
     */
    public static List<String> sample(List<String> pool, int count, Random rnd) {
        List<String> out = new ArrayList<>();
        if (pool == null || pool.isEmpty() || count <= 0) return out;
        List<String> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, rnd);
        for (String row : copy) {
            if (out.size() >= count) break;
            if (row != null && !row.trim().isEmpty()) out.add(row.trim());
        }
        return out;
    }

    /** One random 切入角度 for this generation — see {@link #ANGLES}. */
    public static String angle(Random rnd) {
        return ANGLES[Math.floorMod(rnd.nextInt(), ANGLES.length)];
    }

    /** How many angles one generation is given. Two makes the draw noticeably different from run to
     *  run without turning the request into a different request. */
    public static final int ANGLES_PER_RUN = 2;

    /** {@link #ANGLES_PER_RUN} distinct angles, for the prompt. */
    public static List<String> angles(Random rnd) {
        List<String> out = new ArrayList<>(ANGLES_PER_RUN);
        for (int guard = 0; out.size() < ANGLES_PER_RUN && guard < 32; guard++) {
            String a = angle(rnd);
            if (!out.contains(a)) out.add(a);
        }
        return out;
    }

    /** The temperature this generation is sent at, inside
     *  [{@link #TEMPERATURE_MIN}, {@link #TEMPERATURE_MAX}]. */
    public static double temperature(Random rnd) {
        return TEMPERATURE_MIN + rnd.nextDouble() * (TEMPERATURE_MAX - TEMPERATURE_MIN);
    }

    /**
     * The prompt's reference block: <b>my listening history</b>, <b>the list that is playing</b> (only
     * for a continuation — empty otherwise) and <b>this run's angles</b>.
     *
     * <p>Kept in one place so that what the model is shown is readable in a single string, and so that
     * the order — history first, then the running list, then the instruction — is the same for every
     * generation.
     */
    public static String block(List<String> historySample, List<String> previousList, List<String> angles) {
        StringBuilder sb = new StringBuilder();
        if (historySample != null && !historySample.isEmpty()) {
            sb.append("我的听歌历史（最近播放的随机样本，仅用于判断我的口味和风格）：\n");
            for (String row : historySample) sb.append(row).append('\n');
        }
        if (previousList != null && !previousList.isEmpty()) {
            sb.append("\n上一张歌单（正在播放的老歌，用来延续同一风格；新歌单里既要有其中一部分老歌，"
                    + "也要有新的歌）：\n");
            for (String row : previousList) sb.append(row).append('\n');
        }
        if (angles != null && !angles.isEmpty()) {
            sb.append("\n本次的切入角度（必须体现在选曲里）：\n");
            for (String a : angles) sb.append(a).append('\n');
        }
        return sb.toString();
    }
}
