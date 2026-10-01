(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.LyricsBlossomSource = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const TAU = Math.PI * 2;
  const LOG_ONE_PERCENT = -Math.log(0.01);
  const SOURCE = Object.freeze({
    lineRetarget: "FUN_140295f40",
    lineFrame: "FUN_140299c80",
    lineLayout: "FUN_14029a8f0",
    seek: "FUN_140290210",
    blur: "FUN_14029b510",
    manualScroll: "FUN_1402938e0",
    mergeSyllables: "FUN_140285840",
    syllableSync: "FUN_140285b90",
    wordGroups: "FUN_1402833d0",
    wordDraw: "FUN_140284bd0",
    wordBezier: "FUN_140286810/FUN_1402868a0",
    glassFrame: "FUN_140319860/FUN_14031a560",
    glassShader: "FUN_14031ce90",
    background: "FUN_1402b2890/FUN_1402a85c0/FUN_14027e490/FUN_1402a6c50/FUN_1402ca250",
    controls: "FUN_140157580/FUN_140157cd0/FUN_140158010"
  });

  const C = Object.freeze({
    cascadeStep: 0.05,
    opacityBlurDuration: 0.12,
    opacityBlurBezier: Object.freeze([0.33, 0, 0.20, 0.10]),
    wordBezier: Object.freeze([0.25, 0.10, 0.25, 1]),
    activeOpacity: 0.85,
    inactiveOpacity: 0.175,
    specialActiveOpacity: 0.50,
    activeScale: 1,
    inactiveScale: 0.98,
    specialActiveScale: 0.70,
    specialInactiveScale: 0.63,
    maxBlur: 6,
    blurBypass: 0.10,
    lineAnchorScaleOffset: 140,
    lineAnchorTop: 80,
    mainFontSize: 48,
    mainFontWeight: 700,
    auxiliaryFontSize: 26,
    auxiliaryFontWeight: 700,
    sourceLatinFontFamily: "LB SF Pro Display",
    sourceChineseFontFamily: "LB PingFang SC",
    sourceJapaneseFontFamily: "LB Hiragino Sans",
    trackTitleFontSize: 19,
    trackTitleFontWeight: 700,
    trackArtistFontSize: 16,
    trackArtistFontWeight: 400,
    playerTimeFontSize: 12,
    playerTimeFontWeight: 400,
    footerFontSize: 12,
    footerFontWeight: 400,
    optionalUiScale: 1.20,
    manualPointerRate: 64,
    manualLineRate: 4,
    interactionRate: 14,
    manualIdle: 1.5,
    wordResponseCap: 3,
    wordAuxResponseFactor: 1.25,
    wordDampingRatio: 1,
    wordHistoryLength: 80,
    wordGlowRadius: 5,
    wordGlowRange: Object.freeze([0.4, 30]),
    syllableEndPadding: 30,
    syllableFirstDuration: 0.25,
    syllableDirectFollow: 1 / 12,
    instrumentalBreakMinimum: 7,
    instrumentalBreakDotCount: 3,
    instrumentalBreakViewHeight: 40,
    instrumentalBreakStartOffset: 0.5,
    instrumentalBreakCountdownLead: 1,
    instrumentalBreakDotRadius: 6,
    instrumentalBreakDotSpacing: 8,
    instrumentalBreakDotStagger: 0.06,
    instrumentalBreakDotEntranceDuration: 0.8,
    instrumentalBreakDotIdleAlpha: 0.1,
    instrumentalBreakDotFillTail: 0.1,
    instrumentalBreakScaleDelay: 0.2,
    instrumentalBreakScaleTail: 0.4,
    instrumentalBreakEndLead: 1.8,
    instrumentalBreakEndGrowDuration: 1,
    instrumentalBreakEndFadeDuration: 0.3,
    instrumentalBreakEndShrinkDuration: 0.5,
    instrumentalBreakScaleMaximum: 1.3,
    instrumentalBreakScaleDrop: 1.1,
    instrumentalBreakEndBezier: Object.freeze([0.25, 0.10, 0.25, 1]),
    glassHitRadius: 15,
    glassBaseDiameter: 30,
    glassMenuWidth: 260,
    glassMenuHeight: 116,
    glassRenderPadding: 6,
    glassCanvasWidth: 305,
    glassCanvasHeight: 167,
    glassAnchorCanvasX: 24,
    glassAnchorCanvasY: 143,
    glassOpenButtonOffsetX: 145,
    glassOpenButtonOffsetY: -79,
    glassOpenMenuOffsetX: 30,
    glassOpenMenuOffsetY: -122,
    glassMenuRadius: 40,
    glassCornerSmoothing: 0.6,
    glassUnionSmoothing: 15,
    glassIdleSize: 0.8,
    glassHoverSize: 1.2,
    glassActiveSize: 0.5,
    glassUnionDepth: 0.25,
    glassOpenDuration: 0.42,
    glassOpenBezier: Object.freeze([0.32, 0.72, 0.24, 1]),
    glassCloseSizeDuration: 0.28,
    glassCloseRadiusDuration: 0.32,
    glassCloseBezier: Object.freeze([0.42, 0, 0.40, 1]),
    glassDotHideDuration: 0.08,
    glassDotHideBezier: Object.freeze([0, 0, 0.58, 1]),
    glassDotShowDuration: 0.14,
    glassDotShowBezier: Object.freeze([0.42, 0, 1, 1]),
    glassButtonDamping: 0.9,
    glassButtonResponse: 0.5,
    glassAnchorDamping: 0.6,
    glassAnchorResponse: 0.3,
    glassCloseAnchorResponse: 0.35,
    glassOpenAuxDamping: 1,
    glassOpenAuxResponse: 0.5,
    glassCloseAuxResponse: 0.3,
    glassBezelWidth: 15,
    glassThickness: 90,
    glassIor: 1.45,
    glassDisplacementFactor: 1,
    glassTint: Object.freeze([0.1, 0.1, 0.1, 0.06]),
    glassSpecularOpacity: 0.5,
    glassLightDirection: Object.freeze([0, -1]),
    glassSpecularStrength: 1,
    glassOppositeSpecularStrength: 0.35,
    glassSpecularSharpness: 2,
    glassSpecularFalloff: 1,
    glassSpecularWidth: 1,
    glassStrokeWidth: 1,
    glassStrokeOpacity: 0.5,
    glassStrokeOffsetY: 0.5,
    glassContentIor: 1.5,
    glassContentDepth: 80,
    glassContentDamping: 0.8,
    glassContentResponse: 0.35,
    glassContentEnvelope: 6,
    glassContentEffectDuration: 0.30,
    glassContentEffectBezier: Object.freeze([0, 0, 0.58, 1]),
    glassContentBlurOpen: 0,
    glassContentBlurClosed: 8,
    glassContentBlurBypass: 0.05,
    glassContentScaleOpen: 1,
    glassContentScaleClosed: 2,
    glassContentScaleBypass: 0.005,
    glassContentPivotX: 130,
    glassContentPivotY: 58,
    glassItemStep: 48,
    glassContentTop: 10,
    glassTextInset: 22,
    glassIconSize: 26,
    glassIconDrawSize: 17.25,
    glassIconCenterYOffset: -1,
    glassIconGap: 12,
    glassLyricsIconResource: 0xa5f35312,
    glassSettingsIconResource: 0x8d37a2f3,
    glassItemFontSize: 17,
    glassItemFontWeight: 400,
    glassHoverInsetX: 10,
    glassHoverInsetY: 3,
    glassHoverHeight: 42,
    glassHoverRadius: 48,
    glassTextBaseline: 29,
    glassDividerInset: 20,
    glassDotOffset: 6,
    glassDotRadius: 1.5,
    skipPreviousResource: 0xc16925cb,
    skipNextResource: 0x2dd8123d,
    skipOuterDamping: 0.55,
    skipOuterResponse: 0.4,
    skipOutgoingDamping: 1,
    skipOutgoingResponse: 0.12,
    skipLifetime: 0.7,
    skipShift: 40,
    skipIncomingOffset: 15,
    skipPivotY: 67,
    skipNextIncomingPivotX: 29.48,
    skipNextOutgoingPivotX: 108.54,
    skipPreviousIncomingPivotX: 104.52,
    skipPreviousOutgoingPivotX: 25.46,
    skipIncomingAlphaGain: 1.5,
    backgroundAspectThreshold: 0.75,
    backgroundDownsample: 0.5,
    backgroundLogicalBlur: 80,
    backgroundRenderMaxDimension: 480,
    backgroundBlurStart: 0.5,
    backgroundBlurFull: 0.8,
    backgroundMaskRadiusX: 0.6,
    backgroundMaskRadiusY: 110,
    backgroundMaskHold: 0.35,
    backgroundTopFeather: 20,
    backgroundPhaseRate: 0.13,
    backgroundShaderTimeScale: 1 / 60,
    backgroundMeshPhaseScale: 0.0015,
    backgroundMeshCycleMultiplier: 1,
    backgroundMeshStrength: 1,
    backgroundMeshExpansion: 1.06,
    backgroundMeshGridSize: 9,
    backgroundMeshSubdivisionShift: 3,
    backgroundFallbackFrameMs: 16.667,
    backgroundFrameLimitMs: 100,
    backgroundBlackScrimAlpha: 0.35,
    backgroundWhiteScrimAlpha: 0.095,
    backgroundSaturation: 1,
    backgroundDarkAlpha: 100 / 255
  });

  const clamp = (value, low, high) => Math.min(Math.max(value, low), high);
  const lerp = (start, end, amount) => start + (end - start) * amount;

  function responsePhysics(dampingRatio, response, mass = 1) {
    const omega = TAU / Math.max(response, 0.001);
    return { mass, stiffness: mass * omega * omega, damping: 2 * dampingRatio * mass * omega };
  }

  function springSample(initial, target, elapsed, dampingRatio, response, initialVelocity = 0) {
    const time = Math.max(0, elapsed);
    const omega = TAU / Math.max(response, 0.001);
    const displacement = initial - target;
    let relative;
    let velocity;
    if (dampingRatio < 1) {
      const decay = dampingRatio * omega;
      const damped = omega * Math.sqrt(Math.max(1 - dampingRatio * dampingRatio, 0));
      const coefficient = (initialVelocity + decay * displacement) / damped;
      const cosine = Math.cos(damped * time);
      const sine = Math.sin(damped * time);
      const envelope = Math.exp(-decay * time);
      relative = envelope * (displacement * cosine + coefficient * sine);
      velocity = envelope * (
        (-decay * displacement + damped * coefficient) * cosine
        + (-decay * coefficient - damped * displacement) * sine
      );
    } else if (dampingRatio === 1) {
      const coefficient = initialVelocity + omega * displacement;
      const envelope = Math.exp(-omega * time);
      relative = envelope * (displacement + coefficient * time);
      velocity = envelope * (coefficient - omega * (displacement + coefficient * time));
    } else {
      const root = Math.sqrt(dampingRatio * dampingRatio - 1);
      const first = -omega * (dampingRatio - root);
      const second = -omega * (dampingRatio + root);
      const coefficientA = (initialVelocity - second * displacement) / (first - second);
      const coefficientB = displacement - coefficientA;
      const firstTerm = coefficientA * Math.exp(first * time);
      const secondTerm = coefficientB * Math.exp(second * time);
      relative = firstTerm + secondTerm;
      velocity = first * firstTerm + second * secondTerm;
    }
    return { value: target + relative, velocity };
  }

  function physicsToResponse(physics) {
    const mass = Math.max(physics.mass, 1e-9);
    const stiffness = Math.max(physics.stiffness, 1e-9);
    const omega = Math.sqrt(stiffness / mass);
    return {
      dampingRatio: physics.damping / (2 * Math.sqrt(mass * stiffness)),
      response: TAU / omega
    };
  }

  class AnalyticSpring {
    constructor(value, physics) {
      this.value = value;
      this.velocity = 0;
      this.start = value;
      this.target = value;
      this.initialVelocity = 0;
      this.elapsed = 0;
      this.physics = { ...physics };
    }

    retarget(target, physics = this.physics) {
      this.start = this.value;
      this.initialVelocity = this.velocity;
      this.target = target;
      this.elapsed = 0;
      this.physics = { ...physics };
    }

    reset(value, target = value, physics = this.physics) {
      this.value = value;
      this.velocity = 0;
      this.start = value;
      this.target = target;
      this.initialVelocity = 0;
      this.elapsed = 0;
      this.physics = { ...physics };
    }

    update(dt) {
      this.elapsed += Math.max(dt, 0);
      const converted = physicsToResponse(this.physics);
      const sample = springSample(
        this.start,
        this.target,
        this.elapsed,
        converted.dampingRatio,
        converted.response,
        this.initialVelocity
      );
      this.value = sample.value;
      this.velocity = sample.velocity;
      return this.value;
    }
  }

  function cubicBezier(progress, points) {
    const [x1, y1, x2, y2] = points;
    const input = clamp(progress, 0, 1);
    let parameter = input;
    for (let index = 0; index < 8; index += 1) {
      const oneMinus = 1 - parameter;
      const estimated = 3 * oneMinus * oneMinus * parameter * x1
        + 3 * oneMinus * parameter * parameter * x2
        + parameter * parameter * parameter;
      const error = estimated - input;
      const derivative = 3 * oneMinus * oneMinus * x1
        + 6 * oneMinus * parameter * (x2 - x1)
        + 3 * parameter * parameter * (1 - x2);
      if (Math.abs(error) < 1e-6 || Math.abs(derivative) < 1e-6) break;
      parameter -= error / derivative;
    }
    parameter = clamp(parameter, 0, 1);
    const oneMinus = 1 - parameter;
    return 3 * oneMinus * oneMinus * parameter * y1
      + 3 * oneMinus * parameter * parameter * y2
      + parameter * parameter * parameter;
  }

  class CubicTween {
    constructor(value, duration = C.opacityBlurDuration, points = C.opacityBlurBezier) {
      this.value = value;
      this.start = value;
      this.target = value;
      this.elapsed = duration;
      this.duration = duration;
      this.points = points;
    }

    retarget(target) {
      if (target === this.target) return;
      this.start = this.value;
      this.target = target;
      this.elapsed = 0;
    }

    reset(value) {
      this.value = value;
      this.start = value;
      this.target = value;
      this.elapsed = this.duration;
    }

    update(dt) {
      this.elapsed += Math.max(dt, 0);
      const amount = cubicBezier(this.elapsed / Math.max(this.duration, 0.001), this.points);
      this.value = lerp(this.start, this.target, amount);
      return this.value;
    }
  }

  function gapDrivenLineSpring(interLineGap) {
    const amount = clamp((interLineGap - 0.20) / 0.55, 0, 1);
    return responsePhysics(lerp(0.90, 0.78, amount), lerp(0.48, 0.75, amount));
  }

  // FUN_1402923c0 stores the result of its whole-lyrics syllable scan in
  // view+0x32c.  FUN_140295f40 uses the fixed 1/100/18 spring when that byte
  // is clear, and only enters the gap-adaptive branch when it is set.
  function lineTransitionSpring(hasTimedSyllables, interLineGap) {
    return hasTimedSyllables
      ? gapDrivenLineSpring(interLineGap)
      : { mass: 1, stiffness: 100, damping: 18 };
  }

  function criticalEnvelopeSpring(mass, response) {
    const rate = LOG_ONE_PERCENT / Math.max(response, 0.01);
    return { mass, stiffness: mass * rate * rate, damping: 2 * mass * rate };
  }

  function retimeLineSpring(physics, lineEnd, playhead, continuous) {
    const remaining = lineEnd - playhead - 0.50;
    if (continuous) return { physics: criticalEnvelopeSpring(physics.mass, 0.30), branch: "continuous" };
    const naturalRate = Math.sqrt(physics.stiffness / physics.mass);
    const dampingRatio = physics.damping / (2 * Math.sqrt(physics.mass * physics.stiffness));
    const oldEnvelope = LOG_ONE_PERCENT / (naturalRate * clamp(dampingRatio, 0.10, 1));
    if (remaining < 0.80 && oldEnvelope - remaining < -0.05) {
      const response = Math.max(0.01, Math.max(remaining - 0.40, 0.30));
      return { physics: criticalEnvelopeSpring(physics.mass, response), branch: "remaining-time" };
    }
    return { physics: { ...physics }, branch: "unchanged" };
  }

  const isLargeSeek = (indexDelta, timeDelta) => Math.abs(indexDelta) > 3 || Math.abs(timeDelta) > 2;
  const isHardClockDiscontinuity = (oldTime, newTime) => (
    Math.abs(newTime - oldTime) >= 1.5
    || oldTime - newTime > 1
    || (oldTime > 5 && newTime < oldTime * 0.1)
    || Math.abs(newTime - oldTime) > 2
  );
  const seekSpring = (indexDelta, timeDelta) => isLargeSeek(indexDelta, timeDelta)
    ? { mass: 1, stiffness: 100, damping: 18 }
    : criticalEnvelopeSpring(2, 0.10);
  const cascadeDelay = (validDistance, disabled = false) => disabled
    ? 0
    : Math.max(validDistance - 1, 0) * C.cascadeStep;
  const blurRadius = distance => distance <= 0 ? 0 : clamp((distance - 0.25) * 1.25, 0, C.maxBlur);
  const desktopSelectedLineTop = uiScale => C.lineAnchorScaleOffset * Math.max(uiScale, 0) + C.lineAnchorTop;

  function firstOrder(current, target, dt, rate, snap = 0) {
    const value = current + (target - current) * Math.min(Math.max(dt, 0) * rate, 1);
    return snap > 0 && Math.abs(value - target) < snap ? target : value;
  }

  function validLineDistance(lines, first, second) {
    if (first < 0 || second < 0 || first === second) return 0;
    const low = Math.min(first, second);
    const high = Math.max(first, second);
    let count = 0;
    for (let index = low + 1; index <= high; index += 1) {
      const line = lines[index];
      if (!line || line.sourceFlag || line.specialSubitem || line.skipLayout || line.special) continue;
      count += 1;
    }
    return count;
  }

  function insertInstrumentalBreakRows(lines) {
    const result = [];
    let previousEnd = 0;
    for (const source of lines || []) {
      const start = Number(source?.start);
      const end = Number(source?.end);
      if (Number.isFinite(start) && !source?.special && !source?.skipLayout) {
        const gapDuration = start - previousEnd;
        if (gapDuration > C.instrumentalBreakMinimum) {
          const breakStart = previousEnd + C.instrumentalBreakStartOffset;
          const countdownDuration = Math.max(
            start - breakStart - C.instrumentalBreakCountdownLead,
            0
          );
          result.push(Object.freeze({
            start: breakStart,
            end: start,
            text: "",
            translation: "",
            words: Object.freeze([]),
            special: true,
            instrumentalBreak: true,
            skipInteraction: true,
            gapDuration,
            countdownStart: breakStart + C.instrumentalBreakCountdownLead,
            countdownStep: countdownDuration / C.instrumentalBreakDotCount
          }));
        }
        previousEnd = Number.isFinite(end) ? Math.max(end, start) : start;
      }
      result.push(source);
    }
    return result;
  }

  function sourceEaseInOut(progress) {
    const amount = clamp(progress, 0, 1);
    if (amount < 0.5) return 2 * amount * amount;
    const inverse = 2 - amount * 2;
    return 1 - inverse * inverse * 0.5;
  }

  function instrumentalBreakSample(source, playhead) {
    const start = Number(source?.start);
    const end = Number(source?.end);
    const time = Number(playhead);
    const hidden = Object.freeze({
      visible: false,
      scale: 1,
      dots: Object.freeze(new Array(C.instrumentalBreakDotCount).fill(0))
    });
    if (!Number.isFinite(start) || !Number.isFinite(end) || !Number.isFinite(time)
      || time < start || time > end) return hidden;

    const displayDuration = Math.max(end - start, 0.001);
    const scaleDuration = Math.max(displayDuration - C.instrumentalBreakScaleTail, 0.001);
    const scaleProgress = sourceEaseInOut(
      (time - start - C.instrumentalBreakScaleDelay) / scaleDuration
    );
    let scale = 1 + (C.instrumentalBreakScaleMaximum - 1) * scaleProgress;
    let endAlpha = 1;
    const endStart = end - C.instrumentalBreakEndLead;
    if (time > endStart) {
      const endElapsed = time - endStart;
      if (endElapsed < C.instrumentalBreakEndGrowDuration) {
        const startScaleProgress = sourceEaseInOut(
          (endStart - start - C.instrumentalBreakScaleDelay) / scaleDuration
        );
        const startScale = 1 + (C.instrumentalBreakScaleMaximum - 1) * startScaleProgress;
        const amount = cubicBezier(
          endElapsed / C.instrumentalBreakEndGrowDuration,
          C.instrumentalBreakEndBezier
        );
        scale = lerp(startScale, C.instrumentalBreakScaleMaximum, amount);
      } else {
        const collapse = sourceEaseInOut(
          (endElapsed - C.instrumentalBreakEndGrowDuration)
            / C.instrumentalBreakEndShrinkDuration
        );
        scale = C.instrumentalBreakScaleMaximum - C.instrumentalBreakScaleDrop * collapse;
      }
      const fade = sourceEaseInOut(
        (endElapsed - C.instrumentalBreakEndGrowDuration)
          / C.instrumentalBreakEndFadeDuration
      );
      endAlpha = 1 - fade;
    }

    const countdownStart = Number.isFinite(Number(source?.countdownStart))
      ? Number(source.countdownStart)
      : start + C.instrumentalBreakCountdownLead;
    const countdownStep = Math.max(
      Number(source?.countdownStep)
        || (end - countdownStart) / C.instrumentalBreakDotCount,
      0.001
    );
    const fillDuration = Math.max(countdownStep - C.instrumentalBreakDotFillTail, 0.001);
    const dots = new Array(C.instrumentalBreakDotCount).fill(0).map((_, index) => {
      const entrance = clamp(
        (time - start - index * C.instrumentalBreakDotStagger)
          / C.instrumentalBreakDotEntranceDuration,
        0,
        1
      );
      const fill = clamp(
        (time - countdownStart - index * countdownStep) / fillDuration,
        0,
        1
      );
      const value = lerp(C.instrumentalBreakDotIdleAlpha, 1, fill);
      return clamp(value * entrance * endAlpha, 0, 1);
    });
    return Object.freeze({ visible: true, scale: Math.max(scale, 0), dots: Object.freeze(dots) });
  }

  function utf8VisibleByteCount(text) {
    const bytes = typeof TextEncoder !== "undefined"
      ? new TextEncoder().encode(String(text))
      : Buffer.from(String(text), "utf8");
    let count = 0;
    for (const byte of bytes) if (byte !== 0x20) count += 1;
    return count;
  }

  function mergeTimedSyllables(words) {
    const groups = [];
    for (const word of words || []) {
      const start = Number(word.start);
      const end = Number(word.end);
      if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) continue;
      const previous = groups.at(-1);
      if (previous && previous.start === start && previous.end === end) {
        previous.text += String(word.text || "");
      } else {
        groups.push({ text: String(word.text || ""), start, end });
      }
    }
    return groups.map((group, index) => ({
      ...group,
      visibleByteCount: utf8VisibleByteCount(group.text),
      isLast: index === groups.length - 1,
      cumulativeAdvance: 0
    }));
  }

  function activeSyllableIndex(groups, playhead) {
    if (!groups.length || playhead < groups[0].start) return -1;
    if (playhead >= groups.at(-1).end) return groups.length;
    for (let index = groups.length - 1; index >= 0; index -= 1) {
      if (groups[index].start <= playhead) return index;
    }
    return -1;
  }

  function syllableLeadFactor(group, groupCount) {
    if (group.isLast) return 1;
    if (groupCount < 3) return 0.5;
    if (group.visibleByteCount < 3) return 0.25;
    return 0.12;
  }

  const syllableFillTarget = (group, count) => group.cumulativeAdvance
    + C.syllableEndPadding * syllableLeadFactor(group, count);

  class SyllableSync {
    constructor() { this.reset(); }

    reset() {
      this.current = 0;
      this.previous = 0;
      this.target = 0;
      this.elapsed = 0;
      this.duration = C.syllableFirstDuration;
      this.activeIndex = -2;
      this.animating = false;
    }

    update(groups, playhead, dt, directMode = false) {
      if (!groups.length) return this.current;
      const index = activeSyllableIndex(groups, playhead);
      if (directMode) {
        if (index < 0) this.target = index >= groups.length ? groups.at(-1).cumulativeAdvance + C.syllableEndPadding : 0;
        else {
          const group = groups[index];
          const progress = clamp((playhead - group.start) / Math.max(group.end - group.start, 0.001), 0, 1);
          const previousAdvance = index > 0 ? groups[index - 1].cumulativeAdvance : 0;
          this.target = lerp(previousAdvance, group.cumulativeAdvance, progress)
            + syllableLeadFactor(group, groups.length) * C.syllableEndPadding * progress;
        }
        const follow = 1 - Math.exp(-Math.max(dt, 0) / C.syllableDirectFollow);
        this.current = lerp(this.current, this.target, follow);
        this.previous = this.current;
        this.activeIndex = -2;
        return this.current;
      }

      let normalizedIndex = index;
      let target = 0;
      let duration = C.syllableFirstDuration;
      let group = null;
      if (index >= groups.length) {
        target = groups.at(-1).cumulativeAdvance + C.syllableEndPadding;
        normalizedIndex = -1;
      } else if (index >= 0) {
        group = groups[index];
        target = syllableFillTarget(group, groups.length);
        if (this.activeIndex !== -2) duration = Math.max(group.end - group.start, 0.001);
      }
      if (normalizedIndex !== this.activeIndex) {
        if (normalizedIndex > 0 && this.activeIndex === -1) this.current = groups[normalizedIndex - 1].cumulativeAdvance;
        this.previous = this.current;
        if (group && group.isLast && this.activeIndex !== -2) {
          const rawDelta = group.cumulativeAdvance - this.current;
          const targetDelta = target - this.current;
          if (rawDelta > 0 && rawDelta < targetDelta) duration *= Math.min(targetDelta / rawDelta, 3);
        }
        this.elapsed = 0;
        this.animating = true;
      }
      this.activeIndex = normalizedIndex;
      this.target = target;
      this.duration = duration;
      this.elapsed += Math.max(dt, 0);
      const amount = clamp(this.elapsed / Math.max(duration, 0.001), 0, 1);
      this.current = this.animating ? lerp(this.previous, target, amount) : target;
      this.animating = amount < 1;
      return this.current;
    }
  }

  function wordGroupTiming(lineDuration, groupCount, containsCjk) {
    if (groupCount <= 0) return { step: 0, tailLead: 0 };
    const average = lineDuration / groupCount;
    const factor = containsCjk ? 0.8 : 0.4;
    return { step: Math.min(factor * average, factor), tailLead: 2 * lineDuration / groupCount };
  }

  // FUN_140282970: the alternate motion path is reserved for a long ASCII syllable.
  function isLongAsciiSyllable(text, duration) {
    const value = String(text ?? "");
    for (let index = 0; index < value.length; index += 1) {
      const code = value.charCodeAt(index);
      const sourceWhitespace = code >= 0x09 && code <= 0x0d;
      if (!sourceWhitespace && (code < 0x20 || code > 0x7e)) return false;
    }
    return Number(duration) >= 1 && value.length > 7;
  }

  function graphemeSegments(text) {
    const value = String(text ?? "");
    if (typeof Intl === "object" && typeof Intl.Segmenter === "function") {
      const segmenter = new Intl.Segmenter(undefined, { granularity: "grapheme" });
      return [...segmenter.segment(value)].map(item => item.segment);
    }
    return Array.from(value);
  }

  const neutralWordSample = () => Object.freeze({
    pulse: 0,
    scale: 1,
    xOffset: 0,
    yLift: 0
  });

  // FUN_1402833d0 owns one 0x5f0 motion record per rendered grapheme.
  class WordGlyphMotion {
    constructor(index, count, duration, containsCjk) {
      this.index = index;
      this.count = count;
      this.duration = Math.max(Number(duration) || 0, 0.001);
      const baseResponse = Math.min(this.duration, C.wordResponseCap);
      this.start = new AnalyticSpring(0, responsePhysics(C.wordDampingRatio, baseResponse));
      this.lift = new AnalyticSpring(0, responsePhysics(C.wordDampingRatio, baseResponse * C.wordAuxResponseFactor));
      this.tail = new AnalyticSpring(0, responsePhysics(C.wordDampingRatio, baseResponse * C.wordAuxResponseFactor));
      const timing = wordGroupTiming(this.duration, count, containsCjk);
      this.startTrigger = (index + 1) * timing.step;
      this.tailTrigger = this.startTrigger + timing.tailLead;
      this.started = false;
      this.tailed = false;
      this.hasTimelineSample = false;
      this.history = new Array(C.wordHistoryLength);
      this.historyIndex = 0;
      this.historyCount = 0;
    }

    reset() {
      this.start.reset(0);
      this.lift.reset(0);
      this.tail.reset(0);
      this.started = false;
      this.tailed = false;
      this.hasTimelineSample = false;
      this.history.fill(undefined);
      this.historyIndex = 0;
      this.historyCount = 0;
    }

    update(localTime, dt, liftAmount) {
      if (localTime < 0) {
        if (this.started || this.tailed) this.reset();
        this.hasTimelineSample = true;
        return neutralWordSample();
      }
      if (!this.hasTimelineSample && localTime >= this.tailTrigger) {
        this.start.reset(1);
        this.lift.reset(1);
        this.tail.reset(1);
        this.started = true;
        this.tailed = true;
      }
      this.hasTimelineSample = true;
      if (!this.started && localTime >= this.startTrigger) {
        this.started = true;
        this.start.retarget(1);
        this.lift.retarget(1);
      }
      if (!this.tailed && localTime >= this.tailTrigger) {
        this.tailed = true;
        this.tail.retarget(1);
      }
      const springStart = this.start.update(dt);
      const springLift = this.lift.update(dt);
      const springTail = this.tail.update(dt);
      const pulse = springStart * (1 - springTail);
      const longness = clamp(this.duration, 1, 2) - 1;
      const scaleDelta = 0.14 * longness;
      const sample = Object.freeze({
        pulse,
        scale: 1 + scaleDelta * pulse,
        xOffset: (this.index - (this.count - 1) * 0.5) * 0.5 * pulse * scaleDelta,
        yLift: liftAmount * springLift + 1.5 * pulse * longness
      });
      this.history[this.historyIndex] = sample;
      this.historyIndex = (this.historyIndex + 1) % C.wordHistoryLength;
      this.historyCount = Math.min(this.historyCount + 1, C.wordHistoryLength);
      return sample;
    }
  }

  class WordGroupMotion {
    constructor(index, count, lineDuration, containsCjk, syllable = null) {
      this.index = index;
      this.count = count;
      this.lineDuration = lineDuration;
      this.startTime = Number(syllable?.start) || 0;
      this.duration = Math.max(Number(syllable?.duration) || Number(lineDuration) || 0, 0.001);
      this.graphemes = graphemeSegments(syllable?.text || "");
      this.longAsciiMotion = isLongAsciiSyllable(
        syllable?.text,
        this.duration
      );
      const animatedCount = this.graphemes.filter(grapheme => !/^\s+$/u.test(grapheme)).length;
      let animatedIndex = 0;
      this.glyphMotion = this.graphemes.map(grapheme => {
        if (/^\s+$/u.test(grapheme)) return null;
        const motion = new WordGlyphMotion(
          animatedIndex,
          Math.max(animatedCount, 1),
          this.duration,
          containsCjk
        );
        animatedIndex += 1;
        return motion;
      });
    }

    reset() {
      this.glyphMotion.forEach(motion => motion?.reset());
    }

    update(playhead, dt, liftAmount = 2) {
      const localTime = Number(playhead) - this.startTime;
      // FUN_140282970 routes long ASCII syllables to the glow draw path and
      // bypasses the ordinary per-grapheme transform block.
      if (this.longAsciiMotion) {
        return this.graphemes.map(() => neutralWordSample());
      }
      return this.glyphMotion.map(motion => motion
        ? motion.update(localTime, dt, liftAmount)
        : neutralWordSample());
    }
  }

  function glassButtonSizeTarget(isOpen, pressArmed, hovered) {
    if (isOpen || pressArmed) return C.glassActiveSize;
    return hovered ? C.glassHoverSize : C.glassIdleSize;
  }

  function superellipseRoundRectDistance(x, y, shape) {
    const halfWidth = (shape.x1 - shape.x0) * 0.5;
    const halfHeight = (shape.y1 - shape.y0) * 0.5;
    const cornerLimit = Math.min(halfWidth, halfHeight);
    const radius = Math.min(Math.max(shape.radius, 0), cornerLimit);
    const qx = Math.abs(x - (shape.x0 + shape.x1) * 0.5) - halfWidth + radius;
    const qy = Math.abs(y - (shape.y0 + shape.y1) * 0.5) - halfHeight + radius;
    const maxSmoothing = shape.radius > 0.0001 ? Math.max(cornerLimit / Math.max(shape.radius, 0.0001) - 1, 0) : 0;
    const smoothing = Math.min(clamp(shape.smoothing, 0, 1), maxSmoothing);
    const exponent = 2 + smoothing * 3.3333333;
    const corner = Math.pow(Math.pow(Math.max(qx, 0), exponent) + Math.pow(Math.max(qy, 0), exponent), 1 / exponent);
    return corner + Math.min(Math.max(qx, qy), 0) - radius;
  }

  function smoothUnionDistance(first, second, smoothing) {
    if (smoothing <= 0.0001) return Math.min(first, second);
    const progress = clamp(1 - Math.abs(first - second) / smoothing, 0, 1);
    return Math.min(first, second) - smoothing * C.glassUnionDepth * progress * progress;
  }

  function glassShapeGradient(x, y, shape) {
    const gx = superellipseRoundRectDistance(x + 1, y, shape)
      - superellipseRoundRectDistance(x - 1, y, shape);
    const gy = superellipseRoundRectDistance(x, y + 1, shape)
      - superellipseRoundRectDistance(x, y - 1, shape);
    const length = Math.hypot(gx, gy);
    return length > 0.0001
      ? Object.freeze({ x: gx / length, y: gy / length })
      : Object.freeze({ x: 0, y: -1 });
  }

  function glassSceneSample(x, y, firstShape, secondShape, smoothing) {
    const firstDistance = superellipseRoundRectDistance(x, y, firstShape);
    const secondDistance = superellipseRoundRectDistance(x, y, secondShape);
    const firstGradient = glassShapeGradient(x, y, firstShape);
    const secondGradient = glassShapeGradient(x, y, secondShape);
    const distance = smoothUnionDistance(firstDistance, secondDistance, smoothing);
    let gradient = firstDistance <= secondDistance ? firstGradient : secondGradient;

    if (smoothing > 0.0001) {
      const progress = clamp(1 - Math.abs(firstDistance - secondDistance) / smoothing, 0, 1);
      if (progress > 0.0001) {
        const derivative = clamp(2 * C.glassUnionDepth * progress, 0, 1);
        const firstWeight = firstDistance <= secondDistance ? 1 - derivative : derivative;
        const gx = firstGradient.x * firstWeight + secondGradient.x * (1 - firstWeight);
        const gy = firstGradient.y * firstWeight + secondGradient.y * (1 - firstWeight);
        const length = Math.hypot(gx, gy);
        gradient = length > 0.0001
          ? Object.freeze({ x: gx / length, y: gy / length })
          : Object.freeze({ x: 0, y: -1 });
      }
    }

    const fill = glassFillMask(distance);
    const inward = Math.max(-distance, 0);
    const bezelProgress = clamp(inward / Math.max(C.glassBezelWidth, 1), 0, 1);
    const u = 1 - bezelProgress;
    const derivative = 2 * u * u * u / Math.sqrt(Math.max(1 - u ** 4, 0.0001));
    const slope = inward <= C.glassBezelWidth
      ? Math.min(derivative, Math.tan(1.4835298))
      : 0;
    return Object.freeze({
      distance,
      fill,
      inward,
      gradient,
      surfaceSlope: Object.freeze({ x: gradient.x * slope, y: gradient.y * slope })
    });
  }

  const glassFillMask = distance => 1 - smoothstep(0, 1.4, distance);

  function skipIconFrame(direction, shiftProgress, outgoingProgress, incomingProgress) {
    const next = direction > 0;
    const sign = next ? 1 : -1;
    const incomingAlpha = Math.trunc(Math.min(incomingProgress * C.skipIncomingAlphaGain * 255, 255)) / 255;
    const outgoingAlpha = Math.trunc(outgoingProgress * 255) / 255;
    return {
      shift: {
        translateX: shiftProgress * C.skipShift * sign,
        opacity: 1
      },
      incoming: {
        pivotX: next ? C.skipNextIncomingPivotX : C.skipPreviousIncomingPivotX,
        pivotY: C.skipPivotY,
        scale: incomingProgress,
        offsetX: (1 - incomingProgress) * -C.skipIncomingOffset * sign,
        opacity: incomingAlpha
      },
      outgoing: {
        pivotX: next ? C.skipNextOutgoingPivotX : C.skipPreviousOutgoingPivotX,
        pivotY: C.skipPivotY,
        scale: outgoingProgress,
        opacity: outgoingAlpha
      }
    };
  }

  function refractedOffset(slopeX, slopeY, ior, depth, displacementFactor = 1) {
    const normalLength = Math.hypot(slopeX, slopeY, 1);
    const nx = slopeX / normalLength;
    const ny = slopeY / normalLength;
    const nz = 1 / normalLength;
    const eta = 1 / Math.max(ior, 1.0001);
    const incidentDotNormal = -nz;
    const refractK = Math.max(1 - eta * eta * (1 - incidentDotNormal * incidentDotNormal), 0);
    const refractFactor = eta * incidentDotNormal + Math.sqrt(refractK);
    const rayX = -refractFactor * nx;
    const rayY = -refractFactor * ny;
    const rayZ = -eta - refractFactor * nz;
    const scale = rayZ < 0 ? depth * displacementFactor / -rayZ : 0;
    return Object.freeze({ x: rayX * scale, y: rayY * scale });
  }

  function smoothstep(low, high, value) {
    const amount = clamp((value - low) / Math.max(high - low, 0.001), 0, 1);
    return amount * amount * (3 - 2 * amount);
  }

  const backgroundBlurFactor = (y, height) => {
    const start = height * C.backgroundBlurStart;
    const full = height * C.backgroundBlurFull;
    const linear = clamp((y - start) / Math.max(full - start, 0.001), 0, 1);
    return linear * linear;
  };

  function backgroundEllipseMask(x, y, width, height) {
    const dx = (x - width * 0.5) / Math.max(width * C.backgroundMaskRadiusX, 0.001);
    const dy = (y - height * 0.5) / C.backgroundMaskRadiusY;
    return smoothstep(C.backgroundMaskHold, 1, Math.sqrt(dx * dx + dy * dy));
  }

  return Object.freeze({
    SOURCE, C, clamp, lerp, responsePhysics, springSample, physicsToResponse,
    AnalyticSpring, CubicTween, cubicBezier, gapDrivenLineSpring, lineTransitionSpring,
    criticalEnvelopeSpring, retimeLineSpring, isLargeSeek,
    isHardClockDiscontinuity, seekSpring, cascadeDelay, blurRadius,
    firstOrder, desktopSelectedLineTop, validLineDistance, insertInstrumentalBreakRows,
    sourceEaseInOut, instrumentalBreakSample, utf8VisibleByteCount, mergeTimedSyllables,
    activeSyllableIndex, syllableLeadFactor, syllableFillTarget, SyllableSync,
    wordGroupTiming, isLongAsciiSyllable, graphemeSegments, WordGlyphMotion, WordGroupMotion,
    glassButtonSizeTarget,
    superellipseRoundRectDistance, smoothUnionDistance, glassShapeGradient, glassSceneSample, glassFillMask,
    skipIconFrame, refractedOffset, smoothstep, backgroundBlurFactor, backgroundEllipseMask
  });
});
