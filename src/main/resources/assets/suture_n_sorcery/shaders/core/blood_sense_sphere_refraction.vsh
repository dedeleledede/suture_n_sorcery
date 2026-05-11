#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in vec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 screenCoord;
out vec3 viewNormal;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    vec4 clipPos = ProjMat * viewPos;

    gl_Position = clipPos;
    vertexColor = Color;
    screenCoord = clipPos.xy / clipPos.w * 0.5 + 0.5;
    viewNormal = normalize(mat3(ModelViewMat) * Normal);
}
