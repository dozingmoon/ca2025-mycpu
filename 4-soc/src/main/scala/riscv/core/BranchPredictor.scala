// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

object BranchPredictor {
  /**
   * Fold global history into an index of smaller width using XOR.
   * This compresses the history vector to minimize aliasing in the PHT.
   *
   * @param history The global history register (GHR) value
   * @param historyLength The total number of bits in the history register
   * @param indexLength The target width of the index (PHT depth = 2^indexLength)
   * @return The folded history (UInt of width indexLength)
   */
  def foldHistory(history: UInt, historyLength: Int, indexLength: Int): UInt = {
    val numChunks = (historyLength + indexLength - 1) / indexLength
    val chunks = (0 until numChunks).map { i =>
      val high = math.min((i + 1) * indexLength - 1, historyLength - 1)
      val low  = i * indexLength
      val chunk = history(high, low)
      
      // If the last chunk is smaller than indexLength, pad it with zeros
      if (high - low + 1 < indexLength) {
        Cat(0.U((indexLength - (high - low + 1)).W), chunk)
      } else {
        chunk
      }
    }
    // XOR all chunks together
    chunks.reduce(_ ^ _)
  }

  /**
   * Calculate the GShare index.
   * Index = (PC[n+1:2]) XOR Fold(History)
   * Where n = indexLength.
   *
   * @param pc The Program Counter
   * @param history The Global History Register
   * @param historyLength Length of the history register
   * @param indexLength Width of the resulting index
   * @return The calculated index to access the PHT
   */
  def gshareIndex(pc: UInt, history: UInt, historyLength: Int, indexLength: Int): UInt = {
    // Extract lower bits of PC (ignoring byte offset 0-1)
    val pcIndex = pc(indexLength + 1, 2)
    val foldedHist = foldHistory(history, historyLength, indexLength)
    pcIndex ^ foldedHist
  }
}

class BranchPredictorIO extends Bundle {
  // Prediction interface (IF stage) - combinational lookup
  val pc              = Input(UInt(Parameters.AddrWidth))
  val predicted_pc    = Output(UInt(Parameters.AddrWidth))
  val predicted_taken = Output(Bool())

  // Update interface (ID stage) - registered update
  val update_valid  = Input(Bool())
  val update_pc     = Input(UInt(Parameters.AddrWidth))
  val update_target = Input(UInt(Parameters.AddrWidth))
  val update_taken  = Input(Bool()) // Whether branch was actually taken
}

abstract class BaseBranchPredictor(entries: Int) extends Module {
  val io = IO(new BranchPredictorIO)
}

class GSharePredictor(entries: Int = 16, historyLength: Int = 8) extends BaseBranchPredictor(entries) {
  require(isPow2(entries), "GShare entries must be power of 2")

  val indexBits = log2Ceil(entries)
  val tagBits   = Parameters.AddrBits - indexBits - 2 // -2 for 4-byte alignment

  // ============================================================
  // Separate PHT (Pattern History Table) - for direction prediction
  // PHT is tagless - uses GShare index (PC XOR history)
  // ============================================================
  val pht = RegInit(VecInit(Seq.fill(entries)(1.U(2.W)))) // Initialize to Weakly Not-Taken

  // ============================================================
  // Separate BTB (Branch Target Buffer) - for target storage
  // BTB uses direct PC index with tags
  // ============================================================
  val btb_valid   = RegInit(VecInit(Seq.fill(entries)(false.B)))
  val btb_tags    = Reg(Vec(entries, UInt(tagBits.W)))
  val btb_targets = Reg(Vec(entries, UInt(Parameters.AddrBits.W)))

  // Global History Register
  val history = RegInit(0.U(historyLength.W))

  // ============================================================
  // Helper functions
  // ============================================================
  def getBtbIndex(pc: UInt): UInt = pc(indexBits + 1, 2)
  def getBtbTag(pc: UInt): UInt = pc(Parameters.AddrBits - 1, indexBits + 2)
  
  // GShare index: XOR PC bits with folded history
  def getPhtIndex(pc: UInt, hist: UInt): UInt = {
    val pcBits = pc(indexBits + 1, 2)
    // Use full history, fold if necessary
    val histBits = if (historyLength >= indexBits) {
      BranchPredictor.foldHistory(hist, historyLength, indexBits)
    } else {
      // Pad history if shorter than index
      Cat(0.U((indexBits - historyLength).W), hist)
    }
    pcBits ^ histBits
  }

  // ============================================================
  // Prediction Logic (combinational)
  // ============================================================
  
  // PHT lookup for direction (tagless)
  val pred_pht_index = getPhtIndex(io.pc, history)
  val pred_direction = pht(pred_pht_index) >= 2.U  // Taken if counter >= 2
  
  // BTB lookup for target (tagged)
  val pred_btb_index = getBtbIndex(io.pc)
  val pred_btb_tag   = getBtbTag(io.pc)
  val btb_hit        = btb_valid(pred_btb_index) && (btb_tags(pred_btb_index) === pred_btb_tag)
  val btb_target     = btb_targets(pred_btb_index)
  
  // Final prediction: predict taken if PHT says taken AND BTB has a valid target
  val predict_taken = pred_direction && btb_hit
  io.predicted_taken := predict_taken
  io.predicted_pc    := Mux(predict_taken, btb_target, io.pc + 4.U)

  // ============================================================
  // Update Logic (registered)
  // ============================================================
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

    // Update BTB (target buffer) - only on taken branches
    val upd_btb_index = getBtbIndex(io.update_pc)
    val upd_btb_tag   = getBtbTag(io.update_pc)
    
    when(io.update_taken) {
      btb_valid(upd_btb_index)   := true.B
      btb_tags(upd_btb_index)    := upd_btb_tag
      btb_targets(upd_btb_index) := io.update_target
    }
    // Note: We don't invalidate BTB on not-taken - targets rarely change
  }
}

