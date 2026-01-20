// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

/**
 * Bimodal Predictor
 * Uses a table of 2-bit saturating counters indexed by PC.
 * Predicts direction only (no target storage).
 */
class BimodalPredictor(entries: Int = 16) extends BaseBranchPredictor(entries) {
  require(isPow2(entries), "Bimodal entries must be power of 2")

  val indexBits = log2Ceil(entries)

  // 2-bit saturating counters
  // States: 0=SNT, 1=WNT, 2=WT, 3=ST
  val counters = RegInit(VecInit(Seq.fill(entries)(2.U(2.W)))) // Initialize to Weakly Taken

  def getIndex(pc: UInt): UInt = pc(indexBits + 1, 2)

  // Prediction Logic
  val pred_index = getIndex(io.pc)
  val counter    = counters(pred_index)
  // Predict taken if counter >= 2 (WT or ST)
  io.predicted_taken := counter >= 2.U
  
  // Predicted PC is not handled here (handled by shared BTB)
  io.predicted_pc    := io.pc + 4.U 

  // Update Logic
  when(io.update_valid) {
    val upd_index = getIndex(io.update_pc)
    val current_counter = counters(upd_index)

    when(io.update_taken) {
      // Increment (saturate at 3)
      counters(upd_index) := Mux(current_counter === 3.U, 3.U, current_counter + 1.U)
    }.otherwise {
      // Decrement (saturate at 0)
      counters(upd_index) := Mux(current_counter === 0.U, 0.U, current_counter - 1.U)
    }
  }
}
