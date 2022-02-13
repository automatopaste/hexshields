package data.scripts.shaders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import data.scripts.shaders.util.HexShields_InstancedRenderingPlugin;
import data.scripts.shaders.util.HexShields_ShaderProgram;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.awt.*;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/**
 * Super efficient instanced shader for shield systems
 */
public class HexShields_ShieldGraphicsShaderPlugin extends HexShields_InstancedRenderingPlugin {

    private static final List<String> INCLUDED_HULL_STYLES = new ArrayList<>();

    private final List<ShieldRendererData> drawTargets;
    private final boolean fill;
    private final boolean useDefaultRing;

    public HexShields_ShieldGraphicsShaderPlugin() {
        drawTargets = new ArrayList<>();
        fill = Global.getSettings().getBoolean("HexShields_HexFill");
        useDefaultRing = Global.getSettings().getBoolean("HexShields_UseDefaultRing");

        try {
            JSONArray data = Global.getSettings().getMergedSpreadsheetDataForMod("hullstyle_id", "data/config/hex_whitelist.csv", "HexShields");
            for (int i = 0; i < data.length(); i++) {
                JSONObject row = data.getJSONObject(i);
                String style = row.getString("hullstyle_id");
                if (style != null && !style.startsWith("#")) INCLUDED_HULL_STYLES.add(style);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);
    }

    @Override
    public void advance(float amount) {
        for (Iterator<ShieldRendererData> iterator = drawTargets.iterator(); iterator.hasNext();) {
            ShieldRendererData data = iterator.next();

            // shield fadeout check
            if (data.target.getShield().isOff()) data.alphaMult -= amount * 2f;
            else data.alphaMult = 1f;

            if (!Global.getCombatEngine().isEntityInPlay(data.target) || !data.target.isAlive() || data.target.isHulk() || data.alphaMult <= 0f) {
                data.target.getShield().setRingColor(data.target.getHullSpec().getShieldSpec().getRingColor());
                data.target.getShield().setInnerColor(data.target.getHullSpec().getShieldSpec().getInnerColor());
                iterator.remove();
            }

            Color i = data.target.getShield().getInnerColor();
            if (checkNonZeroAlpha(i)) data.inner = new Color(i.getRed(), i.getGreen(), i.getBlue(), i.getAlpha());
            Color r = data.target.getShield().getRingColor();
            if (checkNonZeroAlpha(r)) data.ring = new Color(r.getRed(), r.getGreen(), r.getBlue(), r.getAlpha());

            data.target.getShield().setInnerColor(new Color(0,0,0,0));
            if (!useDefaultRing) data.target.getShield().setRingColor(new Color(0,0,0,0));
        }

        out:
        for (ShipAPI ship : Global.getCombatEngine().getShips()) {
            if (ship.getShield() != null) {
                if (ship.getShield().isOn() && INCLUDED_HULL_STYLES.contains(ship.getHullStyleId())) {
                    for (ShieldRendererData data : drawTargets) if (data.target.equals(ship)) {
                        continue out;
                    }

                    drawTargets.add(new ShieldRendererData(ship, ship.getHullSpec().getShieldSpec().getInnerColor(), ship.getHullSpec().getShieldSpec().getRingColor()));

                    //avoid single frame shield render
                    ship.getShield().setInnerColor(new Color(0,0,0,0));
                    if (!useDefaultRing) ship.getShield().setRingColor(new Color(0,0,0,0));
                }
            }
        }

//        String s = " ";
//        if (drawTargets.size() > 0) s += Arrays.toString(drawTargets.get(0).inner.getRGBColorComponents(new float[3]));
//        Global.getCombatEngine().maintainStatusForPlayerShip(this, null, "draw targets", "" + drawTargets.size() + s, true);
    }

    @Override
    protected void populateUniformsOnFrame(int numElements, int glProgramID) {
        FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
        ViewportAPI viewport = Global.getCombatEngine().getViewport();
        orthogonal(viewport.getVisibleWidth() / viewport.getViewMult(), viewport.getVisibleHeight() / viewport.getViewMult()).store(projectionBuffer);
        projectionBuffer.flip();
        int loc = glGetUniformLocation(glProgramID, "projection");
        glUniformMatrix4(loc, false, projectionBuffer);
    }

    @Override
    protected BufferDataRelation[] populateBuffersOnFrame(int numElements) {
        final int numBuffers = 5;
        BufferDataRelation[] bufferDataRelations = new BufferDataRelation[numBuffers];

        //
        // BUFFER INITIALISATION
        //

        // Create buffer for vertices
        final float[] vertices = new float[] {
                0f, 1f,
                1f, 0f,
                0f, 0f,

                0f, 1f,
                1f, 1f,
                1f, 0f,
        };
        FloatBuffer verticesBuffer = BufferUtils.createFloatBuffer(vertices.length);
        verticesBuffer.put(vertices).flip();

        final int verticesVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, verticesVBO);
        glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        final int size = 2;
        glVertexAttribPointer(0, size, GL_FLOAT, false, size * Float.SIZE / Byte.SIZE, 0);

        bufferDataRelations[0] = new BufferDataRelation(verticesBuffer, verticesVBO);

        // Create buffer for model view matrices
        final int modelViewVBO = glGenBuffers();
        FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16 * numElements);
        glBindBuffer(GL_ARRAY_BUFFER, modelViewVBO);
        int start = 1;
        for (int i = 0; i < 4; i++) {
            glVertexAttribPointer(start, 4, GL_FLOAT, false, 4 * 4 * 4, i * 4 * 4);
            glVertexAttribDivisor(start, 1);
            glEnableVertexAttribArray(start);
            start++;
        }
        bufferDataRelations[1] = new BufferDataRelation(modelViewBuffer, modelViewVBO);

        // Create buffer for colours
        final int colourVBO = glGenBuffers();
        FloatBuffer colourBuffer = BufferUtils.createFloatBuffer(4 * numElements);
        glBindBuffer(GL_ARRAY_BUFFER, colourVBO);
        glVertexAttribPointer(5, 4, GL_FLOAT, false, 4 * Float.SIZE / Byte.SIZE, 0);
        glVertexAttribDivisor(5, 1);
        glEnableVertexAttribArray(5);
        bufferDataRelations[2] = new BufferDataRelation(colourBuffer, colourVBO);

        final int colour2VBO = glGenBuffers();
        FloatBuffer colour2Buffer = BufferUtils.createFloatBuffer(4 * numElements);
        glBindBuffer(GL_ARRAY_BUFFER, colour2VBO);
        glVertexAttribPointer(6, 4, GL_FLOAT, false, 4 * Float.SIZE / Byte.SIZE, 0);
        glVertexAttribDivisor(6, 1);
        glEnableVertexAttribArray(6);
        bufferDataRelations[3] = new BufferDataRelation(colour2Buffer, colour2VBO);

        // Create buffer for offset vector
        final int shieldVBO = glGenBuffers();
        FloatBuffer shieldBuffer = BufferUtils.createFloatBuffer(4 * numElements);
        glBindBuffer(GL_ARRAY_BUFFER, shieldVBO);
        glVertexAttribPointer(7, 4, GL_FLOAT, false, 4 * Float.SIZE / Byte.SIZE, 0);
        glVertexAttribDivisor(7, 1);
        glEnableVertexAttribArray(7);
        bufferDataRelations[4] = new BufferDataRelation(shieldBuffer, shieldVBO);

        //
        // FILL INSTANCE SPECIFIC BUFFERS
        //

        modelViewBuffer.clear();
        colourBuffer.clear();
        colour2Buffer.clear();
        shieldBuffer.clear();

        ViewportAPI viewport = Global.getCombatEngine().getViewport();
        Random r = new Random();
        Vector4f shield = new Vector4f(r.nextFloat() * 2f - 1f, r.nextFloat() * 2f - 1f, 0f, 0f);

        for (ShieldRendererData data : drawTargets) {
            Matrix4f modelView = getModelViewMatrix(data.target, viewport);
            modelView.store(modelViewBuffer);

            Color c = data.inner;
            Color c2 = data.ring;
            Vector4f colour = new Vector4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);
            Vector4f colour2 = new Vector4f(c2.getRed() / 255f, c2.getGreen() / 255f, c2.getBlue() / 255f, c2.getAlpha() / 255f);
            colour.w *= data.alphaMult;
            colour2.w *= data.alphaMult;

            colour.store(colourBuffer);
            colour2.store(colour2Buffer);

            float z = (fill) ? 1f : 0f;
            shield.setZ(z);
            shield.setW(data.target.getShield().getActiveArc());

            shield.store(shieldBuffer);
        }

        modelViewBuffer.flip();
        colourBuffer.flip();
        colour2Buffer.flip();
        shieldBuffer.flip();

        return bufferDataRelations;
    }

    @Override
    protected void preDraw() {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    protected int getNumElementsFrame() {
        return drawTargets.size();
    }

    @Override
    protected HexShields_ShaderProgram initShaderProgram() {
        HexShields_ShaderProgram program = new HexShields_ShaderProgram();

        String vert, frag;
        try {
            vert = Global.getSettings().loadText("data/shaders/shield.vert");
            frag = Global.getSettings().loadText("data/shaders/shield.frag");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        program.createVertexShader(vert);
        program.createFragmentShader(frag);
        program.link();

        return program;
    }

    @Override
    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    private Matrix4f getModelViewMatrix(ShipAPI ship, ViewportAPI viewport) {
        float viewMult = viewport.getViewMult();
        Matrix4f matrix = new Matrix4f();

        matrix.setIdentity();

        //view
        matrix.translate(new Vector3f(viewport.getVisibleWidth() / (2f * viewMult), viewport.getVisibleHeight() / (2f * viewMult), 0f));
        matrix.scale(new Vector3f(1f / viewport.getViewMult(), 1f / viewport.getViewMult(), 1f));
        matrix.translate(new Vector3f(-viewport.getCenter().x, -viewport.getCenter().y, 0f));

        //model
        Vector2f loc = ship.getShieldCenterEvenIfNoShield();
        matrix.translate(new Vector3f(loc.x, loc.y, 0f));
        matrix.rotate((float) Math.toRadians(ship.getShield().getFacing()), new Vector3f(0f, 0f, 1f));

        Vector2f size = new Vector2f(ship.getShieldRadiusEvenIfNoShield() * 2f, ship.getShieldRadiusEvenIfNoShield() * 2f);
        Vector2f offset = new Vector2f(ship.getShieldRadiusEvenIfNoShield(),ship.getShieldRadiusEvenIfNoShield());
        size.scale(1.01f);
        offset.scale(1.01f);

        matrix.translate(new Vector3f(-offset.x, -offset.y, 0f));
        matrix.scale(new Vector3f(size.x, size.y, 1f));

        return matrix;
    }

    private Matrix4f orthogonal(float right, float top) {
        Matrix4f matrix = new Matrix4f();

        float left = 0f;
        float bottom = 0f;
        float zNear = -100f;
        float zFar = 100f;

        matrix.m00 = 2f / (right - left);

        matrix.m11 = 2f / (top - bottom);
        matrix.m22 = 2f / (zNear - zFar);

        matrix.m30 = -(right + left) / (right - left);
        matrix.m31 = -(top + bottom) / (top - bottom);
        matrix.m32 = -(zFar + zNear) / (zFar - zNear);

        matrix.m33 = 1f;

        return matrix;
    }

    public static class ShieldRendererData {
        public ShipAPI target;
        public Color inner;
        public Color ring;
        public float alphaMult;

        public ShieldRendererData(ShipAPI target, Color inner, Color ring) {
            this.target = target;
            this.inner = inner;
            this.ring = ring;
            alphaMult = 1f;
        }
    }

    private boolean checkNonZeroAlpha(Color c) {
        return c.getAlpha() != 0;
    }
}
