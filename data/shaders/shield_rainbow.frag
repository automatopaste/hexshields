#version 330

in vec2 vCoord;
in vec4 color;
in vec4 color2;
in vec4 shield;

out vec4 fColor;

#define FLAT_TOP_HEXAGON = true;
#define M_PI 3.1415926535897932384626433832795

const vec2 s = vec2(1.7320508, 1);
const float PI = 3.14159265359;

float hash21(vec2 p)
{
    return fract(sin(dot(p, vec2(141.13, 289.97)))*43758.5453);
}

float hex(in vec2 p)
{
    p = abs(p);

    return max(dot(p, s*.5), p.y); // Hexagon.
}

vec4 getHex(vec2 p)
{
    vec4 hC = floor(vec4(p, p - vec2(1, .5))/s.xyxy) + .5;

    vec4 h = vec4(p - hC.xy*s, p - (hC.zw + .5)*s);

    return dot(h.xy, h.xy) < dot(h.zw, h.zw)
    ? vec4(h.xy, hC.xy)
    : vec4(h.zw, hC.zw + .5);
}

vec2 rotate(vec2 v, float angle) {
    float cs = cos(angle);
    float sn = sin(angle);
    return vec2(v.x * cs - v.y * sn, v.x * sn + v.y * cs);
}

vec3 getHueColor(vec2 pos)
{
    float theta = 3.0 + 3.0 * atan(pos.x, pos.y) / M_PI;
    vec3 color = vec3(0.0);
    return clamp(abs(mod(theta + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

void main() {
    const float jitter = 0.00125;
    vec2 n = vec2(vCoord.x * 2.0 - 1.0, vCoord.y * 2.0 - 1.0);
    n += (shield.xx * 2.0 - 1.0) * jitter;

    vec2 pc = vec2(atan(n.y, n.x), length(n));
    pc.y = asin(pc.y);
    vec2 loc = vec2(cos(pc.x), sin(pc.x))*pc.y;

    float dist = 2.0 * length(vCoord - vec2(0.5, 0.5));

    const float mult = 24.0;
    vec4 h = getHex(loc * mult + s.yx - vec2(0.15, 0.2));

    // The beauty of working with hexagonal centers is that the relative edge distance will simply
    // be the value of the 2D isofield for a hexagon.
    float eDist = hex(h.xy); // Edge distance.

    // Initiate the background to a white color, putting in some dark borders.
    float hexColor = mix(shield.z, abs(shield.z - 1.0), smoothstep(0., .2, eDist * eDist * eDist));
    fColor.a = hexColor;

    const float frac = 0.97;
    float l = 1.0 - abs(dist - frac);
    l *= l * l * l;

    // edge ring visual highlight
    float g = 1.0 - (min(1.0, abs(dist - 0.005 - frac) / (1.0 - frac)));
    g = clamp(g, 0.0, 1.0);
    g *= g;
    // circle inside
    float ff = 1.0 - clamp(max(0.0, dist - frac) * 100.0, 0.0, 1.0);

    // shield angle
    vec2 nr = rotate(n, shield.y);
    float fa = abs(atan(nr.y, nr.x));
    fa *= 180.0 / PI;
    //normalised to 0..180 each side

    // shield arc
    float dev = shield.w * 0.5;
//    float dev = 180.0;
    float t = clamp(max(0.0, dev - fa) * 360.0, 0.0, 1.0);

    float numSectors = mult * 6.0;
    float interval = 180.0 / numSectors;

    float damp = clamp(max(0.0, dev - fa + 2.0) * 0.05, 0.0, 1.0);
    damp = clamp(damp + clamp(dev - 179.0, 0.0, 1.0), 0.0, 1.0);

    fColor.a *= l * ff * damp * color.a * 2.0;
//    fColor.rgb = color.rgb;
    fColor.rgb = getHueColor(vCoord);

    //ring color
    fColor += color2 * g * t * 0.25 * damp;
}