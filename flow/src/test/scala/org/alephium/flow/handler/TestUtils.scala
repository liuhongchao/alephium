package org.alephium.flow.handler

import java.nio.file.Path

import akka.actor.ActorSystem
import akka.testkit.TestProbe

import org.alephium.io.IOUtils
import org.alephium.protocol.config.BrokerConfig
import org.alephium.protocol.model.ChainIndex
import org.alephium.util.{ActorRefT, Files => AFiles}

object TestUtils {
  case class AllHandlerProbs(flowHandler: TestProbe,
                             txHandler: TestProbe,
                             blockHandlers: Map[ChainIndex, TestProbe],
                             headerHandlers: Map[ChainIndex, TestProbe])

  def createBlockHandlersProbe(implicit brokerConfig: BrokerConfig,
                               system: ActorSystem): (AllHandlers, AllHandlerProbs) = {
    val flowProbe   = TestProbe()
    val flowHandler = ActorRefT[FlowHandler.Command](flowProbe.ref)
    val txProbe     = TestProbe()
    val txHandler   = ActorRefT[TxHandler.Command](txProbe.ref)
    val blockHandlers = (for {
      from <- 0 until brokerConfig.groups
      to   <- 0 until brokerConfig.groups
      chainIndex = ChainIndex.unsafe(from, to)
      if chainIndex.relateTo(brokerConfig)
    } yield {
      val probe = TestProbe()
      chainIndex -> (ActorRefT[BlockChainHandler.Command](probe.ref) -> probe)
    }).toMap
    val headerHandlers = (for {
      from <- 0 until brokerConfig.groups
      to   <- 0 until brokerConfig.groups
      chainIndex = ChainIndex.unsafe(from, to)
      if !chainIndex.relateTo(brokerConfig)
    } yield {
      val probe = TestProbe()
      chainIndex -> (ActorRefT[HeaderChainHandler.Command](probe.ref) -> probe)
    }).toMap
    val allHandlers = AllHandlers(flowHandler,
                                  txHandler,
                                  blockHandlers.view.mapValues(_._1).toMap,
                                  headerHandlers.view.mapValues(_._1).toMap)
    val allProbes = AllHandlerProbs(flowProbe,
                                    txProbe,
                                    blockHandlers.view.mapValues(_._2).toMap,
                                    headerHandlers.view.mapValues(_._2).toMap)
    allHandlers -> allProbes
  }

  // remove all the content under the path; the path itself would be kept
  def clear(path: Path): Unit = {
    if (path.startsWith(AFiles.tmpDir)) {
      IOUtils.clearUnsafe(path)
    } else throw new RuntimeException("Only files under tmp dir could be cleared")
  }
}