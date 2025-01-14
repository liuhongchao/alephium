// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.flow.mempool

import scala.collection.mutable

import org.alephium.flow.core.FlowUtils.AssetOutputInfo
import org.alephium.io.IOResult
import org.alephium.protocol.Hash
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model._
import org.alephium.protocol.vm.{LockupScript, WorldState}
import org.alephium.util._

class PendingPool(
    groupIndex: GroupIndex,
    val txs: mutable.HashMap[Hash, TransactionTemplate],
    val timestamps: ValueSortedMap[Hash, TimeStamp],
    val indexes: TxIndexes,
    capacity: Int
) extends RWLock {
  def size: Int = readOnly {
    txs.size
  }

  def isFull(): Boolean = txs.size >= capacity

  def contains(txId: Hash): Boolean = readOnly {
    txs.contains(txId)
  }

  def isDoubleSpending(tx: TransactionTemplate): Boolean = readOnly {
    tx.unsigned.inputs.exists(input => indexes.isSpent(input.outputRef))
  }

  def add(tx: TransactionTemplate, timeStamp: TimeStamp): Boolean = writeOnly {
    if (!txs.contains(tx.id)) {
      if (isFull()) {
        false
      } else {
        txs.put(tx.id, tx)
        timestamps.put(tx.id, timeStamp)
        indexes.add(tx)
        measureTransactionsTotal()
        true
      }
    } else {
      true
    }
  }

  def remove(txs: AVector[TransactionTemplate]): Unit = writeOnly {
    txs.foreach(_remove)
    measureTransactionsTotal()
  }

  def remove(tx: TransactionTemplate): Unit = writeOnly {
    _remove(tx)
  }

  def _remove(tx: TransactionTemplate): Unit = {
    if (txs.contains(tx.id)) {
      txs.remove(tx.id)
      timestamps.remove(tx.id)
      indexes.remove(tx)
    }
  }

  def getAll(
      chainIndex: ChainIndex
  )(implicit groupConfig: GroupConfig): AVector[TransactionTemplate] = readOnly {
    AVector.from(txs.values.view.filter(_.chainIndex == chainIndex))
  }

  def getRelevantUtxos(lockupScript: LockupScript): AVector[AssetOutputInfo] = readOnly {
    indexes.getRelevantUtxos(lockupScript)
  }

  def extractReadyTxs(worldState: WorldState.Persisted): IOResult[AVector[TransactionTemplate]] =
    readOnly {
      EitherF.foldTry(txs.values, AVector.empty[TransactionTemplate]) { case (acc, tx) =>
        worldState.containsAllInputs(tx).map {
          case true  => acc :+ tx
          case false => acc
        }
      }
    }

  // Left means the output is spent
  def getUtxo(outputRef: AssetOutputRef): Either[Unit, Option[TxOutput]] = {
    indexes.getUtxo(outputRef)
  }

  def takeOldTxs(timeStampThreshold: TimeStamp): AVector[TransactionTemplate] = readOnly {
    AVector.from(
      timestamps
        .entries()
        .takeWhile(_.getValue < timeStampThreshold)
        .map(entry => txs(entry.getKey))
    )
  }

  private val transactionTotalLabeled =
    MemPool.pendingPoolTransactionsTotal.labels(groupIndex.value.toString)
  def measureTransactionsTotal(): Unit = {
    transactionTotalLabeled.set(txs.size.toDouble)
  }
}

object PendingPool {
  def empty(groupIndex: GroupIndex, capacity: Int): PendingPool =
    new PendingPool(
      groupIndex,
      mutable.HashMap.empty,
      ValueSortedMap.empty,
      TxIndexes.emptyPendingPool,
      capacity
    )
}
