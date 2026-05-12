#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 v_uv;
in vec4 v_param;

out vec4 fragColor;

void main() {
    vec2 center = v_param.rg;
    float radius = max(v_param.b, 0.001);
    float strength = v_param.a;

    vec2 aspect = vec2(ScreenSize.x / ScreenSize.y, 1.0);
    vec2 delta = (v_uv - center) * aspect;
    float dist = length(delta);
    float screenReach = smoothstep(0.0, 1.15, radius);
    float field = 1.0 - smoothstep(0.0, 1.45, dist);
    if (field <= 0.001) {
        discard;
    }

    vec2 dir = dist > 0.0001 ? delta / dist : vec2(0.0, 1.0);
    vec2 unscaledDir = dir / aspect;
    float ripple = sin((dist * 54.0) - GameTime * 1700.0) * 0.5 + 0.5;
    float pull = strength * field * screenReach * (0.018 + ripple * 0.011);

    vec2 refractedUv = clamp(v_uv - unscaledDir * pull, vec2(0.001), vec2(0.999));
    vec4 scene = texture(Sampler0, refractedUv);

    float alpha = clamp(strength * field * screenReach * 0.52, 0.0, 0.52);
    float luminance = dot(scene.rgb, vec3(0.299, 0.587, 0.114));
    float redMemory = smoothstep(0.02, 0.35, scene.r - max(scene.g, scene.b));
    vec3 desaturated = mix(vec3(luminance), scene.rgb, mix(0.42, 1.0, redMemory));
    vec3 darkened = desaturated * (1.0 - field * strength * 0.16);
    vec3 tint = vec3(0.24, 0.015, 0.035) * field * strength;
    vec3 tintedScene = mix(darkened, darkened + tint, clamp(field * strength * 0.55, 0.0, 0.55));
    fragColor = vec4(tintedScene, alpha);
}
