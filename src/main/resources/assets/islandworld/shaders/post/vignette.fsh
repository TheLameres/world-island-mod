#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform VignetteConfig {
    float Strength;
    float Radius;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// Затемнение к краям экрана: радиус и сила задаются из JSON пост-эффекта.
void main() {
    vec4 color = texture(InSampler, texCoord);
    vec2 centered = texCoord - 0.5;
    float dist = length(centered * vec2(OutSize.x / OutSize.y, 1.0));
    float factor = smoothstep(Radius, Radius + 0.45, dist);
    fragColor = vec4(mix(color.rgb, vec3(0.0), factor * Strength), 1.0);
}
