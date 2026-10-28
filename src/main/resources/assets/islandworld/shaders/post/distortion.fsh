#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DistortionConfig {
    float Amount;
    float Frequency;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// Волновое искажение UV: амплитуда Amount, частота Frequency. Без времени — эффект статичен на кадр.
void main() {
    vec2 uv = texCoord;
    uv.x += sin(uv.y * Frequency) * Amount;
    uv.y += cos(uv.x * Frequency) * Amount;
    fragColor = vec4(texture(InSampler, clamp(uv, 0.0, 1.0)).rgb, 1.0);
}
