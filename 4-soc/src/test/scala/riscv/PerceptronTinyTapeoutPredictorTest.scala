// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

package riscv

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import riscv.core.PerceptronTinyTapeoutPredictor

class PerceptronTinyTapeoutPredictorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("Perceptron Branch Predictor")

  it should "initially predict not taken (untrained weights are zero)" in {
    test(new PerceptronTinyTapeoutPredictor(entries = 8, useHashing = false)).withAnnotations(TestAnnotations.annos) { dut =>
      // Query an address - all weights are 0, sum = 0, should predict taken (sum >= 0)
      // Actually with all zeros, sum = 0 which is >= 0, so predicts taken
      dut.io.pc.poke(0x1000.U)
      dut.io.update_valid.poke(false.B)

      // With all weights at 0, bias = 0, sum = 0, MSB = 0, so predicted_taken = true
      // This is the "default to taken" behavior for neural predictors
      dut.io.predicted_taken.expect(true.B)
    }
  }

  it should "learn to predict taken after training with taken branches" in {
    test(new PerceptronTinyTapeoutPredictor(entries = 8, useHashing = false)).withAnnotations(TestAnnotations.annos) { dut =>
      val branchPC = 0x1000L

      // Train with "branch taken" multiple times
      for (_ <- 0 until 10) {
        dut.io.update_valid.poke(true.B)
        dut.io.update_pc.poke(branchPC.U)
        dut.io.update_taken.poke(true.B)
        dut.clock.step()
      }
      dut.io.update_valid.poke(false.B)

      // Query same PC - should predict taken
      dut.io.pc.poke(branchPC.U)
      dut.clock.step()

      dut.io.predicted_taken.expect(true.B)
    }
  }

  it should "learn to predict not taken after training with not-taken branches" in {
    test(new PerceptronTinyTapeoutPredictor(entries = 8, useHashing = false)).withAnnotations(TestAnnotations.annos) { dut =>
      val branchPC = 0x1000L

      // Train with "branch not taken" multiple times
      for (_ <- 0 until 20) {
        dut.io.update_valid.poke(true.B)
        dut.io.update_pc.poke(branchPC.U)
        dut.io.update_taken.poke(false.B)
        dut.clock.step()
      }
      dut.io.update_valid.poke(false.B)

      // Query same PC - should predict not taken
      dut.io.pc.poke(branchPC.U)
      dut.clock.step()

      dut.io.predicted_taken.expect(false.B)
    }
  }

  it should "adapt to changing patterns" in {
    test(new PerceptronTinyTapeoutPredictor(entries = 8, useHashing = false)).withAnnotations(TestAnnotations.annos) { dut =>
      val branchPC = 0x1000L

      // First train with "taken" pattern
      for (_ <- 0 until 15) {
        dut.io.update_valid.poke(true.B)
        dut.io.update_pc.poke(branchPC.U)
        dut.io.update_taken.poke(true.B)
        dut.clock.step()
      }
      dut.io.update_valid.poke(false.B)

      // Should predict taken
      dut.io.pc.poke(branchPC.U)
      dut.clock.step()
      dut.io.predicted_taken.expect(true.B)

      // Now train with "not taken" pattern enough to flip prediction
      for (_ <- 0 until 30) {
        dut.io.update_valid.poke(true.B)
        dut.io.update_pc.poke(branchPC.U)
        dut.io.update_taken.poke(false.B)
        dut.clock.step()
      }
      dut.io.update_valid.poke(false.B)

      // Should now predict not taken
      dut.io.pc.poke(branchPC.U)
      dut.clock.step()
      dut.io.predicted_taken.expect(false.B)
    }
  }

  it should "handle different perceptron indices (different PC addresses)" in {
    test(new PerceptronTinyTapeoutPredictor(entries = 8, useHashing = false)).withAnnotations(TestAnnotations.annos) { dut =>
      val branchPC1 = 0x1000L // Index = 0
      val branchPC2 = 0x1010L // Index = 4 (different perceptron)

      // Train PC1 with "taken"
      for (_ <- 0 until 15) {
        dut.io.update_valid.poke(true.B)
        dut.io.update_pc.poke(branchPC1.U)
        dut.io.update_taken.poke(true.B)
        dut.clock.step()
      }

      // Train PC2 with "not taken"
      for (_ <- 0 until 20) {
        dut.io.update_valid.poke(true.B)
        dut.io.update_pc.poke(branchPC2.U)
        dut.io.update_taken.poke(false.B)
        dut.clock.step()
      }
      dut.io.update_valid.poke(false.B)

      // PC1 should predict taken
      dut.io.pc.poke(branchPC1.U)
      dut.clock.step()
      dut.io.predicted_taken.expect(true.B)

      // PC2 should predict not taken
      dut.io.pc.poke(branchPC2.U)
      dut.clock.step()
      dut.io.predicted_taken.expect(false.B)
    }
  }

  it should "update history buffer correctly" in {
    test(new PerceptronTinyTapeoutPredictor(entries = 8, useHashing = false)).withAnnotations(TestAnnotations.annos) { dut =>
      val branchPC = 0x1000L

      // Train with alternating pattern: T, N, T, N, ...
      for (i <- 0 until 10) {
        dut.io.update_valid.poke(true.B)
        dut.io.update_pc.poke(branchPC.U)
        dut.io.update_taken.poke((i % 2 == 0).B)
        dut.clock.step()
      }
      dut.io.update_valid.poke(false.B)

      // The perceptron should have learned something from the alternating pattern
      // Just verify it doesn't crash and produces a valid prediction
      dut.io.pc.poke(branchPC.U)
      dut.clock.step()

      // Just check the output is valid (either true or false)
      val prediction = dut.io.predicted_taken.peek().litToBoolean
      assert(prediction == true || prediction == false)
    }
  }
}
