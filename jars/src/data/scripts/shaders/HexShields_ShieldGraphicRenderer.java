package data.scripts.shaders;

import cmu.shaders.BaseRenderPlugin;
import cmu.shaders.ShaderProgram;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.awt.*;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

public class HexShields_ShieldGraphicRenderer extends BaseRenderPlugin {

    private static final List<String> INCLUDED_HULL_STYLES = new ArrayList<>();

    private final List<ShieldRendererData> drawTargets;
    private boolean fill;
    private float fillCounter;

    private FloatBuffer projectionBuffer;

    private FloatBuffer modelViewBuffer;
    private FloatBuffer colourBuffer;
    private FloatBuffer colour2Buffer;
    private FloatBuffer shieldBuffer;

    public HexShields_ShieldGraphicRenderer() {
        drawTargets = new ArrayList<>();

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

//            data.target.getShield().setInnerColor(new Color(0,0,0,0));
//            if (!useDefaultRing) data.target.getShield().setRingColor(new Color(0,0,0,0));

            data.target.getShield().setSkipRendering(true);
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
//                    ship.getShield().setInnerColor(new Color(0,0,0,0));
//                    if (!useDefaultRing) ship.getShield().setRingColor(new Color(0,0,0,0));
                    ship.getShield().setSkipRendering(true);
                }
            }
        }

        numElements = drawTargets.size();
    }

    @Override
    protected int[] initBuffers() {
        projectionBuffer = BufferUtils.createFloatBuffer(16);

        // vertices
        int verticesVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, verticesVBO);
        glBufferData(GL_ARRAY_BUFFER, VERTICES_BUFFER, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        int size = 2;
        glVertexAttribPointer(0, size, GL_FLOAT, false, size * Float.SIZE / Byte.SIZE, 0);

        // Create buffer for model view matrices
        final int modelViewVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, modelViewVBO);
        int start = 1;
        for (int i = 0; i < 4; i++) {
            glVertexAttribPointer(start, 4, GL_FLOAT, false, 4 * 4 * 4, i * 4 * 4);
            glVertexAttribDivisor(start, 1);
            glEnableVertexAttribArray(start);
            start++;
        }

        // Create buffer for colours
        final int colourVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, colourVBO);
        glVertexAttribPointer(5, 4, GL_FLOAT, false, 4 * Float.SIZE / Byte.SIZE, 0);
        glVertexAttribDivisor(5, 1);
        glEnableVertexAttribArray(5);

        final int colour2VBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, colour2VBO);
        glVertexAttribPointer(6, 4, GL_FLOAT, false, 4 * Float.SIZE / Byte.SIZE, 0);
        glVertexAttribDivisor(6, 1);
        glEnableVertexAttribArray(6);

        // Create buffer for offset vector
        final int shieldVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, shieldVBO);
        glVertexAttribPointer(7, 4, GL_FLOAT, false, 4 * Float.SIZE / Byte.SIZE, 0);
        glVertexAttribDivisor(7, 1);
        glEnableVertexAttribArray(7);

        return new int[] {
                verticesVBO,
                modelViewVBO,
                colourVBO,
                colour2VBO,
                shieldVBO
        };
    }

    @Override
    protected void populateUniforms(int glProgramID, CombatEngineLayers combatEngineLayers, ViewportAPI viewport) {
        projectionBuffer.clear();
        orthogonal(viewport.getVisibleWidth() / viewport.getViewMult(), viewport.getVisibleHeight() / viewport.getViewMult()).store(projectionBuffer);
        projectionBuffer.flip();
        int loc = glGetUniformLocation(glProgramID, "projection");
        glUniformMatrix4(loc, false, projectionBuffer);
    }

    @Override
    protected void updateBuffers(int[] buffers, CombatEngineLayers combatEngineLayers, ViewportAPI viewport) {
        numElements = drawTargets.size();

        // modelview
        modelViewBuffer = BufferUtils.createFloatBuffer(16 * numElements);
        //colour
        colourBuffer = BufferUtils.createFloatBuffer(4 * numElements);
        colour2Buffer = BufferUtils.createFloatBuffer(4 * numElements);
        //shield data
        shieldBuffer = BufferUtils.createFloatBuffer(4 * numElements);

        Matrix4f view = getViewMatrix(viewport);
        Random r = new Random();
        Vector4f shield = new Vector4f(r.nextFloat() * 2f - 1f, 0f, 0f, 0f);

        for (ShieldRendererData data : drawTargets) {
            Matrix4f modelView = getModelView(view, data.target);
//            Matrix4f modelView = new Matrix4f(view).scale(new Vector3f(1000f, 1000f, 1f));
            modelView.store(modelViewBuffer);

            Color c = data.inner;
            Color c2 = data.ring;
            Vector4f colour = new Vector4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);
            Vector4f colour2 = new Vector4f(c2.getRed() / 255f, c2.getGreen() / 255f, c2.getBlue() / 255f, c2.getAlpha() / 255f);
            colour.w *= data.alphaMult;
            colour2.w *= data.alphaMult;

            colour.store(colourBuffer);
            colour2.store(colour2Buffer);

            if (!Global.getCombatEngine().isPaused()) fillCounter += Global.getCombatEngine().getElapsedInLastFrame() * 0.2f;
            float z = (float) FastTrig.sin(fillCounter);

            float h = data.target.getHardFluxLevel();
            //h *= 0.5f;

            float l;
            if (fill) {
                l = 1f - h;
            } else {
                l = h;
            }
            l += z * 0.2f;
            l = MathUtils.clamp(l, 0f, 1f);

            shield.setZ(l);
            shield.setY((float) Math.toRadians(data.target.getFacing() - data.target.getShield().getFacing() - 90f));
            shield.setW(data.target.getShield().getActiveArc());

            shield.store(shieldBuffer);
        }

        modelViewBuffer.flip();
        colourBuffer.flip();
        colour2Buffer.flip();
        shieldBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, buffers[1]);
        glBufferData(GL_ARRAY_BUFFER, modelViewBuffer, GL_DYNAMIC_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, buffers[2]);
        glBufferData(GL_ARRAY_BUFFER, colourBuffer, GL_DYNAMIC_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, buffers[3]);
        glBufferData(GL_ARRAY_BUFFER, colour2Buffer, GL_DYNAMIC_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, buffers[4]);
        glBufferData(GL_ARRAY_BUFFER, shieldBuffer, GL_DYNAMIC_DRAW);
    }

    @Override
    protected void draw(CombatEngineLayers combatEngineLayers, ViewportAPI viewportAPI) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glDrawElementsInstanced(GL_TRIANGLES, INDICES_BUFFER, numElements);

        modelViewBuffer.clear();
        colourBuffer.clear();
        colour2Buffer.clear();
        shieldBuffer.clear();
    }

    @Override
    protected ShaderProgram initShaderProgram() {
        ShaderProgram program = new ShaderProgram();

        String vert, frag;
        try {
            vert = Global.getSettings().loadText(Global.getSettings().getString("HexShields_Vert"));

            String type = Global.getSettings().getString("HexShields_HexMode");
            if (type.equals("default")) {
                frag = Global.getSettings().loadText("data/shaders/shield.frag");
                fill = true;
            } else if (type.equals("nofill")) {
                frag = Global.getSettings().loadText("data/shaders/shield_nofill.frag");
                fill = false;
            } else {
                frag = Global.getSettings().loadText("data/shaders/shield_vanilla.frag");
                fill = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        program.createVertexShader(vert);
        program.createFragmentShader(frag);
        program.link();

        return program;
    }

    private boolean checkNonZeroAlpha(Color c) {
        return c.getAlpha() != 0;
    }

    private Matrix4f getModelView(Matrix4f view, ShipAPI ship) {
        Matrix4f matrix = new Matrix4f(view);

        Vector2f loc = ship.getShieldCenterEvenIfNoShield();
        matrix.translate(new Vector3f(loc.x, loc.y, 0f));
        matrix.rotate((float) Math.toRadians(ship.getFacing()) - 1.57079632678f, new Vector3f(0f, 0f, 1f));

        Vector2f size = new Vector2f(ship.getShieldRadiusEvenIfNoShield() * 2f, ship.getShieldRadiusEvenIfNoShield() * 2f);
        Vector2f offset = new Vector2f(ship.getShieldRadiusEvenIfNoShield(),ship.getShieldRadiusEvenIfNoShield());
        size.scale(1.01f);
        offset.scale(1.01f);

        matrix.translate(new Vector3f(-offset.x, -offset.y, 0f));
        matrix.scale(new Vector3f(size.x, size.y, 1f));

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
}
