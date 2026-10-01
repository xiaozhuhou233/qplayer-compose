(function (root) {
  "use strict";

  const Source = root.LyricsBlossomSource;
  const C = Source.C;

  const VERTEX_SOURCE = `#version 300 es
    const vec2 positions[3] = vec2[3](
      vec2(-1.0, -1.0),
      vec2(3.0, -1.0),
      vec2(-1.0, 3.0)
    );

    void main() {
      gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);
    }
  `;

  const MESH_VERTEX_SOURCE = `#version 300 es
    layout(location = 0) in vec2 aRegular;
    layout(location = 1) in vec2 aMeshA;
    layout(location = 2) in vec2 aMeshB;

    uniform float uMeshMorph;
    uniform float uMeshStrength;
    uniform float uMeshExpansion;

    out vec2 vCoordinate;

    void main() {
      vec2 deformation = mix(aMeshA, aMeshB, uMeshMorph);
      vec2 warped = mix(aRegular, deformation, uMeshStrength);
      warped = (warped - 0.5) * uMeshExpansion + 0.5;
      gl_Position = vec4(warped.x * 2.0 - 1.0, 1.0 - warped.y * 2.0, 0.0, 1.0);
      vCoordinate = aRegular;
    }
  `;

  const BLUR_FRAGMENT_SOURCE = `#version 300 es
    precision highp float;

    uniform sampler2D uInput;
    uniform vec2 uTexelSize;
    uniform vec2 uDirection;
    uniform float uWeights[31];

    out vec4 outputColor;

    void main() {
      vec2 coordinate = gl_FragCoord.xy * uTexelSize;
      vec4 color = texture(uInput, coordinate) * uWeights[0];
      for (int index = 1; index <= 30; index += 1) {
        vec2 offset = uDirection * uTexelSize * float(index);
        color += texture(uInput, coordinate + offset) * uWeights[index];
        color += texture(uInput, coordinate - offset) * uWeights[index];
      }
      outputColor = color;
    }
  `;

  const COPY_FRAGMENT_SOURCE = `#version 300 es
    precision highp float;

    uniform sampler2D uInput;
    uniform vec2 uResolution;

    out vec4 outputColor;

    void main() {
      vec2 coordinate = gl_FragCoord.xy / uResolution;
      outputColor = texture(uInput, coordinate);
    }
  `;

  // Direct GLSL translation of embedded resource 0x7A9CBB58.
  const FRAGMENT_SOURCE = `#version 300 es
    precision highp float;

    uniform sampler2D uTexture0;
    uniform sampler2D uTexture1;
    uniform float uLerp1;
    uniform float uTime;
    uniform float uLowFreq;
    uniform float uMidFreq;
    uniform float uHighFreq;
    uniform float uHasLyric;
    uniform float uSaturation;
    uniform float uBlackScrimAlpha;
    uniform float uWhiteScrimAlpha;
    uniform float uDarkOverlayAlpha;
    uniform vec2 uResolution;

    in vec2 vCoordinate;

    out vec4 outputColor;

    const float SPEC_MIX_FACTOR = 0.1;
    const float SPEC_SCALE_COEFF = 0.33;
    const float SPEC_CONTRAST_COEFF = 0.076;
    const float SPEC_SAT_COEFF = 0.166;
    const float INSTANCE_SCRIM_STEP = 0.0076;
    const float LUM_R = 0.2126;
    const float LUM_G = 0.7152;
    const float LUM_B = 0.0722;
    const float PINCH_ZOOM_MIN = 1.0;
    const float TIME_SCALE_0 = 120.0;
    const float TIME_SCALE_1 = 70.0;
    const float TIME_SCALE_2 = 90.0;

    vec3 applySaturationMatrix(vec3 color, float saturation) {
      float inverseSaturation = 1.0 - saturation;
      float rLuma = inverseSaturation * LUM_R;
      float gLuma = inverseSaturation * LUM_G;
      float bLuma = inverseSaturation * LUM_B;
      return vec3(
        (rLuma + saturation) * color.r + gLuma * color.g + bLuma * color.b,
        rLuma * color.r + (gLuma + saturation) * color.g + bLuma * color.b,
        rLuma * color.r + gLuma * color.g + (bLuma + saturation) * color.b
      );
    }

    vec3 sampleArtwork(vec2 coordinate) {
      vec3 current = texture(uTexture0, coordinate).rgb;
      vec3 target = texture(uTexture1, coordinate).rgb;
      return min(mix(current, target, uLerp1), vec3(1.0));
    }

    vec4 calculateLayer(
      vec2 uv,
      vec2 center,
      float size,
      float rotation,
      int instanceId,
      float saturation,
      float blackScrim,
      float spectrumContrast
    ) {
      vec2 position = uv - center;
      float cosine = cos(rotation);
      float sine = sin(rotation);
      vec2 rotated = vec2(
        position.x * cosine - position.y * sine,
        position.x * sine + position.y * cosine
      );
      float halfSize = size * 0.5;
      if (abs(rotated.x) > halfSize || abs(rotated.y) > halfSize) {
        return vec4(0.0);
      }

      vec3 color = sampleArtwork(rotated / size - 0.5);
      float scrim = blackScrim + float(instanceId) * INSTANCE_SCRIM_STEP;
      color = mix(color, vec3(0.0), scrim);
      color = (color - 0.5) * spectrumContrast + 0.5;
      color = applySaturationMatrix(color, saturation);
      return vec4(color, 1.0);
    }

    vec3 backdrop(vec2 textureCoordinate) {
      float pinchMix = uHasLyric;
      float zoomScale = (1.0 - pinchMix) * 0.25 + PINCH_ZOOM_MIN;
      float ydx = uResolution.y / uResolution.x;
      float xdy = uResolution.x / uResolution.y;

      float spectrumMix = mix(uLowFreq, uMidFreq, SPEC_MIX_FACTOR);
      float spectrumScale = spectrumMix * spectrumMix * SPEC_SCALE_COEFF + 1.0;
      float spectrumContrast = uLowFreq * SPEC_CONTRAST_COEFF + 1.0;
      float saturation = uSaturation + uHighFreq * SPEC_SAT_COEFF
        + (1.0 - pinchMix) * 0.5;

      const float TWO_PI = 6.2831853;
      float angle0 = uTime * TWO_PI / TIME_SCALE_0;
      float angle1 = uTime * TWO_PI / TIME_SCALE_1;
      float angle2 = uTime * TWO_PI / TIME_SCALE_2 + 3.1415926;

      vec2 centered = textureCoordinate - 0.5;
      centered /= spectrumScale * zoomScale;
      vec2 uv = centered * 2.0;
      if (uResolution.x >= uResolution.y) uv.y *= ydx;
      else uv.x *= xdy;

      vec2 center0 = vec2(0.0);
      vec2 center1 = vec2(0.1 + cos(-angle1 * 0.5), sin(-angle1 * 0.5));
      vec2 center2 = vec2(-0.25, 0.15);

      vec4 layer0 = calculateLayer(
        uv, center0, 2.8, angle0, 0,
        saturation, uBlackScrimAlpha, spectrumContrast
      );
      vec4 layer1 = calculateLayer(
        uv, center1, 1.4, angle1, 1,
        saturation, uBlackScrimAlpha, spectrumContrast
      );
      vec4 layer2 = calculateLayer(
        uv, center2, 1.4, angle2, 2,
        saturation, uBlackScrimAlpha, spectrumContrast
      );

      vec3 result = vec3(0.0);
      result = layer0.rgb * layer0.a + result * (1.0 - layer0.a);
      result = layer2.rgb * layer2.a + result * (1.0 - layer2.a);
      result = layer1.rgb * layer1.a + result * (1.0 - layer1.a);
      result = mix(result, vec3(1.0), uWhiteScrimAlpha);
      return result * (1.0 - uDarkOverlayAlpha);
    }

    void main() {
      outputColor = vec4(backdrop(vCoordinate), 1.0);
    }
  `;

  function compileShader(gl, type, source) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      const message = gl.getShaderInfoLog(shader) || "Unknown shader compile error";
      gl.deleteShader(shader);
      throw new Error(message);
    }
    return shader;
  }

  function createProgram(gl, vertexSource = VERTEX_SOURCE, fragmentSource = FRAGMENT_SOURCE) {
    const vertex = compileShader(gl, gl.VERTEX_SHADER, vertexSource);
    const fragment = compileShader(gl, gl.FRAGMENT_SHADER, fragmentSource);
    const program = gl.createProgram();
    gl.attachShader(program, vertex);
    gl.attachShader(program, fragment);
    gl.linkProgram(program);
    gl.deleteShader(vertex);
    gl.deleteShader(fragment);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      const message = gl.getProgramInfoLog(program) || "Unknown shader link error";
      gl.deleteProgram(program);
      throw new Error(message);
    }
    return program;
  }

  function catmullRom(p0, p1, p2, p3, amount) {
    const amount2 = amount * amount;
    const amount3 = amount2 * amount;
    return 0.5 * (
      2 * p1
      + (-p0 + p2) * amount
      + (2 * p0 - 5 * p1 + 4 * p2 - p3) * amount2
      + (-p0 + 3 * p1 - 3 * p2 + p3) * amount3
    );
  }

  function controlPoint(points, size, x, y) {
    const column = Source.clamp(x, 0, size - 1);
    const row = Source.clamp(y, 0, size - 1);
    return points[row * size + column];
  }

  function sampleControlGrid(points, size, u, v) {
    const gridX = u * (size - 1);
    const gridY = v * (size - 1);
    const x = Math.floor(gridX);
    const y = Math.floor(gridY);
    const amountX = gridX - x;
    const amountY = gridY - y;
    const rowsX = new Float64Array(4);
    const rowsY = new Float64Array(4);

    for (let row = -1; row <= 2; row += 1) {
      const p0 = controlPoint(points, size, x - 1, y + row);
      const p1 = controlPoint(points, size, x, y + row);
      const p2 = controlPoint(points, size, x + 1, y + row);
      const p3 = controlPoint(points, size, x + 2, y + row);
      rowsX[row + 1] = catmullRom(p0[0], p1[0], p2[0], p3[0], amountX);
      rowsY[row + 1] = catmullRom(p0[1], p1[1], p2[1], p3[1], amountX);
    }

    return [
      catmullRom(rowsX[0], rowsX[1], rowsX[2], rowsX[3], amountY),
      catmullRom(rowsY[0], rowsY[1], rowsY[2], rowsY[3], amountY)
    ];
  }

  function buildMeshGeometry(meshData, variantIndex) {
    const size = meshData.gridSize;
    const segments = (size - 1) << meshData.subdivisionShift;
    const side = segments + 1;
    const vertices = new Float32Array(side * side * 6);
    const variant = meshData.variants[variantIndex];
    let vertexOffset = 0;

    for (let y = 0; y <= segments; y += 1) {
      const v = y / segments;
      for (let x = 0; x <= segments; x += 1) {
        const u = x / segments;
        const meshA = sampleControlGrid(meshData.regular, size, u, v);
        const meshB = sampleControlGrid(variant, size, u, v);
        vertices[vertexOffset++] = u;
        vertices[vertexOffset++] = v;
        vertices[vertexOffset++] = meshA[0];
        vertices[vertexOffset++] = meshA[1];
        vertices[vertexOffset++] = meshB[0];
        vertices[vertexOffset++] = meshB[1];
      }
    }

    const indices = new Uint16Array(segments * segments * 6);
    let indexOffset = 0;
    for (let y = 0; y < segments; y += 1) {
      for (let x = 0; x < segments; x += 1) {
        const topLeft = y * side + x;
        const topRight = topLeft + 1;
        const bottomLeft = topLeft + side;
        const bottomRight = bottomLeft + 1;
        indices[indexOffset++] = topLeft;
        indices[indexOffset++] = bottomLeft;
        indices[indexOffset++] = topRight;
        indices[indexOffset++] = topRight;
        indices[indexOffset++] = bottomLeft;
        indices[indexOffset++] = bottomRight;
      }
    }

    return { vertices, indices, indexCount: indices.length, side };
  }

  class DynamicBackground {
    constructor(canvas) {
      this.canvas = canvas;
      this.gl = canvas.getContext("webgl2", {
        alpha: false,
        antialias: false,
        depth: false,
        stencil: false,
        preserveDrawingBuffer: true,
        powerPreference: "high-performance"
      });
      this.context = this.gl ? null : canvas.getContext("2d", { alpha: false });
      this.program = null;
      this.locations = null;
      this.textures = [];
      this.blurProgram = null;
      this.blurLocations = null;
      this.copyProgram = null;
      this.copyLocations = null;
      this.offscreen = [];
      this.offscreenWidth = 1;
      this.offscreenHeight = 1;
      this.offscreenScale = 1;
      this.blurWeights = new Float32Array(31);
      this.meshData = root.LyricsBlossomBackgroundMesh;
      this.meshVariant = this.meshData
        ? Math.floor(Math.random() * this.meshData.variants.length)
        : 0;
      this.meshVao = null;
      this.meshVertexBuffer = null;
      this.meshIndexBuffer = null;
      this.meshIndexCount = 0;
      this.meshMorph = 0;
      this.artwork = null;
      this.video = null;
      this.motion = { tall: "", square: "" };
      this.objectUrl = null;
      this.revision = 0;
      this.pixelRatio = 1;
      this.logicalWidth = 1;
      this.logicalHeight = 1;
      this.phase = 0;
      this.lastTimestamp = null;
      this.animationFrame = 0;
      this.lastVideoTime = -1;
      this.spectrum = { low: 0, mid: 0, high: 0 };
      this.onFrame = timestamp => this.frame(timestamp);

      if (this.gl) this.initializeWebGL();
      this.resize();
      addEventListener("resize", () => this.resize());
    }

    initializeWebGL() {
      const gl = this.gl;
      if (!this.meshData) throw new Error("LyricBlossom background mesh data is missing");
      this.program = createProgram(gl, MESH_VERTEX_SOURCE, FRAGMENT_SOURCE);
      this.blurProgram = (() => {
        const vertex = compileShader(gl, gl.VERTEX_SHADER, VERTEX_SOURCE);
        const fragment = compileShader(gl, gl.FRAGMENT_SHADER, BLUR_FRAGMENT_SOURCE);
        const program = gl.createProgram();
        gl.attachShader(program, vertex);
        gl.attachShader(program, fragment);
        gl.linkProgram(program);
        gl.deleteShader(vertex);
        gl.deleteShader(fragment);
        if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
          throw new Error(gl.getProgramInfoLog(program) || "Blur shader link error");
        }
        return program;
      })();
      this.copyProgram = (() => {
        const vertex = compileShader(gl, gl.VERTEX_SHADER, VERTEX_SOURCE);
        const fragment = compileShader(gl, gl.FRAGMENT_SHADER, COPY_FRAGMENT_SOURCE);
        const program = gl.createProgram();
        gl.attachShader(program, vertex);
        gl.attachShader(program, fragment);
        gl.linkProgram(program);
        gl.deleteShader(vertex);
        gl.deleteShader(fragment);
        if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
          throw new Error(gl.getProgramInfoLog(program) || "Copy shader link error");
        }
        return program;
      })();
      const names = [
        "uTexture0", "uTexture1", "uLerp1", "uTime",
        "uLowFreq", "uMidFreq", "uHighFreq", "uHasLyric",
        "uSaturation", "uBlackScrimAlpha", "uWhiteScrimAlpha",
        "uDarkOverlayAlpha", "uResolution", "uMeshMorph",
        "uMeshStrength", "uMeshExpansion"
      ];
      this.locations = Object.fromEntries(names.map(name => [name, gl.getUniformLocation(this.program, name)]));
      this.blurLocations = {
        input: gl.getUniformLocation(this.blurProgram, "uInput"),
        texelSize: gl.getUniformLocation(this.blurProgram, "uTexelSize"),
        direction: gl.getUniformLocation(this.blurProgram, "uDirection"),
        weights: gl.getUniformLocation(this.blurProgram, "uWeights[0]")
      };
      this.copyLocations = {
        input: gl.getUniformLocation(this.copyProgram, "uInput"),
        resolution: gl.getUniformLocation(this.copyProgram, "uResolution")
      };
      const geometry = buildMeshGeometry(this.meshData, this.meshVariant);
      this.meshVao = gl.createVertexArray();
      this.meshVertexBuffer = gl.createBuffer();
      this.meshIndexBuffer = gl.createBuffer();
      this.meshIndexCount = geometry.indexCount;
      gl.bindVertexArray(this.meshVao);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.meshVertexBuffer);
      gl.bufferData(gl.ARRAY_BUFFER, geometry.vertices, gl.STATIC_DRAW);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.meshIndexBuffer);
      gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, geometry.indices, gl.STATIC_DRAW);
      for (let attribute = 0; attribute < 3; attribute += 1) {
        gl.enableVertexAttribArray(attribute);
        gl.vertexAttribPointer(attribute, 2, gl.FLOAT, false, 24, attribute * 8);
      }
      gl.bindVertexArray(null);
      gl.disable(gl.CULL_FACE);
      gl.disable(gl.DEPTH_TEST);
      this.textures = [this.createTexture(0), this.createTexture(1)];
      this.offscreen = [this.createRenderTarget(), this.createRenderTarget()];
      this.canvas.dataset.renderer = "source-webgl2-0x7a9cbb58-mesh";
      this.canvas.dataset.backgroundMeshVariant = String(this.meshVariant + 1);
      this.canvas.dataset.backgroundMeshGrid = `${geometry.side}x${geometry.side}`;
    }

    createTexture(unit) {
      const gl = this.gl;
      const texture = gl.createTexture();
      gl.activeTexture(gl.TEXTURE0 + unit);
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.MIRRORED_REPEAT);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.MIRRORED_REPEAT);
      gl.texImage2D(
        gl.TEXTURE_2D, 0, gl.RGBA,
        1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE,
        new Uint8Array([32, 37, 52, 255])
      );
      return texture;
    }

    createRenderTarget() {
      const gl = this.gl;
      const texture = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      const framebuffer = gl.createFramebuffer();
      gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      return { texture, framebuffer };
    }

    resizeRenderTargets() {
      const gl = this.gl;
      const maxDimension = Math.max(this.canvas.width, this.canvas.height);
      this.offscreenScale = Math.min(1, C.backgroundRenderMaxDimension / maxDimension);
      this.offscreenWidth = Math.max(1, Math.round(this.canvas.width * this.offscreenScale));
      this.offscreenHeight = Math.max(1, Math.round(this.canvas.height * this.offscreenScale));
      for (const target of this.offscreen) {
        gl.bindTexture(gl.TEXTURE_2D, target.texture);
        gl.texImage2D(
          gl.TEXTURE_2D, 0, gl.RGBA8,
          this.offscreenWidth, this.offscreenHeight, 0,
          gl.RGBA, gl.UNSIGNED_BYTE, null
        );
      }
      const sigma = Math.max(C.backgroundLogicalBlur * this.pixelRatio * this.offscreenScale, 0.001);
      let total = 0;
      for (let index = 0; index < this.blurWeights.length; index += 1) {
        const weight = Math.exp(-0.5 * (index * index) / (sigma * sigma));
        this.blurWeights[index] = weight;
        total += index === 0 ? weight : weight * 2;
      }
      for (let index = 0; index < this.blurWeights.length; index += 1) {
        this.blurWeights[index] /= total;
      }
      this.canvas.dataset.backgroundSurface = `${this.offscreenWidth}x${this.offscreenHeight}`;
      this.canvas.dataset.backgroundSigma = sigma.toFixed(4);
    }

    resize() {
      this.pixelRatio = Math.max(Number(root.devicePixelRatio) || 1, 1);
      this.logicalWidth = Math.max(1, root.innerWidth);
      this.logicalHeight = Math.max(1, root.innerHeight);
      this.canvas.width = Math.max(1, Math.round(this.logicalWidth * this.pixelRatio));
      this.canvas.height = Math.max(1, Math.round(this.logicalHeight * this.pixelRatio));
      this.canvas.dataset.pixelRatio = this.pixelRatio.toFixed(4);
      if (this.gl) this.resizeRenderTargets();
      this.render();
      this.selectMotion();
    }

    clear() {
      if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
      this.artwork = null;
      this.phase = 0;
      this.lastTimestamp = null;
      this.stopMotion();
      this.stop();
      this.render();
    }

    async setArtwork(blob) {
      if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = URL.createObjectURL(blob);
      const image = new Image();
      await new Promise((resolve, reject) => {
        image.onload = resolve;
        image.onerror = reject;
        image.src = this.objectUrl;
      });
      this.artwork = image;
      if (this.gl) {
        this.uploadSource(this.textures[0], image);
        this.uploadSource(this.textures[1], image);
      }
      this.render();
      this.start();
    }

    setSpectrum(low, mid, high) {
      this.spectrum.low = Source.clamp(Number(low) || 0, 0, 1);
      this.spectrum.mid = Source.clamp(Number(mid) || 0, 0, 1);
      this.spectrum.high = Source.clamp(Number(high) || 0, 0, 1);
    }

    setMotionSources(sources = {}) {
      this.motion = { tall: sources.tall || "", square: sources.square || "" };
      this.selectMotion();
    }

    selectMotion() {
      const aspect = this.logicalWidth / Math.max(this.logicalHeight, 1);
      const url = aspect <= C.backgroundAspectThreshold ? this.motion.tall : this.motion.square;
      if (!url) {
        this.stopMotion();
        return;
      }
      if (this.video?.src === url) return;
      this.stopMotion();
      const video = document.createElement("video");
      video.muted = true;
      video.loop = true;
      video.playsInline = true;
      video.crossOrigin = "anonymous";
      video.src = url;
      video.addEventListener("loadeddata", () => {
        video.play().catch(() => {});
        this.start();
      }, { once: true });
      this.video = video;
    }

    stopMotion() {
      if (!this.video) return;
      this.video.pause();
      this.video.removeAttribute("src");
      this.video.load();
      this.video = null;
      this.lastVideoTime = -1;
    }

    uploadSource(texture, source) {
      const gl = this.gl;
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
      gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, source);
    }

    start() {
      if (this.animationFrame || (!this.artwork && !this.video)) return;
      this.lastTimestamp = null;
      this.animationFrame = requestAnimationFrame(this.onFrame);
    }

    stop() {
      if (this.animationFrame) cancelAnimationFrame(this.animationFrame);
      this.animationFrame = 0;
    }

    frame(timestamp) {
      this.animationFrame = 0;
      if (!this.artwork && !this.video) return;
      const dt = this.lastTimestamp === null
        ? C.backgroundFallbackFrameMs
        : Math.min(Math.max(timestamp - this.lastTimestamp, 0), C.backgroundFrameLimitMs);
      this.lastTimestamp = timestamp;
      this.phase += C.backgroundPhaseRate * dt;
      this.render();
      this.animationFrame = requestAnimationFrame(this.onFrame);
    }

    render() {
      if (this.gl) this.renderWebGL();
      else this.renderFallback();
      this.revision += 1;
      this.canvas.dataset.revision = String(this.revision);
      this.canvas.dataset.phase = this.phase.toFixed(6);
    }

    meshMorphValue() {
      const wave = Math.sin(
        C.backgroundMeshCycleMultiplier
        * this.phase
        * C.backgroundMeshPhaseScale
        * Math.PI
      );
      const normalized = Math.acos(Source.clamp(wave, -1, 1)) / Math.PI;
      return normalized * normalized * (3 - 2 * normalized);
    }

    renderWebGL() {
      const gl = this.gl;
      const source = this.video && this.video.readyState >= 2 ? this.video : this.artwork;
      if (!source) {
        gl.bindFramebuffer(gl.FRAMEBUFFER, null);
        gl.viewport(0, 0, this.canvas.width, this.canvas.height);
        gl.clearColor(32 / 255, 37 / 255, 52 / 255, 1);
        gl.clear(gl.COLOR_BUFFER_BIT);
        return;
      }

      if (source === this.video && this.video.currentTime !== this.lastVideoTime) {
        this.lastVideoTime = this.video.currentTime;
        this.uploadSource(this.textures[0], this.video);
        this.uploadSource(this.textures[1], this.video);
      }

      gl.bindFramebuffer(gl.FRAMEBUFFER, this.offscreen[0].framebuffer);
      gl.viewport(0, 0, this.offscreenWidth, this.offscreenHeight);
      gl.clearColor(32 / 255, 37 / 255, 52 / 255, 1);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.useProgram(this.program);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, this.textures[0]);
      gl.activeTexture(gl.TEXTURE1);
      gl.bindTexture(gl.TEXTURE_2D, this.textures[1]);
      gl.uniform1i(this.locations.uTexture0, 0);
      gl.uniform1i(this.locations.uTexture1, 1);
      gl.uniform1f(this.locations.uLerp1, 0);
      gl.uniform1f(this.locations.uTime, this.phase * C.backgroundShaderTimeScale);
      gl.uniform1f(this.locations.uLowFreq, this.spectrum.low);
      gl.uniform1f(this.locations.uMidFreq, this.spectrum.mid);
      gl.uniform1f(this.locations.uHighFreq, this.spectrum.high);
      gl.uniform1f(this.locations.uHasLyric, document.querySelector("#lyrics .lyric-line") ? 1 : 0);
      gl.uniform1f(this.locations.uSaturation, C.backgroundSaturation);
      gl.uniform1f(this.locations.uBlackScrimAlpha, C.backgroundBlackScrimAlpha);
      gl.uniform1f(this.locations.uWhiteScrimAlpha, C.backgroundWhiteScrimAlpha);
      gl.uniform1f(this.locations.uDarkOverlayAlpha, C.backgroundDarkAlpha);
      gl.uniform2f(this.locations.uResolution, this.offscreenWidth, this.offscreenHeight);
      this.meshMorph = this.meshMorphValue();
      this.canvas.dataset.backgroundMeshMorph = this.meshMorph.toFixed(6);
      gl.uniform1f(this.locations.uMeshMorph, this.meshMorph);
      gl.uniform1f(this.locations.uMeshStrength, C.backgroundMeshStrength);
      gl.uniform1f(this.locations.uMeshExpansion, C.backgroundMeshExpansion);
      gl.bindVertexArray(this.meshVao);
      gl.drawElements(gl.TRIANGLES, this.meshIndexCount, gl.UNSIGNED_SHORT, 0);
      gl.bindVertexArray(null);

      this.drawBlurPass(this.offscreen[0].texture, this.offscreen[1].framebuffer, 1, 0);
      this.drawBlurPass(this.offscreen[1].texture, this.offscreen[0].framebuffer, 0, 1);

      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      gl.viewport(0, 0, this.canvas.width, this.canvas.height);
      gl.useProgram(this.copyProgram);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, this.offscreen[0].texture);
      gl.uniform1i(this.copyLocations.input, 0);
      gl.uniform2f(this.copyLocations.resolution, this.canvas.width, this.canvas.height);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.flush();
    }

    drawBlurPass(inputTexture, outputFramebuffer, directionX, directionY) {
      const gl = this.gl;
      gl.bindFramebuffer(gl.FRAMEBUFFER, outputFramebuffer);
      gl.viewport(0, 0, this.offscreenWidth, this.offscreenHeight);
      gl.useProgram(this.blurProgram);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, inputTexture);
      gl.uniform1i(this.blurLocations.input, 0);
      gl.uniform2f(this.blurLocations.texelSize, 1 / this.offscreenWidth, 1 / this.offscreenHeight);
      gl.uniform2f(this.blurLocations.direction, directionX, directionY);
      gl.uniform1fv(this.blurLocations.weights, this.blurWeights);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    }

    renderFallback() {
      const context = this.context;
      const width = this.canvas.width;
      const height = this.canvas.height;
      const source = this.video && this.video.readyState >= 2 ? this.video : this.artwork;
      context.fillStyle = "#202534";
      context.fillRect(0, 0, width, height);
      if (!source) return;
      const sourceWidth = source.videoWidth || source.naturalWidth || source.width;
      const sourceHeight = source.videoHeight || source.naturalHeight || source.height;
      const scale = Math.max(width / sourceWidth, height / sourceHeight);
      const drawWidth = sourceWidth * scale;
      const drawHeight = sourceHeight * scale;
      context.save();
      context.filter = `blur(${C.backgroundLogicalBlur * this.pixelRatio}px)`;
      context.drawImage(source, (width - drawWidth) * 0.5, (height - drawHeight) * 0.5, drawWidth, drawHeight);
      context.restore();
      context.fillStyle = `rgba(0,0,0,${C.backgroundDarkAlpha})`;
      context.fillRect(0, 0, width, height);
    }
  }

  root.LyricsBlossomDynamicBackground = Object.freeze({
    DynamicBackground,
    vertexSource: VERTEX_SOURCE,
    meshVertexSource: MESH_VERTEX_SOURCE,
    fragmentSource: FRAGMENT_SOURCE,
    blurFragmentSource: BLUR_FRAGMENT_SOURCE,
    buildMeshGeometry
  });
})(window);
