// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

/**
 * Branch Target Buffer with 2-bit Saturating Counter Predictor
 *
 * Architecture:
 * - Direct-mapped cache indexed by PC bits (configurable size, default 16 entries)
 * - Each entry stores: valid bit, tag, target address, 2-bit counter
 * - Prediction: taken on hit AND counter >= 2 (weakly/strongly taken)
 *
 * 2-bit Saturating Counter States:
 * - 0: Strongly Not Taken (SNT)
 * - 1: Weakly Not Taken (WNT)
 * - 2: Weakly Taken (WT)
 * - 3: Strongly Taken (ST)
 *
 * Counter Transitions:
 * - Branch taken: increment (saturate at 3)
 * - Branch not taken: decrement (saturate at 0)
 * - New entry: initialize to Weakly Taken (2)
 *
 * Operation:
 * - IF stage: Look up BTB using current PC, predict taken if hit && counter >= 2
 * - ID stage: Update BTB when branch/jump resolves, adjust counter
 *
 * Performance:
 * - Better than static "always taken" for alternating branch patterns
 * - Hysteresis prevents single misprediction from flipping direction
 *
 * @param entries Number of BTB entries (must be power of 2)
 */
class BranchTargetBuffer(entries: Int = 16) extends BaseBranchPredictor(entries) {
  require(isPow2(entries), "BTB entries must be power of 2")

  val indexBits = log2Ceil(entries)
  val tagBits   = Parameters.AddrBits - indexBits - 2 // -2 for 4-byte alignment

  // BTB entry structure
  val valid   = RegInit(VecInit(Seq.fill(entries)(false.B)))
  val tags    = Reg(Vec(entries, UInt(tagBits.W)))
  val targets = Reg(Vec(entries, UInt(Parameters.AddrBits.W)))

  // Index and tag extraction (index/tag bits computed from entry count)
  def getIndex(pc: UInt): UInt = pc(indexBits + 1, 2)
  def getTag(pc: UInt): UInt   = pc(Parameters.AddrBits - 1, indexBits + 2)

  // Lookup Logic
  val pred_index = getIndex(io.pc)
  val pred_tag   = getTag(io.pc)
  val hit        = valid(pred_index) && (tags(pred_index) === pred_tag)

  // Output: Taken only implies we have a valid target. 
  // Direction decision is now up to the external predictor.
  io.predicted_taken := hit
  io.predicted_pc    := Mux(hit, targets(pred_index), io.pc + 4.U)

  // Update Logic
  when(io.update_valid) {
    val upd_index = getIndex(io.update_pc)
    val upd_tag   = getTag(io.update_pc)
    
    // Only update/allocate if the branch was actually taken
    when(io.update_taken) {
      valid(upd_index)   := true.B
      tags(upd_index)    := upd_tag
      targets(upd_index) := io.update_target
    }
  }
}
