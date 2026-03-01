#version 330

uniform sampler2D InSampler; // post effects use InSampler now :contentReference[oaicite:3]{index=3}

layout(std140) uniform HematicBoom {
    vec2  u_center;
    float u_radius;
    float u_width;
    float u_strength;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 p = uv - u_center;
    float d = length(p);

    float inner = u_radius - u_width;
    float outer = u_radius + u_width;

    float m = smoothstep(inner, u_radius, d) * (1.0 - smoothstep(u_radius, outer, d));
    vec2 dir = (d > 0.0001) ? (p / d) : vec2(0.0);

    vec2 warped = uv + dir * (m * u_strength);
    vec4 col = texture(InSampler, warped);

    // make the ring obvious for debugging
    col.rgb = mix(col.rgb, vec3(1.0, 0.15, 0.15), m * 0.75);

    fragColor = col;
}