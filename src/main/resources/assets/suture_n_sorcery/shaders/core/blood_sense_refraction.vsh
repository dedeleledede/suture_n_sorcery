#version 330 core

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 v_uv;
out vec4 v_param;

void main() {
    v_uv = UV0;
    v_param = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
