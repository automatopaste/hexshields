package data.scripts.shaders.util;

import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;

public abstract class HexShields_InstancedRenderingPlugin extends BaseCombatLayeredRenderingPlugin {
    private int vao;
    private HexShields_ShaderProgram program;

    protected abstract void populateUniformsOnFrame(int numElements, int glProgramID);

    /**
     * All buffers are flipped after this method is called
     * @param numElements number of instances
     */
    protected abstract BufferDataRelation[] populateBuffersOnFrame(int numElements);

    /**
     * Define GL funcs etc.
     */
    protected abstract void preDraw();

    protected void postDraw() {

    }

    protected abstract int getNumElementsFrame();

    protected abstract HexShields_ShaderProgram initShaderProgram();

    @Override
    public void init(CombatEntityAPI entity) {
        program = initShaderProgram();

        // Create the VAO and bind to it
        vao = glGenVertexArrays();
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        final int numElements = getNumElementsFrame();
        if (numElements == 0) return;

        glBindVertexArray(vao);
        program.bind();

        initRender(numElements);

        //uniforms
        populateUniformsOnFrame(numElements, program.getProgramID());

        //programmable buffers
        BufferDataRelation[] instanceBuffers = populateBuffersOnFrame(numElements);
        for (BufferDataRelation bufferDataRelation : instanceBuffers) {
            glBindBuffer(GL_ARRAY_BUFFER, bufferDataRelation.vbo);
            glBufferData(GL_ARRAY_BUFFER, bufferDataRelation.buffer, GL_DYNAMIC_DRAW);
        }

        preDraw();

        glDrawArraysInstanced(GL_TRIANGLES, 0, 6, numElements);

        postDraw();

        endRender(numElements);

        for (BufferDataRelation bufferDataRelation : instanceBuffers) {
            glDeleteBuffers(bufferDataRelation.vbo);
            bufferDataRelation.buffer.clear();
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        program.unbind();
        glDisable(GL_BLEND);
    }

    private void initRender(int numElements) {
        int start = 1;
        for (int i = 0; i < numElements; i++) {
            glEnableVertexAttribArray(start + i);
        }
    }

    private void endRender(int numElements) {
        int start = 1;
        for (int i = 0; i < numElements; i++) {
            glDisableVertexAttribArray(start + i);
        }
    }

    public static class BufferDataRelation {
        public final FloatBuffer buffer;
        public final int vbo;

        public BufferDataRelation(FloatBuffer buffer, int vbo) {
            this.buffer = buffer;
            this.vbo = vbo;
        }
    }

    @Override
    public void cleanup() {
        glDeleteVertexArrays(vao);
    }
}
