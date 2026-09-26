package dev.t1m3.qplayer.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

/**
 * Round 35's two policies: the AI DJ's <b>randomness</b> and its <b>references</b>.
 *
 * <p>What these pin, and why each one is here: the user asked for 「随机性」 (「ai 算法生成的歌曲要有随机性」),
 * so the history the model is shown must be a random DRAW and not a prefix — the same account, drawn
 * twice, has to give two different prompts — while a run with the same {@link Random} must still be
 * reproducible, because that is what makes the policy testable at all. And the reference block must
 * carry the history the user asked to be consulted (「推荐歌曲…还需要我的历史记录参考」) beside the list that
 * is playing.
 */
public class AiReferenceTest {

    private static List<String> pool(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add("歌" + i + " - 歌手" + i);
        return out;
    }

    @Test
    public void theHistorySampleIsADrawAndNotAPrefixOfIt() {
        List<String> history = pool(100);
        List<String> first = AiReference.sample(history, AiReference.HISTORY_SAMPLE, new Random(1));
        List<String> second = AiReference.sample(history, AiReference.HISTORY_SAMPLE, new Random(2));
        assertEquals("the sample is the size asked for", AiReference.HISTORY_SAMPLE, first.size());
        assertEquals("nothing is repeated inside one sample", first.size(),
                new HashSet<>(first).size());
        assertNotEquals("two generations must not be shown the same thing — that is the "
                + "randomness the user asked for", first, second);
        assertFalse("and it is not simply the newest rows: " + first,
                first.equals(history.subList(0, AiReference.HISTORY_SAMPLE)));
    }

    @Test
    public void theSameRandomDrawIsReproducible() {
        List<String> history = pool(100);
        assertEquals("a fixed seed is a fixed prompt, which is what makes this testable",
                AiReference.sample(history, 5, new Random(7)),
                AiReference.sample(history, 5, new Random(7)));
    }

    @Test
    public void aSampleNeverExceedsTheHistoryItIsDrawnFrom() {
        assertEquals(10, AiReference.sample(pool(10), 50, new Random(3)).size());
        assertTrue(AiReference.sample(pool(0), 5, new Random(3)).isEmpty());
        assertTrue(AiReference.sample(null, 5, new Random(3)).isEmpty());
        assertTrue(AiReference.sample(pool(10), 0, new Random(3)).isEmpty());
    }

    @Test
    public void everyGenerationGetsAnglesAndTheyAreDistinct() {
        List<String> a = AiReference.angles(new Random(11));
        assertEquals(AiReference.ANGLES_PER_RUN, a.size());
        assertEquals("no angle is repeated in one run", a.size(), new HashSet<>(a).size());
        for (String angle : a) assertFalse("an angle is a sentence, not a blank", angle.trim().isEmpty());
    }

    @Test
    public void theAngleVariesFromRunToRun() {
        Set<String> seen = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) seen.addAll(AiReference.angles(new Random(seed)));
        assertTrue("the offered angles have to actually rotate: " + seen.size(), seen.size() >= 5);
    }

    @Test
    public void theRequestIsSentWarm() {
        // ⚠️ One Random drawing many times, NOT one Random per seed: Java's `new Random(smallSeed)`
        // gives nearly identical FIRST draws for consecutive small seeds (measured: seeds 0…7 all
        // start at 0.89619… and 0.89617…), which says nothing about the policy — that is a property of
        // the LCG, not of this class. A single generator's successive draws are what a running app
        // actually sees, and they are spread over the whole band.
        Random rnd = new Random(42);
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            double t = AiReference.temperature(rnd);
            assertTrue("inside the warm band and off the transition chooser's 0.2: " + t,
                    t >= AiReference.TEMPERATURE_MIN && t <= AiReference.TEMPERATURE_MAX);
            assertTrue("and not the greedy 0.2 the other feature needs", t > 0.5);
            values.add(String.format(java.util.Locale.US, "%.3f", t));
        }
        assertTrue("and it varies from generation to generation: " + values.size(),
                values.size() > 100);
    }

    @Test
    public void theBlockCarriesTheHistoryTheRunningListAndTheAngles() {
        String block = AiReference.block(Arrays.asList("晴天 - 周杰伦", "Yellow - Coldplay"),
                Arrays.asList("老歌A - 某歌手"), Arrays.asList("找几首小众的。"));
        assertTrue(block, block.contains("我的听歌历史"));
        assertTrue(block, block.contains("晴天 - 周杰伦"));
        assertTrue(block, block.contains("上一张歌单"));
        assertTrue(block, block.contains("老歌A - 某歌手"));
        assertTrue(block, block.contains("本次的切入角度"));
        assertTrue(block, block.contains("找几首小众的。"));
    }

    @Test
    public void aFirstGenerationHasNoRunningListSection() {
        String block = AiReference.block(Arrays.asList("晴天 - 周杰伦"), null, null);
        assertTrue(block, block.contains("我的听歌历史"));
        assertFalse("a first generation is not told about a list that is not playing",
                block.contains("上一张歌单"));
        assertFalse(block.contains("本次的切入角度"));
    }
}
