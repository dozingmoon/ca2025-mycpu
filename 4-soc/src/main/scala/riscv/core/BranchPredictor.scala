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

  // BTB entry structure
  val valid   = RegInit(VecInit(Seq.fill(entries)(false.B)))
  val tags    = Reg(Vec(entries, UInt(tagBits.W)))
  val targets = Reg(Vec(entries, UInt(Parameters.AddrBits.W)))

  // 2-bit saturating counters for direction prediction
  val counters = RegInit(VecInit(Seq.fill(entries)(2.U(2.W)))) // Initialize to Weakly Taken

  // Global History Register
  val history = RegInit(0.U(historyLength.W))

  // Helpers
  def getTag(pc: UInt): UInt = pc(Parameters.AddrBits - 1, indexBits + 2)

  // Prediction logic
  val pred_index = BranchPredictor.gshareIndex(io.pc, history, historyLength, indexBits)
  val pred_tag   = getTag(io.pc)
  val hit        = valid(pred_index) && (tags(pred_index) === pred_tag)
  
  // Predict taken if hit AND counter >= 2
  val predict_taken = hit && (counters(pred_index) >= 2.U)
  io.predicted_taken := predict_taken
  io.predicted_pc    := Mux(predict_taken, targets(pred_index), io.pc + 4.U)

  // Debug: Print every cycle where prediction is happening (or just always)
  // printf(p"GShare: PC=%x Hist=%x Index=%x Pred=%d\n", io.pc, history, pred_index, predict_taken)

  // Update logic
  when(io.update_valid) {
      // printf(p"GShare Update: PC=${Hexadecimal(io.update_pc)} Taken=${io.update_taken} Hist=${Hexadecimal(history)}\n")
    // Update global history
    history := Cat(history(historyLength - 2, 0), io.update_taken)

    val upd_index = BranchPredictor.gshareIndex(io.update_pc, history, historyLength, indexBits)
    val upd_tag   = getTag(io.update_pc)
    val entry_hit = valid(upd_index) && (tags(upd_index) === upd_tag)

    when(io.update_taken) {
      valid(upd_index)   := true.B
      tags(upd_index)    := upd_tag
      targets(upd_index) := io.update_target
      when(entry_hit) {
        counters(upd_index) := Mux(counters(upd_index) === 3.U, 3.U, counters(upd_index) + 1.U)
      }.otherwise {
        counters(upd_index) := 2.U
      }
    }.otherwise {
      when(entry_hit) {
        when(counters(upd_index) === 1.U) {
          valid(upd_index) := false.B
        }.elsewhen(counters(upd_index) > 1.U) {
          counters(upd_index) := counters(upd_index) - 1.U
        }
      }
    }
  }
}
