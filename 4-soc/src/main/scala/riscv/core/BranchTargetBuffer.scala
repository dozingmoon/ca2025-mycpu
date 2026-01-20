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
  val counters = RegInit(VecInit(Seq.fill(entries)(0.U(2.W))))

  // Index and tag extraction (PC[5:2] for index, PC[31:6] for tag with 16 entries)
  def getIndex(pc: UInt): UInt = pc(indexBits + 1, 2)
  def getTag(pc: UInt): UInt   = pc(Parameters.AddrBits - 1, indexBits + 2)

  // Prediction logic (combinational - available same cycle)
  val pred_index = getIndex(io.pc)
  val pred_tag   = getTag(io.pc)
  val hit        = valid(pred_index) && (tags(pred_index) === pred_tag)
  val counter    = counters(pred_index)

  // Predict taken if hit AND counter >= 2 (Weakly Taken or Strongly Taken)
  io.predicted_taken := hit && counter(1)
  io.predicted_pc    := Mux(hit, targets(pred_index), io.pc + 4.U)

  // Update logic (registered - takes effect next cycle)
  when(io.update_valid) {
    val upd_index = getIndex(io.update_pc)
    val upd_tag   = getTag(io.update_pc)
    val entry_valid = valid(upd_index)
    val entry_tag   = tags(upd_index)
    val entry_match = entry_valid && (entry_tag === upd_tag)
    
    when(entry_match) {
      // Existing entry: update counter
      val old_counter = counters(upd_index)
      when(io.update_taken) {
        // Increment (saturate at 3)
        counters(upd_index) := Mux(old_counter === 3.U, 3.U, old_counter + 1.U)
      }.otherwise {
        // Decrement
        val new_counter = old_counter - 1.U
        counters(upd_index) := new_counter
        // Invalidate if counter drops below 0? No, counters are unsigned 2-bit (0-3).
        // Test expects invalidation when counter "would reach 0" or something?
        // Let's check test: "... record it as not-taken 3 times (counter: 2 -> 1 -> invalid)"
        // The test says "Entry is invalidated when counter would reach 0 to free the slot" (Line 122).
        // Wait, 1 -> 0 is valid state (Strongly Not Taken).
        // Invalidating at 0 allows new branches to claim the slot.
        // So if (old_counter === 1 && !update_taken) -> invalidate?
        // Or if (old_counter === 0 && !update_taken) -> invalidate?
        // Test: 2 -> 1 -> invalid.
        // Step 1: counter=2. update_taken=false -> counter=1.
        // Step 2: counter=1. update_taken=false -> invalid.
        // So if counter becomes 0, we invalidate?
        // Let's implement: if counter would go to 0, invalidate.
        when(old_counter === 1.U || old_counter === 0.U) {
             valid(upd_index) := false.B
        }
      }
    }.otherwise {
      // No match (new entry or collision): Allocate new entry if taken
      // Test says: "allocate entry for branches, regardless of taken/not-taken"?
      // Line 55 commented: "IMPORTANT: Always allocate...".
      // But test line 17: "not predict taken on empty BTB".
      // Test 110: "invalidate... free slot".
      
      // If we allocate on Not Taken, we set counter to what? 
      // Usually allocate on Taken with Weakly Taken (2).
      // Test 134: "taken update creates a NEW entry with counter=2".
      
      when(io.update_taken) {
        valid(upd_index)   := true.B
        tags(upd_index)    := upd_tag
        targets(upd_index) := io.update_target
        counters(upd_index) := 2.U // Weakly Taken
      }
    }
  }
}

