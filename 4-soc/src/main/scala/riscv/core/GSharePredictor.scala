// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

class GSharePredictor(entries: Int = 64, historyLength: Int = 16, phtIndexBits: Int = 6) extends BaseBranchPredictor(entries) {
  require(isPow2(entries), "GShare entries must be power of 2")

  val indexBits = log2Ceil(entries)
  val tagBits   = Parameters.AddrBits - indexBits - 2 // -2 for 4-byte alignment

  // PHT (Pattern History Table)
  val phtEntries = 1 << phtIndexBits
  val pht = RegInit(VecInit(Seq.fill(phtEntries)(1.U(2.W)))) // Initialize to Weakly Not-Taken

  // Global History Register
  val history = RegInit(0.U(historyLength.W))

  // Helper functions
  
  // GShare index: XOR PC bits with lower bits of history (no folding)
  def getPhtIndex(pc: UInt, hist: UInt): UInt = {
    val pcBits = pc(phtIndexBits + 1, 2)
    // Use lower bits of history directly
    val histBits = hist(phtIndexBits - 1, 0)
    pcBits ^ histBits
  }

  // Prediction Logic
  
  // PHT lookup for direction (tagless)
  val pred_pht_index = getPhtIndex(io.pc, history)
  val pred_direction = pht(pred_pht_index) >= 2.U  // Taken if counter >= 2
  
  io.predicted_taken := pred_direction
  // PC prediction handled by Shared BTB
  io.predicted_pc    := io.pc + 4.U

  // Update Logic
  when(io.update_valid) {
    // Update global history (shift in new outcome)
    history := Cat(history(historyLength - 2, 0), io.update_taken)

    // Update PHT (direction predictor) - always update, tagless
    val upd_pht_index = getPhtIndex(io.update_pc, history)
    when(io.update_taken) {
      // Increment counter (saturate at 3)
      pht(upd_pht_index) := Mux(pht(upd_pht_index) === 3.U, 3.U, pht(upd_pht_index) + 1.U)
    }.otherwise {
      // Decrement counter (saturate at 0)
      pht(upd_pht_index) := Mux(pht(upd_pht_index) === 0.U, 0.U, pht(upd_pht_index) - 1.U)
    }
  }
}
