#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform vec4 UVTransform;

out vec4 vertexColor;
out vec2 texCoord0;
flat out ivec2 texCoord2;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    vec2 textureSize0 = vec2(textureSize(Sampler0, 0));
    texCoord0 = UV0 * UVTransform.zw + UVTransform.xy / textureSize0;
    texCoord2 = UV2;
}
