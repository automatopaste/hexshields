#version 330

in vec2 vCoord;
in vec4 color;
in vec4 color2;
in vec4 shield;

out vec4 fColor;

#define FLAT_TOP_HEXAGON = true;

//#ifdef FLAT_TOP_HEXAGON
const vec2 s = vec2(1.7320508, 1);
//#else
//const vec2 s = vec2(1, 1.7320508);
//#endif

const float PI = 3.14159265359;

float hash21(vec2 p)
{
    return fract(sin(dot(p, vec2(141.13, 289.97)))*43758.5453);
}

float hex(in vec2 p)
{
    p = abs(p);

//    #ifdef FLAT_TOP_HEXAGON
    return max(dot(p, s*.5), p.y); // Hexagon.
//    #else
//    return max(dot(p, s*.5), p.x); // Hexagon.
//    #endif
}

vec4 getHex(vec2 p)
{
//    #ifdef FLAT_TOP_HEXAGON
    vec4 hC = floor(vec4(p, p - vec2(1, .5))/s.xyxy) + .5;
//    #else
//    vec4 hC = floor(vec4(p, p - vec2(.5, 1))/s.xyxy) + .5;
//    #endif

    vec4 h = vec4(p - hC.xy*s, p - (hC.zw + .5)*s);

    return dot(h.xy, h.xy) < dot(h.zw, h.zw)
    ? vec4(h.xy, hC.xy)
    : vec4(h.zw, hC.zw + .5);
}

void main() {
    vec2 n = vec2(vCoord.x * 2.0 - 1.0, vCoord.y * 2.0 - 1.0);
    n += (shield.xy * 2.0 - 1.0) * 0.00175;

    vec2 pc = vec2(atan(n.y, n.x), length(n));
    pc.y = asin(pc.y);
    vec2 loc = vec2(cos(pc.x), sin(pc.x))*pc.y;

    float dist = 2.0 * length(vCoord - vec2(0.5, 0.5));

    const float mult = 13.0;
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
    float fa = abs(atan(n.y, n.x));
    fa *= 180.0 / PI;
    //normalised to 0..180 each side

    // shield arc
    float dev = shield.w * 0.5;
    float t = clamp(max(0.0, dev - fa) * 360.0, 0.0, 1.0);

    float numSectors = mult * 6.0;
    float interval = 180.0 / numSectors;
//    float ii = floor(fa / interval); //integers 0..numSectors
    float shieldLag = shield.w * 0.5;
//    if (shieldLag < (ii + 1.0) * interval) discard;

//    float damp = clamp(max(0.0, shieldLag - ((ii + 1.0) * interval)) / interval * 0.5, 0.0, 1.0);
    float damp = clamp(max(0.0, shieldLag - fa) * 0.1, 0.0, 1.0);
    if (fa > 180.00 - interval * 10.0 && shieldLag > fa) damp = 1.0;

    fColor.a *= l * ff * damp;
    fColor.rgb = color.rgb;

    //ring color
    fColor += color2 * g * t * 0.5 * damp;
}