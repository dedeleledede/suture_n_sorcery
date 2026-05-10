#version 330 core

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 v_uv;
in vec4 v_param; // r,g=center ; b=radius ; a=strength
out vec4 fragColor;

float shellMask(float d, float r, float w) {
    return 1.0 - smoothstep(0.0, w, abs(d - r));
}

void main() {
    vec2 center = v_param.rg;
    float radius = clamp(v_param.b, 0.0, 1.0);
    float strength = clamp(v_param.a, 0.0, 1.0);

    vec2 aspect = vec2(ScreenSize.x / ScreenSize.y, 1.0);
    vec2 delta = (v_uv - center) * aspect;
    float d = length(delta);
    vec2 dir = d > 0.0001 ? delta / d : vec2(0.0);

    float inside = 1.0 - smoothstep(radius - 0.018, radius + 0.018, d);
    float rim = shellMask(d, radius, 0.026);
    float inner = shellMask(d, radius * 0.66, 0.018) * 0.45;
    float lens = max(rim, inner) * strength;

    float signedDistance = d - radius;
    float bend = exp(-abs(signedDistance) * 42.0) * -sign(signedDistance);
    float ripple = sin((d * 90.0) - (strength * 18.0)) * 0.0035;
    vec2 refractUv = v_uv - (dir / aspect) * ((bend * 0.034 + ripple) * lens);
    refractUv = clamp(refractUv, vec2(0.001), vec2(0.999));

    vec4 color = texture(Sampler0, refractUv);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    color.rgb = mix(color.rgb, vec3(gray), inside * strength * 0.18);
    color.rgb += vec3(0.26, 0.015, 0.025) * inside * strength * 0.28;
    color.rgb += vec3(0.9, 0.05, 0.08) * rim * strength * 0.38;

    fragColor = color;
}
