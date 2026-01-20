// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

class PerceptronBlackParrotPredictor(entries: Int = 64, historyLength: Int = 20) extends BaseBranchPredictor(entries) {
  // Configurable parameters
  val weightWidth = 8
  val threshold = (1.93 * historyLength + 14).toInt // Typical threshold formula
  val indexBits = log2Ceil(entries)

  // Perceptron Table
  val weights = RegInit(VecInit(Seq.fill(entries)(VecInit(Seq.fill(historyLength + 1)(0.S(weightWidth.W))))))

  // Global History Register
  val history = RegInit(0.U(historyLength.W))

  // Hash PC to get table index
  def getIndex(pc: UInt): UInt = {
    // Hash: PC bits xor history lower bits
    if (indexBits > 0) {
      val pcBits = pc(indexBits + 1, 2)
      val histBits = history(indexBits - 1, 0)
      pcBits ^ histBits
    } else 0.U
  }

  val pred_index = getIndex(io.pc)
  
  // Calculate dot product
  val dot_product = Wire(SInt((weightWidth + log2Ceil(historyLength + 1) + 1).W))
  
  val current_weights = weights(pred_index)
  val bias = current_weights(0)
  
  // Construct the sum
  val sum = (0 until historyLength).map { i =>
    val weight = current_weights(i + 1)
    val hist_bit = history(i)
    Mux(hist_bit, weight, -weight)
  }.reduce(_ + _)
  
  dot_product := bias + sum
  
  // Prediction: Taken if y >= 0
  io.predicted_taken := dot_product >= 0.S
  io.predicted_pc    := io.pc + 4.U // Target provided by BTB

  // Update Logic
  // Train if misprediction OR dot product magnitude is below threshold (weak prediction)
  val update_index = getIndex(io.update_pc)
  val update_weights = weights(update_index)
  
  // Re-calculate y_out for training check
  val train_y = Wire(SInt((weightWidth + log2Ceil(historyLength + 1) + 1).W))
  val train_bias = update_weights(0)
  val train_sum = (0 until historyLength).map { i =>
    val weight = update_weights(i + 1)
    val hist_bit = history(i)
    Mux(hist_bit, weight, -weight)
  }.reduce(_ + _)
  train_y := train_bias + train_sum

  val t = Mux(io.update_taken, 1.S, -1.S) // Target outcome (+1 or -1)
  val predict_taken = train_y >= 0.S
  val correct_prediction = predict_taken === io.update_taken
  val magnitude = Mux(train_y < 0.S, -train_y, train_y)
  val weak_prediction = magnitude <= threshold.S

  when(io.update_valid) {
    // Update weights if mispredicted or weak
    when(!correct_prediction || weak_prediction) {
      // Update bias
      val new_bias = train_bias + t
      // Saturate bias
      val max_weight = ((1 << (weightWidth - 1)) - 1).S
      val min_weight = (-(1 << (weightWidth - 1))).S
      
      update_weights(0) := Mux(new_bias > max_weight, max_weight, 
                          Mux(new_bias < min_weight, min_weight, new_bias))

      // Update weights
      for (i <- 0 until historyLength) {
        val x_i = Mux(history(i), 1.S, -1.S) // Input from history
        // w_new = w + t * x_i
        // if t = x_i, w += 1; if t != x_i, w -= 1
        val w_adj = Mux(io.update_taken === history(i), 1.S, -1.S)
        val new_w = update_weights(i + 1) + w_adj
        
        update_weights(i + 1) := Mux(new_w > max_weight, max_weight,
                                Mux(new_w < min_weight, min_weight, new_w))
      }
    }

    // Update Global History (shift in new outcome)
    history := Cat(history(historyLength - 2, 0), io.update_taken)
  }
}
