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

import org.alephium.flow.AlephiumFlowSpec
import org.alephium.protocol.model._
import org.alephium.util.{AlephiumFixture, LockFixture}

class TxIndexesSpec
    extends AlephiumFlowSpec
    with TxIndexesSpec.Fixture
    with LockFixture
    with NoIndexModelGeneratorsLike {
  it should "add txs" in {
    val tx      = transactionGen().sample.get
    val indexes = TxIndexes.emptySharedPool
    indexes.add(tx.toTemplate)
    checkTx(indexes, tx.toTemplate)
  }

  it should "be idempotent for adding" in {
    val tx       = transactionGen().sample.get
    val indexes0 = TxIndexes.emptySharedPool
    indexes0.add(tx.toTemplate)

    val indexes1 = TxIndexes.emptySharedPool
    indexes1.add(tx.toTemplate)
    indexes1.add(tx.toTemplate)

    indexes0 is indexes1
  }

  it should "remove tx" in {
    val tx      = transactionGen().sample.get
    val indexes = TxIndexes.emptySharedPool
    indexes.add(tx.toTemplate)
    indexes.remove(tx.toTemplate)
    indexes is TxIndexes.emptySharedPool

    // check for idempotent
    indexes.remove(tx.toTemplate)
    indexes is TxIndexes.emptySharedPool
  }

  trait LockFixture extends WithLock {
    val indexes  = TxIndexes.emptySharedPool
    lazy val rwl = indexes._getLock

    val tx = transactionGen().sample.get.toTemplate
  }

  it should "use locks" in new LockFixture {
    checkReadLock(rwl)(true, indexes.isSpent(tx.unsigned.inputs.head.outputRef), false)
    checkWriteLock(rwl)(0, { indexes.add(tx); indexes.inputIndex.size }, tx.unsigned.inputs.length)
    checkWriteLock(rwl)(100, { indexes.remove(tx); indexes.inputIndex.size }, 0)
  }
}

object TxIndexesSpec {
  trait Fixture extends AlephiumFixture {
    def checkTx(indexes: TxIndexes, tx: TransactionTemplate): Unit = {
      tx.unsigned.inputs.foreach { input =>
        indexes.inputIndex.contains(input.outputRef) is true
      }
      tx.unsigned.fixedOutputs.foreachWithIndex { case (output, index) =>
        val outputRef = AssetOutputRef.from(output, TxOutputRef.key(tx.id, index))
        indexes.outputIndex.contains(outputRef) is true
        indexes.addressIndex(output.lockupScript).contains(outputRef) is true
      }
    }
  }
}
