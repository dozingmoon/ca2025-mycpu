// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

/**
 * Branch Target Buffer: Simple static branch predictor for performance optimization
 *
 * Architecture:
 * - 16-entry direct-mapped cache indexed by PC[5:2] (4 index bits)
 * - Each entry stores: valid bit, tag (PC[31:6]), target address
 * - Prediction: taken on hit (optimistic for backward branches/loops)
 *
 * Operation:
 * - IF stage: Look up BTB using current PC, predict taken if hit
 * - ID stage: Update BTB when branch/jump resolves with actual target
 *
 * Performance:
 * - Reduces branch penalty from 1 cycle to 0 cycles for correctly predicted branches
 * - Mispredictions still incur 1 cycle penalty (same as without BTB)
 * - Expected 3-7% IPC improvement on branch-heavy code
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

  // Index and tag extraction (PC[5:2] for index, PC[31:6] for tag with 16 entries)
  def getIndex(pc: UInt): UInt = pc(indexBits + 1, 2)
  def getTag(pc: UInt): UInt   = pc(Parameters.AddrBits - 1, indexBits + 2)

  // Prediction logic (combinational - available same cycle)
  val pred_index = getIndex(io.pc)
  val pred_tag   = getTag(io.pc)
  val hit        = valid(pred_index) && (tags(pred_index) === pred_tag)

  io.predicted_taken := hit
  io.predicted_pc    := Mux(hit, targets(pred_index), io.pc + 4.U)

  // Update logic (registered - takes effect next cycle)
  when(io.update_valid) {
    val upd_index = getIndex(io.update_pc)
    val upd_tag   = getTag(io.update_pc)

    when(io.update_taken) {
      // Branch taken: update/allocate entry with target
      valid(upd_index)   := true.B
      tags(upd_index)    := upd_tag
      targets(upd_index) := io.update_target
    }.otherwise {
      // Branch not taken: invalidate entry if it matches (reduce mispredictions)
      when(valid(upd_index) && (tags(upd_index) === upd_tag)) {
        valid(upd_index) := false.B
      }
    }
  }
}

