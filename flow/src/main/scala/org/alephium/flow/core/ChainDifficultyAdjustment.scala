package org.alephium.flow.core

import org.alephium.crypto.Keccak256
import org.alephium.flow.platform.PlatformProfile
import org.alephium.util.{ConcurrentHashMap, Duration, TimeStamp}

trait ChainDifficultyAdjustment extends BlockHashPool {
  implicit def config: PlatformProfile

  protected def blockHashesTable: ConcurrentHashMap[Keccak256, BlockHashChain.TreeNode]

  protected def calMedianBlockTime(node: BlockHashChain.TreeNode): Option[TimeStamp] = {
    if (node.height < config.medianTimeInterval) None
    else {
      var cur = node
      val timestamps = Array.fill(config.medianTimeInterval) {
        val timestamp = cur.timestamp
        cur = cur.parentOpt.get
        timestamp
      }
      Some(calMedian(timestamps))
    }
  }

  protected def calMedian(timestamps: Array[TimeStamp]): TimeStamp = {
    scala.util.Sorting.quickSort(timestamps)
    timestamps(timestamps.length / 2)
  }

  // Digi Shield DAA
  protected def calHashTarget(hash: Keccak256, currentTarget: BigInt): BigInt = {
    assert(contains(hash))
    val node = blockHashesTable(hash)
    val targetOpt = for {
      median1 <- calMedianBlockTime(node)
      parent  <- node.parentOpt
      median2 <- calMedianBlockTime(parent)
    } yield {
      var timeSpan = config.expectedTimeSpan + (median1.diff(median2) - config.expectedTimeSpan) / 4
      if (timeSpan < config.timeSpanMin) {
        timeSpan = config.timeSpanMin
      } else if (timeSpan > config.timeSpanMax) {
        timeSpan = config.timeSpanMax
      }
      reTarget(currentTarget, timeSpan)
    }

    targetOpt.fold(currentTarget)(identity)
  }

  protected def reTarget(currentTarget: BigInt, timeSpan: Duration): BigInt = {
    currentTarget * timeSpan.millis / config.expectedTimeSpan.millis
  }
}