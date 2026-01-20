// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

/**
 * Perceptron Branch Predictor
 *
 * A neural branch predictor that uses a linear perceptron model to predict
 * branch outcomes. Each perceptron has a bias weight and weights for each
 * bit of the global history register.
 *
 * Architecture:
 * - Multiple perceptrons indexed by PC bits
 * - Global history buffer tracking recent branch outcomes
 * - Prediction: sum = bias + Σ(weight[i] × history[i]), taken if sum ≥ 0
 * - Training: Update weights when misprediction OR |sum| ≤ threshold
 *
 * Key Parameters (from original Verilog):
 * - HISTORY_LENGTH: 7 (number of history bits per perceptron)
 * - BIT_WIDTH_WEIGHTS: 8 (signed weight precision)
 * - NUM_PERCEPTRONS: 8 (number of perceptrons)
 * - TRAINING_THRESHOLD: 15 (training threshold for weight updates)
 *
 * Perceptron Training Rule:
 * - If prediction wrong OR |sum| ≤ threshold:
 *   - bias += (taken ? +1 : -1)
 *   - weight[i] += (history[i] == taken) ? +1 : -1
 *
 * References:
 * - Jiménez & Lin, "Dynamic Branch Prediction with Perceptrons" (HPCA 2001)
 * - Converted from tt_um_branch_pred Verilog implementation
 *
 * @param numPerceptrons Number of perceptrons (indexed by PC)
 * @param historyLength Number of history bits per perceptron
 * @param weightBits Bit width of signed weights
 * @param trainingThreshold Threshold for training (train if |sum| ≤ threshold)
 */
class PerceptronTinyTapeoutPredictor(
    entries: Int = 64,
    historyLength: Int = 20,
    weightBits: Int = 8,
    useHashing: Boolean = true
) extends BaseBranchPredictor(entries) {
  // Threshold formula from Jimenez: 1.93 * h + 14
  val trainingThreshold = (1.93 * historyLength + 14).toInt
  // entries parameter is used as numPerceptrons
  val numPerceptrons = entries
  require(isPow2(numPerceptrons), "numPerceptrons must be power of 2")
  require(historyLength >= 1, "historyLength must be at least 1")
  require(weightBits >= 2, "weightBits must be at least 2")

  val indexBits = log2Ceil(numPerceptrons)
  // Sum width needs to accommodate: (historyLength + 1) * max_weight
  // Max weight = 2^(weightBits-1) - 1, so sum can be (historyLength+1) * max_weight
  val sumWidth = log2Ceil((historyLength + 1) * (1 << (weightBits - 1))) + 1

  // Note: io is inherited from BaseBranchPredictor with BranchPredictorIO
  // - io.pc: Input PC for prediction
  // - io.predicted_pc: Output predicted PC (we output pc + 4, BTB handles actual target)
  // - io.predicted_taken: Output whether branch is predicted taken
  // - io.update_valid: Input update trigger
  // - io.update_pc: Input PC of branch being updated
  // - io.update_target: Input target address (unused for direction predictor)
  // - io.update_taken: Input actual outcome

  // Default predicted_pc to sequential (BTB provides actual target)
  io.predicted_pc := io.pc + 4.U

  // Weight storage: (numPerceptrons) x (historyLength + 1) weights
  // +1 for bias weight (w0)
  // Weights are signed integers
  val weights = RegInit(
    VecInit(Seq.fill(numPerceptrons)(VecInit(Seq.fill(historyLength + 1)(0.S(weightBits.W)))))
  )

  // Global history buffer: shift register of recent branch outcomes
  // history(0) is most recent, history(historyLength-1) is oldest
  val historyBuffer = RegInit(0.U(historyLength.W))

  // Index extraction from PC: use bits above word alignment
  // Hash: PC bits xor history lower bits
  def getIndex(pc: UInt): UInt = {
    if (indexBits > 0) {
      val pcBits = pc(indexBits + 1, 2)
      if (useHashing) {
        val histBits = historyBuffer(indexBits - 1, 0)
        pcBits ^ histBits
      } else {
        pcBits
      }
    } else 0.U
  }

  // ========== Prediction Logic (Combinational) ==========

  val predIndex       = getIndex(io.pc)
  val perceptronWeights = weights(predIndex)

  // Compute weighted sum: bias + Σ(weight[i] × history[i])
  // history[i] = 1 means taken, 0 means not taken
  // We convert: taken → +1, not taken → -1 for multiplication
  val bias = perceptronWeights(0)

  // Calculate contribution from history bits
  val historyContributions = Wire(Vec(historyLength, SInt(sumWidth.W)))
  for (i <- 0 until historyLength) {
    val weight     = perceptronWeights(i + 1)
    val historyBit = historyBuffer(i)
    // If history bit is 1 (taken), add weight; if 0 (not taken), subtract weight
    historyContributions(i) := Mux(historyBit, weight, -weight)
  }

  // Sum all contributions
  val sum = historyContributions.reduce(_ +& _) +& bias

  // Predict taken if sum >= 0
  io.predicted_taken := !sum(sumWidth - 1) // MSB is sign bit; negative = not taken

  // ========== Training Logic (Registered) ==========

  // Store prediction info for training (needs to survive until update)
  // Note: Actual implementation will use pipeline registers in IF2ID
  val trainIndex = getIndex(io.update_pc)

  when(io.update_valid) {
    // Recompute sum for the update PC (might be different from prediction PC)
    val trainWeights = weights(trainIndex)
    val trainBias    = trainWeights(0)

    val trainHistorySum = Wire(Vec(historyLength, SInt(sumWidth.W)))
    for (i <- 0 until historyLength) {
      val w = trainWeights(i + 1)
      val h = historyBuffer(i)
      trainHistorySum(i) := Mux(h, w, -w)
    }
    val trainSum = trainHistorySum.reduce(_ +& _) +& trainBias

    // Compute absolute value of sum for threshold comparison
    val absSum = Mux(trainSum < 0.S, -trainSum, trainSum)

    // Prediction from sum
    val predictedTaken = !trainSum(sumWidth - 1)
    val misprediction  = predictedTaken =/= io.update_taken

    // Train if misprediction OR |sum| <= threshold (to strengthen confidence)
    val shouldTrain = misprediction || (absSum <= trainingThreshold.S)

    when(shouldTrain) {
      // Update bias weight
      val newBias = Mux(
        io.update_taken,
        saturatingIncrement(trainWeights(0), weightBits),
        saturatingDecrement(trainWeights(0), weightBits)
      )
      weights(trainIndex)(0) := newBias

      // Update history weights
      for (i <- 0 until historyLength) {
        val historyBit = historyBuffer(i)
        // If history[i] agrees with outcome, increment; else decrement
        val agree     = historyBit === io.update_taken
        val oldWeight = trainWeights(i + 1)
        val newWeight = Mux(
          agree,
          saturatingIncrement(oldWeight, weightBits),
          saturatingDecrement(oldWeight, weightBits)
        )
        weights(trainIndex)(i + 1) := newWeight
      }
    }

    // Update global history buffer: shift in new outcome (Newest at LSB)
    historyBuffer := Cat(historyBuffer(historyLength - 2, 0), io.update_taken)
  }

  // ========== Helper Functions ==========

  /** Saturating increment for signed integer */
  def saturatingIncrement(value: SInt, bits: Int): SInt = {
    val maxVal = ((1 << (bits - 1)) - 1).S(bits.W)
    Mux(value === maxVal, maxVal, value + 1.S)
  }

  /** Saturating decrement for signed integer */
  def saturatingDecrement(value: SInt, bits: Int): SInt = {
    val minVal = (-(1 << (bits - 1))).S(bits.W)
    Mux(value === minVal, minVal, value - 1.S)
  }
}
