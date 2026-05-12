#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

in vec4 vertexColor;
in vec2 screenCoord;
in vec3 viewNormal;

out vec4 fragColor;

void main() {
    float strength = vertexColor.a;
    float expansion = vertexColor.r;
    float rim = pow(1.0 - abs(viewNormal.z), 1.15);
    float shell = clamp(0.18 + rim * 0.82, 0.0, 1.0);
    if (shell <= 0.01 || strength <= 0.001) {
        discard;
    }

    vec2 uv = clamp(screenCoord, vec2(0.001), vec2(0.999));
    vec2 normalPush = normalize(viewNormal.xy + vec2(0.0001));
    float pull = strength * shell * (0.008 + expansion * 0.020);
    vec4 scene = texture(Sampler0, clamp(uv + normalPush * pull, vec2(0.001), vec2(0.999)));

    vec3 tint = vec3(0.32, 0.012, 0.045) * shell * strength;
    float alpha = clamp((0.18 + shell * 0.34) * strength, 0.0, 0.52);
    vec3 tintedScene = mix(scene.rgb, scene.rgb + tint, clamp(shell * strength * 0.38, 0.0, 0.38));
    fragColor = vec4(tintedScene, alpha);
}
