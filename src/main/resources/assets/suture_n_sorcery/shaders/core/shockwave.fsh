#version 330 core

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 v_uv;
in vec4 v_param; // r,g=center ; b=strength ; a=radius
out vec4 fragColor;

float ringMask(float d, float r, float w) {
    // 1 at the ring, 0 elsewhere
    return 1.0 - smoothstep(0.0, w, abs(d - r));
}

void main() {
    vec2 center = v_param.rg;
    float strength = clamp(v_param.a, 0.0, 1.0);
    float r = clamp(v_param.b, 0.0, 1.0);

    // aspect correction so ring stays circular on widescreen
    vec2 aspect = vec2(ScreenSize.x / ScreenSize.y, 1.0);
    vec2 dvec = (v_uv - center) * aspect;
    float d = length(dvec);

    // normalized direction (in aspect space)
    vec2 dir = (d > 1e-6) ? (dvec / d) : vec2(0.0);

    // ring look
    float width = 0.035;
    float m = ringMask(d, r, width);

    // "sonic boom" refraction: opposite directions inside vs outside the ring
    float sd = d - r; // signed distance from ring center
    float refraction = exp(-abs(sd) * 80.0) * -sign(sd);

    // amplitude (tweak this)
    float amp = refraction * max(strength, 0.001) * 0.03;

    // only apply where ring exists (keeps distortion localized)
    amp *= m;

    // convert back from aspect space and clamp
    vec2 uv2 = v_uv - (dir / aspect) * amp;
    uv2 = clamp(uv2, vec2(0.001), vec2(0.999));

    fragColor = texture(Sampler0, uv2);
}