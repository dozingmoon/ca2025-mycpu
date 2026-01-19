// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

/**
 * Two-Level Local Branch Predictor
 * 
 * Architecture (per diagram):
 * Level 1: Correlation Table (Local History Table)
 * - Stores branch history per address
 * - Indexed by subset of PC bits
 * 
 * Level 2: Branch History Table (Pattern History Table)
 * - Table of saturating counters
 * - Indexed by history from Level 1
 */
class TwoLevelLocalPredictor(entries: Int = 64, historyLength: Int = 6, counterBits: Int = 2) extends BaseBranchPredictor(entries) {
  require(isPow2(entries), "L1 entries must be power of 2")
  
  val indexBits = log2Ceil(entries)
  
  // Level 1: Correlation Table (Local History Table)
  val correlationTable = RegInit(VecInit(Seq.fill(entries)(0.U(historyLength.W))))

  // Level 2: Branch History Table (Pattern History Table)
  val bhtEntries = 1 << historyLength
  // Initialize to 1 (Weakly Not Taken) as default safer starting point
  val bht = RegInit(VecInit(Seq.fill(bhtEntries)(1.U(counterBits.W))))

  def getIndex(pc: UInt): UInt = pc(indexBits + 1, 2)
  
  // Prediction Logic (Combinational)
  val l1_index   = getIndex(io.pc)
  val l1_history = correlationTable(l1_index) // Read current history
  
  // Use history to index BHT
  val l2_index   = l1_history 
  val counter    = bht(l2_index)
  
  // Predict taken if MSB of counter is 1 (e.g. >= 2 for 2-bit)
  val threshold = 1 << (counterBits - 1)
  io.predicted_taken := counter >= threshold.U
  
  // Target prediction is handled by BTB, only direction here
  io.predicted_pc := io.pc + 4.U


  // Update Logic (Sequential - Next Cycle)
  when(io.update_valid) {
    val upd_index = getIndex(io.update_pc)
    
    // Read the history that was used (approximation: reading current L1)
    val history_at_update = correlationTable(upd_index)
    
    // Update L2 (BHT) Counter based on actual outcome
    val current_counter = bht(history_at_update)
    val max_counter     = (1 << counterBits) - 1
    
    when(io.update_taken) {
      // Increment, saturate at max
      bht(history_at_update) := Mux(current_counter === max_counter.U, max_counter.U, current_counter + 1.U)
    }.otherwise {
      // Decrement, saturate at 0
      bht(history_at_update) := Mux(current_counter === 0.U, 0.U, current_counter - 1.U)
    }
    
    // Update L1 (Correlation Table) History
    val new_history = Cat(history_at_update(historyLength - 2, 0), io.update_taken)
    correlationTable(upd_index) := new_history
  }
}
