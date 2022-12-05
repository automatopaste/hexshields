#version 330

in vec2 vCoord;
in vec4 color;
in vec4 color2;
in vec4 shield;

out vec4 fColor;

const float PI = 3.14159265359;

// credit: MaxBittker from https://github.com/MaxBittker/glsl-voronoi-noise
const mat2 myt = mat2(.12121212, .13131313, -.13131313, .12121212);
const vec2 mys = vec2(1e4, 1e6);

vec2 rhash(vec2 fCoord) {
    fCoord *= myt;
    fCoord *= mys;
    return fract(fract(fCoord / mys) * fCoord);
}

vec3 hash(vec3 p) {
    return fract(sin(vec3(dot(p, vec3(1.0, 57.0, 113.0)),
    dot(p, vec3(57.0, 113.0, 1.0)),
    dot(p, vec3(113.0, 1.0, 57.0)))) *
    43758.5453);
}

float voronoi2d(const in vec2 point) {
    vec2 p = floor(point);
    vec2 f = fract(point);
    float res = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 b = vec2(i, j);
            vec2 r = vec2(b) - f + rhash(p + b);
            res += 1. / pow(dot(r, r), 8.);
        }
    }
    return pow(1. / res, 0.0625);
}

vec2 rotate(vec2 v, float angle) {
    float cs = cos(angle);
    float sn = sin(angle);
    return vec2(v.x * cs - v.y * sn, v.x * sn + v.y * cs);
}

void main() {
    const float jitter = 0.00125;
    vec2 n = vec2(vCoord.x * 2.0 - 1.0, vCoord.y * 2.0 - 1.0);
    n += (shield.xx * 2.0 - 1.0) * jitter;

    vec2 pc = vec2(atan(n.y, n.x), length(n));
    pc.y = asin(pc.y);
    vec2 loc = vec2(cos(pc.x), sin(pc.x))*pc.y;

    float dist = 2.0 * length(vCoord - vec2(0.5, 0.5));

    const float mult = 10.0;
    float eDist = voronoi2d(loc * mult);

    // Initiate the background to a white color, putting in some dark borders.
//    float hexColor = mix(shield.z, abs(shield.z - 1.0), smoothstep(0., .2, eDist * eDist * eDist));
    float hexColor = eDist;
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
    fColor.rgb = color.rgb;

    //ring color
    fColor += color2 * g * t * 0.25 * damp;
}