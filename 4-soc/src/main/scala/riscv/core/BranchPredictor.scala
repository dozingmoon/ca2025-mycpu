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



