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

package org.alephium.flow.core

import org.alephium.flow.model.BlockState
import org.alephium.io.IOResult
import org.alephium.protocol.BlockHash
import org.alephium.protocol.config.BrokerConfig
import org.alephium.protocol.model._
import org.alephium.util.{AVector, TimeStamp}

// scalastyle:off number.of.methods
trait MultiChain extends BlockPool with BlockHeaderPool {
  implicit def brokerConfig: BrokerConfig

  def groups: Int

  protected def aggregateHash[T](f: BlockHashPool => T)(op: (T, T) => T): T

  protected def aggregateHashE[T](f: BlockHashPool => IOResult[T])(op: (T, T) => T): IOResult[T]

  protected def aggregateHeaderE[T](f: BlockHeaderPool => IOResult[T])(op: (T, T) => T): IOResult[T]

  def numHashes: Int = aggregateHash(_.numHashes)(_ + _)

  /* BlockHash apis */
  def contains(hash: BlockHash): IOResult[Boolean] = {
    val index = ChainIndex.from(hash)
    val chain = getHashChain(index)
    chain.contains(hash)
  }

  def containsUnsafe(hash: BlockHash): Boolean = {
    val index = ChainIndex.from(hash)
    val chain = getHashChain(index)
    chain.containsUnsafe(hash)
  }

  def getIndex(hash: BlockHash): ChainIndex = {
    ChainIndex.from(hash)
  }

  protected def getHashChain(from: GroupIndex, to: GroupIndex): BlockHashChain

  def getHashChain(chainIndex: ChainIndex): BlockHashChain = {
    getHashChain(chainIndex.from, chainIndex.to)
  }

  def getHashChain(hash: BlockHash): BlockHashChain = {
    val index = ChainIndex.from(hash)
    getHashChain(index.from, index.to)
  }

  def isTip(hash: BlockHash): Boolean = {
    getHashChain(hash).isTip(hash)
  }

  def getHeightedBlockHeaders(
      fromTs: TimeStamp,
      toTs: TimeStamp
  ): IOResult[AVector[(BlockHeader, Int)]] =
    aggregateHeaderE(_.getHeightedBlockHeaders(fromTs, toTs))(_ ++ _)

  def getHashesAfter(locator: BlockHash): IOResult[AVector[BlockHash]] =
    getHashChain(locator).getHashesAfter(locator)

  def getPredecessor(hash: BlockHash, height: Int): IOResult[BlockHash] =
    getHashChain(hash).getPredecessor(hash, height)

  def chainBack(hash: BlockHash, heightUntil: Int): IOResult[AVector[BlockHash]] =
    getHashChain(hash).chainBack(hash, heightUntil)

  def getState(hash: BlockHash): IOResult[BlockState] =
    getHashChain(hash).getState(hash)

  def getStateUnsafe(hash: BlockHash): BlockState =
    getHashChain(hash).getStateUnsafe(hash)

  def getHeight(hash: BlockHash): IOResult[Int] =
    getHashChain(hash).getHeight(hash)

  def getHeightUnsafe(hash: BlockHash): Int =
    getHashChain(hash).getHeightUnsafe(hash)

  def getWeight(hash: BlockHash): IOResult[Weight] =
    getHashChain(hash).getWeight(hash)

  def getWeightUnsafe(hash: BlockHash): Weight =
    getHashChain(hash).getWeightUnsafe(hash)

  def getBlockHashSlice(hash: BlockHash): IOResult[AVector[BlockHash]] =
    getHashChain(hash).getBlockHashSlice(hash)

  /* BlockHeader apis */

  protected def getHeaderChain(from: GroupIndex, to: GroupIndex): BlockHeaderChain

  def getHeaderChain(chainIndex: ChainIndex): BlockHeaderChain =
    getHeaderChain(chainIndex.from, chainIndex.to)

  def getHeaderChain(header: BlockHeader): BlockHeaderChain =
    getHeaderChain(header.chainIndex)

  def getHeaderChain(hash: BlockHash): BlockHeaderChain =
    getHeaderChain(ChainIndex.from(hash))

  def getBlockHeader(hash: BlockHash): IOResult[BlockHeader] =
    getHeaderChain(hash).getBlockHeader(hash)

  def getBlockHeaderUnsafe(hash: BlockHash): BlockHeader =
    getHeaderChain(hash).getBlockHeaderUnsafe(hash)

  def add(header: BlockHeader): IOResult[Unit]

  def getHashes(chainIndex: ChainIndex, height: Int): IOResult[AVector[BlockHash]] =
    getHeaderChain(chainIndex).getHashes(height)

  def getMaxHeight(chainIndex: ChainIndex): IOResult[Int] =
    getHeaderChain(chainIndex).maxHeight

  /* BlockChain apis */

  protected def getBlockChain(from: GroupIndex, to: GroupIndex): BlockChain

  def getBlockChain(chainIndex: ChainIndex): BlockChain =
    getBlockChain(chainIndex.from, chainIndex.to)

  def getBlockChain(block: Block): BlockChain = getBlockChain(block.chainIndex)

  def getBlockChain(hash: BlockHash): BlockChain = {
    getBlockChain(ChainIndex.from(hash))
  }

  def getBlockUnsafe(hash: BlockHash): Block = {
    getBlockChain(hash).getBlockUnsafe(hash)
  }

  def getBlock(hash: BlockHash): IOResult[Block] = {
    getBlockChain(hash).getBlock(hash)
  }

  def add(block: Block): IOResult[Unit]
}
