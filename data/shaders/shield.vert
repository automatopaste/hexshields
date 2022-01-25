#version 330

layout (location = 0) in vec2 vertex;
layout (location = 1) in mat4 modelViewInstanced;
layout (location = 5) in vec4 colorInstanced;
layout (location = 6) in vec4 color2Instanced;
layout (location = 7) in vec4 shieldData;
// random x, random y, fillmode, arc

out vec2 vCoord;
out vec4 color;
out vec4 color2;
out vec4 shield;

uniform mat4 projection;

void main() {
	gl_Position = projection * modelViewInstanced * vec4(vertex, 0.0, 1.0);
	vCoord = vertex;
	color = colorInstanced;
	color2 = color2Instanced;
	shield = shieldData;
}