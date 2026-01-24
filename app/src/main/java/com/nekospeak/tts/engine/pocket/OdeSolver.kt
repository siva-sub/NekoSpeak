package com.nekospeak.tts.engine.pocket

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.nio.FloatBuffer

/**
 * ODE (Ordinary Differential Equation) solver for flow matching generation.
 * 
 * Uses Euler method to iteratively refine latents through the flow network.
 * The flow network predicts velocity field at each step, and we integrate
 * along this field to transform noise into coherent speech latents.
 */
class OdeSolver(
    private val flowNet: OrtSession,
    private val env: OrtEnvironment,
    private val steps: Int = 10,
    private val temperature: Float = 0.7f,
    private val eosThreshold: Float = -4.0f
) {
    companion object {
        private const val TAG = "OdeSolver"
        const val LATENT_DIM = 32
        const val CONDITIONING_DIM = 1024
    }
    
    private val random = java.util.Random(System.currentTimeMillis())
    
    /**
     * Solve the ODE to generate a latent frame.
     * 
     * @param conditioning [1024] conditioning vector from flow_lm_main
     * @param initialLatent [32] starting latent (typically noise or zeros)
     * @return Final latent [32] ready for decoding
     */
    fun solve(conditioning: FloatArray, initialLatent: FloatArray? = null): FloatArray {
        require(conditioning.size == CONDITIONING_DIM) { 
            "Conditioning must be $CONDITIONING_DIM dims, got ${conditioning.size}" 
        }
        
        // Start from provided latent or random noise
        var x = initialLatent?.clone() ?: FloatArray(LATENT_DIM) { 
            (random.nextGaussian() * 0.1).toFloat() 
        }
        
        val dt = 1.0f / steps
        
        Log.v(TAG, "Solving ODE with $steps steps, dt=$dt, temp=$temperature")
        
        for (step in 0 until steps) {
            val t = step.toFloat() / steps
            val tNext = (step + 1).toFloat() / steps
            
            // Temperature-scaled noise injection for diversity
            if (temperature > 0 && step < steps - 1) {
                val noise = FloatArray(LATENT_DIM) { 
                    (random.nextGaussian() * temperature * dt * 0.5).toFloat() 
                }
                x = x.mapIndexed { i, v -> v + noise[i] }.toFloatArray()
            }
            
            // Get flow direction from flow network
            val flowDir = runFlowNet(conditioning, t, tNext, x)
            
            // Euler step: x = x + flow_dir * dt
            x = x.mapIndexed { i, v -> v + flowDir[i] * dt }.toFloatArray()
        }
        
        return x
    }
    
    /**
     * Run the flow network for one step.
     * 
     * @param c Conditioning vector [1024]
     * @param s Current timestep [0, 1]
     * @param t Target timestep [0, 1]
     * @param x Current latent [32]
     * @return Flow direction [32]
     */
    private fun runFlowNet(c: FloatArray, s: Float, t: Float, x: FloatArray): FloatArray {
        // Create tensors
        val cTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(c),
            longArrayOf(1, CONDITIONING_DIM.toLong())
        )
        val sTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(floatArrayOf(s)),
            longArrayOf(1, 1)
        )
        val tTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(floatArrayOf(t)),
            longArrayOf(1, 1)
        )
        val xTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(x),
            longArrayOf(1, LATENT_DIM.toLong())
        )
        
        val outputs = flowNet.run(mapOf(
            "c" to cTensor,
            "s" to sTensor,
            "t" to tTensor,
            "x" to xTensor
        ))
        
        val flowDir = (outputs[0] as OnnxTensor).floatBuffer.let { buf ->
            FloatArray(buf.remaining()).also { buf.get(it) }
        }
        
        // Cleanup
        cTensor.close()
        sTensor.close()
        tTensor.close()
        xTensor.close()
        outputs.close()
        
        return flowDir
    }
    
    /**
     * Check if the EOS (End of Sentence) logit indicates generation should stop.
     * 
     * @param eosLogit The EOS logit from flow_lm_main
     * @return true if generation should stop
     */
    fun shouldStop(eosLogit: Float): Boolean {
        return eosLogit > eosThreshold
    }
}
